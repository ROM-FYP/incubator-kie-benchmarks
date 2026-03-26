# Binance Causal Trace Logger

A verbose, structured trace logger for the Binance CEP module that captures every **fact lifecycle event** and **rule activation** as a JSON-Lines (`.jsonl`) file. Its purpose is rule-interaction graph construction and decision-tree dataset building — it is **not** a JMH benchmark.

> This logger is completely separate from [`MiningTraceLogger`](trace_logging.md) (CSV, agenda-only). Both can coexist without interference.

---

## Architecture

Three classes work together:

```
BinanceCausalTraceRunner  (standalone main — data collection entry-point)
    │
    ├── BinanceEventProvider      (loads dataset from classpath)
    ├── BinanceRulesProvider      (compiles DRL, creates KieSession)
    ├── EventReplayController     (pseudo-clock event replay)
    │
    └── BinanceCausalTraceListener  (dual listener — attached BEFORE any inserts)
            ├── agendaListener()       →  ACTIVATION_CREATED, ACTIVATION_FIRED
            └── runtimeListener()     →  FACT_INSERT, FACT_UPDATE, FACT_DELETE
                    │
                    └── BinanceFactRegistry  (fact identity + provenance tracking)
```

### `BinanceFactRegistry`
**Location:** `src/main/java/org/kie/benchmark/binance/util/BinanceFactRegistry.java`

Assigns a stable `F<N>` ID to every fact object using `System.identityHashCode` and records provenance (which rule last inserted/updated each fact, or `"EXTERNAL"` for facts inserted from outside the engine).

### `BinanceCausalTraceListener`
**Location:** `src/main/java/org/kie/benchmark/binance/util/BinanceCausalTraceListener.java`

Exposes two listener factory methods to register on any `KieSession`:

```java
BinanceCausalTraceListener listener = new BinanceCausalTraceListener("trace.jsonl");
kieSession.addEventListener(listener.agendaListener());
kieSession.addEventListener(listener.runtimeListener());
// ... replay events ...
listener.close();
```

### `BinanceCausalTraceRunner`
**Location:** `src/main/java/org/kie/benchmark/binance/runner/BinanceCausalTraceRunner.java`

