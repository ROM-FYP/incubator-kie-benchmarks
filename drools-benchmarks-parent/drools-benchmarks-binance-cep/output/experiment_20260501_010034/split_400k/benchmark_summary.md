
### Benchmark Performance Summary — split_400k
| Architecture | Throughput (ops/s) | Events/s | Total Events | Rules Fired | Error Margin | Alloc Rate (MB/s) | Mem per Op (B/op) | GC Count | GC Time (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Sequential Baseline (enableTraceLog=false)** | 0.105 | 41,832 | 400,000 | 3,361,255 | ± 0.0010 | 412.42 | 4.14e+09 | 57 | 145.0 |
| **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 0.104 | 41,710 | 400,000 | 3,361,255 | ± 0.0015 | 411.23 | 4.14e+09 | 56 | 124.0 |
| **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 0.102 | 40,793 | 400,000 | 3,361,255 | ± 0.0018 | 402.18 | 4.14e+09 | 56 | 129.0 |
| **Cluster V3** | 0.115 | 46,049 | 400,000 | 5,166,473 | ± 0.0021 | 12.69 | 1.19e+08 | 84 | 1863.0 |
| **Data-Parallel (poolSize=4)** | 0.212 | 84,619 | 400,000 | 3,361,255 | ± 0.1496 | 77.64 | 3.83e+08 | 149 | 4510.0 |
| **Data-Parallel (threads=2, poolSize=4)** | 0.286 | 114,319 | 400,000 | 3,361,255 | ± 0.0075 | 102.89 | 3.84e+08 | 199 | 6798.0 |
| **Data-Parallel (threads=4, poolSize=4)** | 0.280 | 111,918 | 400,000 | 3,361,255 | ± 0.0018 | 100.86 | 3.82e+08 | 243 | 9157.0 |

