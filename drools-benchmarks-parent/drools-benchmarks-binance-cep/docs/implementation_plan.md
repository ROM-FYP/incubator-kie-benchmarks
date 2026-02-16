# Binance Market Risk Control System - CEP Benchmark Implementation Plan

## 📋 Overview

This benchmark measures **Drools CEP performance** for a **real-time market risk control and kill-switch system** using recorded Binance USDⓈ-M Futures WebSocket data. The system implements a **70-rule taxonomy** covering data validation, feed health monitoring, order book integrity, trade sanity checks, volatility detection, futures dislocation monitoring, liquidation cascade detection, and automated mode transitions (NORMAL → THROTTLED → SAFE → HALTED).

---

## 🎯 Benchmark Objectives

### Primary Goals

1. **Measure CEP performance** for a production-grade market risk control system
2. **Evaluate rule scalability** from 70 baseline rules to 280+ rules (multi-symbol scenarios)
3. **Assess complex event processing** with stateful facts, temporal windows, and mode transitions
4. **Profile memory and CPU** under realistic high-frequency market data loads
5. **Validate kill-switch latency** for critical risk signals

### Key Questions to Answer

- **Throughput**: How many events/sec can the system process with 70 rules across 5 stream types?
- **Latency**: What is the p50/p95/p99 latency from event ingestion to kill-switch activation?
- **Scalability**: How does performance degrade when scaling from 1 symbol (70 rules) to 10 symbols (700 rules)?
- **Memory**: What is the working memory footprint for 1-minute event windows with 10K+ events?
- **Temporal Operators**: What is the overhead of `@role(event)` with sliding time windows?
- **State Management**: How does the system handle 100+ derived facts (BestBidAsk, SpreadState, DepthState, etc.)?

---

## 📊 Data Source: Binance WebSocket Event Replayer

### Stream Types (5 Total)

| Stream | Rate (per symbol) | Event Type | Key Fields |
|--------|-------------------|------------|------------|
| **trade** | 50-500 msg/s | Individual executions | `price`, `qty`, `isBuyerMaker`, `tradeTime` |
| **book** (depth) | 10 msg/s (fixed) | Order book diffs | `bids[]`, `asks[]`, `updateId`, `prevUpdateId` |
| **mark** | 1 msg/s (fixed) | Mark price & funding | `markPrice`, `indexPrice`, `fundingRate` |
| **index** | 1 msg/s (fixed) | Composite spot price | `indexPrice` |
| **liquidation** | 0-100 msg/s (sporadic) | Forced liquidations | `side`, `qty`, `price`, `avgPrice`, `status` |

### Event Envelope Schema

Every event follows this structure (from `data-reference.md`):

```json
{
  "dataset_id": "run_20260213_1645_10sym",
  "source_stream": "trade",
  "symbol": "BTCUSDT",
  "exchange_ts": 1739454300123,  // Exchange time (ms)
  "recv_ts": 1739454300145678,   // Local receive time (µs)
  "local_seq": 42,                // Per-(stream,symbol) monotonic counter
  "raw": { /* original Binance payload */ }
}
```

### Replay Modes

1. **Event-time replay**: Sort by `exchange_ts` (idealized exchange-clock ordering)
2. **Arrival-time replay**: Sort by `recv_ts` (reproduces network jitter)

### Data Volume (10 symbols, 1 hour)

- **Total events**: ~1.25M events
- **Trade events**: ~720K (57%)
- **Book events**: ~360K (29%)
- **Mark/Index**: ~72K (6%)
- **Liquidations**: ~98K (8%)
- **Storage**: ~150 MB compressed (gzip), ~1 GB uncompressed

---

## 📜 Rule Taxonomy: 70-Rule Market Risk Control System

The actual `taxonomy.drl` file implements a **sophisticated kill-switch system** with 9 rule categories:

### A) Ingestion & Schema Sanity (8 rules)

- **A01**: Missing required fields
- **A02**: Invalid numerics (NaN, Infinity)
- **A03**: Timestamp skew bounds (future/past limits)
- **A04**: Symbol allowlist validation
- **A05**: Monotonic per-stream detection (out-of-order events)
- **A06**: Price/qty precision bounds
- **A07**: Decode errors quarantine
- **A08**: Unexpected message types

### B) Feed Health & Connectivity (10 rules)

