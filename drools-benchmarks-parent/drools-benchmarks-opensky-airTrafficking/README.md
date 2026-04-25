# OpenSky Air Trafficking Drools Benchmark

JMH benchmark module that replays OpenSky ADS-B flat JSONL state vectors through a 100-rule Drools rule engine, measuring throughput and latency of air traffic conflict detection.

## Quick Start

```bash
# Build the uber-jar
cd drools-benchmarks-parent/drools-benchmarks-opensky-airTrafficking
mvn clean package -DskipTests

# Run all benchmarks (default: 3 warmup + 5 measurement iterations)
java -jar target/benchmarks.jar

# Quick smoke test (no forking, 1 iteration)
java -jar target/benchmarks.jar -f 0 -wi 0 -i 1 -r 1s
```

## Architecture

```
bench.opensky.model/         15 POJO fact classes (OpenSkyStateVector, Params, Alert, etc.)
bench.opensky.util/          RuleMathUtil — static math helpers (haversine, nm, CPA)
bench.opensky.replay/        OpenSkyJsonlLoader + OpenSkyReplayEngine
bench.opensky.benchmark/     JMH benchmark class
airTraffick_rules.drl        100 Drools rules (data quality, grid pairing, conflict detection, alerting)
data/                        OpenSky flat JSONL dataset (~37k state vectors)
```

## Benchmark Parameters

| Parameter | Default | Options |
|-----------|---------|---------|
| `dataset` | `data/opensky_flat_20260217_160412.jsonl` | any classpath JSONL |
| `mode` | `GROUP_BY_SNAPSHOT` | `STREAM` |
| `updateStrategy` | `UPSERT_BY_ICAO24` | `INSERT_ONLY` |
| `cleanupStrategy` | `RETRACT_PREVIOUS_SNAPSHOT` | `NONE` |
| `fireStrategy` | `FIRE_ALL_PER_SNAPSHOT` | — |
| `batchSize` | `0` | integer |

Override via JMH `-p` flag:
```bash
java -jar target/benchmarks.jar -p updateStrategy=INSERT_ONLY -p cleanupStrategy=NONE
```

## Dataset

The JSONL file contains flat OpenSky state vectors with 5-second snapshot cadence. Each line is a JSON object with `snapshot_time` (epoch seconds) and nested `state` object containing ADS-B fields (icao24, callsign, lat, lon, altitude, velocity, track, etc.).

## Rules Overview

- **R001–R020**: Track/data quality (stale position, missing fields, anomalies)
- **R021–R040**: Kinematics (speed jumps, turn rate, altitude changes)
- **R041–R055**: Grid cell assignment and pair candidate generation
- **R056–R080**: Conflict detection (distance, TTC, CPA, persistence/hysteresis)
- **R081–R095**: Alerting policy (traffic advisories, safety alerts, inhibition, ack)
- **R096–R100**: Recording/performance hooks
