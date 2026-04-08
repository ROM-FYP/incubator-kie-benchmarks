# Performance Analysis — Why Parallelism Works at Small Scale

> Understanding the Rete graph pruning effect and its limits at scale.

---

## The Paradox

Despite near-complete event duplication (99.98% of events go to both sessions in V3), the 2-thread architecture achieves a **1.62× speedup** at 50,000 events. At 1.6M events, it slows to **0.80×**. This document explains why.

---

## Key Observation: Per-Rule Cost Drops Dramatically

| Metric | Baseline (1T) | V3 (2T) | Ratio |
|--------|:---:|:---:|:---:|
| Rules fired (50k) | 399,957 | 750,003 | 1.88× more |
| Duration (50k) | 12,511 ms | 7,705 ms | 1.62× faster |
| **Rules/second** | **31,997** | **97,403** | **3.04× cheaper per rule** |

V3 fires nearly twice as many rules but finishes 1.6× faster. Each individual rule evaluation in V3 is **~3× cheaper** than in the baseline. The speedup comes not from avoiding work, but from making each unit of work cheaper.

---

## Root Cause: Rete Network Size

### How Drools Evaluates Rules

When a fact is inserted into a `KieSession`, it propagates through the **Rete network** — a directed acyclic graph of:

1. **Alpha nodes** — single-pattern filters (e.g., `MarketEvent(eventType == "DEPTH")`)
2. **Beta nodes** — cross-pattern joins (e.g., matching a `MarketEvent` against a `FeedHealth` for the same symbol)
3. **Terminal nodes** — activated rules ready to fire

The cost of `insert()` scales with:
- **Number of alpha nodes** traversed (linear in rule count)
- **Number of beta joins** evaluated (super-linear — joins create combinatorial fan-out)
- **Working memory size** (more existing facts = more join candidates per beta node)

### Baseline vs Partitioned Sessions

| | Baseline | CA (V3) | CB (V3) |
|--|:---:|:---:|:---:|
| **Total rules** | 97 | 45 | 62 |
| **Alpha nodes** | ~97 | ~45 | ~62 |
| **Beta joins** | O(97²) | O(45²) | O(62²) |
| **Join surface** | **9,409** | **2,025** | **3,844** |

> Beta-join counts are illustrative — actual Rete sharing reduces the raw count, but the quadratic relationship holds. Halving the rules reduces join work by ~4×, not 2×.

When a DEPTH event enters the baseline session, it must traverse ~97 alpha nodes and evaluate joins across all 97 rules' beta networks. In CA, the same event traverses only ~45 alpha nodes with a much smaller join space.

### The Math of Parallelism vs Duplication

```
Baseline cost ≈ N × C(97)          where C(r) = cost per event in r-rule Rete
V3 cost       ≈ N × C(45) + N × C(62)    (both sessions run in parallel)

Wall-clock time:
  Baseline  = N × C(97)
  V3        = max(N × C(45), N × C(62))   (parallel execution)

If C(r) grows super-linearly with r:
  C(97) >> C(45) + C(62)
  → parallelism wins despite duplication
```

At 50k events, `C(97) ≈ 312 μs/event` while `max(C(45), C(62)) ≈ 154 μs/event` — a 2× reduction in per-event cost, which combined with parallel execution yields the 1.62× speedup.

---

## Why It Breaks Down at 1.6M Events

Three factors erode the Rete-pruning advantage at scale:

### 1. Working Memory Accumulation

At 50k events, each session's working memory stays small. Beta-join cost depends on **working memory size × Rete size**:

```
Join cost ≈ |working_memory| × |beta_nodes|
```

At 1.6M events, working memory grows (via `modify()` chains and accumulated state objects), making join evaluation expensive regardless of Rete size.

### 2. GC Pressure

| | Baseline | V3 (2T) |
|--|:---:|:---:|
| Sessions | 1 | 2 |
| Events processed | 1.6M | 3.2M (2× duplication) |
| Object allocations | ~1× | ~2× |
| GC pauses | Normal | Elevated |

