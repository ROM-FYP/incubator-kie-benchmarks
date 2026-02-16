# Collected Data Reference — Event Replayer Guide

Everything you need to know about the data produced by the Binance WS Data Collector
to build a deterministic **event replayer**.

---

## 1. Output Directory Layout

After a collection run, the dataset directory looks like this:

```
<output-dir>/<dataset-id>/
├── metadata.json            # Run configuration + final summary
├── reconnects.jsonl         # Connection lifecycle events
├── stats.jsonl              # Periodic throughput snapshots
└── segments/
    ├── events_20260213_164500_000001.jsonl.gz
    ├── events_20260213_164600_000002.jsonl.gz
    └── ...
```

| File | Format | Rotation | Purpose for Replayer |
|---|---|---|---|
| `metadata.json` | JSON (pretty) | No | Discover symbols, streams, timing, and dataset boundaries |
| `segments/*.jsonl.gz` | JSONL, one envelope per line, gzip-compressed | Every 1 min or 250 MB | **Primary event source** — read these in filename order |
| `reconnects.jsonl` | JSONL | No | Detect data gaps to avoid replaying across missing windows |
| `stats.jsonl` | JSONL | No | Throughput baselines for replay rate calibration |

### Segment File Naming

```
events_YYYYMMDD_HHMMSS_NNNNNN.jsonl[.gz]
        └── UTC time ──┘ └ seq ┘
```

- **Sort segments by filename** (lexicographic = chronological).
- `.gz` suffix present when the collector ran with `-compress=true` (default).
- Without compression, files are plain `.jsonl`.

---

## 2. Event Envelope Schema

Every line in a segment file is a JSON object conforming to this schema:

```json
{
  "dataset_id":     "run_20260213_1645_10sym",
  "source_stream":  "trade",
  "symbol":         "BTCUSDT",
  "exchange_ts":    1739454300123,
  "recv_ts":        1739454300145678,
  "local_seq":      42,
  "raw":            { /* original Binance payload */ }
}
```

### Field Reference

| Field | Type | Always Present | Description |
|---|---|---|---|
| `dataset_id` | `string` | ✅ | Identifies this collection run. Same value for every event in the dataset. |
| `source_stream` | `string` | ✅ | One of: `trade`, `book`, `mark`, `index`, `liquidation` |
| `symbol` | `string` | ✅ | Uppercase trading pair, e.g. `BTCUSDT` |
| `exchange_ts` | `int64` | ✅ | Exchange event time in **milliseconds** since Unix epoch |
| `recv_ts` | `int64` | ✅ | Local receive time in **microseconds** since Unix epoch |
| `local_seq` | `int64` | ✅ | Per-`(source_stream, symbol)` 0-based monotonic counter |
| `raw` | `object` | ✅ | Full Binance payload (the `data` field from the combined stream) |
| `raw_text` | `string` | ❌ | Original frame text — only if JSON parsing of `raw` failed |
| `parse_error` | `string` | ❌ | Error message — only present alongside `raw_text` |

> **Replayer note:** Skip events where `parse_error` is present — they represent malformed frames.

---

## 3. Timestamp Semantics

This is the most critical section for building a replayer.

### The Two Clocks

| Timestamp | Source | Precision | Unit | Meaning |
|---|---|---|---|---|
| `exchange_ts` | Binance matching engine | **Milliseconds** | ms since epoch | When the event *happened* on the exchange |
| `recv_ts` | Collector host system clock | **Microseconds** | µs since epoch | When the WebSocket frame *arrived* locally |

### Converting Between Units

```
exchange_ts is in MILLISECONDS  → divide by 1,000 for seconds
recv_ts     is in MICROSECONDS  → divide by 1,000,000 for seconds
```

To compare them in the same unit:
```
recv_ts_ms = recv_ts / 1000
feed_latency_ms = recv_ts_ms - exchange_ts
```

### Replay Modes

