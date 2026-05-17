# Drools CEP RIPE RIS Benchmark

A production-quality **Complex Event Processing (CEP)** benchmark for Apache Drools that uses **real-time RIPE RIS Live BGP events** to test network monitoring and rule interaction capabilities.

## Overview

This benchmark is designed to stress-test Drools CEP with a realistic, high-velocity event stream of BGP (Border Gateway Protocol) messages. Unlike traditional microbenchmarks that test isolated operators, this is a **workload benchmark** that exercises:

- **Real-time event ingestion** from RIPE RIS Live's SSE stream
- **Multi-stage forward chaining** rules
- **Temporal windows** and time-based aggregations
- **Complex pattern matching** and event correlation
- **Truth Maintenance System (TMS)** with logical insertions

## Why RIPE RIS?

The RIPE RIS (Routing Information Service) Live stream provides:

1. **Real-world data**: Actual BGP routing updates happening in real-time globally
2. **High variety**: Different message types (UPDATE, WITHDRAWAL), origin ASNs, and paths
3. **Unbounded stream**: Continuous flow of events
4. **Publicly accessible**: No authentication required for public streams
5. **Well-structured**: Consistent JSON format

This makes it ideal for benchmarking CEP systems under realistic conditions.

## Architecture

```
RIPE RIS Live Stream
        ↓
RipeRisEventSource (JSON parsing & filtering)
        ↓
RisMessage POJOs
        ↓
Drools KieSession (STREAM mode)
        ↓
[Stage 1] BGP Message Classification
        ↓
[Stage 2] Path Analysis
        ↓
[Stage 3] Flap Detection
        ↓
[Stage 4] Alert Generation
        ↓
Alert Facts
```

## Model Classes

### RisMessage
Primary event type representing a BGP message:
- `id`: Message identifier
- `timestamp`: Event timestamp (seconds with decimals)
- `peer`: Peer IP address
- `peerAsn`: Peer Autonomous System Number
- `host`: RRC collector host
- `bgpType`: Message type (UPDATE, etc.)
- `path`: List of AS numbers in the path
- `announcements`: List of announced prefixes
- `withdrawals`: List of withdrawn prefixes

## Building

```bash
cd drools-benchmarks-parent
mvn clean install -DskipTests
```

## Running

### Basic Run (5 minutes default)
```bash
cd drools-benchmarks-ripe-ris-cep
java -jar target/drools-benchmarks-ripe-ris-cep.jar
```

### Custom Duration
```bash
java -jar target/drools-benchmarks-ripe-ris-cep.jar 10
```
(Runs for 10 minutes)

### With Maven Exec
```bash
mvn exec:java -Dexec.mainClass="org.kie.benchmark.cep.riperis.RipeRisCepBenchmark" -Dexec.args="5"
```

### Causal Trace Runner
To generate a causal trace for downstream analysis:
```bash
mvn exec:java -Dexec.mainClass="org.kie.benchmark.cep.riperis.runner.RipeRisCausalTraceRunner" -Dexec.args="path/to/data.jsonl [outputFile] [maxEvents]"
```

## Expected Output

```
========================================
RIPE RIS CEP Benchmark
Duration: 5 minutes
Rules: rules/ripe_rfc4271_benchmark_79_rules.drl
Stream: https://ris-live.ripe.net/v1/stream/
========================================
Connected to RIPE RIS stream, streaming events...
Stats - Events: 1247, Rules Fired: 3891, Terminal Alerts: 12
Stats - Events: 2534, Rules Fired: 8203, Terminal Alerts: 27
...
========================================
FINAL BENCHMARK RESULTS
========================================
Total Events Ingested: 15234
Total Rules Fired: 47891
Total Terminal Alerts: 142
Duration: 5 minutes
Events/sec: 50.78
Rules/sec: 159.64
========================================
```

## Performance Characteristics

### Workload Profile
- **Event Rate**: Varies with global BGP activity
- **Memory**: Moderate (sliding windows maintain events)
- **CPU**: High (continuous rule evaluation)

### Scalability Factors
1. **Window Size**: Larger windows = more memory, more complex joins
2. **Rule Count**: More rules = higher CPU usage
3. **Event Velocity**: Higher event rate = more pressure on rule engine

## Customization

### Adjust Rule Complexity
Edit the DRL file specified in `CepBenchmarkConfig` (default: `rules/ripe_rfc4271_benchmark_79_rules.drl`):
- Modify window sizes
- Add more classification types
- Change detection thresholds

### Modify Event Filtering
Edit `RipeRisEventSource.parseEvent()`:
- Filter by specific peers or ASNs
- Filter by message type

### Change Configuration
Edit `CepBenchmarkConfig.getDefault()`:
- Adjust default duration
- Enable/disable verbose logging
- Modify stream URL

## Monitoring

The benchmark reports:
1. **Events Ingested**: Total BGP messages processed
2. **Rules Fired**: Total rule activations
3. **Terminal Alerts**: Number of detected anomalies or alerts
4. **Throughput**: Events/sec and Rules/sec

## Requirements

- **Java**: 11 or higher
- **Internet**: Required for RIPE RIS stream access
- **Memory**: Recommended 2GB+ heap (`-Xmx2g`)

## Troubleshooting

### Connection Failures
If connection to RIPE RIS fails:
1. Check internet connectivity
2. Verify firewall allows HTTPS connections
3. Check if RIPE RIS stream is operational: https://ris-live.ripe.net/v1/stream/

### Memory Issues
If OutOfMemoryError occurs:
- Reduce window sizes in rules
- Decrease benchmark duration
- Increase heap size: `java -Xmx4g -jar ...`

## License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

## Contributing

This benchmark is part of Apache KIE (incubator-kie-benchmarks). Contributions welcome following Apache governance guidelines.
