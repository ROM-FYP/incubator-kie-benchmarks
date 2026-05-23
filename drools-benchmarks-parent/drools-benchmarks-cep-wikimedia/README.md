# Drools CEP Benchmark — Wikimedia Event Stream

JMH benchmark replaying **1.6 M recorded Wikimedia recent-change events** through
a 13-rule Drools KIE session (`wikimedia_content_moderation_join_heavy.drl`) to measure 
event throughput and peak heap memory under three execution modes.

---

## Module Structure

```
src/main/java/org/kie/benchmark/cep/wikimedia/
  jmh/
    WikimediaJmhBenchmark.java       ← primary JMH benchmark
    WikimediaBuiltinParallelJmhBenchmark.java ← parallel evaluation benchmark
  model/                             ← 15 POJO fact types (WikiEvent, BotAction, etc.)
  CepSessionFactory.java             ← session creation and configuration
  WikimediaEventSource.java          ← event provider and parser

src/main/resources/
  rules/wikimedia_content_moderation_join_heavy.drl ← 13 Drools CEP rules
  data/                              ← NOT in git — must be present at runtime
    data/split_1600k.jsonl           ← baseline dataset (1,600,000 events)
    data/split_1600k_cov25.jsonl     ← ~25% rule coverage variant
    data/split_1600k_cov50.jsonl     ← ~50% rule coverage variant
```

> **Dataset files are not version-controlled** (files exceed 100 MB).
> Ensure the `data/` directory is populated before running benchmarks.

---

## Build

```bash
# From drools-benchmarks-parent/
mvn clean install -DskipTests -pl drools-benchmarks-common,drools-benchmarks-cep-wikimedia -am

# Or from this module directory (after common is installed):
mvn clean package -DskipTests
```

Output: `target/drools-benchmarks-cep-wikimedia.jar` (self-contained shaded JAR)

---

## Execution Modes

| Mode | Description |
|------|-------------|
| `baseline` | Single-session sequential evaluation |
| `PARALLEL_EVALUATION` | Drools built-in parallel LHS evaluation |
| `FULLY_PARALLEL` | Drools built-in fully parallel (LHS + RHS) |

---

## 1. Throughput Measurement

### Smoke test — verify dataset and session setup (using 400k subset)
```bash
java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -p dataFile=src/main/resources/data/data/split_400k.jsonl \
  -wi 0 -i 1 -f 0
```

### Standard run — baseline dataset
```bash
mkdir -p results

java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_wikimedia_baseline.json -rf json
```

### Standard run — cov50 dataset
```bash
java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -p dataFile=src/main/resources/data/data/split_1600k_cov50.jsonl \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_wikimedia_cov50.json -rf json
```

### Standard run — cov25 dataset
```bash
java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -p dataFile=src/main/resources/data/data/split_1600k_cov25.jsonl \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_wikimedia_cov25.json -rf json
```

### Interpreting results

JMH reports **ops/sec** (complete dataset replays per second).

```
events/sec = reported_score × 1,600,000
```

Per-invocation throughput is also logged directly to stdout:
```
[baseline Invocation 1] Events: 1,600,000 | Rules fired: X | Duration: Y ms | Throughput: Z events/sec
```

---

## 2. Peak Heap Memory

Run with JVM GC logging enabled. The maximum live-set value recorded across
all GC cycles represents the true peak heap working set.

### baseline dataset
```bash
mkdir -p results/gc

java -Xms4g -Xmx24g \
  -Xlog:gc*:file=results/gc/gc_wikimedia_baseline.log:time,uptime,tags \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -wi 1 -i 3 -f 1
```

### cov50 dataset
```bash
java -Xms4g -Xmx24g \
  -Xlog:gc*:file=results/gc/gc_wikimedia_cov50.log:time,uptime,tags \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -p dataFile=src/main/resources/data/data/split_1600k_cov50.jsonl \
  -wi 1 -i 3 -f 1
```

### cov25 dataset
```bash
java -Xms4g -Xmx24g \
  -Xlog:gc*:file=results/gc/gc_wikimedia_cov25.log:time,uptime,tags \
  -jar target/drools-benchmarks-cep-wikimedia.jar \
  WikimediaJmhBenchmark \
  -p mode=baseline \
  -p dataFile=src/main/resources/data/data/split_1600k_cov25.jsonl \
  -wi 1 -i 3 -f 1
```

### Extract peak heap from GC log
```bash
# Returns peak live heap in MB
grep -oP '\d+M->\K\d+(?=M\(\d)' results/gc/gc_wikimedia_baseline.log \
  | sort -n | tail -1
```

---

## Dataset Selection

The active dataset is controlled via the `dataFile` JMH parameter.

| Dataset | Param value (`-p dataFile=...`) | Event count |
|---------|---------------------------------|-------------|
| Baseline | `src/main/resources/data/data/split_1600k.jsonl` *(default)* | 1,600,000 |
| 50% coverage | `src/main/resources/data/data/split_1600k_cov50.jsonl` | 1,600,000 |
| 25% coverage | `src/main/resources/data/data/split_1600k_cov25.jsonl` | 1,600,000 |

```bash
# Example: select cov50 dataset
java -jar target/drools-benchmarks-cep-wikimedia.jar WikimediaJmhBenchmark -p dataFile=src/main/resources/data/data/split_1600k_cov50.jsonl ...
```

---

## Results

| Dataset | Throughput (events/sec) | Peak Heap (MB) |
|---------|------------------------|----------------|
| split_1600k | — | — |
| split_1600k_cov50 | — | — |
| split_1600k_cov25 | — | — |
