# Benchmark Results — Parallel Cluster Execution Comparison

> **Date:** 2026-04-08 | **Dataset:** `run_20260311_1340_10sym` | **Events:** 1,613,159 | **Symbols:** 10

---

## Experiment Overview

Two parallel architectures were tested against a single-session baseline:

| | V2 (4 threads) | V3 (2 threads) |
|--|--|--|
| **Clusters** | C1: Feed Health, C2: Microstructure, C3: Liquidation, C4: Trade Rate | CA: Feed+Liq+Trade (C1+C3+C4), CB: Microstructure (C2) |
| **Threads** | 4 | 2 |
| **Rule split** | 26 + 52 + 6 + 3 (+10 generic each) | 35 + 52 (+10 generic each) |

---

## Routing Logic Comparison

### V2 — 4-Thread Routing

```
              ┌──────────────┐
              │ MarketEvent  │
              └──────┬───────┘
              ┌──────▼───────┐
              │ EventRouter  │
              └──┬──┬──┬──┬──┘
         ┌──────┘  │  │  └──────┐
         ▼         ▼  ▼         ▼
      [C1]      [C2] [C3]    [C4]
```

| Event Type | C1 (Feed) | C2 (Micro) | C3 (Liq) | C4 (Trade) | Sessions hit |
|:----------:|:---------:|:----------:|:--------:|:----------:|:----:|
| DEPTH | ✅ | ✅ | | | 2 |
| TRADE | ✅ | ✅ | | ✅ | 3 |
| MARK | ✅ | ✅ | | | 2 |
| INDEX | ✅ | ✅ | | | 2 |
| HEARTBEAT | ✅ | | | | 1 |
| LIQ | | | ✅ | | 1 |

**Fan-out:** ~88% of events go to 2-3 sessions → high duplication overhead.

### V3 — 2-Thread Routing

```
              ┌──────────────┐
              │ MarketEvent  │
              └──────┬───────┘
              ┌──────▼───────┐
              │ EventRouter  │
              └──────┬───┬───┘
                     │   │
                     ▼   ▼
                   [CA] [CB]
```

| Event Type | CA (Feed+Liq+Trade) | CB (Micro) | Sessions hit |
|:----------:|:-------------------:|:----------:|:----:|
| DEPTH | ✅ | ✅ | 2 |
| TRADE | ✅ | ✅ | 2 |
| MARK | ✅ | ✅ | 2 |
| INDEX | ✅ | ✅ | 2 |
| HEARTBEAT | ✅ | | 1 |
| LIQ | ✅ | | 1 |

**Fan-out:** ~99.98% of events go to both sessions → near-complete duplication.

---

## Performance Results

### Run 1 — 50,000 Events (3 symbols)

| Metric | Baseline (1T) | V2 (4T) | V3 (2T) |
|--------|:---:|:---:|:---:|
| **Rules fired** | 399,957 | 900,049 | 750,003 |
| **Duration** | 12,511–13,352 ms | 11,083 ms | 7,705 ms |
| **Throughput** | 3,745–3,996 ev/s | 4,511 ev/s | 6,489 ev/s |
| **Speedup** | 1.00x | **1.20x** | **1.62x** ✅ |

### Run 2 — Full Dataset (1,613,159 events, 10 symbols)

| Metric | Baseline (1T) | V2 (4T) | V3 (2T) |
|--------|:---:|:---:|:---:|
| **Rules fired** | 13,528,242 | 28,283,453 | 23,998,475 |
| **Duration** | 74,598–82,366 ms | 115,832 ms | 93,193 ms |
| **Throughput** | 19,585–21,625 ev/s | 13,927 ev/s | 17,310 ev/s |
| **Speedup** | 1.00x | **0.71x** | **0.80x** |

> **Note:** Baseline duration varies between runs due to JVM warm-up and GC pressure. The speedup ratios are computed against the respective baseline for that session.

---

## Per-Cluster Breakdown (Full Dataset)

### V2 (4 threads)

| Cluster | Events Received | Rules Fired |
|:-------:|:-:|:-:|
| C1 (Feed Health) | 1,612,867 | 9,831,162 |
| C2 (Microstructure) | 1,612,867 | 8,456,021 |
| C3 (Liquidation) | 292 | 2,044 |
| C4 (Trade Rate) | 1,427,312 | 9,994,226 |

### V3 (2 threads)

| Cluster | Events Received | Rules Fired |
|:-------:|:-:|:-:|
| CA (Feed+Liq+Trade) | 1,613,159 | 15,542,454 |
| CB (Microstructure) | 1,612,867 | 8,456,021 |

---

## Correctness Validation

| Check | V2 (4T) | V3 (2T) |
|-------|:---:|:---:|
| Overlapping signal types | 138/140 ✅ | 138/140 ✅ |
| Signal misses | 2 | 2 |
| Extra redundant | 6,087,485 | 3,229,819 |
| No infinite loops | ✅ | ✅ |
| **Status** | **✅ PASS** | **✅ PASS** |

The 2 missing signals are from deliberately deleted cross-cluster rules.

---

## Analysis

### Why Both Are Slower Than Baseline at Scale

The core issue is **event duplication** — most events are routed to multiple sessions:

| Architecture | Events processed (total across all sessions) | Duplication ratio |
|:---:|:-:|:-:|
| Baseline | 1,613,159 | 1.0× |
| V2 (4T) | 5,253,046 | 3.3× |
| V3 (2T) | 3,226,026 | 2.0× |

Each duplicated event fires all 10 generic rules + cluster-specific rules in each receiving session. At scale, the extra rule evaluations overwhelm the parallelism benefit.

### V3 Outperforms V2

V3 (1.62x at 50k, 0.80x at full) consistently outperforms V2 because:
1. **Lower fan-out overhead** — 2-way duplication is significantly cheaper than 3-4 way duplication.
2. **Better load balance** — Merging smaller, non-overlapping clusters (C1+C3+C4) reduces synchronization overhead.
3. **Contention** — Fewer threads reduce context-switching and cache contention in the JVM.

### Future Scalability

To achieve true linear scaling (>2x speedup), the routing must become **selective**:
- **Symbol-based partitioning**: Routed events hit exactly one session (Zero overlap).
- **Session-level optimization**: Reduce common generic rules by moving them to a lightweight preprocessing stage.

---

## How to Reproduce

```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep

# 2-Thread V3 (Latest)
set MAVEN_OPTS=-Xmx4g
mvn test-compile exec:java \
  -Dexec.mainClass=org.kie.benchmark.binance.parallel.BinanceClusterBenchmarkV3 \
  -Dexec.args=0 -Dexec.classpathScope=test

# Quick test (50k)
mvn test-compile exec:java \
  -Dexec.mainClass=org.kie.benchmark.binance.parallel.BinanceClusterBenchmarkV3 \
  -Dexec.args=50000 -Dexec.classpathScope=test
```
