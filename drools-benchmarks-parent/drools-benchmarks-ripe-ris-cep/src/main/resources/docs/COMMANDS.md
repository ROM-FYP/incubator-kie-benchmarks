# RIPE RIS CEP Benchmark — PowerShell Command Reference

> All commands assume Windows PowerShell (5.1 or 7+).
> The backtick `` ` `` is the PowerShell line-continuation character.

---

## Prerequisites

| Requirement | Minimum version |
|---|---|
| Java | 11 |
| Maven | 3.8 |
| Heap (smoke test) | 4 GB |
| Heap (1.6M replay) | 8 GB recommended |

---

## Directory navigation

### 1. Go to the parent module from the repo root

```powershell
cd drools-benchmarks-parent
```

### 2. Go to the RIPE RIS module from the parent module

```powershell
cd drools-benchmarks-ripe-ris-cep
```

All subsequent commands must be run from inside
`drools-benchmarks-ripe-ris-cep`.

---

## Build

### 3. Clean build (skip tests)

Compiles all sources, packages the shaded JAR, and places it under `target/`.

```powershell
mvn clean package -DskipTests --no-transfer-progress
```

If Maven cannot delete the target JAR because a previous Java process is still
running:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
Remove-Item -Recurse -Force .\target
mvn clean package -DskipTests --no-transfer-progress
```

---

## `RipeRisCepBenchmark` — live stream runner

> **Important:** `RipeRisCepBenchmark.main()` connects to the **live RIPE RIS
> SSE stream**. It requires an active internet connection. Use
> `RipeRisBaselineBenchmark` or the JMH benchmark for fully offline, file-based
> replay.

### 4. Run with default configuration (live stream, 1-minute duration)

Configuration is read from `CepBenchmarkConfig.getDefault()`, which picks up
`RIPERIS_STREAM_URL` and `RIPERIS_RULES_FILE` from the environment / `.env`
file.

```powershell
mvn exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.RipeRisCepBenchmark" `
  --no-transfer-progress
```

---

## `RipeRisBaselineBenchmark` — manual offline replay

`RipeRisBaselineBenchmark` executes the CEP workload offline using a pre-recorded dataset. It does not use the live SSE stream.

### 5. Run baseline benchmark — default data file (50 K events)

```powershell
mvn exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark" `
  --no-transfer-progress
```

### 6. Run baseline benchmark — 1.6 M event file

```powershell
mvn exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark" `
  "-Dexec.args=RIPERIS_DATA_FILE_1_6M" `
  --no-transfer-progress
```

---

## `RipeRisBaselineJmhBenchmark` — JMH controlled replay

The JMH benchmark provides statistically rigorous throughput measurements with
configurable warmup, measurement iterations, and a dedicated forked JVM. It
always reads from a local JSONL file via pseudo-clock replay.

**Requires the shaded JAR to be built first (step 3).**

### 7. JMH baseline — default data file (50 K events)

```powershell
java -cp target\drools-benchmarks-ripe-ris-cep.jar `
  org.openjdk.jmh.Main RipeRisBaselineJmhBenchmark `
  -p "dataFile=RIPERIS_DEFAULT_DATA_FILE"
```

### 8. JMH baseline — 1.6 M event file

```powershell
java -cp target\drools-benchmarks-ripe-ris-cep.jar `
  org.openjdk.jmh.Main RipeRisBaselineJmhBenchmark `
  -p "dataFile=RIPERIS_DATA_FILE_1_6M"
```

#### Optional JMH flags

| Flag | Purpose | Example |
|---|---|---|
| `-wi <n>` | Override warmup iterations | `-wi 5` |
| `-i <n>` | Override measurement iterations | `-i 10` |
| `-f <n>` | Number of JVM forks | `-f 1` |
| `-rff result.json` | Write JSON result file | `-rff jmh-result.json` |

Example with custom JVM heap and result file:

```powershell
java -Xms8g -Xmx8g `
  -cp target\drools-benchmarks-ripe-ris-cep.jar `
  org.openjdk.jmh.Main RipeRisBaselineJmhBenchmark `
  -p "dataFile=RIPERIS_DATA_FILE_1_6M" `
  -wi 2 -i 5 -f 1 `
  -rff jmh-result.json