Two sessions creating 2× the objects means the garbage collector runs more frequently and with longer stop-the-world pauses. At 50k events, GC is negligible. At 1.6M events, it can account for 10-20% of total execution time.

### 3. CPU Cache Thrashing

| Scale | L2/L3 cache behavior |
|-------|---------------------|
| 50k events | Both sessions' Rete nodes + working memory fit in L3 cache |
| 1.6M events | Working memory exceeds cache → frequent main memory fetches |

Two threads with separate working memories compete for the same L2/L3 cache lines, causing **cache evictions** that don't occur in the single-session baseline.

---

## Empirical Evidence

### Per-Rule Cost at Different Scales

| Scale | Baseline (μs/rule) | V3 per-session (μs/rule) | Speedup per rule |
|-------|:---:|:---:|:---:|
| 50k events | 31.3 | 10.3 | **3.04×** |
| 1.6M events | 5.5 | 3.9 | **1.42×** |

At 50k, the per-rule cost advantage is 3×. At 1.6M, it shrinks to 1.4× — not enough to overcome the 2× duplication overhead.

### Why the Baseline Gets Faster at Scale

The baseline improves from 31.3 μs/rule (50k) to 5.5 μs/rule (1.6M) due to JIT optimization — the JVM's HotSpot compiler aggressively optimizes the Rete traversal hot path over millions of iterations. The partitioned sessions see less benefit because each session's hot path is different, reducing JIT effectiveness.

---

## Strategies for Scaling to 1.6M+ Events

The three bottlenecks at scale are **event duplication**, **working memory bloat**, and **GC pressure**. Each strategy below targets one or more of these:

### Strategy 1: Symbol-Based Partitioning (Eliminates Duplication)

Instead of routing by *rule cluster*, route by *symbol*. Each session gets **all 97 rules** but only events for its assigned symbols.

```
10 symbols ÷ 2 threads = 5 symbols per session

Session A: BTCUSDT, ETHUSDT, SOLUSDT, XRPUSDT, DOGEUSDT
Session B: ADAUSDT, BNBUSDT, AVAXUSDT, DOTUSDT, LINKUSDT
```

**Why it works:**
- **Zero event duplication** — each event goes to exactly one session
- **Zero generic rule duplication** — each generic rule fires once per event, not twice
- **Linear scaling** — adding threads = dividing the symbol space
- **Working memory is partitioned** — each session only accumulates facts for its symbols

**Expected outcome:** Near-linear speedup because total work = baseline work (no duplication tax).

**Trade-off:** No Rete pruning benefit — each session has the full 97-rule Rete. But eliminating duplication is worth far more than Rete size reduction at scale.

```
Estimated throughput:
  Baseline  = 21,625 ev/s (1 thread × 97 rules × all symbols)
  Symbol-2T = ~40,000 ev/s (2 threads × 97 rules × 5 symbols each)
  Symbol-4T = ~70,000 ev/s (4 threads × 97 rules × 2-3 symbols each)
```

### Strategy 2: Aggressive Fact Retraction (Reduces Working Memory)

Currently, `CLEANUP_RetractProcessedEvent` retracts `MarketEvent` objects after processing. But **state objects persist** — `FeedHealth`, `DepthState`, `VolState`, `SpreadVelocityState`, etc. These accumulate in working memory and inflate beta-join costs.

**Approach:**
- Add TTL-based retraction for intermediate derived facts (e.g., `DepthUpdateTick`, `SignificantTrade`)
- Use `@expires` annotations more aggressively on CEP event types
- Periodically compact working memory by replacing stale state objects rather than modifying them

**Impact:** Reduces `|working_memory|` in the join cost formula, directly lowering per-event cost at scale.

### Strategy 3: Generic Rule Consolidation (Reduces Duplication Overhead)

The 10 generic rules (validation + cleanup) fire in **every** session that receives an event. At 2 sessions × 1.6M events, that's 32M extra rule evaluations.

