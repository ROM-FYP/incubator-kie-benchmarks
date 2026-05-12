# Wikimedia CEP Benchmark — RuleML+RR 2026

> **Branch:** `ruleml-2026/wikimedia-cep-clean` (standalone) · `ruleml-2026/cep-benchmarks-unified` (merged)

A **production-grade, sequential-baseline + Drools built-in parallel** CEP benchmark for Wikimedia content moderation, part of the tri-domain RuleML+RR 2026 benchmark suite.

---

## Module Structure

```
drools-benchmarks-cep-wikimedia/
├── src/main/java/org/kie/benchmark/cep/wikimedia/
│   ├── jmh/WikimediaJmhBenchmark.java       ← JMH: mode={baseline,PARALLEL_EVALUATION,FULLY_PARALLEL}
│   ├── WikimediaHeapProfileMain.java         ← Standalone heap/GC profiler → JSON
│   ├── CepSessionFactory.java                ← KieBase factory (baseline + parallel modes)
│   ├── EventReplayer.java                    ← JSONL replay with PseudoClock
│   ├── WikimediaEventSource.java             ← Streaming event source
│   └── model/                               ← 11 fact types (WikiEvent, Alert, BotAction, …)
├── src/test/java/org/kie/benchmark/cep/wikimedia/
│   └── WikimediaCharacterizationCollector.java ← 21-dim static + dynamic benchmark profile
├── src/main/resources/
│   ├── rules/wikimedia_content_moderation_join_heavy.drl  ← canonical benchmark ruleset
│   ├── rules/wikimedia_content_moderation.drl             ← lightweight variant
│   └── data/data/split_{400k,800k,1200k,1600k}.jsonl      ← event dataset splits
└── docs/
    ├── README.md                             ← This file
    └── semantic_overview.md                  ← Domain rule semantics
```

---

## Quick Start

### 1. Build
```bash
cd drools-benchmarks-parent/drools-benchmarks-cep-wikimedia
mvn clean package -DskipTests
```

### 2. Run JMH Benchmark (all 3 modes × 4 dataset splits)
```bash
# All modes, default dataset (split_400k)
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="WikimediaJmhBenchmark -rf json -rff output/jmh_results.json"

# Single mode, specific split:
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="WikimediaJmhBenchmark \
    -p mode=baseline \
    -p dataFile=src/main/resources/data/data/split_800k.jsonl \
    -rf json -rff output/jmh_baseline_800k.json"
```

### 3. Run Heap / GC Profiler
```bash
mvn exec:java \
  -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.WikimediaHeapProfileMain \
  -Dexec.args="--dataset src/main/resources/data/data/split_400k.jsonl \
               --mode baseline --trials 3 \
               --output output/heap_wikimedia_baseline.json"

# Repeat for: --mode PARALLEL_EVALUATION  and  --mode FULLY_PARALLEL
```

### 4. Run JMH with Built-in GC Profiler
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="WikimediaJmhBenchmark -prof gc -rf json -rff output/jmh_gc.json"
```

### 5. Run Flame Graph Profiling (async-profiler)
```bash
mvn exec:java -Dexec.mainClass=org.openjdk.jmh.Main \
  -Dexec.args="WikimediaJmhBenchmark -p mode=baseline \
    -prof async:output=flamegraph;dir=output/flames"
```
Output: `output/flames/*.html`

### 6. Run Characterization Collector
```bash
mvn exec:java \
  -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.WikimediaCharacterizationCollector \
  -Dexec.classpathScope=test \
  --no-transfer-progress

# Optional args: <dataFile> <maxEvents>
# Example: src/main/resources/data/data/split_400k.jsonl 50000
```

---

## Benchmark Modes

| Mode | Description | KieBase config |
|------|-------------|----------------|
| `baseline` | Single-session sequential evaluation | Default |
| `PARALLEL_EVALUATION` | Drools parallel LHS (pattern matching) | `CepSessionFactory(path, "PARALLEL_EVALUATION")` |
| `FULLY_PARALLEL` | Drools parallel LHS + RHS (agenda) | `CepSessionFactory(path, "FULLY_PARALLEL")` |

---

## Dataset

| Property | Value |
|----------|-------|
| Domain | Wikimedia real-time edit stream |
| Event type | `WikiEvent` (JSONL) |
| Splits | 400k / 800k / 1200k / 1600k events |
| Timestamp | Event-time replay via `SessionPseudoClock` |

> **Reproduction:** Dataset cannot be redistributed (Wikimedia ToS).  
> Collect a fresh stream via the Wikimedia EventStreams SSE API:  
> `https://stream.wikimedia.org/v2/stream/recentchange`

---

## Characterization Output (example)

```
A1  Total rules (DRL parse)            42
A2  Temporal rules                     18
A3  Join-heavy rules (≥2 patterns)     31
C1  Total rules fired             104,521
C2  Firings per event                2.61
C5  Event selectivity              73.4%
C9  Peak WM fact count               847
D3  Replay throughput           31,200 ev/s
```
