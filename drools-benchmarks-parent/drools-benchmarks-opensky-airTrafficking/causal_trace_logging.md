# Causal Trace Logging Implementation

This document details the architecture and mechanisms behind the Causal Trace Logging system injected into the OpenSky AirTraffic Drools Benchmark. The objective forms a deterministic, line-by-line JSON record capturing fact lifecycles and rule activations. 

From this granular telemetry, we can formally extract a **Rule Interaction Causalty Graph** (edges dictating Rule A inherently triggered Rule B) and populate dense Machine Learning feature matrices mapping object data attributes to functional clusters.

## 1. Core Architecture

The tracing system is built natively into the Drools lifecycle by hooking deeply into internal KIE session event dispatches without utilizing any external "heavy" tracking frameworks (beyond Jackson for simple POJO serialization).

### Key Java Components
- `CausalTraceListener.java`: The heavy-lifter. This class serves as a dual implementations extending both `DefaultAgendaEventListener` (for Activation hooks) and `DefaultRuleRuntimeEventListener` (for Fact lifecycle hooks).
- `FactRegistry.java`: A memory-efficient mapper that associates physical object references with stable surrogate strings (e.g., `F12345` derived from `System.identityHashCode()`) to maintain identity consistency as objects mutate through various rules. It actively tracks the **provenance** (the current originating `producer` rule) of every fact.
- `CausalTraceRunner.java`: A standalone execution harness. JMH Benchmarking (via `OpenSkyReplayDroolsBenchmark.java`) executes iteratively in isolated windows. To generate an unbroken, holistic trace of the entire 37,000+ state vector dataset, this runner bypasses JMH to ingest the data in a continuous, single-pass pipeline.

---

## 2. Event Types & Payloads

The listener outputs to a streaming `causal_trace_full.jsonl` file. Every event captures global context (`seq` sequence counter, `ts` timestamp, `tid` thread ID).

### Fact Lifecycle Events (`FACT_INSERT`, `FACT_UPDATE`, `FACT_DELETE`)
Triggered whenever a rule's Right-Hand Side (RHS) modifies the working memory.
- **`fact_id`**: The stable surrogate ID.
- **`fact_type`**: The class name (e.g., `OpenSkyStateVector`).
- **`producer`**: The name of the rule that generated/modified the fact (Or `"EXTERNAL"` if injected by the Java harness).
- **`fact_data`**: The raw, serialized JSON values of the fact's attributes.
  - **How it is obtained**: Inside the `objectInserted` and `objectUpdated` hooks, Drools provides the underlying physical Java object (POJO) currently residing in Working Memory. The listener passes this object to a Jackson `ObjectMapper`, which dynamically serializes the class instance into a flat JSON dictionary (`MAPPER.writeValueAsString(fact)`). 
  - **What is logged**: The resulting payload contains every public field and getter exposed by that specific fact's Java class definitions. For example, if the fact is an `OpenSkyStateVector`, the `fact_data` will explicitly contain literal values for variables like `altitude`, `velocity`, `onGround`, `callsign`, and `spi`. If the fact is an `Alert`, the payload will contain `type`, `severity`, and `timestamp`. This deep serialization is strictly required for downstream Dataset Builders to extract accurate functional Machine Learning features natively from the telemetry stream.

### Rule Activation Events
1. **`ACTIVATION_CREATED`**: 
   - Fired the moment the Left-Hand Side (LHS) conditions are satisfied and the rule enters the agenda.
   - **`rule`**: Name of the triggered rule.
   - **`activation_id`**: A unique UUID mapping the Drools internal `Match` reference.
   - **`supporting_facts`**: An array of `fact_ids` representing the exact instances that matched the LHS conditions and triggered this activation.
2. **`ACTIVATION_FIRED`**:
   - Fired when the rule's RHS block actually executes.

---

## 3. Reconstructing the Causal Directed Graph

The true power of this implementation is tracing deterministic causality (`Ri → Rj`). The pipeline explicitly links rules through the objects they mutate.

**The Derivation Logic:**
1. Rule `Ri` executes and updates Fact `F`. The trace emits `FACT_UPDATE` marking `Ri` as the `producer` of `F`.
2. Rule `Rj` evaluates Fact `F` and its LHS is structurally satisfied.
3. The trace emits `ACTIVATION_CREATED` for `Rj`, specifically listing `F` within its `supporting_facts` array.
4. An external parser queries the `producer` dictionary for `F`, discovering `Ri`. 
5. A directed, weighted causal edge is explicitly verified: Rule `Ri` caused Rule `Rj` to fire.

Ultimately, this streams output natively compatible with topological inference scripts (such as exporting to Infomap `.ftree` matrices) and robust multi-label classification dataset generation.
