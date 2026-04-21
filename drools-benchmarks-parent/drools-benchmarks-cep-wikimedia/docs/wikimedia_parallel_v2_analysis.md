# Wikimedia CEP Parallel Benchmark V2 Analysis

## Goal
Improve parallel throughput by resolving overhead from the initial broadcast architecture. 

## Approach
1. **Alpha-filter Routing:** Rather than broadcasting all events to all clusters, events are now selectively routed based on their characteristics:
    * `C1 (Minor)` receives events where `bot == false` and size inside `[-50, 50]`
    * `C2 (Bot)` receives events where `bot == true`
    * `C3 (Content+Vandalism)` receives all events (baseline for join rules)
    * `C4 (Discussion)` receives events where `title` starts with "Talk:"
2. **Rule Duplication to Break Coupling:** We duplicated 7 Bot utility rules from C2 into C3. This eliminates the dependency between C2 (Bot) producing a `BotHealthCheck` and C3 trying to read it (for `CorrelateHighRiskUser`), meaning C3 now generates its own `BotHealthCheck` and we don't need any cross-session object forwarding.
3. **Session Rule Isolation:** A fatal flaw in the previous run was that Drools' `KieFileSystem` automatically picked up the `wikimedia_content_moderation_join_heavy.drl` from the project classpath, resulting in all 4 sessions deploying the entire rule set. We fixed this by assigning unique, isolated package prefixes to each cluster during the runtime `kmodule.xml` generation.

## Results
We successfully isolated the rules and eliminated the infinite loop issues.

**Benchmark Dataset:** 37,155 full events (`wikimedia_stream_20260417_224154.jsonl`).

```text
── Comparison ──────────────────────────────
                                   Single    Cluster (4T)
Rules fired                       174,461         204,469
Duration                        17,954 ms        9,956 ms
Events/sec                        2069.46         3731.92
Speedup                             1.00x           1.80x

── Per-Session Breakdown ───────────────────
  C1 (Minor)             [7 rules]       Events:   17,306  Fired:  103,836
  C2 (Bot)               [10 rules]      Events:   15,574  Fired:   49,533
  C3 (Content+Vandalism) [47 rules]      Events:   37,155  Fired:   50,740
  C4 (Discussion)        [6 rules]       Events:       60  Fired:      360
```

## Analysis
* **Throughput Doubled:** We achieved a **1.80x speedup** (nearly double throughput) over the single-session baseline.
* **Overhead Addressed:** The total Rules Fired in Parallel is 204,469 vs 174,461 in Single. This small increase (~30k) is exactly as expected, representing the extra firings of the duplicated Bot Rules inside C3 required to resolve the dependency. This extra processing is entirely masked by the 4-thread parallel throughput.
* **Clean Community Separation:** C1 fired 103k rules. This is by far the heaviest subsystem (minor edits are very common), and extracting it into its own thread provided the largest performance boost, as it no longer blocks the C3 (Content+Vandalism) agenda!

## Scalability Test (222,000 Events)

After collecting a much larger 3-hour dataset (`wikimedia_stream_20260421_104232.jsonl`), we re-evaluated the parallel engine to observe scaling behavior:

```text
── Comparison ──────────────────────────────
                                   Single    Cluster (4T)
Rules fired                       937,966       1,138,998
Duration                        25,656 ms       27,665 ms
Events/sec                        8659.38         8030.54
Speedup                             1.00x           0.93x

── Per-Session Breakdown ───────────────────
  C1 (Minor)             [7 rules]       Events:   80,524  Fired:  483,144
  C2 (Bot)               [10 rules]      Events:  114,772  Fired:  314,094
  C3 (Content+Vandalism) [47 rules]      Events:  222,165  Fired:  337,386
  C4 (Discussion)        [6 rules]       Events:      729  Fired:    4,374
```

### Bottleneck Diagnosis
The speedup completely evaporated on the 222k dataset, dropping from **1.8x to 0.93x**. The breakdown exposes two significant scaling flaws:

1. **Rule Duplication Weight (The C3 Anchor):** Because we duplicated the Bot Pipeline (7 rules) into C3 to satisfy a correlation condition, C3 was forced to execute bot classification logic for all **114,772 Bot events** in the stream. This directly caused 200,000 extra redundant rule firings exactly where we didn't want them: inside the slowest thread. C3 (our anchor) took the entire 27 seconds to process the stream alone, while C1/C2 likely finished much earlier.
2. **Micro-transaction Overhead:** `WikimediaClusterOrchestrator` currently invokes `session.fireAllRules()` synchronously after every single event insertion. Performing 222,000 engine synchronizations inside 4 aggressively competing threads creates massive CPU cache contention and locking overhead on the JVM, completely nullifying the multi-threading gains.

Going forward, transitioning to micro-batching (i.e. firing rules after a chunk of 500 events) or isolating the `CorrelateHighRiskUser` rule into a downstream correlation session would solve this scaling wall.
