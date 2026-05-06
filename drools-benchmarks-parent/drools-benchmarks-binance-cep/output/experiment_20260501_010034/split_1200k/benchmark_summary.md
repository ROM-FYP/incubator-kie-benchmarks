
### Benchmark Performance Summary — split_1200k
| Architecture | Throughput (ops/s) | Events/s | Total Events | Rules Fired | Error Margin | Alloc Rate (MB/s) | Mem per Op (B/op) | GC Count | GC Time (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Sequential Baseline (enableTraceLog=false)** | 0.035 | 41,914 | 1,200,000 | 10,053,500 | ± 0.0006 | 412.24 | 1.24e+10 | 72 | 337.0 |
| **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 0.035 | 42,504 | 1,200,000 | 10,053,500 | ± 0.0002 | 418.04 | 1.24e+10 | 72 | 372.0 |
| **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 0.036 | 42,701 | 1,200,000 | 10,053,500 | ± 0.0015 | 419.98 | 1.24e+10 | 72 | 340.0 |
| **Cluster V3** | 0.038 | 45,719 | 1,200,000 | 15,458,659 | ± 0.0005 | 5.66 | 1.57e+08 | 104 | 4987.0 |
| **Data-Parallel (poolSize=4)** | 0.083 | 99,846 | 1,200,000 | 10,053,500 | ± 0.0020 | 31.82 | 4.01e+08 | 153 | 2390.0 |
| **Data-Parallel (threads=2, poolSize=4)** | 0.120 | 143,856 | 1,200,000 | 10,053,500 | ± 0.0021 | 43.39 | 3.99e+08 | 243 | 4566.0 |
| **Data-Parallel (threads=4, poolSize=4)** | 0.131 | 157,049 | 1,200,000 | 10,053,500 | ± 0.0133 | 49.09 | 3.95e+08 | 311 | 7722.0 |

