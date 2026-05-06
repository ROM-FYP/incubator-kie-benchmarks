
### Benchmark Performance Summary — split_1600k
| Architecture | Throughput (ops/s) | Events/s | Total Events | Rules Fired | Error Margin | Alloc Rate (MB/s) | Mem per Op (B/op) | GC Count | GC Time (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Sequential Baseline (enableTraceLog=false)** | 0.027 | 43,141 | 1,600,000 | 13,411,202 | ± 0.0004 | 424.90 | 1.65e+10 | 65 | 395.0 |
| **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 0.027 | 42,650 | 1,600,000 | 13,411,202 | ± 0.0008 | 420.08 | 1.65e+10 | 65 | 428.0 |
| **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 0.027 | 42,839 | 1,600,000 | 13,411,202 | ± 0.0003 | 421.93 | 1.65e+10 | 65 | 427.0 |
| **Cluster V3** | 0.028 | 45,537 | 1,600,000 | 20,597,129 | ± 0.0007 | 4.75 | 1.77e+08 | 93 | 5918.0 |
| **Data-Parallel (poolSize=4)** | 0.067 | 107,553 | 1,600,000 | 13,411,202 | ± 0.0021 | 26.16 | 4.08e+08 | 173 | 2402.0 |
| **Data-Parallel (threads=2, poolSize=4)** | 0.100 | 160,455 | 1,600,000 | 13,411,202 | ± 0.0039 | 38.24 | 4.05e+08 | 290 | 5256.0 |
| **Data-Parallel (threads=4, poolSize=4)** | 0.100 | 160,416 | 1,600,000 | 13,411,202 | ± 0.0037 | 38.10 | 4.02e+08 | 395 | 9554.0 |