- **B09**: Heartbeat missing
- **B10**: Reconnect storm
- **B11**: Stream silence but connection open
- **B12**: Trade active, book silent
- **B13**: Book active, trade silent
- **B14**: Stale mark price
- **B15**: Stale index price
- **B16**: Late event rate high
- **B17**: Out-of-order burst
- **B18**: Persistent book gaps

### C) Order Book State Validity (8 rules)

- **C19**: Crossed book (bid ≥ ask)
- **C20**: Negative/zero spread persistent
- **C21**: Top-of-book jump without trades
- **C22**: Top sizes invalid
- **C23**: Book incomplete (partial depth)
- **C24**: Excessive depth update rate
- **C25**: Book age stale
- **C26**: Book sequence discontinuity unrecovered

### D) Trade Tape Sanity (6 rules)

- **D27**: Trade price out-of-band vs mid
- **D28**: Trade size outlier
- **D29**: Trade timestamp regression
- **D30**: Trade rate tiering
- **D31**: Large trade cluster
- **D32**: Trades while book stale

### E) Derived Metrics: Spread, Depth, Imbalance (10 rules)

- **E33**: Spread compute & tier (LOW/MED/HIGH/CRIT)
- **E34**: Depth tiering
- **E35**: Depth collapse
- **E36**: Spread blowout
- **E37**: Persistent thin liquidity
- **E38**: Imbalance compute & tier (BAL/BIASED/EXTREME)
- **E39**: Imbalance persistence
- **E40**: Imbalance flip-flop
- **E41**: Liquidity stress (spread high + depth low)
- **E42**: Market impact risk (liquidity stress + high trade rate)

### F) Volatility & Movement Regimes (10 rules)

- **F43**: Vol tiering (1s/10s/1m windows)
- **F44**: Price jump detection
- **F45**: Whipsaw detection
- **F46**: Sustained trend
- **F47**: Vol spike
- **F48**: Vol persistence high
- **F49**: Trade-driven move
- **F50**: Book-driven move
- **F51**: Regime shift to safe
- **F52**: Regime normalization eligibility

### G) Futures-Specific Dislocation (8 rules)

- **G53**: Mark-index divergence compute
- **G54**: Mark-mid divergence compute
- **G55**: Dislocation persistence
- **G56**: Dislocation + thin book
- **G57**: Dislocation + trade burst
- **G58**: Index stale but mark moving
- **G59**: Mark stale but market active
- **G60**: Sudden divergence reversal

### H) Liquidation & Cascade Stress (7 rules)

- **H61**: Liquidation tiering (count per 10s)
- **H62**: Liquidation burst jump
- **H63**: Liquidations + vol spike
- **H64**: Liquidations + depth collapse
- **H65**: Liquidations + dislocation
- **H66**: Cascade persistence escalate
- **H67**: Cascade cooldown eligibility

### I) Policy Engine: Mode Transitions & Kill-Switch (3 rules)

- **I68**: Enter THROTTLED (single stress/degraded feed)
- **I69**: Enter SAFE (multiple independent stress signals)
- **I70**: Enter HALTED (kill-switch latch on critical conditions)

**Plus 2 recovery rules** (not counted in 70):
- Exit SAFE → THROTTLED on normalization + cooldown
- Exit THROTTLED → NORMAL when healthy + cooldown

---

## 🏗️ Benchmark Architecture

### Module Structure

