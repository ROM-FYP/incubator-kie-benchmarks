# Reproducible Wikimedia CEP Benchmark

This package (`org.kie.benchmark.cep.reproducible.wikimedia`) contains a standalone, reproducible version of the Drools CEP Wikimedia benchmark. Unlike the live benchmark which connects to the public Wikimedia SSE stream, this version replays recorded events from an NDJSON file to ensure deterministic execution and consistent performance measurements.

## Overview

The `ReproducibleWikimediaRunner` is the main entry point. It simulates a stream of Wikimedia edit events by reading from a dataset and inserting them into a Drools CEP session. It respects the relative timing of events to accurately simulate the original load profile (or a scaled version of it).

### Key Features
*   **Deterministic Replay**: Uses a static dataset (NDJSON) for consistent runs across different environments.
*   **Standalone Execution**: Does not require an internet connection or external stream availability.
*   **Configurable Resources**: Allows overriding the input data and rule files via command-line arguments.
*   **Performance Metrics**: Reports throughput (events/sec) and total processing time.

## Architecture

*   **Runner**: `ReproducibleWikimediaRunner` initializes resources and runs the loop.
*   **Source**: `NdjsonFileEventSource` reads events line-by-line from the NDJSON file.
*   **Model**: `WikiEvent` POJO represents the JSON data.
*   **Rules**: Standard Drools DRL files (e.g., `advanced_viral_rules.drl`) process the events.
*   **Metrics**: `ReplayMetrics` tracks event counts and duration.

## How to Run

This benchmark is packaged as part of the `drools-benchmarks-cep-wikimedia` module. The `maven-shade-plugin` is configured to set `ReproducibleWikimediaRunner` as the main class of the uber-jar.

### 1. Build the Project
From the `drools-benchmarks-parent` directory:
```bash
mvn clean install -DskipTests
```

### 2. Run the Benchmark
Navigate to the module directory and run the shaded jar:
```bash
cd drools-benchmarks-cep-wikimedia
java -jar target/drools-benchmarks-cep-wikimedia.jar
```

By default, this will run using the bundled 60-minute dataset (`wikimedia_60min.ndjson`) and the advanced rules.

## Parameters

The runner accepts optional command-line arguments to override the default resources.

### Command Line Arguments

| Argument | Description | Default |
| :--- | :--- | :--- |
| `--ndjson <path>` | Path to a local NDJSON file containing the event stream. | `null` |
| `--drl <path>` | Path to a local DRL file containing the rules. | `null` |
| `--ndjsonResource <path>` | Classpath resource path for the event stream. | `reproducible/wikimedia/data/wikimedia_60min.ndjson` |
| `--drlResource <path>` | Classpath resource path for the rules. | `reproducible/wikimedia/rules/advanced_viral_rules.drl` |

### Examples

**Run with a custom dataset:**
```bash
java -jar target/drools-benchmarks-cep-wikimedia.jar --ndjson /tmp/my-dataset.ndjson
```

**Run with custom rules:**
```bash
java -jar target/drools-benchmarks-cep-wikimedia.jar --drl /tmp/new-rules.drl
```

**Run with both custom data and rules:**
```bash
java -jar target/drools-benchmarks-cep-wikimedia.jar \
    --ndjson /tmp/dataset_24h.ndjson \
    --drl /tmp/experimental_rules.drl
```

## Input Data Format (NDJSON)

The input file must be in **Newline Delimited JSON** format. Each line represents a single `WikiEvent` JSON object.

Example line:
```json
{"user":"BotUser","type":"edit","timestamp":1615800000000,"title":"Main Page","...": "..."}
```

The runner expects at least a `timestamp` field (in milliseconds) to handle the replay timing correctly.

## Metrics Output

After execution completes, the benchmark prints a summary:

```text
======= Replay Complete =======

Events     : 145023
Duration   : 60.012 min
Throughput : 40.234 events/sec
```

*   **Events**: Total number of events ingested and processed.
*   **Duration**: Total time taken for the replay.
*   **Throughput**: Average processing speed.
