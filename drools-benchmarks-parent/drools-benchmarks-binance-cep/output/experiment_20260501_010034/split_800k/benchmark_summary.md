
### Benchmark Performance Summary — split_800k
| Architecture | Throughput (ops/s) | Events/s | Total Events | Rules Fired | Error Margin | Alloc Rate (MB/s) | Mem per Op (B/op) | GC Count | GC Time (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Sequential Baseline (enableTraceLog=false)** | 0.053 | 42,508 | 800,000 | 6,695,867 | ± 0.0013 | 417.84 | 8.25e+09 | 64 | 207.0 |
| **Built-in Parallel (parallelMode=PARALLEL_EVALUATION)** | 0.051 | 41,126 | 800,000 | 6,695,867 | ± 0.0017 | 404.25 | 8.25e+09 | 64 | 220.0 |
| **Built-in Parallel (parallelMode=FULLY_PARALLEL)** | 0.053 | 42,782 | 800,000 | 6,695,867 | ± 0.0006 | 420.53 | 8.25e+09 | 64 | 215.0 |
| **Cluster V3** | 0.058 | 46,694 | 800,000 | 10,308,621 | ± 0.0004 | 7.58 | 1.38e+08 | 94 | 3181.0 |
| **Data-Parallel (poolSize=4)** | 0.125 | 99,751 | 800,000 | 6,695,867 | ± 0.0038 | 47.12 | 3.96e+08 | 152 | 2995.0 |
| **Data-Parallel (threads=2, poolSize=4)** | 0.172 | 137,336 | 800,000 | 6,695,867 | ± 0.0066 | 62.10 | 3.91e+08 | 245 | 5542.0 |
| **Data-Parallel (threads=4, poolSize=4)** | 0.172 | 137,533 | 800,000 | 6,695,867 | ± 0.0151 | 63.50 | 3.89e+08 | 285 | 8064.0 |