| Mode | Ordering Key | Tiebreaker | Use Case |
|---|---|---|---|
| **Event-time replay** | `exchange_ts` | `local_seq` (for same-ts events) | Idealized exchange-clock ordering — removes network effects |
| **Arrival-time replay** | `recv_ts` | `local_seq` | Reproduces real receive conditions including network jitter |

#### Event-Time Replay Algorithm

```
1. Read all segments in filename order.
2. Collect events into a priority queue keyed by (exchange_ts, local_seq).
3. Emit events in priority order.
4. To simulate real-time pacing:
   delay = (event[n].exchange_ts - event[n-1].exchange_ts) ms
   sleep(delay / speed_multiplier)
```

#### Arrival-Time Replay Algorithm

```
1. Read all segments in filename order (events are already in recv_ts order within segments).
2. Merge segments using a merge-sort on (recv_ts, local_seq).
3. To simulate real-time pacing:
   delay = (event[n].recv_ts - event[n-1].recv_ts) µs
   sleep(delay / speed_multiplier)
```

> **Key insight:** Within a single segment, events are in arrival order. Across segments,
> a boundary event in segment N+1 may have `recv_ts` equal to or slightly before the last
> event in segment N (rotation boundary effect). Use a merge to handle this.

---

## 4. Stream Types & Raw Payload Schemas

### 4.1 `trade` — Individual Trade Executions

**Update rate:** Variable; 50–2000+ msg/s across 10 symbols.

```json
{
  "e": "trade",
  "E": 1739454300123,
  "T": 1739454300123,
  "s": "BTCUSDT",
  "t": 123456789,
  "p": "97250.50",
  "q": "0.001",
  "m": true
}
```

| Key | Type | Description |
|---|---|---|
| `e` | string | Event type = `"trade"` |
| `E` | int64 | Event time (ms) |
| `T` | int64 | **Trade time** (ms) — this is the `exchange_ts` source |
| `s` | string | Symbol |
| `t` | int64 | Unique trade ID |
| `p` | string | Price (decimal string) |
| `q` | string | Quantity (decimal string) |
| `m` | bool | `true` = buyer is market maker (i.e. taker was selling) |

**Replayer usage:** Use `p`, `q`, `m`, and `T` to reconstruct OHLCV bars or feed trade-by-trade into a Drools session as individual `TradeFact` objects.

---

### 4.2 `book` — Depth / Order Book Diff Updates

**Update rate:** Fixed 10 msg/s per symbol (depth@100ms).

```json
{
  "e": "depthUpdate",
  "E": 1739454300100,
  "T": 1739454300098,
  "s": "BTCUSDT",
  "U": 5000001,
  "u": 5000005,
  "pu": 5000000,
  "b": [["97250.00", "1.500"], ["97249.50", "0.000"]],
  "a": [["97251.00", "2.300"]]
}
```

| Key | Type | Description |
|---|---|---|
| `e` | string | Event type = `"depthUpdate"` |
| `E` | int64 | **Event time** (ms) — this is the `exchange_ts` source |
| `T` | int64 | Transaction time (ms) |
| `s` | string | Symbol |
| `U` | int64 | First update ID in this event |
| `u` | int64 | Final update ID in this event |
| `pu` | int64 | Previous event's final update ID |
| `b` | array | Bid-side changes: `[price, quantity]` pairs |
| `a` | array | Ask-side changes: `[price, quantity]` pairs |

**Replayer usage:**
- To maintain a local order book, apply diffs incrementally.
- **Quantity `"0.000"` means remove that price level.**
- Verify continuity: `event[n].U == event[n-1].u + 1` and `event[n].pu == event[n-1].u`.
- A gap in update IDs indicates missed messages (likely a reconnect). Check `reconnects.jsonl` for the window.

---

### 4.3 `mark` — Mark Price & Funding Rate

**Update rate:** Fixed 1 msg/s per symbol.

