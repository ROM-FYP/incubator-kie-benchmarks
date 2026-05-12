# Binance CEP Benchmark — RuleML+RR 2026

> **Branch:** `ruleml-2026/binance-cep-clean` (standalone) · `ruleml-2026/cep-benchmarks-unified` (merged)

A **production-grade, sequential-baseline + Drools built-in parallel** CEP benchmark for Binance market risk control, part of the tri-domain RuleML+RR 2026 benchmark suite.

---

## Module Structure

```
drools-benchmarks-binance-cep/
├── src/main/java/org/kie/benchmark/binance/
│   ├── BinanceFullDatasetBenchmark.java     ← JMH: mode={baseline,PARALLEL_EVALUATION,FULLY_PARALLEL}
│   ├── BinanceRiskControlBenchmark.java     ← JMH: per-symbol throughput
│   ├── BinanceHeapProfileMain.java          ← Standalone heap/GC profiler → JSON
│   ├── model/                               ← 11 fact types (MarketEvent, RiskSignal, …)
│   ├── provider/                            ← BinanceRulesProvider + BinanceEventProvider
│   └── util/                                ← EventReplayController, SegmentReader
├── src/test/java/org/kie/benchmark/binance/
│   └── CharacterizationCollector.java       ← 21-dim static + dynamic benchmark profile
├── src/main/resources/
│   ├── rules/taxonomy.drl                   ← 108-rule risk-control ruleset
│   └── data/run_20260311_1340_10sym/        ← 1.6M events, 10 symbols, 30 min
└── docs/                                    ← This folder
```

---

## Quick Start

### 1. Build
```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep
mvn clean package -DskipTests
```

### 2. Run JMH Benchmark (all 3 modes)
```bash
# Full dataset — baseline + PARALLEL_EVALUATION + FULLY_PARALLEL
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="BinanceFullDatasetBenchmark -rf json -rff output/jmh_results.json"
```

To run a single mode only:
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="BinanceFullDatasetBenchmark -p mode=baseline -rf json -rff output/jmh_baseline.json"
```

### 3. Run Heap / GC Profiler
```bash
mvn exec:java -Dexec.mainClass=org.kie.benchmark.binance.BinanceHeapProfileMain \
  -Dexec.args="--mode baseline --trials 3 --output output/heap_binance_baseline.json"

# Repeat for each mode:
# --mode PARALLEL_EVALUATION
# --mode FULLY_PARALLEL
```

### 4. Run JMH with Built-in GC Profiler
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="BinanceFullDatasetBenchmark -prof gc -rf json -rff output/jmh_gc.json"
```

### 5. Run Flame Graph Profiling (async-profiler)
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="BinanceFullDatasetBenchmark -p mode=baseline \
    -prof async:output=flamegraph;dir=output/flames"
```
Output: `output/flames/*.html` — open in browser.

### 6. Run Characterization Collector
```bash
mvn exec:java \
  -Dexec.mainClass=org.kie.benchmark.binance.CharacterizationCollector \
  -Dexec.classpathScope=test \
  --no-transfer-progress
```
Prints a full A1–D4 dimension table covering static rule properties, dependency structure, runtime coverage, WM dynamics, and data profile.

---

## Benchmark Modes

| Mode | Description | KieBase config |
|------|-------------|----------------|
| `baseline` | Single-session sequential evaluation | Default |
| `PARALLEL_EVALUATION` | Drools parallel LHS (pattern matching) | `multithread="true"` |
| `FULLY_PARALLEL` | Drools parallel LHS + RHS (agenda) | `multithread="true"` + `drools.parallelAgenda=true` |

---

## Dataset

| Property | Value |
|----------|-------|
| Dataset ID | `run_20260311_1340_10sym` |
| Events | ~1.6M market events |
| Symbols | 10 (BTCUSDT, ETHUSDT, …) |
| Duration | 30 minutes real-time |
| Streams | trades, bestBidAsk, depth, markPrice, liquidation |

> **Reproduction:** Dataset cannot be redistributed (Binance ToS).  
> See [`docs/dataset_reproduction.md`](dataset_reproduction.md) for the Go-based collection tool.

---

## Reading JMH Results

| Column | Meaning |
|--------|---------|
| `Score` | Throughput (ops/sec for `BinanceFullDatasetBenchmark`) |
| `Error` | ±99.9% CI — keep below 5% for publication-grade results |
| `mode` | Execution mode param |

**Flame graph reading tip:** Hot methods in `org.drools.core.common` indicate Rete network evaluation time; hot methods in `BinanceEventProvider` or `EventReplayController` indicate I/O or event deserialization overhead.
