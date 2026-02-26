# Execution Instructions - Binance CEP Benchmark

## Quick Start

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- 4GB+ RAM recommended

### 1. Quick Test (Main Method)

Run a quick test without JMH to verify everything works:

```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep
mvn exec:java -Dexec.mainClass="org.kie.benchmark.binance.BinanceRiskControlBenchmark"
```

**Expected Output:**
```
=== Benchmark Setup ===
Symbol: BTCUSDT
Total events: 9085
Dataset: run_20260216_0632_10sym

=== Quick Test Results ===
Events processed: 9085
Rules fired: <varies with taxonomy rules>
Duration: <varies> ms
Throughput: <varies> events/sec
```

---

## Full JMH Benchmark

### 2. Build Uber JAR

```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep
mvn clean package
```

This creates: `target/drools-benchmarks-binance-cep.jar` (67MB)

### 3. Run JMH Benchmark

**Per-symbol benchmark (default):**
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

**Full dataset benchmark (all 67K events, all symbols):**
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceFullDatasetBenchmark
```

**Quick test — full dataset (no JMH):**
```bash
mvn exec:java -Dexec.mainClass="org.kie.benchmark.binance.BinanceFullDatasetBenchmark"
```

**With specific parameters:**
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark \
  -wi 3 \
  -i 5 \
  -f 1 \
  -t 1
```

Parameters:
- `-wi 3` - 3 warmup iterations
- `-i 5` - 5 measurement iterations
- `-f 1` - 1 fork
- `-t 1` - 1 thread

---

## Profiling

### 4. GC Profiling

```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark -prof gc
```

### 5. Async Profiler (CPU Flamegraph)

```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark \
  -prof async:output=flamegraph;dir=profiling-results
```

### 6. JFR Profiling

```bash
java -XX:+UnlockDiagnosticVMOptions \
     -XX:+DebugNonSafepoints \
     -XX:StartFlightRecording=filename=benchmark.jfr,settings=profile \
     -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

---

## Configuration Options

### Rules File Selection

**Use taxonomy.drl (70 rules, default):**
```bash
mvn exec:java -Dexec.mainClass="org.kie.benchmark.binance.BinanceRiskControlBenchmark"
```

**Use demo.drl (10 rules, lightweight):**
```bash
mvn exec:java \
  -Dexec.mainClass="org.kie.benchmark.binance.BinanceRiskControlBenchmark" \
  -Dbinance.rules.file=/rules/demo.drl
```

### Symbol Selection

All 10 symbols from the dataset are benchmarked by default:
`BTCUSDT`, `ETHUSDT`, `SOLUSDT`, `BNBUSDT`, `XRPUSDT`, `DOGEUSDT`, `ADAUSDT`, `AVAXUSDT`, `LINKUSDT`, `ARBUSDT`

**Run a single symbol via JMH parameter override:**
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark -p symbol=BTCUSDT
```

**Run a subset:**
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark -p symbol=BTCUSDT,ETHUSDT,SOLUSDT
```

---

## Benchmark Variants

### Per-Symbol Benchmark
Replays events filtered by each symbol independently (~3K–9K events per symbol, 10 symbols).
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

### Full Dataset Benchmark
Replays ALL 67K events (all symbols combined) in a single invocation. Uses longer iteration times (2×30s warmup, 3×60s measurement) to accommodate the larger workload.
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceFullDatasetBenchmark
```

### Latency Benchmark (Future)
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceLatencyBenchmark
```

---

## Expected Results & Score Interpretation

### What the Benchmark Measures

This benchmark measures how fast the Drools CEP engine can **replay market events through a rule set**. Each benchmark invocation replays all events for a single symbol (~9,000 events for BTCUSDT) through the KieSession, advancing the `SessionPseudoClock` and firing all rules after each event insertion.

---

### Quick Test Expected Output

When you run the quick test via `mvn exec:java`, you should see output like:

```
=== Benchmark Setup ===
Symbol: BTCUSDT
Total events: 9085
Dataset: run_20260216_0632_10sym