```json
{
  "e": "markPriceUpdate",
  "E": 1739454300000,
  "s": "BTCUSDT",
  "p": "97250.12345678",
  "i": "97248.87654321",
  "P": "97255.00000000",
  "r": "0.00010000",
  "T": 1739462400000
}
```

| Key | Type | Description |
|---|---|---|
| `e` | string | Event type = `"markPriceUpdate"` |
| `E` | int64 | **Event time** (ms) — this is the `exchange_ts` source |
| `s` | string | Symbol |
| `p` | string | Mark price |
| `i` | string | Index price |
| `P` | string | Estimated settle price |
| `r` | string | Funding rate |
| `T` | int64 | Next funding time (ms) |

**Replayer usage:** Feed as `MarkPriceFact` for risk/margin rules. Join with trades on symbol.

---

### 4.4 `index` — Index (Composite Spot) Price

**Update rate:** Fixed 1 msg/s per symbol.

```json
{
  "e": "indexPriceUpdate",
  "E": 1739454300000,
  "i": "BTCUSDT",
  "p": "97248.87654321"
}
```

| Key | Type | Description |
|---|---|---|
| `e` | string | Event type = `"indexPriceUpdate"` |
| `E` | int64 | **Event time** (ms) — this is the `exchange_ts` source |
| `i` | string | Pair (acts as symbol) |
| `p` | string | Index price |

**Replayer usage:** Use for basis (futures-vs-spot) rules. Note the symbol is in field `i`, not `s`.

---

### 4.5 `liquidation` — Forced Orders

**Update rate:** Sporadic; 0–100+ msg/s across all symbols.

```json
{
  "e": "forceOrder",
  "E": 1739454300500,
  "o": {
    "s": "BTCUSDT",
    "S": "SELL",
    "o": "LIMIT",
    "f": "IOC",
    "q": "0.050",
    "p": "96500.00",
    "ap": "96520.30",
    "X": "FILLED",
    "l": "0.050",
    "z": "0.050",
    "T": 1739454300495
  }
}
```

| Key | Type | Description |
|---|---|---|
| `e` | string | Event type = `"forceOrder"` |
| `E` | int64 | Event time (ms) |
| `o.s` | string | Symbol |
| `o.S` | string | Side: `"BUY"` or `"SELL"` |
| `o.o` | string | Order type (usually `"LIMIT"`) |
| `o.f` | string | Time in force (`"IOC"`) |
| `o.q` | string | Original quantity |
| `o.p` | string | Price |
| `o.ap` | string | Average fill price |
| `o.X` | string | Order status: `"NEW"`, `"FILLED"`, `"PARTIALLY_FILLED"` |
| `o.l` | string | Last filled quantity |
| `o.z` | string | Accumulated filled quantity |
| `o.T` | int64 | **Order trade time** (ms) — this is the `exchange_ts` source |

**Replayer usage:** Liquidation cascade detection rules. Note the nested `o` object — `exchange_ts` comes from `o.T`.

---

## 5. `metadata.json` — Dataset Discovery

Use this file to bootstrap the replayer: discover what was collected, how long, and which symbols/streams are available.

```json
{
  "dataset_id": "run_20260213_1645_10sym",
  "start_time": "2026-02-13T16:45:00Z",
  "end_time": "2026-02-13T17:45:00Z",
  "duration_seconds": 3600.0,
  "symbols": ["BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
              "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "LINKUSDT", "ARBUSDT"],
  "streams": ["trade", "book", "mark", "index", "liquidation"],
  "depth_interval": "100ms",
  "num_symbols": 10,
  "total_messages": 1250000,
  "messages_per_stream": {
    "trade": 720000,
    "book": 360000,
    "mark": 36000,
    "index": 36000,
    "liquidation": 98000
  },
  "host_info": {
    "hostname": "collector-01",
    "os": "linux",
    "arch": "amd64",
    "cpu_count": 8,
    "memory_gb": 15.5
  },
  "ntp_offset_ms_start": 1.2,
  "ntp_offset_ms_end": 1.5,
  "software_version": "1.0.0",
  "clock_precision": "microseconds",
  "websocket_endpoint": "wss://fstream.binance.com/stream?streams=...",
  "compression": "gzip",
  "rotation_strategy": "time:1m0s,size:250MB",
  "reconnect_count": 0,
  "notes": ""
}
```

