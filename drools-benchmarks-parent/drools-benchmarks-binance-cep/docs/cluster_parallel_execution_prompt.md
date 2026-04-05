# Cluster-Level Parallel KieSession — Implementation Prompt

## Objective

Build a **cluster-level parallel rule execution system** in `incubator-kie-benchmarks` (`binance_stream_parallel` branch). Split the 110-rule [taxonomy.drl](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/rules/taxonomy.drl) into **6 Infomap cluster sessions + 1 fault-tolerant fallback session**. Route events to cluster sessions via **LHS constraint parsing**, and always send to the fallback for safety. Each session runs on its own thread. Generate detailed documentation.

**Preserve all existing code** — symbol-level parallelism must not be affected.

---

## Architecture

```
                   taxonomy.drl (110 rules)
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
      Infomap .ftree              DrlRuleParser
      (47 runtime rules)         (all 110 rules)
              │                       │
              ▼                       ▼
   ┌──────────────────┐   ┌──────────────────────┐
   │ 6 Cluster Sessions│   │ 1 Fallback Session    │
   │ (47 + duplicated  │   │ (63 uncaptured rules) │
   │  bridge rules)    │   │ "catches everything"  │
   └──────────────────┘   └──────────────────────┘
              │                       │
              └───────────┬───────────┘
                          ▼
              ┌──────────────────────┐
              │  LHS Routing Table   │
              │  eventType → clusters│
              │  + ALWAYS → fallback │
              └──────────────────────┘
```

### Barrier-free execution flow

```
      67K MarketEvents (time-ordered)
               │
               ▼
      Main thread (enqueue only, ~ns/event)
               │
       ┌───────┼───────┬──────────┬──────────┐
       ▼       ▼       ▼          ▼          ▼
    Queue-1  Queue-2  Queue-3   Queue-4    Queue-F
       │       │       │          │          │
       ▼       ▼       ▼          ▼          ▼
    Worker-1 Worker-2 Worker-3  Worker-4   Worker-F
    (Cluster1)(Cluster2)(Cluster3)(Cluster4)(Fallback)
    Feed     Depth/   Trade     Mode       63 rules
    Health   Spread   Alpha     Transitions
       │       │       │          │          │
       └───────┴───────┴──────────┴──────────┘
                       │
             Sync only at END (poison pill + Future.get())
                       │
                   Total fired
```

Clusters 5, 6 don't consume TRADE events → not enqueued. Fallback **always** receives every event.

### Why barrier-free? (no per-event synchronization)

1. **Self-contained clusters**: Bridge rule duplication ensures each cluster can independently produce any fact it needs. No cluster ever waits on another cluster's output.
2. **No cross-session state**: Each KieSession has its own Rete network, pseudo-clock, and working memory. Zero shared mutable state.
3. **Event ordering preserved**: Each session's BlockingQueue is FIFO — events arrive in the same time-order as the baseline.
4. **Performance**: Per-event `invokeAll()` would incur ~5-50μs thread dispatch overhead × 67K events = 335ms-3.3s of pure synchronization cost, plus ~335K thread context switches. BlockingQueue workers eliminate this entirely.
5. **Correctness metric unchanged**: Final total rules fired and final RiskSignal sets are compared against the single-session baseline (not per-event counts).

---

## Cluster Building Algorithm

### Step 1: Build Infomap Clusters (47 rules)