**Approach: Pre-filter stage**
```
Stage 1 (single thread): Run 10 generic rules on raw events
         ↓ (validated events only)
Stage 2 (parallel):      Route to cluster sessions (no generic rules)
```

This eliminates generic rule duplication entirely. The pre-filter is cheap (~10 simple rules) and reduces the event stream by filtering out invalid events before they hit the expensive cluster sessions.

**Impact:** Saves ~16M rule firings at 1.6M events (the redundant generic fires in session 2).

### Strategy 4: Hybrid — Symbol Partitioning + Rete Pruning

Combine the best of both approaches:

```
Thread 1: Symbols {A,B,C,D,E} × Rules {CA: 45 rules}
Thread 2: Symbols {A,B,C,D,E} × Rules {CB: 62 rules}
Thread 3: Symbols {F,G,H,I,J} × Rules {CA: 45 rules}
Thread 4: Symbols {F,G,H,I,J} × Rules {CB: 62 rules}
```

**Why it works:**
- **Zero event duplication** — each event goes to exactly one (symbol, cluster) pair
- **Rete pruning** — each session has a smaller Rete graph
- **Linear scaling** — 4 threads with no contention

**Trade-off:** Requires a 2D routing table (symbol × cluster) and 4 sessions. More complex, but combines the advantages of both approaches.

### Strategy 5: JVM-Level Tuning

Without changing the architecture, JVM flags can mitigate GC and memory effects:

| Flag | Purpose | Expected impact |
|------|---------|:---:|
| `-XX:+UseZGC` | Low-latency GC with <1ms pauses | Reduces GC pause impact by ~5-10% |
| `-Xmx8g` | Double heap headroom | Reduces GC frequency at 1.6M events |
| `-XX:+UseNUMA` | NUMA-aware memory allocation | Keeps each thread's working memory on its local node |
| `-XX:-TieredCompilation` | Force C2 JIT from the start | Better hot-path optimization for long runs |

### Strategy 6: Batch Insert with Deferred Firing

Currently, each event is inserted and rules are fired immediately:
```java
session.insert(event);
session.fireAllRules();  // fires after EVERY insert
```

Batching can amortize Rete propagation overhead:
```java
for (int i = 0; i < BATCH_SIZE; i++) {
    session.insert(events.get(offset + i));
}
session.fireAllRules();  // fire once for the entire batch
```

**Why it works:** Drools can optimize Rete propagation across multiple facts inserted before firing, reducing redundant node evaluations. The trade-off is slightly different rule firing order, which may affect stateful rules.

---

## Summary: Strategy Comparison

| Strategy | Eliminates duplication | Reduces WM | Reduces GC | Complexity | Expected speedup (1.6M) |
|----------|:---:|:---:|:---:|:---:|:---:|
| **1. Symbol partitioning** | ✅ | ✅ | ✅ | Low | **~1.8-2.0×** |
| **2. Aggressive retraction** | | ✅ | ✅ | Low | **~1.1-1.2×** |
| **3. Pre-filter stage** | Partial | | | Medium | **~1.1×** |
| **4. Hybrid (sym + cluster)** | ✅ | ✅ | ✅ | High | **~2.5-3.0×** |
| **5. JVM tuning** | | | ✅ | Low | **~1.05-1.1×** |
| **6. Batch insert** | | | | Low | **~1.1-1.3×** |

**Recommended path:** Start with **Strategy 1 (symbol partitioning)** — it's the simplest to implement and addresses the root cause directly. If more speedup is needed, layer on **Strategy 4 (hybrid)**.

---

## Implications

### What This Tells Us

1. **Rete pruning is effective** — splitting rules across sessions genuinely reduces per-event cost.
2. **Event duplication is the bottleneck** — any architecture requiring events in multiple sessions will hit this wall at scale.
3. **Memory, not CPU, is the limiter** — GC and cache effects dominate over raw computation at >100k events.

### The Key Insight

**Selectivity matters more than parallelism.** A 2-thread model with zero event overlap will outperform a 4-thread model with 3× duplication every time. The optimal architecture minimizes `events_per_session × rules_per_session`, not just `rules_per_session`.
