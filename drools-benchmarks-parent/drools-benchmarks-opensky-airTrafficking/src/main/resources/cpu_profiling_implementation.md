# CPU Cost Profiling Implementation

This document describes the implementation of per-rule CPU cost profiling in the OpenSky Air Traffic benchmark.

## Overview
The profiling system is designed to measure the computational weight of individual Drools rules, specifically focusing on the Right-Hand Side (RHS) or 'action' block. This helps identify "heavy" rules that may be bottlenecks in the inference cycle.

## Implementation Details

### 1. Accuracy via ThreadMXBean
Instead of simple wall-clock time (`System.nanoTime`), we use `java.lang.management.ThreadMXBean.getCurrentThreadCpuTime()`.
- **Reason**: This measures only the time the CPU spent executing code for the specific thread, excluding time spent waiting for OS scheduling or context switches.
- **Precision**: Provides nanosecond precision for actual CPU utilization.

### 2. AgendaEventListener Integration
The profiling is implemented in `ProfilingRuleListener.java`, which extends `DefaultAgendaEventListener`.
- **`beforeMatchFired`**: Captures the initial CPU time of the thread.
- **`afterMatchFired`**: Captures the final CPU time and calculates the delta.
- **Aggregation**: Results are stored in a map, tracking total firings and cumulative CPU time per rule name.

### 3. JMH Benchmark Toggle
The profiling is integrated into `OpenSkyReplayDroolsBenchmark.java` as a JMH `@Param`:
- **`profilingEnabled=true`**: Activates the listener.
- **`profilingEnabled=false`**: Zero overhead mode for official throughput measurements.

## Usage
To generate a CPU profiling report, run the benchmark with the profiling parameter:
```powershell
java -jar target/benchmarks.jar OpenSkyReplayDroolsBenchmark -f 1 -wi 1 -i 1 -p profilingEnabled=true
```

## Performance Note
Enabling profiling introduces significant overhead (approx. 50-60%) due to frequent system calls for thread CPU time. Use this mode only for analysis, not for baseline throughput reporting.
