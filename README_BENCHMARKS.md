# CEP Benchmark Suite — RuleML+RR 2026

> **Branch:** `ruleml-2026/cep-benchmarks-unified`  
> Merged from: `ruleml-2026/binance-cep-clean` · `ruleml-2026/wikimedia-cep-clean` · `ruleml-2026/opensky-cep-clean`

A tri-domain **Complex Event Processing (CEP) benchmark suite** built on Apache Drools 8.  
Designed as the experimental artifact for the **RuleML+RR 2026** submission.

---

## Three Benchmark Domains

| Domain | Module | Events | Rules | Domain |
|--------|--------|--------|-------|--------|
| **Binance** | `drools-benchmarks-binance-cep` | ~1.6M market events (10 sym) | 108 | Financial risk control |
| **Wikimedia** | `drools-benchmarks-cep-wikimedia` | 400k–1600k edit events | 42 | Content moderation |
| **OpenSky** | `drools-benchmarks-opensky-airTrafficking` | 400k–1600k ADS-B vectors | 102 | Air traffic conflict detection |

---

## Symmetric Tool Set (all 3 modules)

Every module provides the same three classes:

| Tool | Class | Purpose |
|------|-------|---------|
| **JMH Benchmark** | `XxxBenchmark` | Throughput / latency (`ms/op`) across `mode` × `dataset` |
| **Heap Profiler** | `XxxHeapProfileMain` | Peak heap, post-dispose heap, WM footprint → JSON |
| **Characterization** | `XxxCharacterizationCollector` | A/B/C/D dimension static + dynamic profile |

---

## Execution Modes (all benchmarks)

```
@Param({"baseline", "PARALLEL_EVALUATION", "FULLY_PARALLEL"})
private String mode;
```

| Mode | What runs |
|------|-----------|
| `baseline` | Single KieSession, sequential Rete evaluation |
| `PARALLEL_EVALUATION` | Drools built-in multi-threaded LHS pattern matching |
| `FULLY_PARALLEL` | Drools built-in fully parallel LHS + agenda (RHS) execution |

---

## Running All Three Benchmarks

### Build everything
```bash
cd drools-benchmarks-parent
mvn clean package -DskipTests \
  -pl drools-benchmarks-binance-cep,drools-benchmarks-cep-wikimedia,drools-benchmarks-opensky-airTrafficking
```

### JMH Benchmarks
```bash
# Binance (Throughput mode)
cd drools-benchmarks-binance-cep
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="BinanceFullDatasetBenchmark -rf json -rff output/jmh_binance.json"

# Wikimedia (SingleShotTime mode, 4 splits)
cd ../drools-benchmarks-cep-wikimedia
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="WikimediaJmhBenchmark -rf json -rff output/jmh_wikimedia.json"

# OpenSky (SingleShotTime mode, 4 splits)
cd ../drools-benchmarks-opensky-airTrafficking
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="OpenSkyFullReplayBenchmark -rf json -rff output/jmh_opensky.json"
```

### Heap Profiling
```bash
# Binance
mvn exec:java -Dexec.mainClass=org.kie.benchmark.binance.BinanceHeapProfileMain \
  -Dexec.args="--mode baseline --trials 3 --output output/heap_binance.json"

# Wikimedia
mvn exec:java -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.WikimediaHeapProfileMain \
  -Dexec.args="--dataset src/main/resources/data/data/split_400k.jsonl \
               --mode baseline --trials 3 --output output/heap_wikimedia.json"

# OpenSky
mvn exec:java -Dexec.mainClass=bench.opensky.benchmark.OpenSkyHeapProfileMain \
  -Dexec.args="--dataset data/data/split_400k.jsonl \
               --mode baseline --trials 3 --output output/heap_opensky.json"
```

### Characterization Collectors
```bash
# Binance
mvn exec:java -pl drools-benchmarks-binance-cep \
  -Dexec.mainClass=org.kie.benchmark.binance.CharacterizationCollector \
  -Dexec.classpathScope=test --no-transfer-progress

# Wikimedia
mvn exec:java -pl drools-benchmarks-cep-wikimedia \
  -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.WikimediaCharacterizationCollector \
  -Dexec.classpathScope=test --no-transfer-progress

# OpenSky
mvn exec:java -pl drools-benchmarks-opensky-airTrafficking \
  -Dexec.mainClass=bench.opensky.OpenSkyCharacterizationCollector \
  -Dexec.classpathScope=test --no-transfer-progress
```

### Flame Graphs (async-profiler)
```bash
# Works for any module — substitute class name
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="BinanceFullDatasetBenchmark -p mode=baseline \
    -prof async:output=flamegraph;dir=output/flames/binance"
```

---

## Dataset Reproduction

All datasets are **excluded from this repository** due to third-party ToS restrictions.

| Domain | Source | Reproduction |
|--------|--------|-------------|
| Binance | Binance WebSocket API | [`binance-cep-collector`](https://github.com/ROM-FYP/binance-cep-collector) (Go) |
| Wikimedia | Wikimedia EventStreams (SSE) | `https://stream.wikimedia.org/v2/stream/recentchange` |
| OpenSky | OpenSky REST API | `https://opensky-network.org/api/states/all` |

See each module's `docs/README.md` for detailed collection instructions.

---

## Module Docs

- [`drools-benchmarks-binance-cep/docs/README.md`](drools-benchmarks-parent/drools-benchmarks-binance-cep/docs/README.md)
- [`drools-benchmarks-cep-wikimedia/docs/README.md`](drools-benchmarks-parent/drools-benchmarks-cep-wikimedia/docs/README.md)
- [`drools-benchmarks-opensky-airTrafficking/docs/README.md`](drools-benchmarks-parent/drools-benchmarks-opensky-airTrafficking/docs/README.md)

---

## Branch Map

```
ruleml-2026/binance-cep-clean      ← standalone Binance benchmark
ruleml-2026/wikimedia-cep-clean    ← standalone Wikimedia benchmark
ruleml-2026/opensky-cep-clean      ← standalone OpenSky benchmark
ruleml-2026/cep-benchmarks-unified ← this branch (all 3 merged)
```
