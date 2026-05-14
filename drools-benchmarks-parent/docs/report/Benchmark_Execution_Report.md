# Benchmark Execution Report

This report contains the execution results of the JMH Benchmarks, Heap Profilers, and Characterization Collectors across all three domains (Binance, Wikimedia, OpenSky).

> **Note:** JMH benchmarks and Heap Profilers were run with reduced iterations (`-wi 0 -i 1 -f 0 -t 1` and `--trials 1`) to verify functionality without modifying source code.
> **Note 2:** Per the strict instruction *'no source code changes, no matter what'*, compilation failures encountered during execution are reported exactly as-is without being fixed.

## Binance Domain

### 1. JMH Benchmark (mode=baseline)
```text
BinanceFullDatasetBenchmark.benchmarkFullReplay  baseline  thrpt       0.003          ops/s
```

### 2. Heap Profiler
```text
Heap Profile execution complete. Raw output available in logs.
```

### 3. Characterization Collector
```text
║         CHARACTERIZATION TABLE (Paper-ready)         ║
╚══════════════════════════════════════════════════════╝
│ Property                             │ Binance CEP     │
├──────────────────────────────────────┼─────────────────┤
│ A1  Rules (R)                        │ 108             │
│ A2  Avg conditions/rule              │ 2.02            │
│ A2  Max conditions/rule              │ 4               │
│ A6  Distinct input fact types        │ 22              │
│ A5  Alpha sharing ratio (proxy)      │ 0.899           │
│ B1  Dep. graph density               │ 0.0755          │
│ B2  Largest connected component      │ 100.0         % │
│ B3  Connected components             │ 1               │
│ B4  Max chaining depth               │ 4               │
│ C1  Event arrival rate (ev/s)        │ 0               │
│ C2  IAT coeff of variation (CV)      │ 401.639         │
│ C2  Velocity class                   │ BURSTY          │
│ C3  Selectivity                      │ 98.3          % │
│ C4  Peak WM size (facts)             │ 172             │
│ C4  Avg WM size (facts)              │ 139.8           │
│ C5  Avg WM changes/event             │ 8.45            │
│ C6  Avg conflict set size            │ 4.05            │
│ C6  Peak conflict set size           │ 150             │
│ C7  Rules fired per event            │ 12.96           │
│ C3  Rule coverage on dataset         │ 60.2          % │
│ C8  window:time rules                │ 5               │
│ C8  window:length rules              │ 4               │
│ C9  Temporal CEP patterns            │ 35              │
│ D1  Dataset size (events)            │ 1,613,159       │
│ D2  Distinct symbols                 │ 10              │
│ D2  Distinct event types             │ 4               │
│ D4  Data provenance                  │ Binance WebSocket │
└──────────────────────────────────────┴─────────────────┘
```

---

## Wikimedia Domain

### 1. JMH Benchmark (mode=baseline)
```text
FAILED: java.lang.Error: Unresolved compilation problem: 
	WikimediaBaselineBenchmark cannot be resolved
```

### 2. Heap Profiler
```text
FAILED EXECUTION:
Unresolved compilation problem: 
[ERROR] 	WikimediaBaselineBenchmark cannot be resolved
[ERROR]
```

### 3. Characterization Collector
```text
FAILED EXECUTION:
Unrecognized field "$schema" (class org.kie.benchmark.cep.wikimedia.model.WikiEvent), not marked as ignorable (6 known properties: "timestamp", "bot", "title", "user", "comment", "sizeDelta"])
[ERROR]  at [Source: (String)"{"$schema":"/mediawiki/recentchange/1.0.0","meta":{"uri":"https://www.wikidata.org/wiki/Q22130860","request_id":"e195f11b-6b65-4b18-a877-74d677338776","id":"7ae62d55-f7c3-43bb-b1a8-8c6dc1690e5f","domain":"www.wikidata.org","stream":"mediawiki.recentchange","dt":"2026-04-30T08:29:00.579Z","topic":"eqiad.mediawiki.recentchange","partition":0,"offset":6080675169},"id":2563094823,"type":"edit","namespace":0,"title":"Q22130860","title_url":"https://www.wikidata.org/wiki/Q22130860","comment":"/* wbset"[truncated 1953 chars]; line: 1, column: 13] (through reference chain: org.kie.benchmark.cep.wikimedia.model.WikiEvent["$schema"])
```

---

## Opensky Domain

### 1. JMH Benchmark (mode=baseline)
```text
FAILED: java.lang.Error: Unresolved compilation problems: 
	MultithreadEvaluationOption cannot be resolved to a variable
```

### 2. Heap Profiler
```text
FAILED EXECUTION:
Unresolved compilation problems: 
[ERROR] 	MultithreadEvaluationOption cannot be resolved to a variable
[ERROR] 	MultithreadEvaluationOption cannot be resolved to a variable
[ERROR]
```

### 3. Characterization Collector
```text
FAILED EXECUTION:
Unresolved compilation problems: 
[ERROR] 	MultithreadEvaluationOption cannot be resolved to a variable
[ERROR] 	MultithreadEvaluationOption cannot be resolved to a variable
[ERROR]
```

---

