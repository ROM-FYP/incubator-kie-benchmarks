# Benchmark Results: Baseline vs Routed Execution

Comparison of the single-session baseline vs the decision-tree-routed multi-session architecture.

## Environment

| Property | Value |
|---|---|
| JDK | 17.0.12 (HotSpot 64-Bit Server VM) |
| JMH | 1.37 |
| OS | Windows |
| Warmup | 10 iterations |
| Measurement | 5 iterations |
| Forks | 1 |
| Benchmark mode | Average Time (avgt) |
| Window size | 200 events/op |
| Dataset | 37,748 OpenSky state vectors |

---

## Results Summary

| Mode | Avg (s/op) | Error (99.9% CI) | Min | Max | Stdev |
|---|---|---|---|---|---|
| **Baseline** | 0.272 | ± 0.247 | 0.205 | 0.371 | 0.064 |
| **Routed** | 0.222 | ± 0.089 | 0.198 | 0.260 | 0.023 |

---

## Analysis

### Latency
The routed mode achieved **0.222 s/op** average compared to the baseline's **0.272 s/op**, representing an approximate **18.4% reduction** in average processing time per 200-event window.

### Stability
The routed mode exhibited significantly lower variance:
- **Baseline stdev**: 0.064 s
- **Routed stdev**: 0.023 s (2.8× tighter)

The 99.9% confidence interval for routed mode (±0.089) is substantially narrower than baseline (±0.247), indicating more predictable execution timing.

### Interpretation
The routed architecture dispatches each incoming `OpenSkyStateVector` only to the cluster sessions predicted by the decision tree. This means:

1. **Fewer redundant pattern matches**: Each cluster session contains only 2–23 rules (vs 103 in baseline), resulting in a smaller RETE network per session.
2. **Reduced cross-rule interference**: Rules in unrelated clusters never see facts they wouldn't match, eliminating wasted partial-match overhead.
3. **More consistent timing**: Since the router pre-filters which sessions receive facts, the per-iteration workload variance is lower.

### Cluster Session Breakdown

| Cluster | Rules | Example Rules |
|---|---|---|
| cluster_1 | 17 | R056_BuildConflictCandidateBasic, R042_PairWithinCell |
| cluster_2 | 4 | R074a/b/c_UpdateStreaks, R076_Hysteresis |
| cluster_3 | 23 | R098_PerformanceCounters, R079_MultiConflictHotspot |
| cluster_4 | 3 | R071_UpgradeToWARN, R065_ConflictCandidateAudit |
| cluster_5 | 3 | R049_SkipIfAnyTrackQualityBad, R010_FilterStale |
| cluster_6 | 6 | R090_SafetyAlertPriority, R075_RaiseAlerts |
| cluster_7 | 2 | R013_FilterStaleContact, R003_StaleContactFlag |
| cluster_8 | 4 | R081_RaiseTrafficAdvisory, R093_Escalate |
| cluster_9 | 2 | R055_HighAccelerationAudit, R021_DeltaVelocity |

---

## Commands Used

```bash
# Baseline
java -jar target/benchmarks.jar OpenSkyReplayDroolsBenchmark \
  -f 1 -wi 10 -i 5 -p mode=baseline -bm avgt

# Routed
java -jar target/benchmarks.jar OpenSkyReplayDroolsBenchmark \
  -f 1 -wi 10 -i 5 -p mode=routed -bm avgt
```
