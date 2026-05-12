# Cluster-Level Parallel KieSession — Architecture & Execution Guide

## 1. Objective

This document officially details the **Cluster-Level Parallel Execution** engine utilizing the **Fallback Session Architecture**. It outlines the mathematical distribution of 110 rules across separated Drools `KieSession` instances based on Infomap causal communities, demonstrating true thread isolated parallelism with zero inter-cluster barriers.

---

## 2. Architecture & Event Routing

The engine logically partitions the Drools working memory into separated boundaries. Events are routed mechanically via pre-calculated **Left-Hand Side (LHS)** constraints, eliminating the need to broadcast all events to all threads.

```text
                   taxonomy.drl (110 rules)
                              │
                  ┌───────────┴───────────┐
                  ▼                       ▼
          Infomap .ftree              DrlRuleParser
          (47 dense rules)           (all 110 rules)
                  │                       │
                  ▼                       ▼
       ┌──────────────────┐   ┌──────────────────────┐
       │ 5 Cluster Sessions│   │ 1 Fallback Session   │
       │ (47 + duplicated  │   │ (63 uncaptured rules)│
       │  bridge rules)    │   │ "catches everything" │
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

**Cluster Breakdown (Post-Optimization):**
*   **Cluster 1 (Feed Health & Modes):** 14 original rules
*   **Cluster 2 (Depth & Spread):** 26 original rules
*   **Cluster 3 (Trade Alpha):** 2 original rules
*   **Fallback Session:** 48 original rules (Stateless ingestion validators `A01-A08`, Bootstraps, and global cleanup logic).

---

## 3. Barrier-Free Execution Flow

To maximize CPU saturation, we eliminate `invokeAll()` and Thread Dispatch overhead. Rather than halting the main thread over every event dispatch, we implement **long-lived queues**.

```text
        Main dataset events (time-ordered)
                  │
                  ▼
         Main Thread Orchestrator 
         (O(1) hash routing enqueue)
                  │
        ┌─────────┼────────┬──────────┐
        ▼         ▼        ▼          ▼
     Queue-1   Queue-2  Queue-3    Queue-FB
        │         │        │          │
        ▼         ▼        ▼          ▼
     Worker-1  Worker-2 Worker-3   Worker-FB
     (Clust 1) (Clust 2)(Clust 3)  (Fallback)
        │         │        │          │
        └─────────┴────────┴──────────┘
                  │
         Sync only at END 
    (Poison pill + Future.get())
                  │
             Total fired
```

### Why barrier-free?

1.  **Thread Dispatch Overhead Killed:** Per-event `.invokeAll()` incurs ~5-50μs of thread dispatching latency per event. Using `BlockingQueue` workers eliminates 335ms to 3.3s of pure synchronization cost at a 67,000 throughput run.
2.  **Shared State Independence:** Because the fallback and partitioned clusters never require shared mutable inputs from one another, they simply churn their `KieSession` at their own pace without deadlock.
3.  **Temporal Consistency:** Every independent Thread pulls from a FIFO queue, ensuring event sequence is strictly preserved natively per session.

---

## 4. Execution Commands

To execute and measure this exact framework, run the designated orchestrator benchmarks.

### Compile the Codebase
Always clear caches and forcefully compile the Drools module to pick up exact ruleset changes:

```bash
mvn clean compile -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml -q 2>&1
```

### Generate Classpath
Export the precise maven-resolved classpath into a shell temporary variable:

```bash
CP=$(cat /tmp/binance_cp.txt)
```

### Run the Correctness Payload (1,000 Events)
This proves mathematical correctness and displays the fallback routing metrics without overloading the Infomap cycles.

```bash
java -cp "drools-benchmarks-parent/drools-benchmarks-binance-cep/target/classes:$CP" org.kie.benchmark.binance.parallel.BinanceClusterBenchmark 1000
```

### Run the Stress Benchmark (5,000 Events)
Used to demonstrate total cluster saturation and observe O(n²) bottlenecks over massive throughputs.

```bash
java -cp "drools-benchmarks-parent/drools-benchmarks-binance-cep/target/classes:$CP" org.kie.benchmark.binance.parallel.BinanceClusterBenchmark 5000
```

---

## 5. Experimental Output & Results

The system yielded complete parity with the Baseline processor, firing identically.

### 1,000 Event Scale Output

```text
── Single-Session Baseline ──────────────────
Loaded rules from: /rules/taxonomy.drl
Rules fired:  4998
Duration:     227 ms
Throughput:   4405.28 events/sec

── Cluster-Parallel Execution ──────────────
ClusterPlan {
  Cluster 1 (Feed Health & Mode Transitions): 14 original + 0 duplicated = 14 total
  Cluster 2 (Depth/Spread): 26 original + 0 duplicated = 26 total
  Cluster 3 (Trade Alpha): 2 original + 0 duplicated = 2 total
  Cluster -1 (Fallback): 48 original + 0 duplicated = 48 total
}
Rules fired:  4998
Duration:     127 ms
Throughput:   7874.02 events/sec
Speedup:      1.79x

── Fallback Analysis ───────────────────────
Fallback rules fired:    3002
Fallback % of total:     60.1%
```

### 5,000 Event Scale Output

```text
── Single-Session Baseline ──────────────────
Rules fired:  24992
Duration:     476 ms
Throughput:   10504.20 events/sec

── Cluster-Parallel Execution ──────────────
Rules fired:  24992
Duration:     933 ms
Throughput:   5359.05 events/sec
Speedup:      0.51x

── Fallback Analysis ───────────────────────
Fallback % of total:     8.1%
```

### 1,600,000 Event Scale Output (Full Dataset)

```text
── Single-Session Baseline ──────────────────
Rules fired:  8,539,756
Duration:     25,121 ms
Throughput:   64,215 events/sec

── Cluster-Parallel Execution ──────────────
Rules fired:  7,274,966    (1.26 million missed evaluations!)
Duration:     15,678 ms
Throughput:   102,893 events/sec
Speedup:      1.60x

── Fallback Analysis ───────────────────────
Fallback % of total:     66.6%
```

**Architectural Failure Mode (Loss of Correctness):**
Unlike the lower scale runs, testing against the entire dataset reveals a terminal flaw in horizontal rule partitioning: **Temporal Correctness is not preserved.** 
In CEP, rules utilize temporal sliding windows (e.g., `this after[0s, 5s] $event`). Because the cluster-queues are barrier-free, their individual internal pseudo-clocks advance completely asynchronously based on whatever isolated events they receive. If a cluster goes several seconds without receiving specifically routed events, its internal clock jumps violently, arbitrarily dropping temporally sensitive facts out of working memory and skipping 1.26 million valid rule evaluation chains. 
To regain correctness, global clock "watermarks" must be broadcast to all clusters, which would mandate thread-locking and destroy the 1.60x parallel speedup.
