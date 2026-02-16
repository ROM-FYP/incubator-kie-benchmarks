# Binance WebSocket CEP Benchmark - Implementation Plan

## 📋 Overview
Design a comprehensive JMH-based CEP benchmark for Drools using recorded Binance WebSocket market data with a taxonomy of 70+ extensible rules.

---

## 🎯 Benchmark Objectives

### Primary Goals
1. **Measure CEP performance** with real-world financial streaming data
2. **Evaluate rule scalability** from 70 to thousands of rules
3. **Assess temporal pattern matching** on high-frequency market events
4. **Profile memory and CPU** characteristics under realistic load

### Key Questions to Answer
- How does throughput scale with rule count (70, 140, 280, 560, 1000+ rules)?
- What is the latency distribution for event processing?
- How does memory consumption grow with event window size?
- What is the impact of different temporal operators on performance?
- How does the system handle event bursts vs. steady streams?

---

## 📊 Metrics to Measure (Based on Existing CEP Benchmarks)

### 1. **JMH Core Metrics**

#### **Throughput Mode** (`@BenchmarkMode(Mode.Throughput)`)
- **Events processed per second** (ops/sec)
- **Rules fired per second**
- **Use case**: Sustained load testing

#### **Average Time Mode** (`@BenchmarkMode(Mode.AverageTime)`)
- **Average latency per event** (μs/op or ms/op)
- **Average time to process event batch**
- **Use case**: Understanding typical performance

#### **Single Shot Time** (`@BenchmarkMode(Mode.SingleShotTime)`)
- **First event latency** (cold start)
- **Rule compilation time**
- **Use case**: Startup performance

### 2. **CEP-Specific Metrics**
- Number of events inserted
- Number of rules fired
- Clock advancement time
- Event expiration handling
- Window size impact

### 3. **JFR (Java Flight Recorder) Profiling Metrics**

#### **CPU Profiling**
- Method hotspots (which rules consume most CPU)
- Rule evaluation time distribution
- Pattern matching overhead
- Temporal operator evaluation cost

#### **Memory Profiling**
- Heap allocation rate
- Event object lifecycle
- Working memory size over time
- GC pressure and pause times
- Event expiration effectiveness

#### **Lock Contention** (if using concurrent sessions)
- Thread synchronization overhead
- Agenda lock contention
- Session access patterns

### 4. **Custom Business Metrics**
- Events per symbol processed
- Pattern match accuracy (true positives)
- Rule activation distribution
- Event window retention time
- Out-of-order event handling

---

## 🏗️ Benchmark Architecture

### Module Structure
```
drools-benchmarks-binance-cep/
├── docs/
│   ├── implementation_plan.md
│   ├── data_format.md
│   ├── rule_taxonomy.md
│   └── results/
├── src/main/java/org/kie/benchmark/binance/
│   ├── BinanceCEPBenchmark.java
│   ├── BinanceScalabilityBenchmark.java
│   ├── BinanceThroughputBenchmark.java
│   ├── model/
│   │   ├── BinanceEvent.java
│   │   ├── TradeEvent.java
│   │   ├── OrderBookEvent.java
│   │   ├── KlineEvent.java
│   │   └── AggTradeEvent.java
│   ├── provider/
│   │   ├── BinanceEventProvider.java
│   │   └── BinanceRulesProvider.java
│   └── util/
│       ├── BinanceDataLoader.java
│       └── EventReplayController.java
└── src/main/resources/
    ├── data/
    │   └── (recorded Binance WebSocket data)
    └── rules/
        ├── taxonomy_base_70.drl
        └── taxonomy_extended.drl.ftl
```

---

## 📜 Rule Taxonomy Design

### Base 70 Rules Categories

#### 1. **Price Movement Rules** (20 rules)
- Price spike detection (1%, 2%, 3%, 5%, 10%)
- Price drop detection
- Rapid price changes over time windows
- Support/resistance level breaks

#### 2. **Volume Anomaly Rules** (15 rules)
- Volume surge detection (2x, 3x, 5x average)
- Volume accumulation patterns
- Low volume periods
- Volume-price divergence

#### 3. **Temporal Pattern Rules** (15 rules)
- Consecutive price increases/decreases
- Alternating patterns
- Time-based sequences
- Multi-timeframe alignment

#### 4. **Order Book Imbalance Rules** (10 rules)
- Bid-ask imbalance detection
- Order book depth changes
- Large order detection
- Spread widening/narrowing

#### 5. **Multi-Symbol Correlation Rules** (10 rules)
- Cross-asset price movements
- Correlation breakdowns
- Leading/lagging indicators
- Basket movements

---

## 🔧 JMH Configuration

### Recommended Settings

```java
// For latency benchmarks
@Warmup(iterations = 300)
@Measurement(iterations = 50)
@OutputTimeUnit(TimeUnit.MICROSECONDS)

// For throughput benchmarks
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 2, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 20, timeUnit = SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)

// JVM settings
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
```

### JFR Profiling Commands

```bash
# CPU Profiling with Flamegraph
java -jar target/drools-benchmarks-binance-cep.jar \
  -prof async:output=flamegraph \
  -jvmArgs "-Xms8g -Xmx8g" \
  BinanceCEPBenchmark

# JFR Recording
java -jar target/drools-benchmarks-binance-cep.jar \
  -prof jfr:dir=./jfr-results \
  BinanceCEPBenchmark

# GC Profiling
java -jar target/drools-benchmarks-binance-cep.jar \
  -prof gc \
  BinanceCEPBenchmark
```

---

## 📈 Performance Targets

- **Throughput**: > 10,000 events/sec with 70 rules
- **Latency**: < 500μs average per event with 70 rules
- **Scalability**: Linear degradation up to 280 rules
- **Memory**: < 2GB heap for 100K events in 1-minute window

---

## 🚀 Implementation Phases

### Phase 1: Foundation
- [x] Create module structure
- [ ] Design event model
- [ ] Implement BinanceDataLoader
- [ ] Create base 70 rules taxonomy

### Phase 2: Core Benchmarks
- [ ] Implement BinanceCEPBenchmark
- [ ] Implement BinanceThroughputBenchmark
- [ ] Add rule scaling template
- [ ] Test with 70, 140, 280 rules

### Phase 3: Profiling & Analysis
- [ ] Run JFR profiling sessions
- [ ] Generate flamegraphs
- [ ] Analyze memory patterns
- [ ] Identify bottlenecks

### Phase 4: Optimization & Documentation
- [ ] Optimize based on profiling
- [ ] Document findings
- [ ] Create performance report
- [ ] Add to CI/CD pipeline

---

## 💡 Key Design Decisions

1. **Event Replay Strategy**: Use `SessionPseudoClock` for deterministic timing
2. **Rule Generation**: Template-based using Freemarker
3. **Data Loading**: Pre-load and cache events in memory
4. **Measurement Approach**: Separate build-time from runtime

---

## 📚 References

Based on existing benchmarks:
- `AbstractCEPBenchmark` - Base class pattern
- `SlidingTimeWindowBenchmark` - Window patterns
- `AbstractThroughputBenchmark` - Sustained load testing
- `BasicEventProvider` - Event generation
- `CepRulesProvider` - Rule generation
