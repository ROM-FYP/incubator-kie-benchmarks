# Parallel KieSession Benchmark — Architecture & Usage Guide

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [File Layout](#file-layout)
3. [How It Works](#how-it-works)
4. [Commands](#commands)
5. [Expected Outputs](#expected-outputs)
6. [How to Change Inputs](#how-to-change-inputs)
7. [Inspecting Stratified DRLs](#inspecting-stratified-drls)

---

## Architecture Overview

### Symbol-Level Parallelism (Execution Engine)

```
           1.6M MarketEvents (time-ordered)
                      │
              partition by symbol
         ┌──────┬─────┼─────┬──────┐
         ▼      ▼     ▼     ▼      ▼
      BTCUSDT ETHUSDT SOLUSDT ...  ARBUSDT
      407K ev  609K ev 150K ev     25K ev
         │      │     │     │      │
         ▼      ▼     ▼     ▼      ▼
      Session  Session Session .. Session   ← each has FULL 110-rule DRL
      (all 110  (all 110 rules)   (all 110
       rules)                      rules)
         │      │     │     │      │
         └──────┴─────┼─────┴──────┘
                      ▼
              ExecutorService(poolSize)
                      │
                      ▼
              Sum rule-fire counts
```

> [!IMPORTANT]
> **Why symbol-level, not rule-cluster?** All 96 active rules join on
> `symbol == $sym`. Zero cross-symbol state exists. Splitting by rule
> cluster creates 20 sessions per event (20x overhead). Splitting by
> symbol creates 10 independent sessions processing disjoint event
> streams — near-linear speedup.

### Rule Dependency Analysis (Static Analysis Layer)

The static analysis layer (Stratifier + Infomap) is preserved for research
documentation but is NOT used for the execution engine:

```
   taxonomy.drl → DrlRuleParser → DependencyGraphBuilder → Stratifier → Phases
   .ftree → FtreeParser → Infomap Clusters
   Phases × Clusters → ClusterPartitioner → PartitionPlan (reference only)
```

---

## File Layout

```
drools-benchmarks-binance-cep/src/main/
├── java/org/kie/benchmark/binance/
│   ├── parallel/
│   │   ├── ParallelRuleOrchestrator.java    Symbol-level parallel engine
│   │   ├── BinanceParallelBenchmark.java    JMH benchmark + main() test
│   │   ├── PartitionPlanDumper.java         CLI: dump stratified DRLs
│   │   ├── ClusterPartitioner.java          Merge phases + clusters → plan
│   │   ├── PartitionPlan.java               Data model (Phase → SessionGroup)
│   │   ├── FtreeParser.java                 Parse .ftree → rule→cluster map
│   │   ├── DrlSplitter.java                 Split DRL per group
│   │   └── SharedFactStore.java             Thread-safe state store
│   ├── analysis/                            Ported from drools repo
│   │   ├── DrlRuleParser.java               DRL → List<RuleMeta>
│   │   ├── RuleMeta.java                    Rule metadata (inputs/outputs)
│   │   ├── DependencyGraphBuilder.java      Directed dependency graph
│   │   ├── Stratifier.java                  SCC + topo-sort → phases
│   │   └── ForwardChainFinder.java          BFS forward chain traversal
│   ├── model/                               13 fact classes (unchanged)
│   └── provider/                            Event + rules providers (unchanged)
└── resources/
    ├── rules/taxonomy.drl                   110-rule DRL
    └── clusters/binance_rule_graph.ftree    Infomap .ftree
```

---

## How It Works

### Execution Engine (Symbol-Level)

1. **Load events** from `data/<datasetId>/segments/` (1.6M events, 10 symbols)
2. **Partition** events by `MarketEvent.getSymbol()`
3. **Create** one full KieSession per symbol (same 110-rule DRL as baseline)
4. **Bootstrap** each session with `RiskConfig(symbol)` + `fireAllRules()`
5. **Replay** each symbol's events in parallel via `ExecutorService(poolSize)`
6. **Sum** rule-fire counts across all symbol sessions

### Static Analysis (Reference)

1. `DrlRuleParser` → extract rule inputs/outputs
2. `DependencyGraphBuilder` → directed dependency graph
3. `Stratifier` → SCC + topological sort → execution phases
4. `FtreeParser` → Infomap community clusters
5. `ClusterPartitioner` → merge phases × clusters → `PartitionPlan`
6. `PartitionPlanDumper` → write per-group DRLs to disk

---

## Commands

All commands run from the repo root:
```
cd /home/maheshdila/mahesh/research/incubator-kie-benchmarks
```

### Compile

```bash
mvn compile -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml
```

### Quick Correctness Test (Single vs Parallel)

```bash
mvn exec:java -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml \
  -Dexec.mainClass="org.kie.benchmark.binance.parallel.BinanceParallelBenchmark"
```

### Dump & Inspect Stratified DRLs

```bash
mvn exec:java -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml \
  -Dexec.mainClass="org.kie.benchmark.binance.parallel.PartitionPlanDumper" \
  -Dexec.args="output/stratified_drls"
```

### JMH Benchmark (Full)

```bash
mvn package -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml -DskipTests
java -jar drools-benchmarks-parent/drools-benchmarks-binance-cep/target/drools-benchmarks-binance-cep.jar \
  "BinanceParallelBenchmark" -p poolSize=1,2,4,8
```

### Single-Session Baseline

```bash
mvn exec:java -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml \
  -Dexec.mainClass="org.kie.benchmark.binance.BinanceFullDatasetBenchmark"
```

---

## Expected Outputs

### Quick Correctness Test

```
╔══════════════════════════════════════════════╗
║  Parallel KieSession Benchmark - Quick Test  ║
╚══════════════════════════════════════════════╝

Events: 1613159 | Symbols: 10

── Single-Session Baseline ──────────────────
Rules fired:  13,528,252
Duration:     67,710 ms
Throughput:   23,824.53 events/sec

── Parallel Execution ──────────────────────
[Orchestrator] Symbol-level parallelism mode
[Orchestrator] Symbols: 10 | Pool size: 2
[Orchestrator] Event distribution by symbol:
  ETHUSDT:  609,103 events
  BTCUSDT:  407,132 events
  SOLUSDT:  149,780 events
  DOGEUSDT: 110,368 events
  ...
Rules fired:  13,528,252
Duration:     44,145 ms
Throughput:   36,542.28 events/sec

── Comparison ──────────────────────────────
                          Single        Parallel
Rules fired           13,528,252      13,528,252
Duration                67,710 ms       44,145 ms
Events/sec             23,824.53       36,542.28
Speedup                  1.00x           1.53x

Correctness: ✅ PASS (rules fired match)
```

---

## How to Change Inputs

### Change DRL Rules

```bash
# Use a different DRL via system property (for PartitionPlanDumper)
-Dbinance.rules.file="/rules/demo.drl"
```

For the benchmark, edit `BinanceRulesProvider` or place DRL at `src/main/resources/rules/taxonomy.drl`.

### Change .ftree File

```bash
# Generate new .ftree: ./Infomap your_graph.net . --clu --ftree
# Copy to resources:
cp output.ftree src/main/resources/clusters/binance_rule_graph.ftree
# Or use system property with PartitionPlanDumper:
-Dbinance.ftree.file="/clusters/your_new.ftree"
```

### Change Event Dataset

```bash
-Dbinance.dataset="run_20260216_0632_10sym"
```

Place `.jsonl` segments in `src/main/resources/data/<id>/segments/`.

### Change Thread Pool Size

- **Quick test**: Edit `poolSize` in `BinanceParallelBenchmark.main()` (default: 2)
- **JMH**: `-p poolSize=1,2,4,8`

---

## Inspecting Stratified DRLs

### Method 1: Dumper CLI (Recommended)

```bash
mvn exec:java -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml \
  -Dexec.mainClass="org.kie.benchmark.binance.parallel.PartitionPlanDumper" \
  -Dexec.args="output/stratified_drls"
```

Outputs to `output/stratified_drls/`:
- `phase1_cluster1.drl`, `phase2_cluster2.drl`, ... (self-contained DRL subsets)
- `partition_summary.txt` (full plan + rule inputs/outputs/cluster IDs)

### Method 2: Programmatic

```java
String drlContent = /* load taxonomy.drl */;
InputStream ftreeIs = /* load .ftree */;
PartitionPlan plan = ClusterPartitioner.buildFromResources(drlContent, ftreeIs);
System.out.println(plan); // prints all phases, groups, rules, inputs/outputs
```

### Method 3: Quick Test stdout

`BinanceParallelBenchmark.main()` prints the full `PartitionPlan.toString()`.