### Key Fields for the Replayer

| Field | How the Replayer Uses It |
|---|---|
| `symbols` | Iterate to set up per-symbol fact streams |
| `streams` | Validate which stream types exist in the data |
| `start_time` / `end_time` | Define the replay window; skip events outside this range |
| `duration_seconds` | Calculate expected replay duration at 1× speed |
| `total_messages` | Progress bar: `events_replayed / total_messages` |
| `messages_per_stream` | Pre-allocate buffers per stream proportionally |
| `clock_precision` | Confirms `recv_ts` is in microseconds |
| `compression` | Tells the replayer whether to open files with gzip decompression |
| `reconnect_count` | If >0, check `reconnects.jsonl` for gap windows |

---

## 6. `reconnects.jsonl` — Data Gap Detection

Each line records a connection lifecycle event:

```json
{
  "ts": 1739457900123456,
  "event": "disconnect",
  "reason": "read_error: i/o timeout",
  "downtime_ms": 0,
  "streams_affected": ["btcusdt@trade", "btcusdt@depth@100ms", "...all 50..."],
  "attempt_number": 0
}
```

| Event Type | Meaning |
|---|---|
| `disconnect` | Connection lost. `downtime_ms` = 0 here; filled on success. |
| `reconnect_failure` | A reconnect attempt failed. `attempt_number` increments. |
| `reconnect_success` | Reconnected successfully. `downtime_ms` = total gap duration. |

### How the Replayer Should Handle Gaps

1. Parse `reconnects.jsonl` at startup.
2. Build a list of `(disconnect_ts, reconnect_success_ts)` windows.
3. During replay, if the current event falls inside a gap window:
   - **Option A:** Skip gap silently (fast-forward).
   - **Option B:** Announce gap to consumers: "gap from T1 to T2, X ms missing."
4. After a gap, depth order-book state is stale — consumers may need to reset their local book.

---

## 7. `stats.jsonl` — Throughput Baselines

```json
{
  "ts": 1739454310000000,
  "interval_sec": 10.0,
  "total_msgs": 3250,
  "msgs_per_stream": { "trade": 2000, "book": 1000, "mark": 100, "index": 100, "liquidation": 50 },
  "total_msgs_sec": 325.0,
  "per_stream_sec": { "trade": 200.0, "book": 100.0, "mark": 10.0, "index": 10.0, "liquidation": 5.0 }
}
```

**Replayer usage:** Use `total_msgs_sec` to calibrate the expected event arrival rate. If replaying at 10× speed, the replayer should target `total_msgs_sec × 10` events/second throughput.

---

## 8. Per-Stream `local_seq` — Ordering & Gap Detection

`local_seq` is scoped to `(source_stream, symbol)`. For example, `(trade, BTCUSDT)` has its own counter starting at 0.

### Properties

- **0-based:** First event for each `(stream, symbol)` has `local_seq = 0`.
- **Gapless:** `local_seq` increments by exactly 1 for each event.
- **Survives reconnects:** Counters do NOT reset on reconnect.
- **Arrival-ordered:** `local_seq` reflects the order events were received, not exchange time order.

### How the Replayer Should Use `local_seq`

```
For arrival-time replay:
  Sort by (recv_ts, local_seq) globally, or just read segment files in order.

For event-time replay:
  Sort by (exchange_ts, local_seq) — local_seq breaks ties for events
  with identical exchange_ts (common for mark/index which arrive in batches).

For integrity checks:
  Verify no gaps: for each (source_stream, symbol), local_seq should be 0, 1, 2, ...
```

