# Drools CEP Benchmark — Binance Market Risk

JMH benchmark replaying **1.6 M recorded Binance WebSocket events** through
a 108-rule Drools KIE session (`taxonomy.drl`) to measure event throughput and
peak heap memory under three execution modes.

---

## Module Structure

```
src/main/java/org/kie/benchmark/binance/
  BinanceFullDatasetBenchmark.java   ← primary JMH benchmark (all symbols, full replay)
  BinanceRiskControlBenchmark.java   ← per-symbol JMH benchmark
  BinanceHeapProfileMain.java        ← standalone heap profiler (non-JMH)
  model/                             ← 12 POJO fact types
  provider/                          ← BinanceEventProvider, BinanceRulesProvider
  util/                              ← EventReplayController, SegmentReader

src/main/resources/
  rules/taxonomy.drl                 ← 108 Drools rules (single active ruleset)
  data/                              ← NOT in git — must be present at runtime
    split_1600k/                     ← baseline dataset (1,600,000 events, 10 symbols)
    split_1600k_cov25/               ← ~25% rule coverage variant
    split_1600k_cov50/               ← ~50% rule coverage variant
```

> **Dataset files are not version-controlled** (files exceed 100 MB).
> Ensure the `data/` directory is populated before running benchmarks.

---

## Build

```bash
# From drools-benchmarks-parent/
mvn clean install -DskipTests -pl drools-benchmarks-common,drools-benchmarks-binance-cep -am

# Or from this module directory (after common is installed):
mvn clean package -DskipTests
```

Output: `target/drools-benchmarks-binance-cep.jar` (self-contained shaded JAR)

---

## Execution Modes

| Mode | Description |
|------|-------------|
| `baseline` | Single-session sequential evaluation |
| `PARALLEL_EVALUATION` | Drools built-in parallel LHS evaluation |
| `FULLY_PARALLEL` | Drools built-in fully parallel (LHS + RHS) |

---

## 1. Throughput Measurement

### Smoke test — verify dataset and session setup
```bash
java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 0 -i 1 -f 0
```

### Standard run — baseline dataset
```bash
mkdir -p results

java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_binance_baseline.json -rf json
```

### Standard run — cov50 dataset
```bash
java -Xms4g -Xmx24g \
  -Dbinance.dataset=split_1600k_cov50 \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_binance_cov50.json -rf json
```

### Standard run — cov25 dataset
```bash
java -Xms4g -Xmx24g \
  -Dbinance.dataset=split_1600k_cov25 \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_binance_cov25.json -rf json
```

### Interpreting results

JMH reports **ops/sec** (complete dataset replays per second).

```
events/sec = reported_score × 1,600,000
```

Per-invocation throughput is also logged directly to stdout:
```
[Invocation 1] Events: 1,600,000 | Rules fired: X | Duration: Y ms | Throughput: Z events/sec
```

---

## 2. Peak Heap Memory

Run with JVM GC logging enabled. The maximum live-set value recorded across
all GC cycles represents the true peak heap working set.

### baseline dataset
```bash
mkdir -p results/gc

java -Xms4g -Xmx24g \
  -Xlog:gc*:file=results/gc/gc_binance_baseline.log:time,uptime,tags \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1
```

### cov50 dataset
```bash
java -Xms4g -Xmx24g \
  -Dbinance.dataset=split_1600k_cov50 \
  -Xlog:gc*:file=results/gc/gc_binance_cov50.log:time,uptime,tags \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1
```

### cov25 dataset
```bash
java -Xms4g -Xmx24g \
  -Dbinance.dataset=split_1600k_cov25 \
  -Xlog:gc*:file=results/gc/gc_binance_cov25.log:time,uptime,tags \
  -jar target/drools-benchmarks-binance-cep.jar \
  BinanceFullDatasetBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1
```

### Extract peak heap from GC log
```bash
# Returns peak live heap in MB
grep -oP '\d+M->\K\d+(?=M\(\d)' results/gc/gc_binance_baseline.log \
  | sort -n | tail -1
```

---

## Dataset Selection

The active dataset is controlled via the `binance.dataset` system property:

| Dataset | System property value | Event count |
|---------|-----------------------|-------------|
| Baseline | `split_1600k` *(default)* | 1,600,000 |
| 50% coverage | `split_1600k_cov50` | 1,600,000 |
| 25% coverage | `split_1600k_cov25` | 1,600,000 |

```bash
# Example: select cov50 dataset
java -Dbinance.dataset=split_1600k_cov50 -jar target/drools-benchmarks-binance-cep.jar ...
```

---

## Results

| Dataset | Throughput (events/sec) | Peak Heap (MB) |
|---------|------------------------|----------------|
| split_1600k | — | — |
| split_1600k_cov50 | — | — |
| split_1600k_cov25 | — | — |
