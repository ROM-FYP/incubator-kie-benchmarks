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
| **Dataset** | `split_800k.jsonl` |
| **Events per Invocation** | 800,000 |
| **Rule File** | `rules/wikimedia_content_moderation_join_heavy.drl` |

## 2. Correctness Validation

**Run 1:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 3,224,576 | 55,485 | ✅ |
| Built-in PARALLEL_EVAL | 3,224,576 | 55,564 | ✅ |
| Built-in FULLY_PARALLEL | 3,224,576 | 55,136 | ✅ |
| Cluster V3 | 3,871,614 | 52,589 | Δ+647038 |

**Run 2:** ✅ PASS

| Architecture | Rules Fired | Duration (ms) | Status |
| :--- | ---: | ---: | :---: |
| Baseline (Sequential) | 3,224,576 | 57,304 | ✅ |
| Built-in PARALLEL_EVAL | 3,224,576 | 54,795 | ✅ |
| Built-in FULLY_PARALLEL | 3,224,576 | 55,866 | ✅ |
| Cluster V3 | 3,871,614 | 53,777 | Δ+647038 |

> **Determinism:** ✅ Rule-fire counts identical across both runs.

## 3. Performance Summary

| # | Architecture | Time (ms/op) | Speedup | Events/s | Rules Fired | Error (±) | Alloc Rate (MB/s) | Mem/Op (B) | Mem Reduction | GC Count | GC Time (ms) |
| :---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | **Sequential Baseline** | 52596.0 | 1.00× | 15,210 | 3,224,576 | ± 3098.67 | 380.85 | 2.10e+10 | — | 42 | 835.0 |
| 2 | **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 52526.1 | 1.00× | 15,231 | 3,224,576 | ± 1019.32 | 381.29 | 2.10e+10 | +0.0% | 41 | 807.0 |
| 3 | **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 52903.4 | 0.99× | 15,122 | 3,224,576 | ± 183.06 | 378.56 | 2.10e+10 | -0.0% | 41 | 805.0 |
| 4 | **Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)** | 53475.6 | 0.98× | 14,960 | 3,224,576 | ± 633.07 | 374.52 | 2.10e+10 | -0.0% | 41 | 800.0 |
| 5 | **Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)** | 52671.2 | 1.00× | 15,189 | 3,224,576 | ± 857.36 | 380.24 | 2.10e+10 | -0.0% | 42 | 817.0 |
| 6 | **Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)** | 52677.8 | 1.00× | 15,187 | 3,224,576 | ± 1787.85 | 378.82 | 2.09e+10 | +0.4% | 41 | 787.0 |
| 7 | **Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)** | 52560.9 | 1.00× | 15,220 | 3,224,576 | ± 1340.81 | 381.04 | 2.10e+10 | -0.0% | 41 | 826.0 |
| 8 | **Cluster V3** | 15358.9 | 3.42× | 52,087 | 2,848,219 | ± 639.45 | 358.97 | 5.77e+09 | +72.5% | 16 | 661.0 |

## 4. Per-Iteration Timing (ms/op)

| Architecture | N | Min | Median | Max | Mean | Stdev | CV (%) |
| :--- | :---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 5 | 51949.1 | 52144.1 | 53791.0 | 52596.0 | 804.7 | 1.5 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 5 | 52361.1 | 52414.8 | 52996.5 | 52526.1 | 264.7 | 0.5 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 5 | 52832.1 | 52917.1 | 52959.3 | 52903.4 | 47.5 | 0.1 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 5 | 53210.1 | 53555.8 | 53606.6 | 53475.6 | 164.4 | 0.3 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 5 | 52525.0 | 52584.0 | 53063.3 | 52671.2 | 222.7 | 0.4 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 5 | 52166.9 | 52915.4 | 53078.8 | 52677.8 | 464.3 | 0.9 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 5 | 52030.4 | 52649.1 | 52879.3 | 52560.9 | 348.2 | 0.7 |
| Cluster V3 | 5 | 15133.9 | 15394.6 | 15516.1 | 15358.9 | 166.1 | 1.1 |

## 5. GC Detail (Per-Iteration)

| Architecture | GC Count (total) | GC Time (total ms) | GC Time/Iter (avg ms) | GC Time (min) | GC Time (max) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Sequential Baseline | 42 | 835 | 167.0 | 156 | 177 |
| Built-in Parallel (parallelMode=PARALLEL_EVALUATION) | 41 | 807 | 161.4 | 142 | 182 |
| Built-in Parallel (parallelMode=FULLY_PARALLEL) | 41 | 805 | 161.0 | 154 | 172 |
| Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION) | 41 | 800 | 160.0 | 154 | 171 |
| Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL) | 42 | 817 | 163.4 | 149 | 183 |
| Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION) | 41 | 787 | 157.4 | 148 | 173 |
| Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL) | 41 | 826 | 165.2 | 145 | 182 |
| Cluster V3 | 16 | 661 | 132.2 | 93 | 175 |

## 6. Cluster V3 — Per-Session Breakdown

| Cluster | Events Routed | Rules Fired | % of Total Events | % of Total Rules |
| :--- | ---: | ---: | ---: | ---: |
| **C1 (Minor)** | 278,100 | 1,656,882 | 23.2% | 58.2% |
| **C2 (Bot)** | 431,473 | 429,488 | 35.9% | 15.1% |
| **C3 (Content+Vandalism)** | 489,340 | 750,719 | 40.8% | 26.4% |
| **C4 (Discussion)** | 1,869 | 11,130 | 0.2% | 0.4% |
| **Total (routed)** | **1,200,782** | **2,848,219** | **100%** | **100%** |

> **Note:** Events are routed to multiple clusters based on edit-type classification, so routed event totals exceed the unique event count.

## 8. Flame Graph Artifacts

Generated **16** flame graphs:

- **Sequential Baseline**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (FULLY_PARALLEL)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Built-in Parallel (PARALLEL_EVALUATION)**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html
- **Cluster V3**: flame-alloc-forward.html, flame-alloc-reverse.html, flame-cpu-forward.html, flame-cpu-reverse.html

---
_Report generated from `.`_
