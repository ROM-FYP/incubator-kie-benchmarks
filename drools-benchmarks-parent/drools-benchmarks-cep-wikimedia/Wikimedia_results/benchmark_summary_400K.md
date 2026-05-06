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
| **Dataset** | `split_400k.jsonl` |
| **Events per Invocation** | 400,000 |
| **Rule File** | `rules/wikimedia_content_moderation_join_heavy.drl` |

## 2. Correctness Validation

**Run 1:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 1,643,863 | 29,758 | ✅ |
| Built-in PARALLEL_EVAL | 1,643,863 | 27,042 | ✅ |
| Built-in FULLY_PARALLEL | 1,643,863 | 27,604 | ✅ |
| Cluster V3 | 1,976,699 | 26,781 | Δ+332836 |

**Run 2:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 1,643,863 | 29,487 | ✅ |
| Built-in PARALLEL_EVAL | 1,643,863 | 27,834 | ✅ |
| Built-in FULLY_PARALLEL | 1,643,863 | 27,725 | ✅ |
| Cluster V3 | 1,976,699 | 26,852 | Δ+332836 |

> **Determinism:** ✅ Rule-fire counts identical across both runs.

## 3. Performance Summary

| # | Architecture | Time (ms/op) | Speedup | Events/s | Rules Fired | Error (±) | Alloc Rate (MB/s) | Mem/Op (B) | Mem Reduction | GC Count | GC Time (ms) |
| :---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | **Sequential Baseline** | 26247.8 | 1.00× | 15,239 | 1,643,863 | ± 1342.55 | 377.62 | 1.04e+10 | — | 20 | 411.0 |
| 2 | **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 26851.9 | 0.98× | 14,897 | 1,643,863 | ± 1039.42 | 369.10 | 1.04e+10 | +0.0% | 20 | 356.0 |
| 3 | **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 26748.0 | 0.98× | 14,954 | 1,643,863 | ± 1065.75 | 370.54 | 1.04e+10 | +0.0% | 21 | 405.0 |
| 4 | **Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)** | 26213.3 | 1.00× | 15,259 | 1,643,863 | ± 1578.42 | 378.14 | 1.04e+10 | +0.0% | 21 | 514.0 |
| 5 | **Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)** | 25898.6 | 1.01× | 15,445 | 1,643,863 | ± 661.80 | 382.67 | 1.04e+10 | +0.0% | 21 | 390.0 |
| 6 | **Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)** | 26817.7 | 0.98× | 14,916 | 1,643,863 | ± 845.61 | 368.16 | 1.04e+10 | +0.4% | 20 | 401.0 |
| 7 | **Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)** | 26339.3 | 1.00× | 15,186 | 1,643,863 | ± 1383.83 | 376.31 | 1.04e+10 | +0.0% | 20 | 400.0 |
| 8 | **Cluster V3** | 7935.5 | 3.31× | 50,406 | 1,421,284 | ± 487.09 | 362.77 | 3.03e+09 | +70.9% | 7 | 232.0 |

## 4. Per-Iteration Timing (ms/op)

| Architecture | N | Min | Median | Max | Mean | Stdev | CV (%) |
| :--- | :---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 5 | 25757.6 | 26168.7 | 26597.2 | 26247.8 | 348.7 | 1.3 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 5 | 26494.6 | 26984.2 | 27131.7 | 26851.9 | 269.9 | 1.0 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 5 | 26360.3 | 26839.7 | 27002.3 | 26748.0 | 276.8 | 1.0 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 5 | 25705.5 | 26123.4 | 26718.9 | 26213.3 | 409.9 | 1.6 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 5 | 25742.2 | 25801.1 | 26137.7 | 25898.6 | 171.9 | 0.7 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 5 | 26707.6 | 26716.1 | 27209.8 | 26817.7 | 219.6 | 0.8 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 5 | 26079.4 | 26198.4 | 26972.0 | 26339.3 | 359.4 | 1.4 |
| Cluster V3 | 5 | 7789.4 | 7959.6 | 8087.1 | 7935.5 | 126.5 | 1.6 |

## 5. GC Detail (Per-Iteration)

| Architecture | GC Count (total) | GC Time (total ms) | GC Time/Iter (avg ms) | GC Time (min) | GC Time (max) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 20 | 411 | 82.2 | 72 | 101 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 20 | 356 | 71.2 | 68 | 75 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 21 | 405 | 81.0 | 68 | 101 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 21 | 514 | 102.8 | 67 | 174 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 21 | 390 | 78.0 | 70 | 93 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 20 | 401 | 80.2 | 69 | 99 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 20 | 400 | 80.0 | 69 | 97 |
| Cluster V3 | 7 | 232 | 46.4 | 32 | 70 |

## 6. Cluster V3 — Per-Session Breakdown

| Cluster | Events Routed | Rules Fired | % of Total Events | % of Total Rules |
| :--- | ---: | ---: | ---: | ---: |
| **C1 (Minor)** | 140,734 | 835,260 | 23.5% | 58.8% |
| **C2 (Bot)** | 214,621 | 212,675 | 35.9% | 15.0% |
| **C3 (Content+Vandalism)** | 242,141 | 368,681 | 40.5% | 25.9% |
| **C4 (Discussion)** | 787 | 4,668 | 0.1% | 0.3% |
| **Total (routed)** | **598,283** | **1,421,284** | **100%** | **100%** |

> **Note:** Events are routed to multiple clusters based on edit-type classification, so routed event totals exceed the unique event count.

## 8. Flame Graph Artifacts

Generated **16** flame graphs:

- **Sequential Baseline**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (FULLY_PARALLEL)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (PARALLEL_EVALUATION)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Cluster V3**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html

---
_Report generated from `.`_