=== Quick Test Results ===
Events processed: 9085
Rules fired: 45000           ← example value, actual varies
Duration: 1200 ms            ← example value
Throughput: 7570.83 events/sec
```

**What each metric means:**

| Metric | Description | Example | Interpretation |
|--------|-------------|---------|----------------|
| **Events processed** | Total market events (trades, depth updates, mark, index) replayed from the dataset | `9085` | Fixed per symbol; depends on data captured during the 5-minute collection window |
| **Rules fired** | Total number of rule activations across all events | `~45,000` (taxonomy), `~20,000` (demo) | Higher count = more rules matched per event. Taxonomy (70 rules) fires more than demo (10 rules) |
| **Duration** | Wall-clock time to replay all events | `1200 ms` | Lower is better. Includes event insertion, clock advance, and `fireAllRules()` |
| **Throughput** | Calculated as `(events × 1000) / duration_ms` | `7570.83 events/sec` | Higher is better. The primary performance indicator |

> [!NOTE]
> The **Rules fired** count depends on which rule file is used:
> - **taxonomy.drl** (70 rules): Expect **4–6× the event count** in rules fired (e.g., ~40,000–55,000 for 9,085 events), because multiple rules match each event (ingestion validation, feed health tracking, derived metrics, etc.)
> - **demo.drl** (10 rules): Expect **2–3× the event count** (e.g., ~18,000–27,000), since fewer rules match per event

---

### JMH Output Example

After running the full JMH benchmark, the final summary table looks like:

```
Benchmark                                                   (symbol)  Mode  Cnt    Score    Error  Units
BinanceRiskControlBenchmark.benchmarkEventReplay             BTCUSDT  thrpt    5    3.52  ±  0.21  ops/s
BinanceRiskControlBenchmark.benchmarkEventReplay             ETHUSDT  thrpt    5    4.18  ±  0.15  ops/s
BinanceRiskControlBenchmark.benchmarkEventReplay             SOLUSDT  thrpt    5    5.91  ±  0.33  ops/s
```

**Column-by-column breakdown:**

| Column | Meaning |
|--------|---------|
| **Benchmark** | The Java method being measured (`benchmarkEventReplay`) |
| **(symbol)** | The `@Param` value — which cryptocurrency symbol this row is for |
| **Mode** | `thrpt` = **Throughput mode**. Measures how many complete replays finish per second |
| **Cnt** | Number of measurement iterations (default: 5). More iterations → more reliable results |
| **Score** | **The main result.** Average number of complete event replays completed per second |
| **Error** | The ± margin at 99.9% confidence interval. Smaller error = more stable results |
| **Units** | `ops/s` = operations per second, where 1 operation = replaying all events for that symbol |

---

### Understanding the Score (ops/s)

The **Score** is the most important number. Here's what it means:

> **Score = 3.52 ops/s** means the engine completed **3.52 full event replays per second**.

To convert to **events/sec throughput**:
```
events/sec = Score × events_per_symbol
           = 3.52 × 9085
           ≈ 31,979 events/sec
