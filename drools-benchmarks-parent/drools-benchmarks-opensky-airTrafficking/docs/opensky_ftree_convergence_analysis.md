# OpenSky ATC: Infomap Clusters & Monolithic Convergence Analysis

This document details the step-by-step breakdown of the OpenSky Rule network, beginning with the Infomap causal trace clusters, assigning the un-triggered rules, establishing the cross-cluster dependencies, and proving mathematically how attempting to resolve these dependencies fundamentally collapses the architecture into a single 89-rule Monolith.

## 1. The ftree Cluster Distribution (Infomap Output)

The Infomap analysis produced **9 communities/clusters** from the runtime causal trace logs:

| Cluster | Flow % | Trace Rules | Domain |
|:---:|:---:|:---|:---|
| **C1** | 37.6% | R000, R041, R042, R048, R051, R056, R059, R061, R063, R064, R067b, R068, R068b, R069, R070, R072, R073 | **Core Conflict Pipeline** |
| **C2** | 36.3% | R074a, R074b, R074c, R076 | **Streak Tracking & Hysteresis** |
| **C3** | 17.0% | R006, R007, R008, R016, R019, R024, R025, R026, R029, R030, R036, R037, R038, R052, R053, R058, R060, R062, R066, R067a, R079, R080, R098 | **Auditing & Escalation** |
| **C4** | 6.2% | R065, R071, R099 | **CPA Warn Path** |
| **C5** | 1.4% | R002, R010, R049 | **Stale Position Filtering** |
| **C6** | 1.0% | R014, R015, R075, R082, R090, R096 | **Safety Alert Lifecycle** |
| **C7** | 0.3% | R003, R013 | **Stale Contact Filtering** |
| **C8** | 0.3% | R081, R091, R093, R097 | **Traffic Advisory Lifecycle** |
| **C9** | ~0% | R021, R055 | **Kinematics Delta** |

---

## 2. Assignment of Non-Trace Rules

39 rules did not fire during the causal trace run. Each was assigned to the exact cluster whose fact dependencies it matched by analyzing its Left-Hand Side (LHS) inputs and Right-Hand Side (RHS) outputs:

| Rule | LHS Facts Read | RHS Action | Assigned Cluster |
|:---|:---|:---|:---:|
| **R001** MissingPositionFlag | `OpenSkyStateVector` | insert `TrackQuality` | **C5** |
| **R004** BadAltitudeFlag | `OpenSkyStateVector` | insert `TrackQuality` | **C5** |
| **R005** BadVelocityFlag | `OpenSkyStateVector` | insert `TrackQuality` | **C5** |
| **R009** FilterMissingPos | `OpenSkyStateVector`, `TrackQuality` | insert `AuditEvent` | **C5** |
| **R011** FilterBadVel | `OpenSkyStateVector`, `TrackQuality` | insert `AuditEvent` | **C5** |
| **R012** FilterBadAlt | `OpenSkyStateVector`, `TrackQuality` | insert `AuditEvent` | **C5** |
| **R017, R018, R020, R023, R027, R028, R031, R032, R033, R034, R035, R039** | `OpenSkyStateVector` | insert `AuditEvent` | **C3** (Generic Audits) |
| **R022** SuddenSpeedJump | `KinematicDelta` | insert `AuditEvent` | **C9** (Reads C9 outputs) |
| **R043 - R047, R049b** | `PairCandidate`, `ConflictCandidate` | retract | **C1** (Operates on C1 outputs) |
| **R057** DowngradeVertSep | `Params`, `CpaMetrics` | modify `ConflictCandidate` | **C1** |
| **R077, R083 - R087, R092, R095** | `Alert`, `AlertAck`, `AlertInhibit` | retract/modify `Alert` | **C6** (Alert Lifecycle) |
| **R078, R088, R089** | `AlertInhibit`, `Status` | insert `AuditEvent` | **C3** |
| **R094** ClearOldAdvisories | `Alert(TRAFFIC_ADVISORY)` | retract | **C8** |
| **R100** EndOfCycleMarker | (none) | no-op | **C3** |

### Complete Cluster Composition (After Assignment)
| Cluster | Final Rule Count |
|:---:|:---:|
| **C1** (Core Conflict Pipeline) | **24** |
| **C2** (Streak Tracking) | **4** |
| **C3** (Auditing & Escalation) | **39** |
| **C4** (CPA Warn Path) | **3** |
| **C5** (Stale Position Filtering) | **9** |
| **C6** (Safety Alert Lifecycle) | **14** |
| **C7** (Stale Contact Filtering) | **2** |
| **C8** (Traffic Advisory) | **5** |
| **C9** (Kinematics Delta) | **3** |

**Total:** 103 rules thoroughly accounted for across 9 native clusters.

---

## 3. Dependency Analysis Matrix

To parallelize these clusters cleanly, facts produced by one cluster must perfectly satisfy the entire pipeline within that cluster. However, checking cross-cluster consumption proved they are highly entangled:

*   **C1 (Core Conflict Pipeline) — 24 rules**
    *   **Produces**: `Params`, `GridCell`, `PairCandidate`, `ConflictCandidate`, `CpaMetrics`, `PairRiskState`
    *   🟡 **Soft Dependency**: Rule `R049b` requires `TrackQuality` facts produced externally by **C5** & **C7**.

*   **C2 (Streak Tracking) — 4 rules**
    *   **Reads**: `ConflictCandidate` and `PairRiskState` which are 🔴 **Produced by C1**.

*   **C3 (Auditing & Escalation) — 39 rules**
    *   **Reads**: `ConflictCandidate`, `PairCandidate`, `GridCell`, `CpaMetrics` which are all 🔴 **Produced by C1**.

*   **C4 (CPA Warn Path) — 3 rules**
    *   **Reads**: `ConflictCandidate` and `CpaMetrics` which are 🔴 **Produced by C1**.

*   **C6 (Safety Alert Lifecycle) — 14 rules**
    *   **Reads**: `ConflictCandidate` 🔴 **Produced by C1**.
    *   **Reads**: `PairRiskState` 🔴 **Produced by C1, modified by C2**.
    *   **Reads**: `TrackQuality` 🔴 **Produced by C5 and C7**.

*   **C8 (Traffic Advisory Lifecycle) — 5 rules**
    *   **Reads**: `ConflictCandidate` 🔴 **Produced by C1**. 

*   ✅ **C5, C7, and C9** are effectively strictly autonomous anomaly-checking modules, requiring no inputs spawned by other clusters.

**Summary:** `C1` creates the foundational pairwise data structures. `C2`, `C3`, `C4`, `C6`, `C8` are structurally dependent on `C1` completing its entire processing path before they can begin.

---

## 4. Resolving Dependencies: Rule Duplication Strategy

To make the dependent clusters standalone, we must mathematically satisfy their inputs by duplicating the fact-producing rules into their isolated sessions.

### Defining the Core Production Chain
Any cluster that requires `ConflictCandidate` to operate (C2, C3, C4, C6, C8) requires the entire continuous pipeline that creates it mathematically duplicated into its memory:
1. `Params` initialization (1)
2. `TrackQuality` filters (5)
3. `GridCell` initialization (1)
4. `PairCandidate` lifecycle (10)
5. `ConflictCandidate` generation (3)
6. Severity classification & deduplication (16)
7. `PairRiskState` creation (1)

This adds up to **37 rules** per dependent cluster solely to produce `ConflictCandidate`. Furthermore, clusters that require `Alert` lifecycle outputs (C6 and C8) require an additional **6 rules**, totaling **43 rules**.

### Duplication Cost Ratio
When we calculate the cost to resolve dependencies by injecting these chains across the dependent domains:
*   **C1**: Self-sufficient at 29 rules (absorbs TrackQuality).
*   **C2**: Balloons from 4 to 44 rules.
*   **C3**: Balloons from 39 to 62 rules.
*   **C4**: Balloons from 3 to 38 rules.
*   **C6**: Balloons from 14 to 52 rules.
*   **C8**: Balloons from 5 to 45 rules.

**The Duplication Math:**
```text
Total original unique rules:  103
Sum of dependent clusters:    270 (sum of all necessary duplicated pipelines)
Average rules per cluster:    45  (43.7% of the original DRL footprint!)
Duplication overhead:         2.62x Expansion Factor
```

Rather than splitting workloads to minimize operational surface, duplicating cross-cluster dependencies causes every parallel node to literally execute **nearly half the total engine** independently. This is extremely antithetical to scaling.

---

## 5. Resolving Dependencies: Converging into the Monolith (Merge Strategy)

If duplicating the rules yields an unworkable `2.62x` overhead load, the only mathematically viable alternate method to maintain topological data constraints is to eliminate cross-cluster boundaries by recursively merging dependent clusters into massive conglomerates.

Mapping the merge strategy directly exposes the convergence:
1. **C2** depends on **C1** → Merge C1 + C2
2. **C3** depends on **C1** → Merge (C1+C2) + C3
3. **C4** depends on **C1** → Merge (C1+C2+C3) + C4
4. **C6** depends on **C1** and **C2** → Merge (C1+C2+C3+C4) + C6
5. **C8** depends on **C1** → Merge (C1+C2+C3+C4+C6) + C8

### The Final Convergence
Eliminating all soft and hard dependencies via targeted cluster collapsing produces the following structural topology:
*   **The Monolithic Core Cluster (C1+C2+C3+C4+C6+C8):** 89 Rules
*   **C5** (Stale Position): 8 Rules
*   **C7** (Stale Contact): 2 Rules
*   **C9** (Kinematics): 3 Rules

**Verdict:** 
The topological constraints and rigorous spatial interdependencies naturally force **89 rules (86.4% of the graph)** to inevitably collapse back into a strictly singular Monolithic session to function properly. 

Attempting to run the engine in a clustered orientation therefore essentially means running one massive 89-rule global system alongside three completely trivial disjoint modules. Because the core pipeline handles pairwise joins involving every StateVector globally, parallel execution partitions fundamentally and physically fail.
