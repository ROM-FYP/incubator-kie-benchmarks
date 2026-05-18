# Drools CEP RIPE RIS Benchmark

A Drools Complex Event Processing (CEP) benchmark for replaying and analysing **RIPE RIS Live BGP events** with a 79-rule RFC/RIS-driven rule base.

This benchmark is part of the `incubator-kie-benchmarks` workspace and is located under:

```text
incubator-kie-benchmarks/
└── drools-benchmarks-parent/
    └── drools-benchmarks-ripe-ris-cep/
```

## Current benchmark status

The current implementation uses:

- **Input domain:** RIPE RIS Live BGP event stream / recorded JSONL stream
- **Main event fact:** `RisMessage`
- **Rule file:** `rules/ripe_rfc4271_benchmark_79_rules.drl`
- **Rule count:** 79 rules
- **Rule authority:** RFC 4271 + RIPE RIS Live message semantics
- **Cisco-specific rules:** not included
- **Artificial depth facts:** removed; no `ChainStage` or `BenchmarkMetric` should be used
- **Forward-chain depth measurement:** handled by benchmark/characterization tooling, not by fields inside DRL facts

The currently characterised dataset contains:

```text
Total events:              42,119
Dataset time span:         13,120 ms
Dataset arrival rate:      ~3,210 events/sec
Distinct peers:            1,026
Distinct event types:      5
Event type distribution:   UPDATE=98.8%, KEEPALIVE=0.9%, STATE=0.3%, OPEN=0.0%, NOTIFICATION=0.0%
Mean per-peer IAT:         127.98 ms
Median per-peer IAT:       0.00 ms
Per-peer IAT std dev:      412.54 ms
Coefficient of variation:  3.223, bursty
```

The current static rule-base characterization reports:

```text
Total rules:                  79
Average conditions/rule:      2.25
Condition distribution:       1 condition=3, 2 conditions=59, 3 conditions=15, 4+ conditions=2
Distinct input fact types:    22
Dependency graph density:     0.1827
Largest connected component:  100.0%
Connected components:         1
Static max chaining depth:    5
Negation patterns:            84
Eval patterns:                33
```

> Note: static max chaining depth is currently 5. The target design goal is 6-9, so the DRL still needs topology tuning if this target is required for a final benchmark run.

## Why RIPE RIS?

RIPE RIS Live is suitable for a CEP benchmark because it provides:

1. Real BGP routing observations from global collectors.
2. High-volume and bursty routing events.
3. Public stream access without authentication.
4. JSON message structure suitable for parsing into Java POJOs.
5. Realistic BGP attributes such as AS paths, announcements, withdrawals, communities, origin, MED, aggregator, peer, peer ASN, and collector host.

This benchmark uses RIPE RIS as an observable routing-event workload. It does **not** simulate router-local Cisco state such as `WEIGHT`, configured local preference, IGP metric to next hop, or oldest-path router state.

## Rule-base scope

The rule base is intentionally limited to **RFC 4271 and RIPE RIS observable data**.

It covers a balanced mix of:

- RIS envelope and BGP message classification
- UPDATE-message normalization
- AS path extraction and validation
- origin classification
- MED and aggregator extraction
- community extraction
- announcement and withdrawal extraction
- prefix classification
- next-hop extraction and validation
- route-candidate creation
- observable route scoring
- conceptual RIB event derivation
- anomaly generation
- aggregation hints
- route-interaction facts for dependency analysis

The rule base should not contain:

- Cisco `WEIGHT`
- Cisco default-local-preference behaviour as a router-local decision rule
- Cisco oldest-eBGP-path tie-breaking
- Cisco multipath installation rules
- artificial `depth` fields
- `ChainStage` facts
- `BenchmarkMetric` facts for max-depth recording

## Architecture

```text
Recorded RIPE RIS JSONL / RIPE RIS Live Stream
        ↓
RipeRisBaselineBenchmark.loadEvents(...)
        ↓
RisMessage
        ↓
Drools KieSession, STREAM mode, pseudo-clock
        ↓
NormalizedRisMessage
        ↓
BgpUpdateFact
        ↓
RouteAnnouncement / RouteWithdrawal
        ↓
BgpPath / OriginFact / MedFact / CommunityFact / AggregatorFact
        ↓
PrefixFact / NextHopFact / RouteValidationResult
        ↓
RouteCandidate
        ↓
RouteScore / RouteDecision
        ↓
RibEvent / RouteAnomaly / AggregationHint
```

The intended chain depth should come from real rule dependencies, where a fact inserted by one rule becomes a required input of a later rule.