```
drools-benchmarks-binance-cep/
├── docs/
│   ├── implementation_plan.md          ← This file (REVISED)
│   ├── data-reference.md               ← Event replayer guide (EXISTS)
│   ├── rule_taxonomy.md                ← Rule breakdown (TODO)
│   ├── data_format.md                  ← Legacy? (EXISTS)
│   └── results/
│       └── README.md
├── src/main/java/org/kie/benchmark/binance/
│   ├── BinanceRiskControlBenchmark.java       ← Main benchmark (70 rules, 1 symbol)
│   ├── BinanceMultiSymbolBenchmark.java       ← Scalability (70→700 rules, 1→10 symbols)
│   ├── BinanceKillSwitchLatencyBenchmark.java ← Kill-switch latency (p99)
│   ├── model/
│   │   ├── MarketEvent.java                   ← Base event (@role(event))
│   │   ├── FeedHealth.java                    ← Stateful fact
│   │   ├── BestBidAsk.java                    ← Derived fact
│   │   ├── SpreadState.java                   ← Derived fact
│   │   ├── DepthState.java                    ← Derived fact
│   │   ├── ImbalanceState.java                ← Derived fact
│   │   ├── TradeStats.java                    ← Derived fact
│   │   ├── VolState.java                      ← Derived fact
│   │   ├── MarkIndexState.java                ← Derived fact
│   │   ├── LiquidationStats.java              ← Derived fact
│   │   ├── RiskSignal.java                    ← Output fact
│   │   ├── ModeState.java                     ← Policy state (NORMAL/THROTTLED/SAFE/HALTED)
│   │   └── RiskConfig.java                    ← Per-symbol thresholds
│   ├── provider/
│   │   ├── BinanceEventProvider.java          ← Loads segments/*.jsonl.gz
│   │   └── BinanceRulesProvider.java          ← Loads taxonomy.drl + generates scaled rules
│   └── util/
│       ├── EventReplayController.java         ← SessionPseudoClock-based replay
│       └── SegmentReader.java                 ← JSONL.gz decompression
└── src/main/resources/
    ├── data/
    │   └── sample_dataset/                    ← Small 1-min sample for CI
    │       ├── metadata.json
    │       ├── reconnects.jsonl
    │       ├── stats.jsonl
    │       └── segments/
    │           └── events_*.jsonl.gz
    └── rules/
        ├── taxonomy.drl                       ← 70-rule baseline (EXISTS)
        └── taxonomy_scaled.drl.ftl            ← Freemarker template for multi-symbol (TODO)
```

---

## 🔧 JMH Configuration

### Benchmark 1: Baseline Throughput (70 rules, 1 symbol)

```java
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3, time = 5, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 20, timeUnit = SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
@State(Scope.Benchmark)
public class BinanceRiskControlBenchmark {
    // Replay 1-minute segment (~20K events) at max speed
    // Measure: events/sec, rules fired/sec
}
```

### Benchmark 2: Multi-Symbol Scalability

```java
@Param({"1", "2", "5", "10"})
int symbolCount; // 70, 140, 350, 700 rules

// Measure throughput degradation as rule count scales
```

### Benchmark 3: Kill-Switch Latency (p99)

```java
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)

// Inject critical events (crossed book, severe dislocation)
// Measure time from event insert to HALTED mode transition
```

### JFR Profiling Commands

```bash
# CPU Profiling with Flamegraph
java -jar target/benchmarks.jar BinanceRiskControlBenchmark \
  -prof async:output=flamegraph;dir=./profiling \
  -jvmArgs "-Xms8g -Xmx8g"

# JFR Recording (30-second sample)
java -jar target/benchmarks.jar BinanceRiskControlBenchmark \
  -prof jfr:dir=./jfr-results

# GC Profiling
java -jar target/benchmarks.jar BinanceRiskControlBenchmark \
  -prof gc
```

---

## 📈 Performance Targets

| Metric | Target | Rationale |
|--------|--------|-----------|
| **Throughput (70 rules, 1 symbol)** | > 15,000 events/sec | 10 symbols × 600 msg/s = 6K events/sec real-world; 2.5× headroom |
| **Latency (p50)** | < 200 µs/event | Sub-millisecond risk detection |
| **Latency (p99)** | < 1 ms/event | Acceptable tail latency |
| **Kill-switch latency (p99)** | < 5 ms | Critical signal → HALTED mode |
| **Scalability (10 symbols)** | > 5,000 events/sec | 700 rules; 3× degradation acceptable |
| **Memory (1-min window)** | < 1 GB heap | 20K events + derived facts |
| **GC pause (p99)** | < 10 ms | Minimal disruption |

---

## 🚀 Implementation Phases

### Phase 1: Foundation ✅ (Partially Complete)

- [x] Create module structure
- [x] Copy `data-reference.md` (event replayer spec)
- [x] Copy `taxonomy.drl` (70-rule baseline)
- [ ] **Implement Java model classes** (MarketEvent, FeedHealth, BestBidAsk, etc.)
- [ ] **Implement SegmentReader** (JSONL.gz decompression)
- [ ] **Implement EventReplayController** (SessionPseudoClock integration)

### Phase 2: Core Benchmarks

- [ ] **BinanceRiskControlBenchmark** (baseline 70 rules, 1 symbol)
  - Load 1-minute segment (~20K events)
  - Replay at max speed
  - Measure throughput + latency
- [ ] **BinanceMultiSymbolBenchmark** (scalability test)
  - Generate scaled rules (1/2/5/10 symbols)
  - Measure throughput degradation
