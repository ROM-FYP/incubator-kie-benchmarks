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
Rules fired: 11578
Duration: 10686 ms
Throughput: 850.18 events/sec
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

**Basic run:**
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
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

**Use demo.drl (10 rules, default):**
```bash
mvn exec:java -Dexec.mainClass="org.kie.benchmark.binance.BinanceRiskControlBenchmark"
```

**Use taxonomy.drl (70 rules, needs syntax fixes):**
```bash
mvn exec:java \
  -Dexec.mainClass="org.kie.benchmark.binance.BinanceRiskControlBenchmark" \
  -Dbinance.rules.file=/rules/taxonomy.drl
```

### Symbol Selection

Edit `BinanceRiskControlBenchmark.java`:
```java
@Param({"BTCUSDT", "ETHUSDT", "BNBUSDT"})
private String symbol;
```

---

## Benchmark Variants

### Single Symbol (Current)
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

### Multi-Symbol (Future)
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceMultiSymbolBenchmark
```

### Latency Benchmark (Future)
```bash
java -jar target/drools-benchmarks-binance-cep.jar BinanceLatencyBenchmark
```

---

## Output Interpretation

### JMH Output Example

```
Benchmark                                    Mode  Cnt    Score   Error  Units
BinanceRiskControlBenchmark.benchmarkEventReplay  thrpt    5  850.18 ± 45.2  ops/s
```

**Metrics:**
- **Mode**: `thrpt` = throughput (events/sec)
- **Cnt**: Number of measurement iterations
- **Score**: Average throughput
- **Error**: Standard deviation
- **Units**: Operations per second

### Quick Test Output

```
Events processed: 9085        # Total events replayed
Rules fired: 11578            # Total rule activations
Duration: 10686 ms            # Wall-clock time
Throughput: 850.18 events/sec # Calculated throughput
```

---

## Troubleshooting

### Issue: OutOfMemoryError

**Solution:** Increase heap size
```bash
java -Xms4g -Xmx4g -jar target/drools-benchmarks-binance-cep.jar BinanceRiskControlBenchmark
```

### Issue: Rules compilation errors

**Cause:** Using taxonomy.drl with syntax errors  
**Solution:** Use demo.drl (default) or fix taxonomy.drl modify() syntax

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
- **Rules**: `demo.drl` (10 rules, working) or `taxonomy.drl` (70 rules, needs fixes)
- **Clock**: SessionPseudoClock for deterministic replay
- **Throughput**: ~850 events/sec baseline (demo.drl, BTCUSDT)