## Important source layout

```text
src/main/java/org/kie/benchmark/cep/riperis/model/
    Main model POJOs such as RisMessage, NormalizedRisMessage, BgpUpdateFact,
    RouteAnnouncement, RouteCandidate, RouteScore, RouteDecision, RibEvent, etc.

src/main/java/org/kie/benchmark/cep/riperis/runner/
    Baseline runner and causal trace runner.

src/main/java/org/kie/benchmark/cep/riperis/jmh/
    JMH benchmark classes.

src/main/java/org/kie/benchmark/cep/riperis/parallel/
    Cluster-splitting and parallel orchestration utilities.

src/main/java/org/kie/benchmark/cep/riperis/analysis/
    Static DRL parsing and dependency graph analysis utilities.

src/main/java/org/kie/benchmark/cep/riperis/util/
    Event source, stream recorder, config, session factory, causal trace listener,
    and fact registry utilities.

src/test/java/org/kie/benchmark/cep/riperis/
    CharacterizationCollector.java

src/main/resources/rules/
    ripe_rfc4271_benchmark_79_rules.drl
```

## Model classes

### `RisMessage`

The primary incoming CEP event. It represents one RIPE RIS message after JSON parsing.

Important fields:

- `envelopeType`
- `id`
- `timestamp`
- `peer`
- `peerAsn`
- `host`
- `bgpType`
- `path`
- `community`
- `origin`
- `med`
- `aggregator`
- `announcements`
- `withdrawals`

`RisMessage` is the main event class and should remain annotated for CEP use:

```java
@Role(Role.Type.EVENT)
@Timestamp("timestamp")
@Expires("30m")
public class RisMessage implements Serializable {
    ...
}
```

### Derived model facts

The current rule base uses derived facts such as:

```text
NormalizedRisMessage
BgpUpdateFact
PeerSessionFact
CollectorFact
TimeBucketFact
BgpPath
OriginFact
MedFact
AggregatorFact
CommunityFact
RouteAnnouncement
RouteWithdrawal
PrefixFact
NextHopFact
RouteValidationResult
RouteCandidate
RouteScore
RouteDecision
RibEvent
RouteAnomaly
AggregationHint
```

These POJOs should not contain artificial `depth` fields. Runtime causal depth should be measured by the listener/benchmark infrastructure.

## Building

From the benchmark module:

```powershell
cd D:\Projects\Benchmarks\incubator-kie-benchmarks\drools-benchmarks-parent\drools-benchmarks-ripe-ris-cep
mvn clean package -DskipTests
```

If Windows locks the generated JAR, stop Java processes and clean again:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item -Recurse -Force .\target
mvn clean package -DskipTests
```

## Running the baseline runner

Use this for controlled smoke tests because it accepts a `maxEvents` argument:

```powershell
mvn exec:java `
  -Dexec.mainClass=org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark `
  -Dexec.args="data/riperis_stream_20260512_004525.jsonl 1000" `
  --no-transfer-progress
```

Scale up gradually:

```text
1000 → 5000 → 10000 → 20000 → 42119
```

## Running JMH baseline

After packaging:

```powershell
java -cp target/drools-benchmarks-ripe-ris-cep.jar org.openjdk.jmh.Main RipeRisBaselineJmhBenchmark -p dataFile=data/riperis_stream_20260512_004525.jsonl
```

The JMH baseline currently uses a single Drools session and pseudo-clock replay.

## Running the characterization collector

`CharacterizationCollector.java` is under `src/test`, so use test classpath:

```powershell
mvn test-compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.CharacterizationCollector" `
  "-Dexec.classpathScope=test" `
  --no-transfer-progress
```

This collector reports:

- static rule-base properties
- dependency graph properties
- dataset/domain properties
- runtime replay metrics

## Running the causal trace runner

Generate a causal trace JSONL file for downstream rule-interaction graph analysis:

```powershell
mvn exec:java `
  -Dexec.mainClass=org.kie.benchmark.cep.riperis.runner.RipeRisCausalTraceRunner `
  -Dexec.args="data/riperis_stream_20260512_004525.jsonl riperis_causal_trace.jsonl 5000" `
  --no-transfer-progress