- [ ] **BinanceKillSwitchLatencyBenchmark** (p99 latency)
  - Inject critical events
  - Measure mode transition latency

### Phase 3: Profiling & Analysis

- [ ] Run JFR profiling (CPU hotspots)
- [ ] Generate flamegraphs (identify expensive rules)
- [ ] Analyze memory patterns (working memory growth)
- [ ] Identify bottlenecks (rule evaluation vs. fact insertion)

### Phase 4: Optimization & Documentation

- [ ] Optimize based on profiling (e.g., rule salience tuning)
- [ ] Document findings in `results/`
- [ ] Create performance report
- [ ] Add sample dataset to CI/CD

---

## 💡 Key Design Decisions

### 1. Event Replay Strategy

- **Use `SessionPseudoClock`** for deterministic timing
- **Replay mode**: Event-time (sort by `exchange_ts`) for reproducibility
- **Pacing**: Max-speed (no sleep) for throughput benchmarks; real-time (1× speed) for latency benchmarks

### 2. Rule Scaling Strategy

- **Baseline**: 70 rules for 1 symbol (BTCUSDT)
- **Scaling**: Duplicate rule set per symbol (10 symbols = 700 rules)
- **Template**: Use Freemarker to generate `taxonomy_scaled.drl.ftl` with symbol parameter

### 3. Data Loading Strategy

- **Pre-load**: Decompress and parse all events into memory before benchmark
- **Segment merging**: Merge-sort segments by `exchange_ts` + `local_seq`
- **Sample dataset**: 1-minute segment (~20K events) for CI; 1-hour dataset (~1.25M events) for full profiling

### 4. Measurement Approach

- **Separate build-time from runtime**: Pre-compile KieBase in `@Setup`
- **Measure only event processing**: Start timer after session creation
- **Include clock advancement**: Advance `SessionPseudoClock` per event

---

## 🔍 Critical Implementation Details

### Fact Lifecycle

1. **Insert `RiskConfig`** (per symbol, salience 1000)
2. **Bootstrap `ModeState` + `FeedHealth`** (salience 999)
3. **Insert `MarketEvent`** (from replayer)
4. **Derive facts** (BestBidAsk, SpreadState, DepthState, etc.)
5. **Fire rules** (generate `RiskSignal` facts)
6. **Update `ModeState`** (NORMAL → THROTTLED → SAFE → HALTED)
7. **Expire events** (temporal windows)

### Temporal Windows

- **Trade stats**: 1s/10s/1m sliding windows
- **Volatility**: 1s/10s/1m windows
- **Liquidations**: 10s window
- **Dislocation persistence**: Configurable (e.g., 5s)

### Kill-Switch Conditions (Rule I70)

Triggers **HALTED** mode (latched) on:
- `DATA_CONFLICT` (trades while book stale/untrusted)
- `SEVERE_DISLOCATION` (mark-index divergence + thin book)
- `DISLOCATION_PERSIST` (high divergence for > 5s)
- `SEVERE_CASCADE_RISK` (liquidations + dislocation)
- `BOOK_UNTRUSTED` (unrecovered sequence gaps)

---

## 📚 References

### Existing Benchmarks (Patterns to Follow)

- **`AbstractCEPBenchmark`**: Base class for CEP benchmarks
- **`SlidingTimeWindowBenchmark`**: Temporal window patterns
- **`AbstractThroughputBenchmark`**: Sustained load testing
- **`BasicEventProvider`**: Event generation interface
- **`CepRulesProvider`**: Rule generation interface

### External Documentation

- **Binance Futures WebSocket API**: [https://binance-docs.github.io/apidocs/futures/en/](https://binance-docs.github.io/apidocs/futures/en/)
- **Drools CEP Documentation**: [https://docs.drools.org/latest/drools-docs/html_single/#cep-con_decision-engine](https://docs.drools.org/latest/drools-docs/html_single/#cep-con_decision-engine)

---

## ⚠️ Known Challenges

1. **Timestamp precision mismatch**: `exchange_ts` (ms) vs `recv_ts` (µs) — use `exchange_ts` for CEP
2. **Out-of-order events**: Segment boundaries may have slight `recv_ts` overlap — merge-sort required
3. **Derived fact explosion**: 10 symbols × 10 derived fact types = 100+ facts in working memory
4. **Rule salience tuning**: Bootstrap rules (salience 1000) must fire before data rules
5. **Temporal operator overhead**: `@role(event)` + `@expires` may impact performance
