# OpenSky JMH Benchmark Results: Clustered vs Baseline

## Experimental Setup

| Parameter | Value |
|:---|:---|
| **Dataset** | `opensky_flat_20260217_160412.jsonl` (37,748 events) |
| **Ruleset** | Corrected STCA rules (103 rules, including Eurocontrol gap fixes) |
| **JMH Config** | 10 warmup × 5s, 5 measurement × 10s, Fork 1 |
| **Window Size** | 200 events/op |
| **JDK** | 17.0.12, HotSpot 64-Bit Server VM |
| **Threads** | 1 JMH thread (engine uses `parallelStream()` internally) |

## Two-Session Architecture

| Session | Role | Rule Count | Clusters |
|:---|:---|:---:|:---|
| **Session A** | Conflict & Alert Core | **60** | C1 (Pairing) + C4 (Conflict) + C6 (Escalation) + C7 (Safety Alert) + C8 (Advisory) + duplicated R080/R049 |
| **Session B** | Audit & Quality Gates | **44** | C2 (Data Quality) + C3 (Stale Position) + C5 (Stale Contact) + C9 (Kinematics) |

Every `OpenSkyStateVector` is **broadcast** to both sessions in parallel via `parallelStream()`. Each session advances its own pseudo-clock independently. Sessions are trial-scoped (not recreated per iteration).

## Results

### Throughput (ops/s) — higher is better

| Mode | Score | Error (99.9% CI) |
|:---|:---:|:---:|
| **Baseline** (Single Session) | **14.293** | ± 3.583 |
| **Clustered** (Two-Session Parallel) | **5.223** | ± 3.210 |

### Average Time (s/op) — lower is better

| Mode | Score | Error (99.9% CI) |
|:---|:---:|:---:|
| **Baseline** (Single Session) | **0.088** | ± 0.044 |
| **Clustered** (Two-Session Parallel) | **0.279** | ± 0.492 |

### Summary

> [!CAUTION]
> The clustered two-session mode is **63% slower** than the single-session baseline.
> Throughput dropped from 14.3 to 5.2 ops/s; average latency increased from 88ms to 279ms per window.

## Experiment 2: Pure C2 Isolation

Based on dependency analysis, a second experiment isolated **only** the C2 Grid/Audit block into Session B, leaving all other clusters (including data quality flags C3, C5, C9) in Session A. `R000_LoadDefaultParams` and `R041_AssignGridCell` were duplicated.

| Session | Role | Rule Count | Clusters |
|:---|:---|:---:|:---|
| **Session A** | Everything Else | **76** | C1, C3, C4, C5, C6, C7, C8, C9 |
| **Session B** | Pure Audit / Grid | **29** | C2 + (R000, R041 duplicated) |

### Experiment 2 Results

| Metric | Score | Error (99.9% CI) |
|:---|:---:|:---:|
| **Throughput (ops/s)** | **4.847** | ± 4.348 |
| **Average Time (s/op)** | **0.199** | ± 0.099 |

---

## Root Cause Analysis

### Why is the clustered mode consistently slower (~65% throughput drop)?

1. **Extreme Workload Imbalance (Amdahl's Law):**
   Session A holds the heavy `N × N` pairing pipeline (`R042`), CPA projections, streak counters, and the entire alert lifecycle — **>96% of the computational work**. In Experiment 1, Session B had 44 rules; in Experiment 2, it had 29. In both cases, Session B performs lightweight stateless checks and completes instantly, leaving the CPU to wait entirely on Session A.

2. **Synchronization Overhead:**
   Each `KieSession.insert()` + `fireAllRules()` is wrapped in a `synchronized` block to prevent concurrent access. This serialization point, combined with `parallelStream()` fork-join overhead, adds latency per event.

3. **Duplicate RETE Network Evaluation:**
   Session A's RETE network must independently evaluate all its rules against every inserted `OpenSkyStateVector`. This is slower than the single-session baseline because splitting rules across sessions destroys RETE alpha-node sharing (e.g., ObjectTypeNodes for OSV).

4. **Memory Pressure:**
   Two separate KieSession instances double the working-set footprint (fact handles, beta memory, agenda queues), increasing GC pressure during the trial-scoped run.

## Conclusion

The OpenSky STCA ruleset is **intrinsically monolithic**. Rule-level clustering, even with optimal Infomap-guided partitions, yields **no speedup**. The core bottleneck is the O(N²) pair generation and intersection geometry in C1/C4. Future parallelization must focus on **data-level parallelism** (e.g., spatial partitioning of airspace sectors into independent JMH workers) rather than rule-level splitting.
