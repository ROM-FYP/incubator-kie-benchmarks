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

| Metric | Baseline | Routed | Difference |
|---|---|---|---|
| **Total Alerts** | 197 | 369 | +172 |
| **Performance (s/op)** | 0.195 ± 0.131 | 0.470 ± 0.345 | ~2.4x slower |

---

## Analysis

### Core Fact Injection
Initially, the routed architecture produced 0 alerts because intermediate derived facts (like `ConflictCandidate`) were trapped inside isolated cluster `KieSession`s and could not trigger downstream rules in other clusters. To fix this, a backbone of 15 core rules (Grid, Pair, CPA, Conflict, State, Alert) was injected into **every** cluster's DRL. 

### Over-Generation of Alerts (197 vs 369)
The decision tree Router (`tree_rules.txt`) performs **multi-label routing**, meaning a single flight vector can be dispatched to 2 or 3 clusters simultaneously. Because each cluster now contains its own independent inference backbone, multiple isolated sessions will independently track the same aircraft, compute the same conflict, and raise their own localized `Alert`. This results in the same physical airspace conflict generating duplicate alerts across the parallel sessions.

### Latency Degradation
The routed mode is now performing roughly **2.4x slower** than the monolithic baseline (0.470 s/op vs 0.195 s/op). 
- **In Baseline:** The system evaluates the math to build a `ConflictCandidate` exactly once.
- **In Routed:** If an event is routed to 3 clusters, the system dynamically calculates the `ConflictCandidate` geometry and `CpaMetrics` 3 separate times across 3 isolated REST networks. This redundant computation across the multi-label sessions heavily degrades the benchmark throughput.

### Takeaways for Drools Clustering
While static clustering via Infomap generates logically cohesive groupings, using isolated `KieSession` instances for stateful, forward-chaining rules is problematic for multi-label facts. A more performant integration for the decision tree would be executing within a single `KieSession` and using the Router to dynamically push/pop `agenda-groups` so facts remain shared.

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