```

**Performance reference ranges** (taxonomy.drl, 70 rules):

| Score (ops/s) | Events/sec (BTCUSDT) | Assessment |
|---------------|---------------------|------------|
| < 1.0 | < 9,085 | 🔴 **Poor** — possible memory pressure, excessive GC, or rule compilation issues |
| 1.0 – 3.0 | 9,085 – 27,255 | 🟡 **Baseline** — typical unoptimized first run |
| 3.0 – 8.0 | 27,255 – 72,680 | 🟢 **Good** — reasonable performance for 70 complex rules |
| > 8.0 | > 72,680 | 🟢 **Excellent** — well-optimized or simpler rule set (demo.drl) |

> [!TIP]
> Symbols with **fewer events** (e.g., ARBUSDT ~3,000) will naturally show **higher ops/s** scores since each replay completes faster. Always compare the **same symbol** across runs for meaningful comparisons.

---

### Understanding the Error (±)

The **Error** value represents the confidence interval:

```
Score ± Error → 3.52 ± 0.21 means the true throughput is between 3.31 and 3.73 ops/s (99.9% confidence)
```

| Error relative to Score | Interpretation |
|------------------------|----------------|
| < 5% of Score | ✅ **Stable** — results are reliable and reproducible |
| 5–15% of Score | ⚠️ **Acceptable** — some variance, consider more iterations (`-i 10`) |
| > 15% of Score | ❌ **Unstable** — background processes, GC pressure, or warm-up issues; increase forks (`-f 3`) or warmup (`-wi 5`) |

---

### GC Profiler Output (with `-prof gc`)

When running with GC profiling, you'll see additional rows:

```
BinanceRiskControlBenchmark.benchmarkEventReplay:gc.alloc.rate         BTCUSDT  thrpt  5  512.34 ± 12.5  MB/sec
BinanceRiskControlBenchmark.benchmarkEventReplay:gc.alloc.rate.norm    BTCUSDT  thrpt  5  152.6M ± 3.2M  B/op
BinanceRiskControlBenchmark.benchmarkEventReplay:gc.count              BTCUSDT  thrpt  5   45.00          counts
BinanceRiskControlBenchmark.benchmarkEventReplay:gc.time               BTCUSDT  thrpt  5  120.00          ms
```

| Metric | What it means | Healthy range |
|--------|---------------|---------------|
| **gc.alloc.rate** | Memory allocation rate | < 1 GB/sec |
| **gc.alloc.rate.norm** | Bytes allocated per operation | Lower is better; < 200 MB/op is reasonable |
| **gc.count** | Number of GC pauses during the test | Fewer is better |
| **gc.time** | Total time spent in GC | < 5% of total benchmark time |

> [!WARNING]
> If `gc.alloc.rate.norm` exceeds **500 MB/op** or `gc.time` is a significant fraction of measurement time, you may have a memory leak. Check that the `CLEANUP_RetractProcessedEvent` rule is retracting processed events.

---

### Per-Invocation Output (During Run)

During the benchmark, each invocation prints a line:

```
[Invocation 1] Events: 9085 | Rules fired: 45231 | Duration: 284 ms | Throughput: 31990.49 events/sec
```

At the end of each trial (per symbol), a cumulative summary is printed:

```
=== Trial Summary [BTCUSDT] ===
Total invocations:      85
Total events processed: 772225
Total rules fired:      3844635
Total time elapsed:     24140 ms (24.14 s)
Avg throughput:         31990.49 events/sec
==============================
```

This shows **per-invocation** metrics (one complete replay). Use these to spot outliers or warm-up effects — early invocations are typically slower as the JIT compiler warms up.

---

### Comparing Rule Sets

| Rule Set | Rules | Expected Rules Fired (BTCUSDT) | Expected ops/s | Use Case |
|----------|-------|-------------------------------|----------------|----------|
| `taxonomy.drl` | 70 | ~40,000 – 55,000 | 1.0 – 8.0 | Full risk control benchmark (default) |
| `demo.drl` | 10 | ~18,000 – 27,000 | 5.0 – 15.0+ | Quick infrastructure verification |

---

## Troubleshooting

### Issue: OutOfMemoryError

**Solution:** Increase heap size
```bash
java -Xms4g -Xmx4g -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

### Issue: Rules compilation errors

**Cause:** DRL syntax error in a custom rules file
**Solution:** Use the default `taxonomy.drl` (verified working) or check your custom DRL for `modify()` syntax issues

### Issue: No events loaded

**Cause:** Dataset not found
**Solution:** Verify `src/main/resources/data/run_20260216_0632_10sym/` exists

### Issue: ClassCastException with SessionClock

**Cause:** KieSession not configured with SessionPseudoClock
**Solution:** Already fixed in BinanceRulesProvider.java

---

## Performance Tuning

### JVM Options

```bash
java -Xms4g -Xmx4g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+AlwaysPreTouch \
     -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

### Drools Options

Edit `BinanceRulesProvider.java`:
```java
KieSessionConfiguration config = KieServices.Factory.get().newKieSessionConfiguration();
config.setOption(ClockTypeOption.PSEUDO);
config.setOption(TimedRuleExecutionOption.YES); // Enable timing
```

---

## CI/CD Integration

### Maven Command

```bash
mvn clean verify \
  -Dbenchmark.skip=false \
  -Dbenchmark.forks=1 \
  -Dbenchmark.iterations=3
```

### Docker

```dockerfile
FROM openjdk:11-jdk
COPY target/drools-benchmarks-binance-cep.jar /app/benchmark.jar
CMD ["java", "-jar", "/app/benchmark.jar", "BinanceRiskControlBenchmark"]
```

---

## Next Steps

1. **Verify Installation**: Run quick test
2. **Baseline Measurement**: Run full JMH benchmark
3. **Profile**: Use GC profiler to identify bottlenecks
4. **Optimize**: Tune based on profiling results
5. **Scale**: Test with multiple symbols
6. **Production**: Deploy with monitoring

---

## Support

- **Dataset**: `run_20260216_0632_10sym` (67K events, 10 symbols)
- **Rules**: `taxonomy.drl` (70 rules, default) or `demo.drl` (10 rules, lightweight)
- **Clock**: SessionPseudoClock for deterministic replay
