# Wikimedia CEP Benchmark Utilities

This package (`org.kie.benchmark.cep.wikimedia`) contains utility classes for running Drools CEP benchmarks with real-time Wikimedia event streams and generating mining traces for process analysis.

## Overview

This directory includes three utility runners for different benchmark scenarios:

| Class | Purpose |
|-------|---------|
| `WikimediaStreamRecorder` | Records raw Wikimedia SSE stream data to JSONL files |
| `BalancedBenchmarkRunner` | Runs benchmark with balanced cluster ruleset for Infomap analysis |
| `ContentModerationRunner` | Runs benchmark with semantic content moderation ruleset |

---

## WikimediaStreamRecorder

Records raw Wikimedia SSE stream data to a JSONL file for later replay or analysis.

### Usage

```bash
cd drools-benchmarks-cep-wikimedia
mvn compile -DskipTests
java -cp "target/classes;target/dependency/*" org.kie.benchmark.cep.wikimedia.WikimediaStreamRecorder [durationMinutes]
```

### Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `durationMinutes` | Recording duration in minutes | 5 |

### Output

- **Location**: `src/main/java/org/kie/benchmark/cep/wikimedia/data/`
- **Filename**: `wikimedia_stream_YYYYMMDD_HHMMSS.jsonl`
- **Format**: Newline-delimited JSON (NDJSON)

---

## BalancedBenchmarkRunner

Runs the CEP benchmark using the `new_graph_partitioning_benchmark.drl` ruleset, designed to produce balanced clusters when analyzed with Infomap community detection.

### Usage

```bash
cd drools-benchmarks-cep-wikimedia
mvn compile -DskipTests
java -cp "target/classes;target/dependency/*" org.kie.benchmark.cep.wikimedia.BalancedBenchmarkRunner [durationMinutes]
```

### Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `durationMinutes` | Benchmark duration in minutes | 2 |

### Ruleset

Uses `rules/new_graph_partitioning_benchmark.drl` with 5 clusters:
- **Alpha** (hash % 5 == 0)
- **Beta** (hash % 5 == 1)
- **Gamma** (hash % 5 == 2)
- **Delta** (hash % 5 == 3)
- **Epsilon** (hash % 5 == 4)

Each cluster contains 6 chained rules with staggered salience for clean cluster separation.

### Output

- **Mining Trace**: `mining_trace.csv` (for Infomap analysis)

---

## ContentModerationRunner

Runs the CEP benchmark using the `wikimedia_content_moderation.drl` ruleset with semantically meaningful content moderation pipelines.

### Usage

```bash
cd drools-benchmarks-cep-wikimedia
mvn compile -DskipTests
java -cp "target/classes;target/dependency/*" org.kie.benchmark.cep.wikimedia.ContentModerationRunner [durationMinutes]
#alternatively
java -cp target/drools-benchmarks-cep-wikimedia.jar org.kie.benchmark.cep.wikimedia.ContentModerationRunnerarent\drools-benchmarks-cep-wikimedia>
```

### Parameters

| Parameter | Description | Default |
|-----------|-------------|---------|
| `durationMinutes` | Benchmark duration in minutes | 2 |

### Pipelines

The ruleset includes 5 content moderation pipelines:

| Pipeline | Trigger Condition | Purpose |
|----------|-------------------|---------|
| **Vandalism** | `sizeDelta < -100` | Detect potential vandalism (large deletions) |
| **Bot** | `bot == true` | Monitor automated bot activity |
| **Content** | `sizeDelta > 200` (non-bot) | Track significant content additions |
| **Minor** | `-50 <= sizeDelta <= 50` (non-bot) | Handle minor edits and corrections |
| **Discussion** | `title matches "Talk:.*"` | Analyze talk page activity |

### Output

- **Mining Trace**: `mining_trace.csv` (for Infomap analysis)

---

## Related Resources

- **Semantic Overview**: [`src/main/resources/docs/semantic_overview.md`](../../../../../../resources/docs/semantic_overview.md)
- **Balanced Ruleset**: [`src/main/resources/rules/new_graph_partitioning_benchmark.drl`](../../../../../../resources/rules/new_graph_partitioning_benchmark.drl)
- **Moderation Ruleset**: [`src/main/resources/rules/wikimedia_content_moderation.drl`](../../../../../../resources/rules/wikimedia_content_moderation.drl)
