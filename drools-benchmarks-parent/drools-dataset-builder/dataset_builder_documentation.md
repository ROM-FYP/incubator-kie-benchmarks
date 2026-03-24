# Drools Dataset Builder Documentation

This document explains the end-to-end architecture and implementation details of the generic `dataset_builder.py` script. The intent of this tool is to map Drools execution logic (Facts, Activations, and Provenance) into a structured Multi-Label Classification dataset using causal graphs and Infomap clustering.

## 1. Core Objective
The fundamental goal of the Pipeline is to bridge three disparate data sources:
1. **Rule Logic (`.drl`):** Defining what variables exist.
2. **Execution Traces (`.jsonl`):** Defining what happened dynamically at runtime.
3. **Network Clustering (`.ftree`):** Defining how rules relate structurally based on causal inference algorithms (like Infomap).

The generated output is a `.csv` dataset where:
- **Rows:** Represent individual, unique `FACT_INSERT` telemetry events.
- **Features (X):** The attributes of that fact.
- **Targets (Y):** Binary flags (`1` or `0`) for the clusters triggered by that fact.

---

## 2. A-Z Implementation Breakdown

### Step A: The Java Logger Upgrade (Prerequisite)
By default, the `CausalTraceListener.java` logged fact provenance (`producer`), `fact_type`, and `fact_id`, but it did not serialize the actual object variables (like altitude, velocity, etc.). 
To supply the python script with actual machine-learning features, we injected a fast JSON serializer into the listener engine using Jackson (`ObjectMapper`):
```java
// CausalTraceListener.java (FACT_INSERT Hook)
String factData = MAPPER.writeValueAsString(fact);
sb.append(",\"fact_data\":").append(factData);
```
This ensured that the generated `causal_trace_full.jsonl` became structurally dense and self-contained.

### Step B: Feature Schema Extraction (`parse_drl()`)
To ensure our dataset outputs robust feature columns—even if an attribute is intermittently missing from the telemetry—the builder establishes a strict schema directly from the source code.
It parses the `.drl` language using Regular Expressions to identify evaluation bindings (`$var : field`) and conditional checks (`field >= value`).
By scanning constraints within parentheses (e.g. `OpenSkyStateVector( onGround == false, $a: altitude )`), the script establishes that `onGround` and `altitude` are guaranteed feature columns for that Fact Type.

### Step C: Module Path Resolution (`parse_ftree()`)
The script processes Infomap `.ftree` inputs structurally. Infomap designates community structure in the format `cluster:node PageRank "Rule_Name"`.
The builder strips out these elements to build a simple deterministic `HashMap` routing `Rule_Name -> Cluster_ID`.

### Step D: Execution Ingestion (`parse_traces()`)
Streaming operations allow the script to evaluate millions of executions with a very low memory footprint.
1. **FACT events:** Captures the `fact_id` and the deeply serialized `fact_data` payload.
2. **ACTIVATION events:** Extracts the `supporting_fact_ids`.

### Step E: Causal Graph Mapping (`build_fact_rule_mapping()`)
Here, the script ties everything together. 
For every rule activation, it retrieves the `supporting_fact_ids` that caused it to fire. It then looks up which Infomap structural `Cluster_ID` that rule belongs to. It continuously aggregates this into a multi-label Map associating a single origin `fact_id` with multiple downstream trigger clusters.

### Step F: CSV Construction & Noise Filtration (`build_dataset()`)
The final phase flattens the multidimensional map into CSV rows:
1. Filters out *noise* (Facts that entered the system but never triggered any rules, resulting in an empty set of clusters).
2. Uses the `Schema` from Step B to write strict column headers (e.g., `attr_altitude`).
3. Writes out the features.
4. Appends a multi-hot binary label vector across all discovered clusters (e.g., `cluster_1: 1`, `cluster_2: 0`).
5. Implements an optional filter (`--fact-type`) to restrict output generation solely to a relevant context class (like `OpenSkyStateVector`).

---

## 3. Example Execution

```bash
python dataset_builder.py \
  --trace ../drools-benchmarks-opensky-airTrafficking/src/main/resources/causal_trace_full.jsonl \
  --drl ../drools-benchmarks-opensky-airTrafficking/src/main/resources/airTraffick_rules.drl \
  --clusters ../drools-benchmarks-opensky-airTrafficking/src/main/resources/rule_graph_full.ftree \
  --fact-type OpenSkyStateVector \
  --out-csv dataset_output.csv
```
