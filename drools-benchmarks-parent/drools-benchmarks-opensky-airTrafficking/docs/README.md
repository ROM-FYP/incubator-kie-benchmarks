# OpenSky AirTraffic CEP Benchmark — RuleML+RR 2026

> **Branch:** `ruleml-2026/opensky-cep-clean` (standalone) · `ruleml-2026/cep-benchmarks-unified` (merged)

A **production-grade, sequential-baseline + Drools built-in parallel** CEP benchmark for OpenSky air-traffic conflict detection, part of the tri-domain RuleML+RR 2026 benchmark suite.

---

## Module Structure

```
drools-benchmarks-opensky-airTrafficking/
├── src/main/java/bench/opensky/
│   ├── benchmark/
│   │   ├── OpenSkyFullReplayBenchmark.java  ← JMH: mode={baseline,PARALLEL_EVALUATION,FULLY_PARALLEL}
│   │   └── OpenSkyHeapProfileMain.java      ← Standalone heap/GC profiler → JSON
│   ├── replay/
│   │   ├── OpenSkyReplayEngine.java         ← CEP engine: init(mode), ingestEvent(), dispose()
│   │   └── OpenSkyJsonlLoader.java          ← Flat JSONL loader
│   └── model/                              ← 11 fact types (OpenSkyStateVector, Alert, ConflictCandidate, …)
├── src/test/java/bench/opensky/
│   └── OpenSkyCharacterizationCollector.java ← 21-dim static + dynamic benchmark profile
├── src/main/resources/
│   ├── airTraffick_rules.drl                ← canonical benchmark ruleset (102 rules)
│   └── data/data/split_{400k,800k,1200k,1600k}.jsonl  ← event dataset splits
└── docs/
    └── README.md                            ← This file
```

---

## Quick Start

### 1. Build
```bash
cd drools-benchmarks-parent/drools-benchmarks-opensky-airTrafficking
mvn clean package -DskipTests
```

### 2. Run JMH Benchmark (all 3 modes × 4 dataset splits)
```bash
# All modes, default dataset (split_400k)
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="OpenSkyFullReplayBenchmark -rf json -rff output/jmh_results.json"

# Single mode, specific split:
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="OpenSkyFullReplayBenchmark \
    -p mode=baseline \
    -p dataset=data/data/split_800k.jsonl \
    -rf json -rff output/jmh_baseline_800k.json"
```

### 3. Run Heap / GC Profiler
```bash
mvn exec:java \
  -Dexec.mainClass=bench.opensky.benchmark.OpenSkyHeapProfileMain \
  -Dexec.args="--dataset data/data/split_400k.jsonl \
               --mode baseline --trials 3 \
               --output output/heap_opensky_baseline.json"

# Repeat for: --mode PARALLEL_EVALUATION  and  --mode FULLY_PARALLEL
```

### 4. Run JMH with Built-in GC Profiler
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="OpenSkyFullReplayBenchmark -prof gc -rf json -rff output/jmh_gc.json"
```

### 5. Run Flame Graph Profiling (async-profiler)
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="OpenSkyFullReplayBenchmark -p mode=baseline \
    -prof async:output=flamegraph;dir=output/flames"
```
Output: `output/flames/*.html`

### 6. Run Characterization Collector
```bash
mvn exec:java \
  -Dexec.mainClass=bench.opensky.OpenSkyCharacterizationCollector \
  -Dexec.classpathScope=test \
  --no-transfer-progress

# Optional arg: <dataFile>
# Example: data/data/split_400k.jsonl
```

---

## Benchmark Modes

| Mode | Description | Engine config |
|------|-------------|---------------|
| `baseline` | Single-session sequential evaluation | `engine.init()` |
| `PARALLEL_EVALUATION` | Drools parallel LHS (pattern matching) | `engine.init("PARALLEL_EVALUATION")` |
| `FULLY_PARALLEL` | Drools parallel LHS + RHS (agenda) | `engine.init("FULLY_PARALLEL")` |

---

## Dataset

| Property | Value |
|----------|-------|
| Domain | OpenSky Network — ADS-B air traffic state vectors |
| Event type | `OpenSkyStateVector` (JSONL flat format) |
| Splits | 400k / 800k / 1200k / 1600k events |
| Time model | `snapshotTime` (seconds) → pseudo-clock (ms) |

> **Reproduction:** Dataset cannot be redistributed (OpenSky Network ToS).  
> Collect via the OpenSky REST API:  
> `GET https://opensky-network.org/api/states/all`

---

## Domain Rules Summary

The `airTraffick_rules.drl` implements a **conflict detection and safety monitoring** system:

| Rule Group | Description |
|------------|-------------|
| Track management | State vector ingestion, track quality, kinematic derivation |
| Proximity detection | Horizontal + vertical separation computation (CPA) |
| Conflict classification | Short-term conflict alert (STCA) generation |
| Alert lifecycle | Alert acknowledgement, inhibition, timeout |
| Grid indexing | Spatial cell partitioning for efficient proximity queries |

---

## Characterization Output (example)

```
A1  Total rules (DRL parse)           102
A2  Temporal rules                     34
A3  Join-heavy rules (≥2 patterns)     67
C1  Total rules fired             287,431
C2  Firings per event                0.72
C5  Event selectivity              45.2%
C9  Peak WM fact count             2,847
D3  Replay throughput           18,500 ev/s
```