```

The causal trace listener emits events such as:

```jsonl
{"type":"FACT_INSERT","fact_id":"F1","producer":"EXTERNAL"}
{"type":"ACTIVATION_CREATED","rule":"001_normalize_ris_message","activation_id":"...","supporting_facts":["F1"]}
{"type":"FACT_INSERT","fact_id":"F2","producer":"001_normalize_ris_message"}
```

The intended dependency interpretation is:

```text
Rule A inserts/updates Fact X
Fact X supports activation of Rule B
Therefore: Rule A → Rule B
```

## Recording live RIPE RIS data

Use the stream recorder to save SSE data to JSONL:

```powershell
mvn exec:java `
  -Dexec.mainClass=org.kie.benchmark.cep.riperis.util.RipeRisStreamRecorder `
  -Dexec.args="5" `
  --no-transfer-progress
```

This records roughly 5 minutes by default if no duration is supplied.

## Configuration

Default rule path:

```text
rules/ripe_rfc4271_benchmark_79_rules.drl
```

Useful environment variables:

```text
RIPERIS_RULES_FILE
RIPERIS_DEFAULT_DATA_FILE
RIPERIS_STREAM_URL
RIPERIS_RECORDED_TRACE_DIR
```

Example:

```powershell
$env:RIPERIS_DEFAULT_DATA_FILE="data/riperis_stream_50K.jsonl"
$env:RIPERIS_RULES_FILE="rules/ripe_rfc4271_benchmark_79_rules.drl"
```

## Known current issues and tuning notes

### 1. Static chain depth is currently below target

The current static dependency graph reports max chaining depth `5`. If the benchmark target is `6-9`, the DRL needs one or more additional real domain dependency layers, for example:

```text
RouteValidationResult → FeasibleRouteFact → RouteCandidate
```

Do not add synthetic `ChainStage` facts to inflate the number. The target should be reached through real causal rule dependencies.

### 2. Negation count is high

The current rule base reports `84` `not` patterns. This is high and can cause memory pressure in Drools beta/not-node memories. Prefer removing duplicate-prevention `not` guards from simple one-fact anomaly rules.

Keep `not` mainly for rules where duplicate creation is genuinely risky, such as selected `RouteDecision`, `AggregationHint`, or `RibEvent` rules.

### 3. Memory pressure during full replay

The full dataset has 42,119 events in roughly 13 seconds. This is dense. If derived facts do not expire, working memory can grow rapidly.

Recommended DRL tuning:

```drl
declare NormalizedRisMessage
    @role(event)
    @expires(15s)
end
```

Apply similar short but safe expiry to derived facts such as:

```text
BgpUpdateFact
BgpPath
RouteAnnouncement
RouteWithdrawal
PrefixFact
NextHopFact
RouteValidationResult
RouteCandidate
RouteScore
RouteDecision
RibEvent
RouteAnomaly
AggregationHint
```

Avoid extremely short expiry such as `10ms`, because that can break deeper forward chaining.

### 4. JMH heap

If the full JMH replay runs out of heap, increase the fork heap in the JMH class, for example:

```java
@Fork(value = 1, jvmArgs = { "-Xms8g", "-Xmx8g" })
```

This should be done together with working-memory reduction; increasing heap alone is not the proper fix.

### 5. DRL package warning

Drools may warn:

```text
File 'rules/ripe_rfc4271_benchmark_79_rules.drl' is in folder 'rules'
but declares package 'org.kie.benchmark.cep.riperis.rules'
```

This is not fatal. To remove the warning, either:

1. move the DRL under a matching resource path, or
2. keep the current path and ignore the warning.

## Troubleshooting

### Maven clean cannot delete target JAR on Windows

Cause: the JAR is locked by a running Java/JMH process.

Fix:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item -Recurse -Force .\target
mvn clean package -DskipTests
```

### `NoClassDefFoundError` when running `CharacterizationCollector`

Use `test-compile` and `exec.classpathScope=test`:

```powershell
mvn test-compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.CharacterizationCollector" `
  "-Dexec.classpathScope=test" `
  --no-transfer-progress
```

### DRL compile errors around MVEL comparisons

If MVEL rejects property comparisons, bind first and compare inside `eval`:

```drl
$p : PrefixFact($len : prefixLength)
eval($len > 24)
```

### OutOfMemoryError during JMH or runtime characterization

Apply these in order:

1. reduce unnecessary `not` patterns,
2. add safe expiry to derived facts,
3. limit pairwise joins with timestamp proximity,
4. smoke-test with smaller event counts,
5. increase JMH heap.

## Requirements

- Java 11 or higher
- Maven 3.8+
- Internet access only for live stream recording/ingestion
- For full local replay: 4GB heap minimum; 8GB+ recommended while tuning the 79-rule workload

## License

Licensed under the Apache License, Version 2.0. See the repository license for details.