---

## 9. Reading the Data — Quick Reference

### Decompress & Count

```bash
# Count total events across all segments
zcat segments/events_*.jsonl.gz | wc -l

# Count events by stream type
zcat segments/events_*.jsonl.gz | jq -r '.source_stream' | sort | uniq -c | sort -rn

# Count events by symbol
zcat segments/events_*.jsonl.gz | jq -r '.symbol' | sort | uniq -c | sort -rn
```

### Extract Specific Streams

```bash
# All BTCUSDT trades
zcat segments/events_*.jsonl.gz | jq -c 'select(.source_stream == "trade" and .symbol == "BTCUSDT")'

# All liquidation events
zcat segments/events_*.jsonl.gz | jq -c 'select(.source_stream == "liquidation")'
```

### Check `local_seq` Continuity

```bash
zcat segments/events_*.jsonl.gz \
  | jq -c 'select(.source_stream == "trade" and .symbol == "BTCUSDT") | .local_seq' \
  | awk 'NR>1 && $1 != prev+1 {print "GAP at seq " prev " -> " $1} {prev=$1}'
```

### Compute Feed Latency

```bash
zcat segments/events_*.jsonl.gz \
  | jq 'select(.source_stream == "trade") | (.recv_ts / 1000 - .exchange_ts)' \
  | head -100
# Values in milliseconds; typically 5-50ms for a well-placed host
```

---

## 10. Replayer Architecture Sketch

```
┌──────────────────────────────────┐
│        metadata.json             │  ◄── Load config: symbols, streams,
│   + reconnects.jsonl             │      compression, gap windows
└───────────────┬──────────────────┘
                │
                ▼
┌──────────────────────────────────┐
│     Segment File Reader          │
│  • Open segments in order        │
│  • Decompress (gzip) if needed   │
│  • Parse JSON per line           │
│  • Yield EventEnvelope structs   │
└───────────────┬──────────────────┘
                │
                ▼
┌──────────────────────────────────┐
│     Ordering / Merge             │
│  • Event-time: sort by           │
│    (exchange_ts, local_seq)      │
│  • Arrival-time: sort by         │
│    (recv_ts, local_seq)          │
│  • Gap-aware: skip/announce      │
│    reconnect windows             │
└───────────────┬──────────────────┘
                │
                ▼
┌──────────────────────────────────┐
│     Pacing / Rate Control        │
│  • 1× speed: sleep real deltas   │
│  • N× speed: divide delays by N  │
│  • Max-speed: no sleep           │
└───────────────┬──────────────────┘
                │
                ▼
┌──────────────────────────────────┐
│     Event Consumer               │
│  • Drools session (insert facts) │
│  • Analytics pipeline            │
│  • Order book reconstructor      │
│  • Metric collector              │
└──────────────────────────────────┘
```

---

## 11. Data Volume Estimates

Use these to size buffers and plan storage for the replayer:

| Stream | Per Symbol | 10 Symbols | Per Hour (est.) |
|---|---|---|---|
| `trade` | 50–500 msg/s | 500–5000 msg/s | 1.8M – 18M events |
| `book` | 10 msg/s (fixed) | 100 msg/s | 360K events |
| `mark` | 1 msg/s (fixed) | 10 msg/s | 36K events |
| `index` | 1 msg/s (fixed) | 10 msg/s | 36K events |
| `liquidation` | 0–50 msg/s | variable | 0 – 180K events |

**Typical average payload sizes:**

| Stream | Avg. Envelope Size (JSON) |
|---|---|
| `trade` | ~300 bytes |
| `book` | ~800 bytes (varies with book activity) |
| `mark` | ~350 bytes |
| `index` | ~250 bytes |
| `liquidation` | ~450 bytes |

**Hourly storage:** ~1 GB uncompressed, ~150 MB with gzip (default).
