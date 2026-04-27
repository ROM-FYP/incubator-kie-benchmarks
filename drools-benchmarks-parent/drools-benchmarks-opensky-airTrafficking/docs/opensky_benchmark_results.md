# OpenSky Air Trafficking Benchmark Results

## 1. Overview
This document compares the empirical results of running the OpenSky Air Traffic Control CEP ruleset over two exact architectures:
1.  **Baseline System:** Standard Drools event stream processed within a singular monolithic KieSession.
2.  **Clustered Architecture (89/13 Split):** The ruleset decomposed into a Core Monolith (89 rules) and an Independent Anomaly Cluster (13 rules). Events are explicitly routed into the clusters using parallel Java streams imitating `AlphaNode` filtering.

The benchmark evaluates a uniform window size of `10,000` OpenSky StateVector telemetry events fed directly from `dataset_output.jsonl`.

## 2. Event Routing Fallacy
An initial attempt was made to exclusively route events using static property checks (Alpha Routing). 

| Mode | Events to Core Cluster | Events to Independent Cluster |
| :--- | :--- | :--- |
| **Filtered Routing** | `10,000` (100%) | `10,000` (100%) |

**Result:** Routing completely collapses. Because both the Core Block and Independent Block contain rules evaluating comprehensive temporal windows (`clock.getCurrentTime()`) and edge safety permutations (`timePosition != null`), the static alpha filter is universally bypassed. The system is forced to broadcast 100% of telemetry to both clusters.

## 3. Throughput Inversion
Measurements taken via JMH (10 warmups, 5 measurements iterations) operating the identical `10,000` event sliding window.

| Execution Mode | Operations Per Second | Scaling Factor |
| :--- | :--- | :--- |
| **Baseline (Single Session)** | `~21.0` ops/s | `1.00x` |
| **Parallel Alpha Routed (89/13)** | `~17.2` ops/s | `0.81x` |

**Result:** Attempting to process events via duplicate parallel networks induces a `19%` drop in ops/s. The dual-session synchronization heavily suppresses the native RETE evaluation engine capability. Parallelization reduces velocity.

## 4. Workload and Correctness Failure
Crucially, when measuring the actual business output objects (`Alert` facts and `AuditEvent` flags) created over the deterministic 10,000 event window, the partitioned engine fundamentally drifted from mathematical correctness.

| Metric | Baseline Architecture | Clustered Architecture (89/13 Split) | Deviation / Variance |
| :--- | :--- | :--- | :--- |
| **Total Rules Fired** | `135,068` evaluations | `110,303` combined evaluations| `- 18.3%` |
| **Output Audits** | `1,732` Audit Events generated | `1,412` Audit Events generated | `- 18.4%` false negatives |
| **Output Alerts** | `175` Active Alerts raised | `206` Active Alerts raised | `+ 17.7%` ghost positive rates |

**Result:** The Clustered Approach is catastrophically incorrect. 
Because dependencies between conflict anomaly detection (Independent Cluster) and the core inhibition/conflict tracker (Core Cluster) evaluate entirely disjointed working memories, cross-cluster inhibitions fail. Consequently, the independent cluster creates states out-of-order or suppresses state creation, causing a massive increase in false positive `Alerts` (+17.7%) and an identical drop in missing `AuditEvents`.

## 5. Final Conclusion
Parallelizing the OpenSky benchmark via disjoint graph segments is definitively infeasible because:
1.  **Throughput decays by ~19%** owing to duplicate insertions forcing the parallel stream overhead to throttle.
2.  **State Semantics Break.** Decoupling the dependency-dense topology natively spawns phantom alerts and suppresses legitimate audit pipelines. Correct operation demands 100% of facts are synchronized in a shared sequence.
