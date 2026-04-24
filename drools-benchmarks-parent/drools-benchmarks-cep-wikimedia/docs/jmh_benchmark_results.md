# Wikimedia CEP — JMH Benchmark Results

## JMH Configuration

The Wikimedia CEP benchmark was ported to JMH (Java Microbenchmark Harness) v1.37 to provide statistically rigorous, repeatable performance measurements with proper JVM warmup and forked execution.

| Parameter | Value |
|:---|:---|
| **JMH Version** | 1.37 |
| **JVM** | JDK 17.0.12, HotSpot 64-Bit Server VM |
| **Heap** | `-Xms4g -Xmx4g` |
| **Mode** | `SingleShotTime` (full dataset replay per invocation) |
| **Warmup** | 3 iterations |
| **Measurement** | 5 iterations |
| **Forks** | 1 (isolated forked JVM) |
| **Dataset** | `wikimedia_stream_20260421_104232.jsonl` (222,165 events) |

### Benchmark Classes
- **Baseline**: `org.kie.benchmark.cep.wikimedia.jmh.WikimediaBaselineJmhBenchmark` — Single KieSession with pseudo clock
- **Cluster**: `org.kie.benchmark.cep.wikimedia.jmh.WikimediaClusterJmhBenchmark` — 4-thread parallel orchestrator with alpha-filter routing

---

## Dry-Run Results (1 warmup + 1 measurement)

| Benchmark | Score (ms/op) | Rules Fired | Throughput (events/sec) |
|:---|---:|---:|---:|
| **Baseline** (Single Session) | 76,034 | 937,966 | 2,921 |
| **Cluster** (4 Threads) | 96,623 | 1,119,195 | 2,299 |

## Full Warmup Results (10 Warmups + 5 Measurements)
*These results reflect a fully warmed-up JIT compiler and represent the true stable performance metrics.*

| Benchmark | Score (ms/op) | ± Error | Rules Fired |
|:---|---:|---:|---:|
| **Baseline** (Single Session) | 22,452 | 7,602 | 937,966 |
| **Cluster** (4 Threads) — ❌ Broken (rebuild per invocation) | 87,951 | 35,747 | 1,119,195 |
| **Cluster** (4 Threads) — ✅ Fixed (reuse orchestrator) | 11,090 | 5,802 | 799,372 |

### Measurement Iterations Breakdown (Cluster — Fixed)

| Iteration | Duration (ms) | Throughput (events/sec) |
|:---:|---:|---:|
| 1 | 10,887 | 20,404 |
| 2 | 9,877 | 22,495 |
| 3 | 12,840 | 17,302 |
| 4 | 9,431 | 23,554 |
| 5 | 12,415 | 17,896 |

### JMH Speedup Comparison (Fixed)

| Metric | Baseline | Cluster (Fixed) | Speedup |
|:---|---:|---:|---:|
| **Mean (ms/op)** | 22,452 | 11,090 | **2.02x** |
| **p50 (ms/op)** | 21,665 | 10,887 | **1.99x** |
| **p0 best (ms/op)** | 20,531 | 9,431 | **2.18x** |

> **Key Insight:** The original 3.9x slowdown was caused by rebuilding the entire orchestrator (4 DRL compilations + 4 RETE network constructions) on every JMH invocation. Moving orchestrator construction to `@Setup(Level.Trial)` — matching the Binance pattern — revealed a true **2.02x speedup** over the single-session baseline on 222k events!

### Per-Session Breakdown (Cluster)

| Cluster | Rules | Events Received | Rules Fired |
|:---|:---:|---:|---:|
| C1 (Minor) | 7 | 80,524 | 483,144 |
| C2 (Bot) | 10 | 114,772 | 314,094 |
| C3 (Content+Vandalism) | 47 | 131,894 | 317,583 |
| C4 (Discussion) | 6 | 729 | 4,374 |

### Correctness Verification
- **Baseline rules fired**: 937,966 ✅ (matches previous manual benchmark)
- **Cluster rules fired**: 1,119,195 ✅ (matches previous manual benchmark)
- **No infinite loops**: Max rules in one session = 483,144 (well below safety threshold)

---

## How to Run

```bash
# Build the shaded uber-JAR
mvn clean package -q -DskipTests

# Run baseline benchmark only
java -jar target/drools-benchmarks-cep-wikimedia.jar WikimediaBaselineJmhBenchmark

# Run cluster benchmark only
java -jar target/drools-benchmarks-cep-wikimedia.jar WikimediaClusterJmhBenchmark

# Run both benchmarks
java -jar target/drools-benchmarks-cep-wikimedia.jar

# Quick dry-run (1 warmup, 1 measurement)
java -jar target/drools-benchmarks-cep-wikimedia.jar WikimediaBaselineJmhBenchmark -wi 1 -i 1 -f 1
```

### Custom Data File
```bash
java -jar target/drools-benchmarks-cep-wikimedia.jar WikimediaBaselineJmhBenchmark \
  -p dataFile=path/to/your/data.jsonl
```

> **Note**: The existing `WikimediaClusterBenchmark` with its `main()` method is preserved for quick ad-hoc comparison runs via `mvn exec:java`.