Parse [.ftree](file:///home/maheshdila/Downloads/binance_rule_graph_new.net%282%29.ftree) → assign 47 runtime-traced rules to clusters 1-6. For each cluster, apply **bridge rule duplication**: if a rule consumes a fact not produced within the cluster, duplicate the producer rule into the cluster.

```java
// Bridge duplication (recursive with depth cap = 3)
for (SelfContainedCluster cluster : clusters) {
    Queue<String> missingFacts = findMissingInputFacts(cluster);
    int depth = 0;
    while (!missingFacts.isEmpty() && depth < 3) {
        String fact = missingFacts.poll();
        List<RuleMeta> producers = findProducersOf(fact);
        for (RuleMeta p : producers) {
            if (!cluster.contains(p)) {
                cluster.addDuplicated(p);
                // Check if duplicated rule needs more facts
                missingFacts.addAll(findNewMissingFacts(p, cluster));
            }
        }
        depth++;
    }
}
```

### Step 2: Build Fallback Session (remaining rules)

All rules **not** in any Infomap cluster go into the fallback:

```java
Set<String> allClusterRules = clusters.stream()
    .flatMap(c -> c.getOriginalRules().stream())
    .collect(toSet());

List<String> fallbackRules = allRules.stream()
    .map(RuleMeta::getRuleName)
    .filter(name -> !allClusterRules.contains(name))
    .toList();
// ≈ 63 rules
```

The fallback session contains ALL 63 uncaptured rules with the full DRL preamble. No duplication needed — it gets all events, and Rete handles pattern matching internally.

### Step 3: Build LHS Routing Table

Parse each cluster rule's `when` clause to determine which `MarketEvent.eventType` values it responds to:

```java
public class LhsConstraintParser {
    // Regex: eventType\s*==\s*"(\w+)"
    private static final Pattern EVENT_TYPE_PATTERN =
        Pattern.compile("eventType\\s*==\\s*\"(\\w+)\"");
    
    /**
     * For a given rule, extract which MarketEvent.eventType values
     * would match its LHS pattern.
     * Returns empty set if rule doesn't consume MarketEvent,
     * returns ALL_TYPES if rule consumes MarketEvent without eventType filter.
     */
    public static Set<String> extractEventTypes(String ruleName, String drlContent) {
        String whenClause = extractWhenClause(ruleName, drlContent);
        if (whenClause == null || !whenClause.contains("MarketEvent")) {
            return Collections.emptySet();  // doesn't consume MarketEvent
        }
        Matcher m = EVENT_TYPE_PATTERN.matcher(whenClause);
        Set<String> types = new LinkedHashSet<>();
        while (m.find()) types.add(m.group(1));
        return types.isEmpty() ? ALL_EVENT_TYPES : types;
    }
}
```

Aggregate per cluster:
```java
Map<String, List<Integer>> routingTable = new LinkedHashMap<>();
// ALL_EVENT_TYPES = {"TRADE", "DEPTH", "MARK", "INDEX", "HEARTBEAT"}

for (SelfContainedCluster cluster : clusters) {
    Set<String> clusterEventTypes = new LinkedHashSet<>();
    for (String rule : cluster.getAllRules()) {
        clusterEventTypes.addAll(LhsConstraintParser.extractEventTypes(rule, drlContent));
    }
    for (String eventType : clusterEventTypes) {
        routingTable.computeIfAbsent(eventType, k -> new ArrayList<>())
            .add(cluster.getClusterId());
    }
}
// Fallback: receives ALL events (added separately in orchestrator)
```

Expected routing table:
```
TRADE     → [1, 2, 3, 4] + Fallback
DEPTH     → [1, 2, 4]    + Fallback
MARK      → [1, 2, 4]    + Fallback
INDEX     → [1, 4]       + Fallback
HEARTBEAT → [1, 4]       + Fallback
```

---

## Existing Code (DO NOT MODIFY)

| File | Role |
|---|---|
| [parallel/ParallelRuleOrchestrator.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/ParallelRuleOrchestrator.java) | Symbol-level parallelism |
| [parallel/BinanceParallelBenchmark.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/BinanceParallelBenchmark.java) | Symbol-level JMH benchmark |
| [parallel/FtreeParser.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/FtreeParser.java) | .ftree → rule→cluster map |
| [parallel/DrlSplitter.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/DrlSplitter.java) | DRL subsetting + `@expires`/`@role` sanitization |
| [parallel/SharedFactStore.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/SharedFactStore.java) | Cross-phase store (unused, keep) |
| [parallel/PartitionPlanDumper.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/PartitionPlanDumper.java) | Dumps plan to files |
| `analysis/*` (5 files) | DrlRuleParser, RuleMeta, DependencyGraphBuilder, Stratifier, ForwardChainFinder |
| `provider/*`, `util/*`, `model/*` | Providers, controllers, fact POJOs |
| [BinanceFullDatasetBenchmark.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/BinanceFullDatasetBenchmark.java) | Single-session baseline |

---

## Files to MODIFY

### [ClusterPartitioner.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/ClusterPartitioner.java)

Add method `buildSelfContainedClusters()` implementing:
1. Infomap cluster assignment (47 rules)
2. Bridge rule duplication per cluster
3. Fallback session construction (remaining rules)
4. LHS routing table construction

Keep existing [buildPlan()](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/ClusterPartitioner.java#61-139) untouched.

### [PartitionPlan.java](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/PartitionPlan.java)

Add inner classes (keep existing [Phase](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/PartitionPlan.java#71-83)/[SessionGroup](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/PartitionPlan.java#87-113) untouched):

```java
public static class SelfContainedCluster {
    int clusterId;
    String name;
    List<String> originalRules;
    List<String> duplicatedRules;
    List<String> allRules;
    Set<String> consumedEventTypes;
    Set<String> inputFacts;
    Set<String> outputFacts;
    String drlContent;
}

public static class ClusterPlan {
    List<SelfContainedCluster> clusters;
    SelfContainedCluster fallbackCluster;    // clusterId = -1
    Map<String, List<Integer>> routingTable;
}
```

---

## Files to CREATE

### `LhsConstraintParser.java`

Utility class that extracts `eventType` constraints from DRL `when` clauses. Used by [ClusterPartitioner](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/ClusterPartitioner.java#42-157) to build the routing table.

### `ClusterParallelOrchestrator.java`

Uses **barrier-free per-session `BlockingQueue` workers** instead of per-event `invokeAll()`. Each session has a long-lived worker thread that drains its queue independently. The main thread only enqueues events (nanoseconds per event). Synchronization happens once, at the end.

```java
public class ClusterParallelOrchestrator {
    private static final MarketEvent POISON_PILL = new MarketEvent("__STOP__", -1L, "__STOP__", 0, 0, 0, "");
    private static final int FALLBACK_ID = -1;

    private final Map<Integer, KieSession> clusterSessions;  // 1-6
    private final KieSession fallbackSession;                 // -1
    private final Map<String, List<Integer>> routingTable;
    private final Map<Integer, BlockingQueue<MarketEvent>> eventQueues;
    private final ExecutorService threadPool;

    // Constructor: build persistent KieSessions + bootstrap + worker threads
    public ClusterParallelOrchestrator(
            PartitionPlan.ClusterPlan plan, int poolSize, Set<String> symbols) {
        threadPool = Executors.newFixedThreadPool(poolSize);
        clusterSessions = new LinkedHashMap<>();
        eventQueues = new LinkedHashMap<>();

        for (SelfContainedCluster c : plan.getClusters()) {
            KieSession session = buildSessionFromDrl(c.getDrlContent(), c.getClusterId());
            insertBootstrapFacts(session, symbols);
            session.fireAllRules();
            clusterSessions.put(c.getClusterId(), session);
            eventQueues.put(c.getClusterId(), new LinkedBlockingQueue<>());
        }

        SelfContainedCluster fb = plan.getFallbackCluster();
        fallbackSession = buildSessionFromDrl(fb.getDrlContent(), FALLBACK_ID);
        insertBootstrapFacts(fallbackSession, symbols);
        fallbackSession.fireAllRules();
        eventQueues.put(FALLBACK_ID, new LinkedBlockingQueue<>());

        routingTable = plan.getRoutingTable();
    }

    public int replayEvents(List<MarketEvent> events) {
        // Start long-lived worker threads (one per session)
        Map<Integer, Future<Integer>> futures = new LinkedHashMap<>();
        for (var entry : getAllSessions().entrySet()) {
            int cid = entry.getKey();
            KieSession session = entry.getValue();
            BlockingQueue<MarketEvent> queue = eventQueues.get(cid);
            futures.put(cid, threadPool.submit(() -> drainAndFire(session, queue)));
        }

        // Main thread: route events to queues (very fast, ~ns per enqueue)
        for (MarketEvent event : events) {
            List<Integer> targets = routingTable.getOrDefault(
                event.getEventType(), Collections.emptyList());
            for (int cid : targets) {
                eventQueues.get(cid).put(event);
            }
            eventQueues.get(FALLBACK_ID).put(event);  // always
        }

        // Send poison pills to all queues
        for (var q : eventQueues.values()) q.put(POISON_PILL);

        // Collect results (only sync point)
        int totalFired = 0;
        for (Future<Integer> f : futures.values()) {
            totalFired += f.get();
        }
        return totalFired;
    }

    /** Worker loop: drain queue, advance clock, insert, fire. */
    private int drainAndFire(KieSession session, BlockingQueue<MarketEvent> queue) {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;
        while (true) {
            MarketEvent event = queue.take();
            if (event == POISON_PILL) break;
            long current = clock.getCurrentTime();
            if (event.getTsMs() > current) {
                clock.advanceTime(event.getTsMs() - current, TimeUnit.MILLISECONDS);
            }
            session.insert(event);
            fired += session.fireAllRules();
        }
        return fired;
    }

    private KieSession buildSessionFromDrl(String drl, int clusterId) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/cluster_" + clusterId + ".drl", drl);
        ReleaseId rid = ks.newReleaseId("org.kie.cluster", "c-" + clusterId, "1.0");
        kfs.generateAndWritePomXML(rid);
        ks.newKieBuilder(kfs).buildAll();
        KieContainer kc = ks.newKieContainer(rid);
        KieSessionConfiguration cfg = ks.newKieSessionConfiguration();
        cfg.setOption(ClockTypeOption.PSEUDO);
        return kc.newKieSession(cfg);
    }

    public void dispose() {
        threadPool.shutdown();
        clusterSessions.values().forEach(KieSession::dispose);
        fallbackSession.dispose();
    }
}
```

### `BinanceClusterBenchmark.java`

JMH benchmark + correctness test + documentation:

```java
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 30)
@Measurement(iterations = 3, time = 60)
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
public class BinanceClusterBenchmark {
    @Param({"4", "8"})
    private int poolSize;
    // ... same JMH structure as BinanceParallelBenchmark

    public static void main(String[] args) {
        // Step 1: Single-session baseline
        // Step 2: Cluster-parallel execution
        // Step 3: Correctness comparison (RiskSignals)
        // Step 4: Performance comparison table
        // Step 5: Fallback analysis (what % of rules fired in fallback vs clusters)
        // Step 6: Generate documentation report
    }
}
```

---

## Documentation Report

Generate `output/cluster_parallel_report.md` in [main()](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/java/org/kie/benchmark/binance/parallel/BinanceParallelBenchmark.java#179-285):

```markdown
# Cluster-Level Parallel Execution Report

## 1. Methodology
- Hybrid static-dynamic partitioning
- 6 Infomap clusters + 1 fault-tolerant fallback
- LHS-parsed event-type routing

## 2. Cluster Configuration
| Cluster | Name | Original | Duplicated | Total | Event Types |
|---------|------|----------|------------|-------|-------------|
| 1 | Feed Health | 9 | +2 | 11 | ALL |
| 2 | Depth/Spread | 26 | +2 | 28 | TRADE, DEPTH, MARK |
| ... | ... | ... | ... | ... | ... |
| F | Fallback | 63 | 0 | 63 | ALL (fault-tolerant) |

- Infomap coverage: 47/110 rules (43%)
- Bridge rules duplicated: ~10 (21% overhead on clusters)
- Fallback coverage: 63/110 rules (57%)

## 3. Routing Table
| Event Type | Cluster Sessions | + Fallback | Total Sessions |
|------------|-----------------|------------|----------------|
| TRADE | [1,2,3,4] | always | 5 |
| DEPTH | [1,2,4] | always | 4 |
| MARK | [1,2,4] | always | 4 |

## 4. Performance Results
| Metric | Single | Cluster (4T) | Cluster (8T) | Symbol (10T) |
|--------|--------|-------------|-------------|-------------|
| Events/sec | X | Y | Z | W |
| Duration (ms) | ... | ... | ... | ... |
| Speedup | 1.00x | ?x | ?x | ?x |

## 5. Correctness Validation
- Baseline rules fired: X
- Cluster + Fallback fired: Y (≥ X due to duplication)
- RiskSignal comparison: PASS/FAIL

## 6. Fallback Analysis (key research metric)
| Metric | Value |
|--------|-------|
| Fallback rules fired | N |
| Total rules fired | M |
| Fallback % of total | N/M × 100% |
| Unique rules fired in fallback | K out of 63 |

> If fallback fires <5% of total activations, Infomap clustering
> captured the dominant execution paths. If >20%, the clustering
> missed significant rule interactions and needs refinement.

## 7. Per-Cluster Breakdown
| Cluster | Rules | Events Recv | Fired | Avg μs/event |
|---------|-------|-------------|-------|--------------|
| 1 | 11 | 67K | ... | ... |
| ... | ... | ... | ... | ... |
| F | 63 | 67K | ... | ... |

## 8. Conclusions
- Summary of findings
- Comparison: cluster-level vs symbol-level parallelism
- Infomap quality assessment (based on fallback %)
```

---

## File Structure

```
parallel/
├── BinanceParallelBenchmark.java         (DO NOT MODIFY)
├── ParallelRuleOrchestrator.java         (DO NOT MODIFY)
├── ClusterPartitioner.java               (MODIFY — add buildSelfContainedClusters)
├── PartitionPlan.java                    (MODIFY — add SelfContainedCluster, ClusterPlan)
├── FtreeParser.java                      (DO NOT MODIFY)
├── DrlSplitter.java                      (DO NOT MODIFY)
├── SharedFactStore.java                  (DO NOT MODIFY)
├── PartitionPlanDumper.java              (DO NOT MODIFY)
├── LhsConstraintParser.java             (NEW — extracts eventType from when clauses)
├── ClusterParallelOrchestrator.java      (NEW — multi-threaded cluster + fallback execution)
└── BinanceClusterBenchmark.java          (NEW — JMH + correctness + documentation)
```

## Resources (classpath)
- [/rules/taxonomy.drl](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/rules/taxonomy.drl)
- `/clusters/binance_rule_graph.ftree`
- Dataset: `run_20260311_1340_10sym` (~67K events)
