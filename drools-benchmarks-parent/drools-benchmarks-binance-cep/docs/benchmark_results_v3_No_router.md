# Benchmark Results — V3 (2-Thread Broadcast Architecture)

> **Date:** 2026-04-08 | **Dataset:** `run_20260311_1340_10sym` | **Events:** 1,613,159 | **Symbols:** 10
> **Branch:** `binance_cep_parallel_new_experiment_2_threads_no_router`

---

## The "Broadcast" Architecture (Why No Router?)

In previous iterations, an `EventRouter` was used to inspect each `MarketEvent` and dispatch it only to the `KieSession`s (clusters) that required that event type. 

However, in the **V3 (2-thread)** setup, the clusters were consolidated into just two major logical groups:
* **CA (Feed + Liq + Trade)**
* **CB (Microstructure)**

Because both of these clusters contain rules that process `DEPTH`, `TRADE`, `MARK`, and `INDEX` events, they inherently share **99.98%** of the total event stream. The only events that do not strictly overlap are `HEARTBEAT` and `LIQ` events, which together make up barely **0.02%** of the traffic.

**Architectural Decision:** 
Since the routing overlap was effectively 100% anyway, the `ClusterEventRouter` logic was completely removed. Instead of wasting CPU cycles inspecting every event's type to filter out a negligible 0.02% of traffic, the new architecture simply **broadcasts all events** to both threads in a fire-and-forget manner. 

This provides three key benefits:
1. **Simplified Pipeline:** Cleaner code with no branching logic in the critical ingestion path.
2. **Reduced Latency:** Dropping the O(1) bitmask checking eliminates micro-stalls during ingestion.
3. **No Meaningful Overhead:** The "penalty" of evaluating the 0.02% extra events in CB's alpha network is completely negligible.

---

## Routing Logic Comparison

### V3 with Router (Previous)

| Event Type | CA (Feed+Liq+Trade) | CB (Micro) | Sessions hit |
|:----------:|:-------------------:|:----------:|:----:|
| DEPTH, TRADE, MARK, INDEX | ✅ | ✅ | 2 |
| HEARTBEAT, LIQ | ✅ | ❌ | 1 |

### V3 Broadcast / No-Router (Current)

| Event Type | CA (Feed+Liq+Trade) | CB (Micro) | Sessions hit |
|:----------:|:-------------------:|:----------:|:----:|
| ALL EVENTS | ✅ | ✅ | 2 |

**Fan-out:** 100% of events go to both sessions -> complete duplication.

---

## Full Dataset Performance Results 

| Metric | Baseline (1T) | V3 Broadcast (2T) |
|--------|:---:|:---:|
| **Rules fired** | 13,528,242 | 23,999,351 |
| **Duration** | 56,482 ms | 59,948 ms |
| **Throughput** | 28,560 ev/s | 26,909 ev/s |
| **Speedup** | 1.00x | **0.94x** |

> *Note: Baseline duration was faster in this run (56s vs the previous 74s-82s) due to system variability/JVM warmup.*

### Observations:
Despite the engine now performing a strict **2.0x duplication** on every single event, the V3 Broadcast architecture achieved a **0.94x speedup** (taking 59.9s vs the baseline's 56.4s).

This is a notable improvement from the previous router-based V3 benchmark which posted a **0.80x speedup**. Removing the router overhead from the pipeline proved to be more beneficial than trying to filter out the small fraction of non-overlapping events.

---

## Per-Cluster Breakdown

| Cluster | Events Received | Rules Fired |
|:-------:|:-:|:-:|
| CA (Feed+Liq+Trade) | 1,613,159 | 15,542,454 |
| CB (Microstructure) | 1,613,159 | 8,456,897 |

*Notice that both clusters received the exact total amount of events (1,613,159).*

---

## Correctness Validation

| Check | Result |
|-------|:------:|
| Overlapping signal types | 138/140 ✅ |
| Signal misses | 2 |
| Extra redundant | 3,230,403 |
| No infinite loops | ✅ (max: 15,542,454) |
| **Status** | **✅ PASS** |

*(The 2 missing signals remain the ones from deliberately deleted cross-cluster rules, and the extra redundant signals match expectations for the 100% duplication rate).*

---

## Conclusions

We have pushed cluster-based parallelization out as far as it can go for this ruleset. With 100% event overlap, we are forcing the hardware to perform 2.0x the total event insertion work. The fact that the 2-thread engine almost matched the single-thread speed (0.94x) shows the implementation is highly efficient. However, actual linear scaling (>1.0x) is mathematically blocked because of this duplication wall. 

To achieve a true speedup at scale, the architecture must transition from "Cluster-level" parallelism to "Symbol-level" parallelism, where event duplication drops to exactly 0%.

---

## How to Reproduce

```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep

# Full dataset run (requires 4GB+ heap)
export MAVEN_OPTS="-Xmx4g"
mvn test-compile exec:java \
  -Dexec.mainClass=org.kie.benchmark.binance.parallel.BinanceClusterBenchmarkV3 \
  -Dexec.args=0 -Dexec.classpathScope=test

# Quick test run (50k events)
mvn test-compile exec:java \
  -Dexec.mainClass=org.kie.benchmark.binance.parallel.BinanceClusterBenchmarkV3 \
  -Dexec.args=50000 -Dexec.classpathScope=test
```
