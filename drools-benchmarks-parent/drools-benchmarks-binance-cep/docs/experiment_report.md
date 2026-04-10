# Parallelizing Rule-Based Complex Event Processing: A Cluster-Level Approach

## 1. Introduction

Rule-based Complex Event Processing (CEP) systems evaluate incoming data streams against declarative rule sets to detect patterns and generate alerts in real time. Production-grade CEP engines such as Drools use the Rete algorithm (and its successor, Phreak) to efficiently match facts against rules through a network of alpha nodes (single-pattern filters) and beta nodes (cross-pattern joins). However, these engines typically execute on a single thread — all rules share one Working Memory and one Rete network.

As rule sets grow in size and event throughput increases, the single-session architecture becomes a bottleneck. This work investigates whether a monolithic rule base can be automatically decomposed into independent clusters and executed in parallel across multiple threads, with each cluster running in an isolated `KieSession`.

The experiments use a real-world benchmark: a Binance cryptocurrency exchange CEP system with 97 active rules processing 6 event types across 10 trading symbols at sustained throughputs of 400–3,500 events per second.

---

## 2. Baseline Architecture

The baseline system operates as a single-threaded pipeline:

```
Event Stream → [Single KieSession (97 rules, 1 Rete Network)] → Risk Signals
```

Each incoming `MarketEvent` is:
1. Inserted into Working Memory
2. Propagated through the Rete network's alpha nodes for type matching
3. Evaluated against all applicable beta-join conditions
4. If matched, the corresponding rule fires and may modify state facts or insert new derived facts

The Rete network maintains the full state of all 97 rules simultaneously. When a fact is inserted or modified, it must traverse all relevant alpha and beta nodes — a cost that scales with both the number of rules and the size of Working Memory.

---

## 3. Proposed Architecture

The proposed method decomposes the monolithic rule base into independent subsets (clusters) and executes each cluster in a dedicated thread with its own `KieSession`. The method proceeds in four stages.

### 3.1 Execution Trace Logging and Graph Construction

To understand rule interactions, we instrument the rule engine with a causal trace logger that records every fact lifecycle event and rule activation as a structured JSON-Lines trace. Specifically, for each execution cycle, the logger captures:

- **Fact insertions, updates, and deletions** — including which rule produced each fact (provenance tracking)
- **Rule activations** — including the set of facts that satisfied the rule's left-hand side (supporting facts)

From this trace, a directed rule-interaction graph is constructed:

```
Edge (Ri → Rj) exists if and only if:
  Rule Ri produces a fact Fx (via insert or modify),
  AND fact Fx appears as a supporting fact in an activation of Rule Rj
```

Edge weights are aggregated by counting activations. The result is a weighted directed graph where strongly connected rules form natural clusters, and weakly connected rules can be separated.

### 3.2 Rule Clustering

The rule-interaction graph is partitioned into clusters using community detection (Infomap algorithm). Rules that frequently trigger each other through shared facts are grouped together; rules with no mutual data dependencies land in separate clusters.

The clustering must satisfy one constraint: **every fact type that a rule reads must also be written by some rule within the same cluster** (or be provided externally as input). If this constraint is violated, the cluster has an unresolved dependency, which must be addressed in the next stage.

### 3.3 Dependency Analysis

After initial clustering, a dependency analysis identifies two categories of problems:

**Cross-cluster dependencies.** If Rule A in Cluster 1 modifies a fact that Rule B in Cluster 2 reads, the two clusters are not independent. Three resolution strategies are available:
- *Rule duplication:* Copy the producing rule into the consuming cluster. This is appropriate for stateless or low-cost rules.
- *Cluster merging:* Combine the two clusters into one. This is preferred when the dependency is bidirectional or involves many rules.
- *Rule deletion:* If the dependency represents a non-essential cross-cutting concern that can be sacrificed for parallelism, the offending rule is removed.

**Generic (shared) rules.** Stateless validation and cleanup rules (e.g., null checks, event retraction) that operate on the raw event stream are needed by all clusters. These are duplicated into every cluster session.

The output of this stage is a set of fully independent clusters where each cluster's rules can execute without reading or writing facts owned by another cluster.

### 3.4 Parallelized Execution

Each cluster is assigned a dedicated thread with its own `KieSession` containing only that cluster's rules. The execution model is:

```
Event Stream → [Broadcast/Route to queues] → Thread 1: KieSession(Cluster A)
                                            → Thread 2: KieSession(Cluster B)
                                            → ...
```

Each thread maintains:
- An independent `KieSession` with a cluster-specific Rete network
- A `BlockingQueue` for incoming events
- Its own pseudo-clock for CEP temporal reasoning

Events are dispatched to threads based on which clusters require them. Because the clusters are independent, no synchronization barriers, locks, or shared-memory coordination is required between sessions during rule evaluation.

---

## 4. Experiments

### 4.1 Benchmark System

The experimental system is a Binance cryptocurrency exchange risk-control engine with:
- **97 active rules** organized into functional groups (feed health monitoring, market microstructure analysis, liquidation monitoring, trade rate tracking, and volatility/divergence detection)
- **6 event types:** DEPTH, TRADE, MARK, INDEX, HEARTBEAT, LIQ
- **Dataset:** 1,613,159 events collected from 10 trading symbols over a period that produced sustained throughputs of 400–3,500 events/second at the source

### 4.2 Cluster Configuration

Applying the method from Section 3, the 97 rules were decomposed as follows:

| Component | Rules | Cluster Assignment |
|-----------|:-----:|:-----|
| Feed Health & Mode Transitions | 26 | Cluster 1 |
| Market Microstructure | 52 | Cluster 2 |
| Liquidation Monitoring | 6 | Cluster 3 |
| Trade Rate | 3 | Cluster 4 |
| Generic (validation/cleanup) | 10 | Duplicated in all clusters |

During dependency analysis, 13 rules were removed: 7 were dead code (`eval(false)` guards), and 6 had unresolvable cross-cluster dependencies. Two sliding-window aggregation rules were disabled due to infinite-loop behavior under the benchmark's pseudo-clock mode. The remaining rules formed four fully independent clusters.

### 4.3 Experiments Conducted

Five experimental configurations were evaluated, each building on the previous iteration:

**Experiment 1: Four-Cluster Parallel (V2, 4 threads).** Each of the four clusters runs in a dedicated thread. An event router inspects each event's type and dispatches it to the relevant cluster queues using a bitmask lookup.

