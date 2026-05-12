# Wikimedia CEP — Parallel Execution V1 Analysis

**Date:** 2026-04-18  
**Dataset:** `wikimedia_stream_20260417_224154.jsonl` — 13,914 events  
**Architecture:** 4-cluster broadcast model with event-level parallelism

---

## 1. Benchmark Results

| Metric               |  Baseline (Single) |  Cluster (4T)   |
|-----------------------|-------------------:|----------------:|
| Rules fired           |           69,056   |       276,224   |
| Duration              |       157,543 ms   |   500,627 ms    |
| Throughput            |   88.32 events/sec |  27.79 events/sec|
| Speedup               |            1.00×   |        **0.31×**|

### Per-Session Breakdown

| Cluster                     | Events Received | Rules Fired |
|-----------------------------|----------------:|------------:|
| C1 (Minor Edits)            |          13,914 |      69,056 |
| C2 (Bot)                    |          13,914 |      69,056 |
| C3 (Content + Vandalism)    |          13,914 |      69,056 |
| C4 (Discussion)             |          13,914 |      69,056 |

**Correctness:** ✅ PASS (no infinite loops detected)

---

## 2. Root Cause Analysis — Why 0.31× (Slower, Not Faster)

### Problem 1: Every Session Fired the Same Number of Rules as the Baseline

Each of the 4 sessions fired exactly **69,056 rules** — the identical count as the baseline single-session. This means the DRL splitting did **not** produce the expected effect. Instead of each session containing only its cluster-specific rules (e.g., C1 should have only 7 rules, C4 only 6 rules), every session ended up executing the full rule base.

**Root Cause:** The `DrlSplitter.java` regex-based splitting is likely failing due to **Windows `\r\n` line endings** in the DRL resource. The splitter calls `fullDrl.split("\n")`, which leaves trailing `\r` on each line. While the regex patterns use `\s*$` (which should consume `\r`), the `RULE_START_PATTERN`:

```java
Pattern.compile("^\\s*rule\\s+[\"']?([^\"']+?)[\"']+?\\s*$", Pattern.MULTILINE)
```

...may fail to match because the `[^"']+?` non-greedy quantifier combined with trailing `\r` before `\s*$` could cause the regex to not capture the rule name correctly. If `extractRulesByName()` returns an empty map, `buildDrlForRules()` would produce a DRL containing **only the preamble** (package, imports, all declares). Since all event type declarations (`@role(event)`) are in the preamble, and the DRL also includes all `function` definitions, the Drools compiler would still compile successfully — but with **no rule bodies**.

However, the fact that 69,056 rules fired per session (same as baseline) rather than 0 suggests a different issue: **KieServices singleton contamination**. The `KieServices.Factory.get()` returns a global singleton, and even though each cluster uses a unique `ReleaseId`, the default KieBase discovery mechanism may be loading rules from the project's classpath in addition to the dynamically compiled DRL. This would cause each session to have access to **all 53 rules** regardless of the DRL splitting.

### Problem 2: Broadcast Overhead — 4× Event Insertion

The broadcast architecture inserts **every event into every session**:

```
Total events × sessions = 13,914 × 4 = 55,656 event insertions
```

Even if DRL splitting worked correctly, the overhead model is:
- Event serialization and insertion cost is repeated 4×
- `session.fireAllRules()` is called 4× per event (once in each thread)
- The Drools engine still evaluates all LHS patterns against each inserted event, even if no rule fires

For the Wikimedia DRL, every rule begins with matching a `WikiEvent`, so **every session must pattern-match every event** against its rules, regardless of whether the event triggers any rule. This is fundamentally different from the Binance architecture where different event types could be routed to specific sessions.

### Problem 3: Queue Contention and Thread Synchronization

The orchestrator uses `LinkedBlockingQueue` for event distribution:

```java
for (WikiEvent event : events) {
    for (BlockingQueue<WikiEvent> q : eventQueues.values()) {
        q.put(event);  // Producer pushes to 4 queues sequentially
    }
}
```

This creates a producer-consumer bottleneck:
- The main thread acts as the single producer for all 4 queues
- Each `q.put()` is a synchronized operation
- If a consumer is slow (e.g., C3 with 40 rules), its queue backs up while the producer blocks on the next queue

### Problem 4: Pseudo-Clock Synchronization Cost

Each session maintains its own `SessionPseudoClock`:

```java
long currentTime = clock.getCurrentTime();
if (event.getTimestamp() > currentTime) {
    clock.advanceTime(event.getTimestamp() - currentTime, TimeUnit.MILLISECONDS);
}
```

This means each of the 4 sessions independently advances its clock for each of the 13,914 events, adding synchronization overhead without benefit.

---

## 3. Cross-Cluster Dependency Analysis

### Infomap Clustering Output

Infomap detected **4 top-level modules** from the causal trace of 50 rules and 88 edges:

| Cluster | Pipeline            | Rules | Flow Weight |
|---------|---------------------|------:|------------:|
| C1      | Minor Edits         |     6 |  0.655 (49%) |
| C2      | Bot                 |    10 |  0.198 (15%) |
| C3      | Content + Vandalism |    40 |  0.137 (10%) |
| C4      | Discussion          |     6 |  0.006 (<1%) |

> **Note:** 3 rules from the full DRL (53 total) did not appear in the ftree: `Minor_Validate_Trusted`, `Content_Review_Major_Experienced`, and certain dead-end vandalism rules. These rules never fired during the trace generation run, so Infomap excluded them from the graph.

### Cross-Cluster Dependencies

The dependency analysis (via `dependency_analysis.py`) identified **one cross-cluster dependency**:

