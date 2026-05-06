# Wikimedia CEP Benchmark Report

## 1. Experiment Configuration

| Parameter | Value |
| :--- | :--- |
| **Experiment Date** | N/A |
| **JMH Version** | 1.37 |
| **JDK** | 17.0.12 (Java HotSpot(TM) 64-Bit Server VM) |
| **JVM Path** | `/usr/lib/jvm/jdk-17.0.12-oracle-x64/bin/java` |
| **JVM Args** | `-Xms4g -Xmx4g` |
| **Benchmark Mode** | SingleShotTime (`ss`) |
| **Warmup Iterations** | 3 |
| **Measurement Iterations** | 5 |
| **Forks** | 1 |
| **Dataset** | `split_1600k.jsonl` |
| **Events per Invocation** | 1,600,000 |
| **Rule File** | `rules/wikimedia_content_moderation_join_heavy.drl` |

## 2. Correctness Validation

**Run 1:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 6,487,815 | 115,165 | ✅ |
| Built-in PARALLEL_EVAL | 6,487,815 | 110,497 | ✅ |
| Built-in FULLY_PARALLEL | 6,487,815 | 112,257 | ✅ |
| Cluster V3 | 7,731,317 | 106,609 | Δ+1243502 |

**Run 2:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 6,487,815 | 112,367 | ✅ |
| Built-in PARALLEL_EVAL | 6,487,815 | 110,472 | ✅ |
| Built-in FULLY_PARALLEL | 6,487,815 | 109,631 | ✅ |
| Cluster V3 | 7,731,317 | 108,531 | Δ+1243502 |

> **Determinism:** ✅ Rule-fire counts identical across both runs.

## 3. Performance Summary

| # | Architecture | Time (ms/op) | Speedup | Events/s | Rules Fired | Error (±) | Alloc Rate (MB/s) | Mem/Op (B) | Mem Reduction | GC Count | GC Time (ms) |
| :---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | **Sequential Baseline** | 107591.1 | 1.00× | 14,871 | 6,487,815 | ± 4366.48 | 372.52 | 4.20e+10 | — | 84 | 3091.0 |
| 2 | **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 109115.6 | 0.99× | 14,663 | 6,487,815 | ± 4863.96 | 367.32 | 4.20e+10 | +0.0% | 84 | 3184.0 |
| 3 | **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 105994.5 | 1.02× | 15,095 | 6,487,815 | ± 198.42 | 378.10 | 4.20e+10 | +0.0% | 84 | 3168.0 |
| 4 | **Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)** | 107560.0 | 1.00× | 14,875 | 6,487,815 | ± 3545.33 | 372.62 | 4.20e+10 | +0.0% | 84 | 3116.0 |
| 5 | **Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)** | 104796.9 | 1.03× | 15,268 | 6,487,815 | ± 2520.71 | 382.43 | 4.20e+10 | +0.0% | 84 | 3103.0 |
| 6 | **Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)** | 106483.0 | 1.01× | 15,026 | 6,487,815 | ± 4825.62 | 376.41 | 4.20e+10 | +0.0% | 84 | 3138.0 |
| 7 | **Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)** | 106298.8 | 1.01× | 15,052 | 6,487,815 | ± 4365.46 | 377.05 | 4.20e+10 | +0.0% | 84 | 3140.0 |
| 8 | **Cluster V3** | 38923.1 | 2.76× | 41,107 | 5,804,294 | ± 979.74 | 295.66 | 1.21e+10 | +71.3% | 67 | 1832.0 |

## 4. Per-Iteration Timing (ms/op)

| Architecture | N | Min | Median | Max | Mean | Stdev | CV (%) |
| :--- | :---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 5 | 106535.0 | 107175.0 | 109087.4 | 107591.1 | 1134.0 | 1.1 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 5 | 107431.9 | 108813.2 | 110723.0 | 109115.6 | 1263.2 | 1.2 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 5 | 105939.8 | 106000.4 | 106058.8 | 105994.5 | 51.5 | 0.0 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 5 | 106575.5 | 107622.8 | 108839.9 | 107560.0 | 920.7 | 0.9 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 5 | 104347.3 | 104566.6 | 105954.1 | 104796.9 | 654.6 | 0.6 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 5 | 104888.2 | 106351.8 | 108401.0 | 106483.0 | 1253.2 | 1.2 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 5 | 104584.9 | 106224.6 | 107582.8 | 106298.8 | 1133.7 | 1.1 |
| Cluster V3 | 5 | 38525.0 | 39009.5 | 39146.7 | 38923.1 | 254.4 | 0.7 |

## 5. GC Detail (Per-Iteration)

| Architecture | GC Count (total) | GC Time (total ms) | GC Time/Iter (avg ms) | GC Time (min) | GC Time (max) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 84 | 3091 | 618.2 | 605 | 635 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 84 | 3184 | 636.8 | 619 | 675 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 84 | 3168 | 633.6 | 609 | 661 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 84 | 3116 | 623.2 | 609 | 647 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 84 | 3103 | 620.6 | 609 | 634 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 84 | 3138 | 627.6 | 605 | 659 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 84 | 3140 | 628.0 | 618 | 642 |
| Cluster V3 | 67 | 1832 | 366.4 | 272 | 417 |

## 6. Cluster V3 — Per-Session Breakdown

| Cluster | Events Routed | Rules Fired | % of Total Events | % of Total Rules |
| :--- | ---: | ---: | ---: | ---: |
| **C1 (Minor)** | 577,139 | 3,452,904 | 24.2% | 59.5% |
| **C2 (Bot)** | 843,022 | 841,045 | 35.3% | 14.5% |
| **C3 (Content+Vandalism)** | 959,880 | 1,481,173 | 40.2% | 25.5% |
| **C4 (Discussion)** | 4,881 | 29,172 | 0.2% | 0.5% |
| **Total (routed)** | **2,384,922** | **5,804,294** | **100%** | **100%** |

> **Note:** Events are routed to multiple clusters based on edit-type classification, so routed event totals exceed the unique event count.

## 8. Flame Graph Artifacts

Generated **16** flame graphs:

- **Sequential Baseline**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (FULLY_PARALLEL)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (PARALLEL_EVALUATION)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Cluster V3**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html

---
_Report generated from `.`_