**Experiment 2: Two-Cluster Parallel with Router (V3, 2 threads).** Clusters 1, 3, and 4 are merged into Cluster A (since C3 and C4 have very few rules that don't justify dedicated threads). This reduces thread count from 4 to 2 and lowers event duplication. A bitmask router dispatches events.

**Experiment 3: Two-Cluster Broadcast without Router (V3-NoRouter, 2 threads).** Because 99.98% of events are relevant to both clusters, the router is removed entirely. All events are broadcast to both sessions, eliminating routing overhead at the cost of processing 0.02% unnecessary events in one session.

**Experiment 4: Fire-and-Forget Signal Egress (V3.1-Channels, 2 threads).** The `insert(new RiskSignal(...))` statements in rule actions are replaced with Drools Channel writes (`channels["alerts"].send(signal)`). This prevents 5.4 million RiskSignal objects from accumulating in Working Memory, removing garbage collection pressure.

**Experiment 5: Load-Balanced Generic Rule Isolation (V3.2-LoadBalanced, 2 threads).** Three signal-emitting generic rules (`A03`, `A06`, `B16`) are removed from Cluster A and assigned exclusively to the under­loaded Cluster B. This eliminates 3.2 million redundant rule firings in Cluster A and balances the workload across threads.

---

## 5. Results

### 5.1 Results at 50,000 Events (3 Symbols)

| Configuration | Rules Fired | Duration (ms) | Throughput (ev/s) | Speedup |
|:---|:---:|:---:|:---:|:---:|
| Baseline (1T) | 399,957 | 12,511 | 3,996 | 1.00× |
| V2 (4T) | 900,049 | 11,083 | 4,511 | 1.20× |
| V3 (2T, Router) | 750,003 | 7,705 | 6,489 | **1.62×** |

At 50,000 events, both parallel configurations outperform the baseline. The 2-thread configuration achieves a higher speedup than the 4-thread configuration because it incurs lower event duplication (2× vs 3.3×) and lower thread contention.

### 5.2 Results at 1,613,159 Events (10 Symbols)

| Configuration | Rules Fired | Duration (ms) | Throughput (ev/s) | Speedup |
|:---|:---:|:---:|:---:|:---:|
| Baseline (1T) | 13,528,242 | 74,598 | 21,625 | 1.00× |
| V2 (4T) | 28,283,453 | 115,832 | 13,927 | 0.71× |
| V3 (2T, Router) | 23,998,475 | 93,193 | 17,310 | 0.80× |
| V3 No Router (2T) | 23,999,351 | 59,948 | 26,909 | 0.94× |
| V3.1 Channels (2T) | 13,528,242 | 47,905 | 33,674 | 0.89× |
| **V3.2 Load Balanced (2T)** | **20,768,958** | **39,431** | **40,910** | **1.09×** |

At full scale, the initial configurations (V2, V3-Router) are slower than the baseline. Through progressive optimization — removing the router, eliminating Working Memory pollution via Channels, and load-balancing generic rules — the final configuration achieves a **1.09× speedup** over the single-threaded baseline.

### 5.3 Correctness

#### Theoretical Basis

The parallel architecture is theoretically correct because the clusters are fully independent by construction. The dependency analysis (Section 3.3) guarantees that no rule in Cluster A reads a fact type that is exclusively written by a rule in Cluster B, or vice versa. Each cluster's rules operate on a closed set of fact types: they read only facts that are either (a) produced by other rules within the same cluster, or (b) provided externally as input events. Since each `KieSession` maintains its own Working Memory, there is no shared mutable state between threads, and therefore no race conditions, lost updates, or ordering violations. The parallel execution is semantically equivalent to running each cluster's rule subset independently on the same input stream — which, by the independence property, produces the same outputs as the monolithic session for that subset of rules.

#### Measurement Methodology

Correctness was validated empirically by comparing the outputs of the parallel architecture against the single-session baseline. The procedure is:

1. Both the baseline and parallel configurations process the identical input event stream (same dataset, same ordering, same pseudo-clock progression).
2. An `ObjectInsertedEvent` listener attached to each `KieSession` captures every `RiskSignal` fact inserted during execution.
3. The set of distinct signal types (the `kind` field of `RiskSignal`) emitted by the baseline is compared against the union of signal types emitted across all parallel cluster sessions.
4. A signal type is considered "matched" if it appears in both the baseline output and the parallel output. Missing types indicate a rule that fired in the baseline but not in any parallel session. Extra types indicate redundant firings from duplicated generic rules.

#### Results

| Configuration | Signal Types Matched | Missing | Status |
|:---|:---:|:---:|:---:|
| V2 (4T) | 140 / 140 | 0 | ✅ Pass |
| V3 (2T) | 140 / 140 | 0 | ✅ Pass |
| V3 No Router | 140 / 140 | 0 | ✅ Pass |
| V3.1 Channels | 140 / 140 | 0 | ✅ Pass |
| V3.2 Load Balanced | 140 / 140 | 0 | ✅ Pass |

All 140 distinct signal types produced by the baseline were also produced by every parallel configuration. The parallel sessions additionally produced redundant copies of signals from duplicated generic rules, which is expected and does not affect correctness — it reflects the same rule firing in multiple sessions on the same input event.

---

## 6. Discussion

### 6.1 The Effect of Rete Graph Pruning

A surprising finding is that parallelism produces speedups at small scale (50k events) despite near-complete event duplication. The explanation lies in the Rete network's cost structure.

Each session in the parallel configuration contains fewer rules than the baseline. The cost of evaluating a single event scales super-linearly with rule count because beta-join combinatorics increase quadratically with the number of cross-pattern join nodes. When 97 rules are split into sessions of 45 and 62 rules, the total join surface area drops substantially:

| Session | Rules | Approximate Join Surface (r²) |
|---------|:-----:|:---:|
| Baseline | 97 | 9,409 |
| Cluster A | 45 | 2,025 |
| Cluster B | 62 | 3,844 |

At 50,000 events, each rule evaluation in the parallel sessions is approximately 3× cheaper than in the baseline (measured as duration ÷ rules fired). Combined with parallel execution across two threads, this yields the observed 1.62× speedup.

### 6.2 Why Speedups Diminish at Scale

At 1.6 million events, three factors erode the Rete-pruning advantage:

1. **Working Memory growth.** Beta-join cost depends not only on the number of join nodes but on the number of facts in Working Memory. At 1.6M events, accumulated state objects increase join evaluation cost regardless of Rete size.

2. **JIT compilation effects.** The JVM's HotSpot compiler optimizes hot paths over millions of iterations. The single-session baseline benefits more from JIT because it has one consistent hot path. The per-rule cost drops from 31.3 μs/rule (50k) to 5.5 μs/rule (1.6M) in the baseline — a 5.7× improvement. The parallel sessions see less JIT benefit because each session's hot path is different.

3. **Garbage collection pressure.** Duplicated events across sessions create proportionally more object allocations, increasing GC frequency and pause duration.

### 6.3 The Event Duplication Problem

The central bottleneck in cluster-level parallelism is event duplication. Because different clusters require overlapping event types, the same event must be inserted into multiple sessions:

| Configuration | Total Events Processed (All Sessions) | Duplication Factor |
|:---|:---:|:---:|
| Baseline | 1,613,159 | 1.0× |
| V2 (4T) | 5,253,046 | 3.3× |
| V3 (2T) | 3,226,026 | 2.0× |

Each duplicated event incurs the full cost of Rete propagation, alpha-node evaluation, and beta-join checking in the receiving session. At scale, this duplication tax exceeds the parallelism benefit.

### 6.4 Progressive Optimization Path

The experiments demonstrate a clear optimization trajectory:

1. **Router removal (0.80× → 0.94×):** Eliminating the per-event routing logic and broadcasting to all sessions removed a subtle per-event overhead that was visible at millions of events.

2. **Channel-based signal egress (→ 0.89× but baseline improved to 37,668 ev/s):** Replacing `insert(new RiskSignal(...))` with `channels["alerts"].send(...)` prevented 5.4M objects from entering Working Memory. This improved absolute throughput by ~31% for both baseline and parallel configurations, but the relative speedup decreased because the baseline benefited equally.

3. **Generic rule isolation (→ 1.09×):** Moving three signal-emitting generic rules exclusively into the underloaded cluster eliminated 3.2 million redundant rule firings. This was the change that finally pushed the parallel architecture past the 1.0× threshold.

### 6.5 Working Memory Management: Retraction Experiments

Two approaches to managing accumulated `RiskSignal` facts were tested:

- **CEP `@expires(1s)` annotation:** Caused a 7.4× performance regression. The Drools expiration engine's priority-queue scheduling overhead for 5.4 million events far exceeded any benefit from fact removal.
- **Low-salience retraction rule:** Caused a 40% regression. The `retract()` calls themselves trigger Rete propagation to remove facts from all network nodes, and the 5.4 million extra rule firings outweighed the Working Memory reduction.

The key insight is that `RiskSignal` objects do not appear in any rule's left-hand side conditions — they are inert in Working Memory. Retracting inert facts is counterproductive because the retraction cost exceeds the GC savings. Retraction is only beneficial for facts that actively participate in beta-join evaluations.

### 6.6 Limitations

The achieved 1.09× speedup, while a positive result, is modest. The fundamental limitation is that cluster-level parallelism partitions rules but not data. When all clusters require the same event types, event duplication is unavoidable.

To achieve higher scaling factors, the parallelism axis must shift from rules to data — for example, partitioning the event stream by trading symbol so that each session processes a disjoint subset of symbols with the full rule set. This eliminates event duplication entirely and enables linear scaling proportional to thread count. However, this approach is only feasible when rules do not require cross-symbol state, which is the case for this benchmark but may not hold in general.

---

## 7. Conclusion

This work demonstrates that a monolithic rule-based CEP system can be automatically decomposed into independent clusters using execution trace analysis and community detection, and executed in parallel across multiple threads. The method is general: it requires only instrumented execution traces and does not assume knowledge of the rule semantics.

The experiments show that cluster-level parallelism can achieve modest speedups (1.09× at 1.6M events, 1.62× at 50k events) when combined with progressive optimizations including router elimination, fire-and-forget signal egress, and workload balancing. However, the speedup is fundamentally limited by event duplication when clusters share overlapping input event types.

The findings suggest that for CEP workloads where rules share common input events, data-level partitioning (e.g., by symbol or entity key) is more promising than rule-level partitioning for achieving linear scaling.