```
C2 (Bot) ──[BotHealthCheck]──> C3 (Content + Vandalism)
```

**Specifically:** The rule `CorrelateHighRiskUser` in C3 requires both:
- `BotHealthCheck` — produced by C2 (Bot pipeline)
- `VandalismAnalysis` — produced by C3 (already local)

### How the Dependency Was Handled

The dependency was handled by **accepting the trade-off**: the `CorrelateHighRiskUser` rule is assigned to C3 but will **never fire** in the parallel configuration because `BotHealthCheck` facts from C2 are not forwarded to C3.

This was a deliberate design decision, consistent with the Binance approach:

```java
// From WikimediaClusterDrlGenerator.java (lines 36-41):
// "Since this rule is in C3 and BotHealthCheck is produced by C2,
//  a strict separation would miss this correlation. We accept this:
//  the correlation rule simply won't fire in the parallel configuration
//  (same trade-off as Binance). This is acceptable because correlations
//  are rare and don't affect pipeline correctness."
```

The CorrelateHighRiskUser flow weight in the ftree is **0.000311475** (<0.03% of total flow), confirming its negligible impact.

**Alternative approaches not implemented:**
1. **Fact forwarding** — Forward `BotHealthCheck` from C2 to C3 via a listener. This was used in the routing-based architecture (`FactForwardingListener.java`) but was rejected for the clean broadcast model.
2. **Merge C2 into C3** — Would eliminate the dependency but increase C3's rule count to 50, creating severe load imbalance.
3. **Duplicate bot rules in C3** — Would cause double-counting of bot metrics.

---

## 4. Implementation Details

### Files Created

| File | Purpose |
|------|---------|
| `DrlSplitter.java` | Regex-based DRL parser that extracts preamble + specific rules |
| `WikimediaClusterDrlGenerator.java` | Maps Infomap clusters to rule name lists |
| `WikimediaClusterOrchestrator.java` | 4-thread producer-consumer event broadcaster |
| `WikimediaClusterBenchmark.java` | Benchmark harness comparing baseline vs parallel |

### Architecture

```
                    ┌─────────────┐
                    │  Event File │
                    │  .jsonl     │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  Main Thread│
                    │  (Producer) │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┬───────────┐
              ▼            ▼            ▼           ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
        │ Queue C1 │ │ Queue C2 │ │ Queue C3 │ │ Queue C4 │
        └────┬─────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
             ▼            ▼            ▼           ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
        │Session C1│ │Session C2│ │Session C3│ │Session C4│
        │ 7 rules  │ │ 10 rules│ │ 40 rules │ │ 6 rules  │
        │ Thread-1 │ │ Thread-2│ │ Thread-3 │ │ Thread-4 │
        └──────────┘ └──────────┘ └──────────┘ └──────────┘
```

Each session receives ALL events (broadcast model) and filters via rule LHS patterns.

---

## 5. What Went Wrong — Summary

| Issue | Impact | Severity |
|-------|--------|----------|
| DRL splitting not isolating rules | Each session runs ALL 53 rules instead of its cluster subset | 🔴 Critical |
| Broadcast model (all events → all sessions) | 4× event insertion overhead | 🟡 High |
| Single-producer queue bottleneck | Sequential writes to 4 queues serializes the broadcast | 🟠 Medium |
| No warmup or JIT stabilization | First run may include compilation overhead | 🟡 Medium |

---

## 6. Root Cause Investigation: DRL Splitting Failure

The most critical issue is that each session fired exactly 69,056 rules (identical to baseline). This needs detailed root-cause analysis:

### Hypothesis A: Regex Mismatch on Windows Line Endings

The `DrlSplitter` splits on `"\n"` but DRL files on Windows have `"\r\n"`. After splitting, each line retains a trailing `"\r"`. The rule start regex:

```regex
^\s*rule\s+["']?([^"']+?)["']?\s*$
```

When applied to a line like `rule "Minor_Detect"\r`, the pattern should still match because `\s*` at the end consumes `\r`. **However**, if the DRL is loaded via `InputStream.readAllBytes()` from a jar/classpath resource, the line endings may be platform-specific and the regex behavior varies.

### Hypothesis B: KieServices Singleton Contamination

The more likely cause: `KieServices.Factory.get()` returns a **JVM-wide singleton**. The project's `kmodule.xml` already defines KieBases with rules on the classpath. When `buildSessionFromDrl()` creates a new `KieContainer` from a unique `ReleaseId`, the container should be isolated — but if there is any classpath scanning pollution, the session may inherit rules from the project's default KieBase.

### Recommended Fix

1. **Debug the DRL output** — Add a line in `WikimediaClusterDrlGenerator.generateClusterDrls()` to write each cluster's generated DRL to a temp file. Count the rules in each file.
2. **Debug the session** — After building each session, print `session.getKieBase().getKiePackages()` and count the rules per package.
3. **Normalize line endings** — Replace `fullDrl.split("\n")` with `fullDrl.replace("\r\n", "\n").split("\n")` in `DrlSplitter.java`.

---

## 7. Next Steps

1. **Fix DRL splitting** — Verify and fix the regex/line-ending issue; confirm each session has only its cluster rules.
2. **Add session rule count logging** — Print the number of rules loaded per session at startup.
3. **Re-run benchmark** — After fixing, expect C1 and C4 sessions to fire significantly fewer rules.
4. **Consider selective routing** — Instead of broadcasting all events, route events to relevant sessions based on event characteristics (e.g., `bot == true` → only C2).
5. **Test on larger dataset** — The current 13,914-event dataset is small; need ~1.6M events to match Binance scale.
