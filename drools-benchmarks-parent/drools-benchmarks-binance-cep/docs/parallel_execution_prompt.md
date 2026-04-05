# Prompt: Parallel KieSession Benchmark Module

## Goal

Build a **parallel rule execution benchmark** inside the `incubator-kie-benchmarks` repo that splits the 110-rule [taxonomy.drl](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/rules/taxonomy.drl) into structurally-safe parallel clusters, runs each in a separate KieSession, and compares throughput against the existing single-session baseline benchmark.

---

## Context & Architecture

### The problem
Drools evaluates rules sequentially in a single KieSession. For a 110-rule CEP taxonomy processing ~67K Binance market events, we want to parallelize rule evaluation by splitting rules into independent groups that can fire concurrently across multiple KieSessions.

### Correctness constraint
Rules have structural dependencies through forward chaining (Rule A's RHS inserts a fact that Rule B's LHS consumes). Rules with such dependencies **must not run in parallel** — they must execute in phased order. Only rules within the same structural phase (no inter-dependencies) can be parallelized.

### Two inputs define the partitioning
1. **DRL file** ([taxonomy.drl](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/rules/taxonomy.drl)) → parsed to extract rule inputs/outputs, then the dependency graph is built and stratified into sequential **phases** (using the existing [DrlRuleParser](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/DrlRuleParser.java#58-234) + [DependencyGraphBuilder](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/DependencyGraphBuilder.java#18-125) + [Stratifier](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/Stratifier.java#51-294) from the drools repo)
2. **Infomap .ftree file** (`binance_rule_graph_new.net(2).ftree`) → provides community-detection clusters that group rules by information flow affinity

### How they combine
- **Phases** (from structural analysis) define the hard execution order — Phase N must complete before Phase N+1 starts
- **Infomap clusters** are used as a **grouping heuristic within each phase** — rules in the same phase AND same Infomap cluster go into the same KieSession
- If Infomap puts two structurally-dependent rules in the same cluster but different phases, the **phase ordering overrides** Infomap

### Execution flow per event

```
MarketEvent arrives
  │
  Phase 1 (parallel KieSessions):
  │  Session-1A: [rules in Phase 1 ∩ Cluster X]  ─┐
  │  Session-1B: [rules in Phase 1 ∩ Cluster Y]   ├── concurrent
  │  Session-1C: [rules in Phase 1 ∩ Cluster Z]  ─┘
  │  ← barrier: wait for all Phase 1 sessions ←
  │
  Phase 2 (parallel KieSessions):
  │  Session-2A: [rules in Phase 2 ∩ Cluster X]  ─┐
  │  Session-2B: [rules in Phase 2 ∩ Cluster Y]  ─┘ concurrent
  │  ← barrier ←
  │  ... until all phases complete
```

### Shared state between phases
Use a **SharedFactStore** (`ConcurrentHashMap<String, Object>` keyed by fact type + symbol). After each phase completes, extract modified state facts and publish them. Before the next phase fires, inject the latest shared state into each session. This is safe because each state fact type has a single-writer cluster.

---

## What to Build

### New package: `org.kie.benchmark.binance.parallel`

#### 1. `FtreeParser.java`
Parses the Infomap [.ftree](file:///home/maheshdila/Downloads/binance_rule_graph_new.net%282%29.ftree) file and returns a `Map<String, Integer>` of rule name → cluster ID.

**Input**: path to [.ftree](file:///home/maheshdila/Downloads/binance_rule_graph_new.net%282%29.ftree) file
**Output**: `Map<String, Integer>` (e.g., `{"B12_TradeActiveBookSilent" → 1, "K76_Beta_SpreadVelocity" → 2, ...}`)

Parse lines matching the pattern: `<path> <flow> "<ruleName>" <nodeId>` — extract the top-level module from the path (first number before the first colon) and the quoted rule name.

#### 2. `ClusterPartitioner.java`
The core orchestration planner. Takes:
- The DRL file content (String)
- The [.ftree](file:///home/maheshdila/Downloads/binance_rule_graph_new.net%282%29.ftree) cluster assignments

Does:
1. Parses DRL using [DrlRuleParser](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/DrlRuleParser.java#58-234) (reuse from drools repo — see below)
2. Builds [DependencyGraphBuilder](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/DependencyGraphBuilder.java#18-125) → gets structural edges
3. Runs [Stratifier](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/Stratifier.java#51-294) → gets execution phases
4. Merges phases with Infomap clusters → produces `PartitionPlan`

**Output**: `PartitionPlan` containing:
```java
public class PartitionPlan {
    List<Phase> phases;  // ordered list of sequential phases
    
    public static class Phase {
        int phaseNumber;
        List<SessionGroup> groups;  // can run in parallel within this phase
    }
    
    public static class SessionGroup {
        int clusterId;           // Infomap cluster
        int phaseNumber;
        List<String> ruleNames;  // rules assigned to this session
        Set<String> requiredInputFacts;   // facts this group needs injected
        Set<String> producedOutputFacts;  // facts this group will produce
        String drlContent;       // generated DRL substring for this group
    }
}
```

Key logic: For each rule, assign it to [(phase, cluster)](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/WaltzDbAnalyzer.java#80-106). If a rule isn't in any Infomap cluster (uncaptured), assign it to a catch-all group.

#### 3. `DrlSplitter.java`
Generates per-cluster DRL file content. Given the original DRL and a list of rule names for a group:
- Keep the `package`, `import`, `declare`, and `function` blocks (shared across all splits)
- Include only the rules assigned to that group

#### 4. `SharedFactStore.java`
Thread-safe store for cross-phase state sharing:
```java
public class SharedFactStore {
    // key: "FeedHealth:BTCUSDT", value: the fact object
    private final ConcurrentHashMap<String, Object> store;
    
    void publish(String factType, String symbol, Object fact);
    <T> T read(String factType, String symbol, Class<T> clazz);
    Collection<Object> getAllForSymbol(String symbol);
}
```

#### 5. `ParallelRuleOrchestrator.java`
The parallel execution engine:
- Takes a `PartitionPlan` and creates `KieBase` + `KieSession` per `SessionGroup`
- For each incoming `MarketEvent`:
  1. Iterate through phases sequentially
  2. Within each phase, submit all session groups to a `ForkJoinPool` in parallel
  3. After each phase, extract modified facts → publish to `SharedFactStore`
  4. Before next phase, inject latest facts from store
- Returns total rules fired

#### 6. `BinanceParallelBenchmark.java`
JMH benchmark class (modeled after existing [BinanceFullDatasetBenchmark.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/BinanceFullDatasetBenchmark.java)):
- `@Setup(Level.Trial)`: Parse DRL + .ftree → build `PartitionPlan` → create `ParallelRuleOrchestrator`
- `@Benchmark`: Replay all 67K events through the parallel orchestrator
- `@TearDown`: Print comparison metrics vs. single-session baseline
- JMH params: `@Param({"1", "2", "4"})` for thread pool size

Also include a [main()](file:///home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/WaltzDbAnalyzer.java#80-106) method for quick non-JMH testing (like the existing benchmarks have).

---

## Reusable Components

### From `incubator-kie-drools` (the other repo)

The following classes are in `drools-impact-analysis-parser` at:
`/home/maheshdila/mahesh/research/incubator-kie-drools/drools-impact-analysis/drools-impact-analysis-parser/src/main/java/org/drools/impact/research/`

| Class | What to reuse |
|---|---|
| `DrlRuleParser.java` | Parse DRL → `List<RuleMeta>` (rule name, inputs, outputs) |
| `RuleMeta.java` | Data class for rule metadata |
| `DependencyGraphBuilder.java` | Build directed graph of rule dependencies |
| `Stratifier.java` | Compute execution phases via topological sort + SCC |
| `ForwardChainFinder.java` | Find transitive forward chain from entry fact type |

> [!IMPORTANT]
> These classes live in a different repo. You have two options:
> **Option A (recommended)**: Copy these 5 files into the benchmarks repo under `org.kie.benchmark.binance.analysis` package. They only depend on `jgrapht-core` and `drools-drl-parser` (both already available in the benchmarks classpath).
> **Option B**: Add `drools-impact-analysis-parser` as a Maven dependency. This requires the drools repo to be built first (`mvn install -pl drools-impact-analysis/drools-impact-analysis-parser`).

### From `incubator-kie-benchmarks` (this repo)

All files under:
`/home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/`

| Class | Reuse how |
|---|---|
| `BinanceFullDatasetBenchmark.java` | Template for the JMH benchmark structure |
| `BinanceRulesProvider.java` | Reuse `createSession()` pattern; modify to accept DRL substrings |
| `BinanceEventProvider.java` | Reuse as-is for loading the 67K event dataset |
| `EventReplayController.java` | Adapt for multi-session replay (one controller per session) |
| `model/*` (14 files) | Reuse all fact classes as-is |

### Input files

| File | Path |
|---|---|
| DRL rules | `/home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/rules/taxonomy.drl` |
| Infomap clusters | `/home/maheshdila/Downloads/binance_rule_graph_new.net(2).ftree` (copy to `resources/clusters/`) |

---

## Verification Plan

### Step 1: Correctness check
Run both single-session and parallel benchmarks on the same 67K-event dataset. Compare:
- Total rules fired (must be **identical** — this proves correctness)
- Final state of all singleton facts per symbol (must match)

### Step 2: Performance comparison (JMH)
```bash
# Baseline (existing single-session)
mvn exec:java -Dexec.mainClass="org.kie.benchmark.binance.BinanceFullDatasetBenchmark"

# Parallel (new)
mvn exec:java -Dexec.mainClass="org.kie.benchmark.binance.parallel.BinanceParallelBenchmark"
```

Compare: events/sec throughput, p50/p99 latency per event, total wall-clock time.

### Step 3: Thread scaling
Run parallel benchmark with 1, 2, 4, 8 threads to measure scaling behavior.

---

## File Structure (what to create)

```
drools-benchmarks-binance-cep/src/main/
├── java/org/kie/benchmark/binance/
│   ├── parallel/
│   │   ├── FtreeParser.java              [NEW]
│   │   ├── ClusterPartitioner.java       [NEW]
│   │   ├── DrlSplitter.java             [NEW]
│   │   ├── SharedFactStore.java          [NEW]
│   │   ├── ParallelRuleOrchestrator.java [NEW]
│   │   ├── PartitionPlan.java            [NEW]
│   │   └── BinanceParallelBenchmark.java [NEW]
│   ├── analysis/                          (copied from drools repo)
│   │   ├── DrlRuleParser.java            [COPY]
│   │   ├── RuleMeta.java                 [COPY]
│   │   ├── DependencyGraphBuilder.java   [COPY]
│   │   ├── Stratifier.java              [COPY]
│   │   └── ForwardChainFinder.java       [COPY]
│   └── (existing files unchanged)
└── resources/
    ├── rules/taxonomy.drl                 (existing)
    └── clusters/binance_rule_graph.ftree  [NEW - copy from Downloads]
```
