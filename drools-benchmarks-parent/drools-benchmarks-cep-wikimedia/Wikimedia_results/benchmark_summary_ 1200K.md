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
| **Dataset** | `split_1200k.jsonl` |
| **Events per Invocation** | 1,200,000 |
| **Rule File** | `rules/wikimedia_content_moderation_join_heavy.drl` |

## 2. Correctness Validation

**Run 1:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 4,859,438 | 84,161 | ✅ |
| Built-in PARALLEL_EVAL | 4,859,438 | 80,232 | ✅ |
| Built-in FULLY_PARALLEL | 4,859,438 | 79,828 | ✅ |
| Cluster V3 | 5,794,334 | 77,813 | Δ+934896 |

**Run 2:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 4,859,438 | 84,704 | ✅ |
| Built-in PARALLEL_EVAL | 4,859,438 | 80,634 | ✅ |
| Built-in FULLY_PARALLEL | 4,859,438 | 80,745 | ✅ |
| Cluster V3 | 5,794,334 | 77,682 | Δ+934896 |

> **Determinism:** ✅ Rule-fire counts identical across both runs.

## 3. Performance Summary

| # | Architecture | Time (ms/op) | Speedup | Events/s | Rules Fired | Error (±) | Alloc Rate (MB/s) | Mem/Op (B) | Mem Reduction | GC Count | GC Time (ms) |
| :---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | **Sequential Baseline** | 77081.5 | 1.00× | 15,568 | 4,859,438 | ± 2229.76 | 385.82 | 3.12e+10 | — | 61 | 1191.0 |
| 2 | **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 77697.6 | 0.99× | 15,444 | 4,859,438 | ± 3869.60 | 382.79 | 3.12e+10 | +0.0% | 61 | 1183.0 |
| 3 | **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 79880.2 | 0.96× | 15,023 | 4,859,438 | ± 2331.70 | 372.30 | 3.12e+10 | +0.0% | 61 | 1174.0 |
| 4 | **Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)** | 79668.4 | 0.97× | 15,062 | 4,859,438 | ± 1876.09 | 373.29 | 3.12e+10 | +0.0% | 61 | 1228.0 |
| 5 | **Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)** | 77462.8 | 1.00× | 15,491 | 4,859,438 | ± 4802.48 | 383.98 | 3.12e+10 | +0.0% | 62 | 1259.0 |
| 6 | **Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)** | 78195.7 | 0.99× | 15,346 | 4,859,438 | ± 3972.73 | 380.36 | 3.12e+10 | +0.0% | 62 | 1206.0 |
| 7 | **Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)** | 78212.7 | 0.99× | 15,343 | 4,859,438 | ± 3667.03 | 380.27 | 3.12e+10 | +0.0% | 61 | 1227.0 |
| 8 | **Cluster V3** | 27042.6 | 2.85× | 44,374 | 4,338,088 | ± 1550.42 | 305.04 | 8.67e+09 | +72.2% | 35 | 1115.0 |

## 4. Per-Iteration Timing (ms/op)

| Architecture | N | Min | Median | Max | Mean | Stdev | CV (%) |
| :--- | :---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 5 | 76760.5 | 76841.4 | 78115.3 | 77081.5 | 579.1 | 0.8 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 5 | 76694.7 | 77866.9 | 79154.1 | 77697.6 | 1004.9 | 1.3 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 5 | 78797.0 | 80149.4 | 80156.3 | 79880.2 | 605.5 | 0.8 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 5 | 78852.5 | 79818.4 | 80122.1 | 79668.4 | 487.2 | 0.6 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 5 | 76184.1 | 77401.1 | 78793.2 | 77462.8 | 1247.2 | 1.6 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 5 | 77076.3 | 77752.2 | 79727.4 | 78195.7 | 1031.7 | 1.3 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 5 | 76964.0 | 78161.4 | 79645.8 | 78212.7 | 952.3 | 1.2 |
| Cluster V3 | 5 | 26571.9 | 26932.3 | 27534.2 | 27042.6 | 402.6 | 1.5 |

## 5. GC Detail (Per-Iteration)

| Architecture | GC Count (total) | GC Time (total ms) | GC Time/Iter (avg ms) | GC Time (min) | GC Time (max) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 61 | 1191 | 238.2 | 227 | 253 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 61 | 1183 | 236.6 | 226 | 248 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 61 | 1174 | 234.8 | 216 | 247 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 61 | 1228 | 245.6 | 238 | 261 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 62 | 1259 | 251.8 | 220 | 268 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 62 | 1206 | 241.2 | 221 | 268 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 61 | 1227 | 245.4 | 226 | 267 |
| Cluster V3 | 35 | 1115 | 223.0 | 209 | 253 |

## 6. Cluster V3 — Per-Session Breakdown

| Cluster | Events Routed | Rules Fired | % of Total Events | % of Total Rules |
| :--- | ---: | ---: | ---: | ---: |
| **C1 (Minor)** | 433,036 | 2,589,606 | 24.2% | 59.7% |
| **C2 (Bot)** | 633,462 | 631,278 | 35.4% | 14.6% |
| **C3 (Content+Vandalism)** | 718,396 | 1,098,328 | 40.2% | 25.3% |
| **C4 (Discussion)** | 3,171 | 18,876 | 0.2% | 0.4% |
| **Total (routed)** | **1,788,065** | **4,338,088** | **100%** | **100%** |

> **Note:** Events are routed to multiple clusters based on edit-type classification, so routed event totals exceed the unique event count.

## 8. Flame Graph Artifacts

Generated **16** flame graphs:

- **Sequential Baseline**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (FULLY_PARALLEL)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (PARALLEL_EVALUATION)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Cluster V3**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html

---
_Report generated from `.`_
