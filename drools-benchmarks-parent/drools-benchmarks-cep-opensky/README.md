# Drools Benchmark CEP — OpenSky Air Traffic

This module contains the **OpenSky Air Traffic** benchmark suite for the Drools Complex Event Processing (CEP) engine. This benchmark evaluates Drools' capability to process high-frequency geospatial state vectors representing live aircraft positions and telemetry.

## Dataset

The dataset consists of flat JSONL records representing aircraft state vectors from the OpenSky Network. The dataset contains fields such as `icao24`, `callsign`, `originCountry`, `timePosition`, `lastContact`, `longitude`, `latitude`, `baroAltitude`, `geoAltitude`, `velocity`, `trueTrack`, `verticalRate`, `onGround`, `spi`, `positionSource`, etc.

The dataset must be placed in `src/main/resources/data/`.
By default, the benchmark looks for `src/main/resources/data/opensky_states_20260314_143703_flat.jsonl`.

*Note: The `data/` directory is excluded from Git and the shaded JAR to prevent repository bloat and build-time `OutOfMemoryError`s.*

## Setup & Build

Build the shaded benchmark JAR from the module directory:

```bash
cd drools-benchmarks-cep-opensky
mvn clean package -DskipTests
```

This generates `target/drools-benchmarks-cep-opensky.jar`.

## Execution Modes

| Mode | Description |
|------|-------------|
| `baseline` | Single-session sequential evaluation |
| `PARALLEL_EVALUATION` | Drools built-in parallel LHS evaluation |
| `FULLY_PARALLEL` | Drools built-in fully parallel (LHS + RHS) |

---

## 1. Throughput Measurement

### Standard run — baseline dataset
```bash
mkdir -p results

java -Xms4g -Xmx24g \
  -jar target/drools-benchmarks-cep-opensky.jar \
  OpenSkyFullReplayBenchmark \
  -p mode=baseline \
  -p dataFile=src/main/resources/data/opensky_states_20260314_143703_flat.jsonl \
  -wi 1 -i 3 -f 1 \
  -rff results/jmh_opensky_baseline.json -rf json
```

## 2. Peak Heap Measurement

The peak heap footprint is measured using a specialized runner that injects `-XX:+PrintGCDetails` (or equivalent GC logs).

### Run GC Profiler
```bash
mkdir -p results

java -Xms4g -Xmx24g \
  -Xlog:gc*=info:file=results/gc_opensky_baseline.log:time,uptime,level,tags \
  -cp target/drools-benchmarks-cep-opensky.jar \
  bench.opensky.benchmark.OpenSkyHeapProfileMain \
  src/main/resources/data/opensky_states_20260314_143703_flat.jsonl
```

### Analyze GC Logs

After execution, parse the log to find the maximum memory consumption (peak heap) post-garbage collection.

```bash
grep "Pause Full" results/gc_opensky_baseline.log
```
Look for lines indicating the heap size after full GC (e.g., `2100M->1500M(24000M)`), where the second number represents the live dataset retention.
