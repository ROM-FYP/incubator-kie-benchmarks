
# Cross-Dataset Scalability Summary

Experiment: experiment_20260501_010034
Datasets:   split_1200k, split_1600k, split_400k, split_800k

| Architecture | 1200K (ops/s) | 1600K (ops/s) | 400K (ops/s) | 800K (ops/s) | Trend |
| :--- | ---: | ---: | ---: | ---: | :--- |
| **Baseline enableTraceLog=false** | 0.035 | 0.027 | 0.105 | 0.053 | ↑ 1.52× |
| **Built-in Parallel parallelMode=FULLY_PARALLEL** | 0.036 | 0.027 | 0.102 | 0.053 | ↑ 1.50× |
| **Built-in Parallel parallelMode=PARALLEL_EVALUATION** | 0.035 | 0.027 | 0.104 | 0.051 | ↑ 1.45× |
| **Cluster V3** | 0.038 | 0.028 | 0.115 | 0.058 | ↑ 1.53× |
| **Data-Parallel poolSize=4** | 0.083 | 0.067 | 0.212 | 0.125 | ↑ 1.50× |
| **Data-Parallel t2 poolSize=4** | 0.120 | 0.100 | 0.286 | 0.172 | ↑ 1.43× |
| **Data-Parallel t4 poolSize=4** | 0.131 | 0.100 | 0.280 | 0.172 | ↑ 1.31× |

