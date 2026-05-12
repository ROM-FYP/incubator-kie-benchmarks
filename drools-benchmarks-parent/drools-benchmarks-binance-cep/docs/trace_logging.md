# Trace Logging — Technical Reference

## Architecture

`MiningTraceLogger` extends Drools' `DefaultAgendaEventListener`, hooking into the rule engine's agenda to capture every rule activation. It sits in the `org.kie.benchmark.binance.util` package alongside `EventReplayController` and `SegmentReader`.

```
KieSession
  └── addEventListener(MiningTraceLogger)
        └── afterMatchFired(event)
              └── writes → CSV file
```

### Class: `MiningTraceLogger`

**Location:** `src/main/java/org/kie/benchmark/binance/util/MiningTraceLogger.java`

| Method | Purpose |
|--------|---------|
| `MiningTraceLogger(String filePath)` | Opens file, writes CSV header |
| `startNewTransaction()` | Increments CaseID, resets sequence counter — call once per benchmark invocation |
| `afterMatchFired(event)` | Captures rule name + timestamp, writes CSV row (called by Drools engine) |
| `close()` | Flushes and closes the writer |

### Integration Points

Both benchmark classes (`BinanceRiskControlBenchmark`, `BinanceFullDatasetBenchmark`) follow the same pattern:

```java
// Field
@Param({ "false" })
private boolean enableTraceLog;
private MiningTraceLogger traceLogger;

// setupTrial() — create once
if (enableTraceLog) {
    traceLogger = new MiningTraceLogger("binance-risk-" + symbol + "-" + ts + ".csv");
}

// setupInvocation() — register on each new session
if (traceLogger != null) {
    kieSession.addEventListener(traceLogger);
}

// benchmark method — mark new transaction
if (traceLogger != null) {
    traceLogger.startNewTransaction();
}

// teardownTrial() — close file
if (traceLogger != null) {
    traceLogger.close();
}
```

---

## CSV Schema

| Column | Type | Description |
|--------|------|-------------|
| `CaseID` | `long` | Monotonically increasing per invocation (1, 2, 3...) |
| `SequenceNr` | `int` | Rule firing order within an invocation (resets each invocation) |
| `Activity` | `String` | Drools rule name (commas replaced with spaces) |
| `Timestamp` | `long` | `System.currentTimeMillis()` at fire time |

Example:
```csv
CaseID,SequenceNr,Activity,Timestamp
1,1,INGEST_ValidateTimestamp,1710000000001
1,2,FEED_UpdateLastSeen,1710000000002
1,3,SPREAD_ComputeBidAskSpread,1710000000003
2,1,INGEST_ValidateTimestamp,1710000000100
```

---

## Performance Impact

The logger writes to disk on every rule firing (unbuffered `write()` per event). For a typical run with taxonomy.drl (70 rules, ~45K firings per invocation):

| Scenario | Overhead |
|----------|----------|
| `enableTraceLog=false` | **Zero** — no logger object created |
| `enableTraceLog=true`, single symbol | ~5–15% throughput reduction (disk I/O bound) |
| `enableTraceLog=true`, full dataset (67K events) | ~10–20% throughput reduction |

> **Note:** The `BufferedWriter` reduces syscall overhead, but the volume of writes (one per rule firing) still impacts benchmark numbers. Always compare throughput numbers from runs **without** logging enabled.

---

## Extending the Logger

To add extra columns (e.g., matched fact types, rule salience):

1. Edit `afterMatchFired()` in `MiningTraceLogger.java`
2. Access `event.getMatch().getObjects()` for matched facts
3. Access `event.getMatch().getRule().getSalience()` for priority
4. Update the CSV header accordingly

To log to a different format (JSON, Parquet):
- Replace the `BufferedWriter` with the appropriate writer
- Keep the same `DefaultAgendaEventListener` hook
