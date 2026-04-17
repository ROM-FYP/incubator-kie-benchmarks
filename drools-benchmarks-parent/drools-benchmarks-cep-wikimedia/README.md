# Drools CEP Wikimedia Benchmark

A production-quality **Complex Event Processing (CEP)** benchmark for Apache Drools that uses **real-time Wikimedia edit events** to test viral topic detection capabilities.

## Overview

This benchmark is designed to stress-test Drools CEP with a realistic, high-velocity event stream. Unlike traditional microbenchmarks that test isolated operators, this is a **workload benchmark** that exercises:

- **Real-time event ingestion** from Wikimedia's SSE stream
- **Multi-stage forward chaining** rules
- **Temporal windows** and time-based aggregations
- **Complex pattern matching** and event correlation
- **Truth Maintenance System (TMS)** with logical insertions

## Why Wikimedia?

The Wikimedia recent changes stream provides:

1. **Real-world data**: Actual Wikipedia edits happening in real-time
2. **High variety**: Multiple languages, topics, edit types
3. **Unbounded stream**: Continuous flow of events
4. **Publicly accessible**: No authentication required
5. **Well-structured**: Consistent JSON format

This makes it ideal for benchmarking CEP systems under realistic conditions.

## Architecture

```
Wikimedia SSE Stream
        ↓
WikimediaEventSource (JSON parsing & filtering)
        ↓
WikiEvent POJOs
        ↓
Drools KieSession (STREAM mode)
        ↓
[Stage 1] Edit Classification Rules
        ↓
[Stage 2] User Diversity Detection
        ↓
[Stage 3] Edit Velocity Detection
        ↓
[Stage 4] Viral Topic Detection
        ↓
ViralTopicAlert Facts
```

## Model Classes

### WikiEvent
Primary event type representing a Wikipedia edit:
- `title`: Page title
- `user`: Editor username
- `comment`: Edit comment
- `bot`: Whether edit was by a bot
- `timestamp`: Event timestamp (milliseconds)
- `sizeDelta`: Change in page size (bytes)

### Derived Facts
- **EditClassification**: Semantic categorization (substantial, major, minor, deletion)
- **UserDiversity**: Number of unique editors per page
- **EditVelocity**: Edit rate within time windows
- **ViralTopicAlert**: Final viral topic detection output

## Rule Processing Stages

### Stage 1: Edit Classification
Classifies edits based on size delta:
- Substantial: > 500 bytes
- Major: 100-500 bytes
- Minor: 0-100 bytes
- Deletion: < -200 bytes

### Stage 2: User Diversity
Counts unique editors per page within a 2-minute window.

### Stage 3: Edit Velocity
Tracks edit rate per page within a 1-minute window.

### Stage 4: Viral Detection
Detects viral topics based on:
- **High Activity**: High velocity + diversity + edit weight
- **Controversy**: Multiple deletions + high diversity
- **Rapid Growth**: Burst of substantial edits

## Building

```bash
cd /home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent
mvn clean install -DskipTests
```

## Running

### Basic Run (5 minutes default)
```bash
cd drools-benchmarks-cep-wikimedia
java -jar target/drools-benchmarks-cep-wikimedia.jar
```

### Custom Duration
```bash
java -jar target/drools-benchmarks-cep-wikimedia.jar 10
```
(Runs for 10 minutes)

### With Maven Exec
```bash
mvn exec:java -Dexec.mainClass="org.kie.benchmark.cep.wikimedia.WikimediaCepBenchmark" -Dexec.args="5"
```

## Expected Output

```
========================================
Wikimedia CEP Benchmark
Duration: 5 minutes
Rules: rules/advanced_viral_rules.drl
Stream: https://stream.wikimedia.org/v2/stream/recentchange
========================================
Connected to Wikimedia stream, streaming events...
Stats - Events: 1247, Rules Fired: 3891, Viral Alerts: 12
Stats - Events: 2534, Rules Fired: 8203, Viral Alerts: 27
...
========================================
FINAL BENCHMARK RESULTS
========================================
Total Events Ingested: 15234
Total Rules Fired: 47891
Total Viral Alerts: 142
Duration: 5 minutes
Events/sec: 50.78
Rules/sec: 159.64
========================================
```

## Performance Characteristics

### Workload Profile
- **Event Rate**: 30-100 events/second (varies with Wikipedia activity)
- **Rule Firing Rate**: 100-300 firings/second
- **Memory**: Moderate (sliding windows maintain ~2-5 minutes of events)
- **CPU**: High (continuous rule evaluation)

### Scalability Factors
1. **Window Size**: Larger windows = more memory, more complex joins
2. **Rule Count**: More rules = higher CPU usage
3. **Event Velocity**: Higher event rate = more pressure on rule engine

## Customization

### Adjust Rule Complexity
Edit `src/main/resources/rules/advanced_viral_rules.drl`:
- Modify window sizes (`window:time(Xm)`)
- Add more classification types
- Change viral detection thresholds
- Add new detection categories

### Modify Event Filtering
Edit `WikimediaEventSource.parseEvent()`:
- Change namespace filter (currently only namespace 0 = articles)
- Filter by language
- Filter by size delta ranges

### Change Configuration
Edit `CepBenchmarkConfig.getDefault()`:
- Adjust default duration
- Enable/disable verbose logging
- Modify stream URL

## Monitoring

The benchmark reports:
1. **Events Ingested**: Total Wikipedia edits processed
2. **Rules Fired**: Total rule activations
3. **Viral Alerts**: Number of detected viral topics
4. **Throughput**: Events/sec and Rules/sec

## Requirements

- **Java**: 11 or higher
- **Internet**: Required for Wikimedia stream access
- **Memory**: Recommended 2GB+ heap (`-Xmx2g`)

## Troubleshooting

### Connection Failures
If connection to Wikimedia fails:
1. Check internet connectivity
2. Verify firewall allows HTTPS connections
3. Check if Wikimedia stream is operational: https://stream.wikimedia.org/v2/stream/recentchange

### Low Event Rate
Wikipedia edit rate varies by:
- Time of day
- Day of week
- Global events

Typical rate: 30-100 events/second during active hours.

### Memory Issues
If OutOfMemoryError occurs:
- Reduce window sizes in rules
- Decrease benchmark duration
- Increase heap size: `java -Xmx4g -jar ...`

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Contributing

This benchmark is part of Apache KIE (incubator-kie-benchmarks). Contributions welcome following Apache governance guidelines.