Standalone `main()` that orchestrates the full data collection run. See [How to Run](#how-to-run) below.

---

## Output Format

The trace file is a **JSON-Lines** file (`.jsonl`) — one JSON object per line, strictly ordered by the `seq` field.

### Event Types

| `type` | When emitted | Unique fields |
|--------|-------------|---------------|
| `FACT_INSERT` | A fact is inserted into working memory | `fact_id`, `fact_type`, `producer`, `fact_data` |
| `FACT_UPDATE` | A fact is updated in working memory | same |
| `FACT_DELETE` | A fact is retracted from working memory | same |
| `ACTIVATION_CREATED` | A rule's LHS pattern has been matched | `rule`, `activation_id`, `supporting_facts[]` |
| `ACTIVATION_FIRED` | A rule's RHS has been executed | `rule`, `activation_id` |

### Common fields (all event types)

| Field | Type | Description |
|-------|------|-------------|
| `type` | `string` | Event type (one of the 5 above) |
| `seq` | `long` | Global sequence number — strictly increasing, guarantees ordering within same millisecond |
| `ts` | `long` | Wall-clock timestamp (`System.currentTimeMillis()`) |
| `tid` | `long` | Thread ID of the emitting thread |

### Example output

```jsonl
{"type":"FACT_INSERT","seq":1,"ts":1742892000000,"tid":1,"fact_id":"F1","fact_type":"RiskConfig","producer":"EXTERNAL","fact_data":{"maxVolatility":0.05,...}}
{"type":"FACT_INSERT","seq":2,"ts":1742892000001,"tid":1,"fact_id":"F2","fact_type":"ModeState","producer":"EXTERNAL","fact_data":{"symbol":"BTCUSDT",...}}
{"type":"FACT_INSERT","seq":3,"ts":1742892000010,"tid":1,"fact_id":"F3","fact_type":"MarketEvent","producer":"EXTERNAL","fact_data":{"symbol":"BTCUSDT","eventType":"TRADE",...}}
{"type":"ACTIVATION_CREATED","seq":4,"ts":1742892000011,"tid":1,"rule":"INGEST_ValidateTimestamp","activation_id":"a1b2c3...","supporting_facts":["F3"]}
{"type":"ACTIVATION_FIRED","seq":5,"ts":1742892000012,"tid":1,"rule":"INGEST_ValidateTimestamp","activation_id":"a1b2c3..."}
{"type":"FACT_INSERT","seq":6,"ts":1742892000013,"tid":1,"fact_id":"F4","fact_type":"RiskSignal","producer":"INGEST_ValidateTimestamp","fact_data":{...}}
```

### Causal edge derivation

From the trace you can reconstruct a directed rule-dependency edge `Ri → Rj`:

```
Ri → Rj  iff  some fact_id "Fx" appears in ACTIVATION_CREATED.supporting_facts of Rj
              AND that Fx has a FACT_INSERT (or FACT_UPDATE) with producer = Ri
```

---

## How to Run

### Step 1 — Build the uber-JAR

```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep
mvn clean package -DskipTests
```

### Step 2 — Run the trace collector

```bash
java -cp target/drools-benchmarks-binance-cep.jar \
     org.kie.benchmark.binance.runner.BinanceCausalTraceRunner
```

This uses the **default dataset** and writes output to `binance_causal_trace.jsonl` in the **current working directory** (wherever you run the command from).

#### With explicit arguments

```bash
java -cp target/drools-benchmarks-binance-cep.jar \
     org.kie.benchmark.binance.runner.BinanceCausalTraceRunner \
     [datasetId] [outputFile]
```

| Argument | Default | Description |
|----------|---------|-------------|
| `datasetId` | `run_20260311_1340_10sym` | Dataset directory name under `src/main/resources/data/` |
| `outputFile` | `binance_causal_trace.jsonl` | Path to the output trace file (will be **overwritten** if it exists) |

#### Example with custom output path

```bash
java -cp target/drools-benchmarks-binance-cep.jar \
     org.kie.benchmark.binance.runner.BinanceCausalTraceRunner \
     run_20260311_1340_10sym \
     /tmp/traces/binance_causal_2026.jsonl
```

### Step 3 — Verify the output

```bash
# Count total trace events
wc -l binance_causal_trace.jsonl

# Check all 5 event types are present
grep -o '"type":"[^"]*"' binance_causal_trace.jsonl | sort | uniq -c

# Inspect the first few events
head -5 binance_causal_trace.jsonl | python3 -m json.tool
```

### Console output during a run

```
[BinanceCausalTraceRunner] Loading dataset...
[BinanceCausalTraceRunner] Loaded 67,234 events from dataset: run_20260311_1340_10sym
[BinanceCausalTraceRunner] Initializing Drools engine...
[BinanceCausalTraceRunner] Causal trace listener attached → binance_causal_trace.jsonl
[BinanceCausalTraceRunner] Bootstrap facts inserted for 10 symbols.
[BinanceCausalTraceRunner] Ingesting 67,234 events → binance_causal_trace.jsonl
[BinanceCausalTraceRunner]   5,000 / 67,234 events ingested...
[BinanceCausalTraceRunner]   10,000 / 67,234 events ingested...
...
[BinanceCausalTraceRunner] Done.  Total rule firings: 182,451  |  Time: 12.3 s
[BinanceCausalTraceRunner] Trace written to: binance_causal_trace.jsonl
```

---

## Output File Location

The output file is written to the **current working directory** when the runner is launched. To control the location:

- **Pass a full path as the second argument** (recommended):
  ```bash
  java -cp target/drools-benchmarks-binance-cep.jar \
       org.kie.benchmark.binance.runner.BinanceCausalTraceRunner \
       run_20260311_1340_10sym \
       /home/maheshdila/traces/my_trace.jsonl
  ```

- **Or `cd` to your target folder before running:**
  ```bash
  mkdir -p /home/maheshdila/traces
  cd /home/maheshdila/traces
  java -cp /path/to/benchmarks.jar \
       org.kie.benchmark.binance.runner.BinanceCausalTraceRunner
  # → writes ./binance_causal_trace.jsonl here
  ```

> [!WARNING]
> The output file is opened in **overwrite mode** — if a file with the same name already exists it will be silently replaced. Always pass a unique filename if you want to keep multiple trace runs.

---

## Changing the Dataset

### Available datasets

Datasets live under:
```
src/main/resources/data/<datasetId>/segments/
```

List available datasets:
```bash
ls src/main/resources/data/
# e.g.: run_20260311_1340_10sym   run_20260216_0632_10sym
```

### Switch dataset via CLI argument

Pass the dataset directory name as the first argument:
```bash
java -cp target/drools-benchmarks-binance-cep.jar \
     org.kie.benchmark.binance.runner.BinanceCausalTraceRunner \
     run_20260216_0632_10sym \
     old_dataset_trace.jsonl
```

### Change the default dataset

To permanently change the default, edit `BinanceCausalTraceRunner.java`:
```java
// Line that creates the provider (when no CLI arg is passed)
BinanceEventProvider eventProvider = (datasetId != null)
        ? new BinanceEventProvider(datasetId)
        : new BinanceEventProvider();   // ← uses BinanceEventProvider's own default
```

The `BinanceEventProvider` default is controlled by the system property `binance.dataset`:
```bash
# Override without recompiling by passing -D at runtime:
java -Dbinance.dataset=run_20260216_0632_10sym \
     -cp target/drools-benchmarks-binance-cep.jar \
     org.kie.benchmark.binance.runner.BinanceCausalTraceRunner
```

Or change the compile-time default in `BinanceEventProvider.java`:
```java
private static final String DEFAULT_DATASET = System.getProperty("binance.dataset",
        "run_20260311_1340_10sym");   // ← change this string
```

### Adding a new dataset

1. Place segment files in `src/main/resources/data/<new-dataset-id>/segments/`
2. Rebuild: `mvn clean package -DskipTests`
3. Run with the new dataset ID as the first argument

---

## Changing the Rules

The runner uses `BinanceRulesProvider`, which defaults to `taxonomy.drl`. To use a different rules file:

```bash
java -Dbinance.rules.file=/rules/demo.drl \
     -cp target/drools-benchmarks-binance-cep.jar \
     org.kie.benchmark.binance.runner.BinanceCausalTraceRunner
```

Rules files must be on the classpath under `src/main/resources/rules/`.

---

## Building the Rule Interaction Graph

Once you have a `.jsonl` trace file, use `build_causal_graph.py` to construct a directed rule interaction graph.

### Basic usage

```bash
cd drools-benchmarks-parent/drools-benchmarks-binance-cep

python3 build_causal_graph.py binance_causal_trace.jsonl
# → binance_rule_graph.csv  (edge list for pandas / networkx / Gephi)
# → binance_rule_graph.net  (Pajek format for Infomap community detection)
```

### CLI options

| Argument | Default | Description |
|---|---|---|
| `log_file` | *(required)* | Path to JSONL trace file |
| `--min-weight N` | `1` | Drop edges with weight < N |
| `--keep-self-loops` | off | Retain self-loop edges (Ri → Ri) |
| `--normalize` | off | Normalize outgoing edge weights to [0,1] |
| `--exclude R1 R2 ...` | none | Exclude specific rules by name |
| `--out-csv PATH` | `binance_rule_graph.csv` | CSV output path |
| `--out-net PATH` | `binance_rule_graph.net` | Pajek .net output path |

### Examples

```bash
# Filter weak edges and exclude bootstrap rules
python3 build_causal_graph.py binance_causal_trace.jsonl \
  --min-weight 5 \
  --exclude BOOTSTRAP_FeedHealth BOOTSTRAP_DepthState

# Normalized weights for community detection
python3 build_causal_graph.py binance_causal_trace.jsonl \
  --normalize \
  --out-net rule_graph_normalized.net
```

### Algorithm

1. **Fact Provenance** — tracks which rule produced each fact (`FACT_INSERT`/`FACT_UPDATE`)
2. **Activation Extraction** — on `ACTIVATION_CREATED`, resolves the producer of every supporting fact
3. **Edge Aggregation** — accumulates `(Ri → Rj)` weights using activation-level counting (each producer counted at most once per activation)

---

## Comparison with `MiningTraceLogger`

| | `MiningTraceLogger` | `BinanceCausalTraceListener` |
|---|---|---|
| **Output format** | CSV (`CaseID, SeqNr, Activity, Timestamp`) | JSON-Lines (5 structured event types) |
| **Listeners** | 1 — agenda only | 2 — agenda + runtime |
| **Fact tracking** | None | Full identity (`fact_id`) + provenance |
| **Causal linkage** | No | Yes — `Ri → Rj` derivable from trace |
| **Fact payload** | No | Yes — full Jackson-serialized `fact_data` |
| **Entry point** | JMH benchmark (`-p enableTraceLog=true`) | Standalone `main()` runner |
| **Purpose** | Process mining (PM4Py, ProM) | Rule dependency graph / ML dataset building |
