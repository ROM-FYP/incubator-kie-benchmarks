# Binance CEP Benchmark

## Overview
JMH-based CEP benchmark for Drools using recorded Binance WebSocket market data.

## Features
- **70+ Rule Taxonomy**: Extensible rule set covering price movements, volume anomalies, temporal patterns, order book imbalance, and multi-symbol correlations
- **Real Market Data**: Uses recorded Binance WebSocket streams for realistic testing
- **Scalability Testing**: Test with 70, 140, 280, 560, 1000+ rules
- **Comprehensive Metrics**: JMH benchmarks + JFR profiling

## Quick Start

### Build
```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep
mvn clean install
```

### Run Benchmarks
```bash
# Run all benchmarks
java -jar target/drools-benchmarks-binance-cep.jar

# Run specific benchmark
java -jar target/drools-benchmarks-binance-cep.jar BinanceCEPBenchmark

# Run with profiling
java -jar target/drools-benchmarks-binance-cep.jar \
  -prof async:output=flamegraph \
  BinanceCEPBenchmark
```

## Documentation

- [Implementation Plan](docs/implementation_plan.md) - Detailed design and architecture
- [Data Format](docs/data_format.md) - Binance WebSocket data format
- [Rule Taxonomy](docs/rule_taxonomy.md) - 70-rule taxonomy details

## Benchmark Classes

### BinanceCEPBenchmark
Baseline latency benchmark measuring microseconds per event.

**Parameters:**
- `ruleCount`: 70, 140, 280, 560, 1000
- `eventCount`: 1000, 10000, 100000
- `windowSize`: 10s, 30s, 60s

### BinanceThroughputBenchmark
Sustained throughput benchmark measuring events/second.

### BinanceScalabilityBenchmark
Pattern-specific benchmarks for different rule categories.

## Results

Results are stored in CSV format:
```
results/
├── binance_cep_baseline.csv
├── binance_throughput.csv
└── flamegraphs/
```

## Performance Targets

- **Throughput**: > 10,000 events/sec with 70 rules
- **Latency**: < 500μs average per event
- **Scalability**: Linear degradation up to 280 rules
- **Memory**: < 2GB heap for 100K events

## Contributing

When adding new rules or data:
1. Update `docs/rule_taxonomy.md`
2. Update `docs/data_format.md`
3. Add test cases
4. Run benchmarks and document results