```

---

## `CharacterizationCollector` — research-grade analysis

`CharacterizationCollector` is a one-shot analysis tool that computes all
dimensions from the Benchmark Characterization Guide:

| Dimension group | Contents |
|---|---|
| A — Static rule-base | Rule count, avg conditions, input/output fact types, negation/eval counts |
| B — Dependency graph | Graph density, connected components, max chaining depth |
| C — Runtime metrics | Rules fired, WM peak, throughput, causal depth distribution |
| D — Data / domain | Event count, time span, peer diversity, IAT statistics, type distribution |

`CharacterizationCollector` lives under `src/test`, so it must be compiled with
the test classpath (`-Dexec.classpathScope=test`).

### 9. Characterization collector — default data file (50 K events)

```powershell
mvn test-compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.CharacterizationCollector" `
  "-Dexec.classpathScope=test" `
  --no-transfer-progress
```

### 10. Characterization collector — 1.6 M event file

```powershell
mvn test-compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.CharacterizationCollector" `
  "-Dexec.classpathScope=test" `
  "-Dexec.args=RIPERIS_DATA_FILE_1_6M" `
  --no-transfer-progress
```

> The 1.6 M replay may take several minutes. Memory usage can reach 6–8 GB for
> the full causal-chain workload. Increase Maven's heap if the process is killed:
>
> ```powershell
> $env:MAVEN_OPTS = "-Xms4g -Xmx10g"
> ```

---

## Available data files

| Variable (`.env`) | File | Size | Events |
|---|---|---|---|
| `RIPERIS_DATA_FILE_50K` | `data/riperis_stream_50K.jsonl` | ~32 MB | 50,000 |
| `RIPERIS_DATA_FILE_1_6M` | `data/riperis_stream_1,6M.jsonl` | ~1,030 MB | 1,600,000 |

The default for all runners is the **50 K file** unless overridden via
`RIPERIS_DEFAULT_DATA_FILE` or a command-line argument.

---

## Overriding configuration via environment variables

Set these in your shell before running any `mvn` or `java` command:

```powershell
# Switch default data file globally
$env:RIPERIS_DEFAULT_DATA_FILE = "data/riperis_stream_1,6M.jsonl"

# Override rules file
$env:RIPERIS_RULES_FILE = "rules/ripe_rfc4271_benchmark_79_rules.drl"

# Override stream URL (for live recording)
$env:RIPERIS_STREAM_URL = "https://ris-live.ripe.net/v1/stream/?format=sse"
```

---

## Recording a new live stream snapshot

Saves raw SSE events to a timestamped JSONL file in `data/`. Duration is in
**seconds**.

```powershell
# Record for 1 minute 15 seconds (75 seconds)
mvn compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.util.RipeRisStreamRecorder" `
  "-Dexec.args=75" `
  --no-transfer-progress
```

```powershell
# Record for 5 minutes (300 seconds, the default)
mvn compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.util.RipeRisStreamRecorder" `
  --no-transfer-progress
```

---

## Quick-start sequence

Run these in order to go from a clean repo to a full characterization report:

```powershell
# 1. Enter the module
cd D:\Projects\Benchmarks\incubator-kie-benchmarks\drools-benchmarks-parent\drools-benchmarks-ripe-ris-cep

# 2. Build
mvn clean package -DskipTests --no-transfer-progress

# 3. Smoke test with the baseline runner (1000 events)
mvn exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark" `
  "-Dexec.args=RIPERIS_DEFAULT_DATA_FILE 1000" `
  --no-transfer-progress

# 4. Run the characterization collector (full 50K dataset)
mvn test-compile exec:java `
  "-Dexec.mainClass=org.kie.benchmark.cep.riperis.CharacterizationCollector" `
  "-Dexec.classpathScope=test" `
  --no-transfer-progress

# 5. Run JMH baseline (full 50K dataset, default warmup/measurement)
java -cp target\drools-benchmarks-ripe-ris-cep.jar `
  org.openjdk.jmh.Main RipeRisBaselineJmhBenchmark `
  -p "dataFile=RIPERIS_DEFAULT_DATA_FILE"
```
