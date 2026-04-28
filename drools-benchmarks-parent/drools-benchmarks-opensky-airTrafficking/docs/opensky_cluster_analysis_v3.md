# OpenSky ATC — Cluster Dependency Analysis (Post-Fix Ruleset)

This document performs a complete cluster analysis on the **corrected ruleset** (after applying 7 semantic gap fixes), using the new Infomap output (`rule_graph_new.net.ftree`) and the updated static dependency analysis.

Key changes from the previous analysis:
- **New rule R050** (`SkipIfVerticalSeparationSufficient`) now appears in C1 with high flow — vertical gate is working
- **R011** (`FilterBadVelocityFromPairing`) now captured in C9 — confirming GAP-1 kinematic→TrackQuality feedback is active
- **R088** has been removed (duplicate inhibit auditor)
- Total dependency edges: **1,093** (up from 1,011 in pre-fix ruleset)

---

## 1. Infomap Cluster Distribution (from `rule_graph_new.net.ftree`)

9 clusters, **64 rules** captured in the causal trace (62% of 103-rule DRL).

| Cluster | Flow % | Rules in Cluster | Domain |
|:---:|:---:|:---|:---|
| **C1** | 77.27% | R000, R042, R048, R050, R051, R052, R068b | **Core Pairing Pipeline** |
| **C2** | 15.11% | R006, R007, R008, R016, R019, R024, R025, R026, R029, R030, R036, R037, R038, R041, R053, R080, R098 | **Data Quality & Grid Auditing** |
| **C3** | 4.00% | R002, R010, R049 | **Stale Position Filtering** |
| **C4** | 1.85% | R056, R059, R061, R063, R064, R065, R067b, R068, R069, R070, R071, R072, R073, R074a, R074b, R074c, R099 | **Conflict Classification & Streak Tracking** |
| **C5** | 1.30% | R003, R013 | **Stale Contact Filtering** |
| **C6** | 0.39% | R058, R060, R062, R066, R067a, R079 | **Alert Escalation** |
| **C7** | 0.07% | R014, R075, R082, R090, R096 | **Safety Alert Lifecycle** |
| **C8** | 0.01% | R081, R091, R093, R097 | **Traffic Advisory Lifecycle** |
| **C9** | 0.008% | R011, R021, R055 | **Kinematics & Bad-Velocity Filter** |

> **Key Observation:** C1 now holds **77.27% of all flow** (up from 72.99%), meaning the new R050 vertical gate is highly active and tightly coupled to the core pairing loop. C9 now correctly includes R011 because the kinematic rules now produce `TrackQuality(badVel=true)` that feeds into quality filtering.

---

## 2. Assigning the 39 Non-Trace Rules

Rules not captured in the causal trace are assigned by matching LHS inputs and RHS outputs to the closest cluster.

| Rule | LHS Inputs | RHS Action | Assigned To | Rationale |
|:---|:---|:---|:---:|:---|
| **R001** MissingPositionFlag | `OpenSkyStateVector` | insert `TrackQuality` | **C3** | Same pattern as R002 (StalePositionFlag) in C3 |
| **R004** BadAltitudeFlag | `OpenSkyStateVector` | insert `TrackQuality` | **C3** | Same pattern as R002 |
| **R005** BadVelocityFlag | `OpenSkyStateVector` | insert `TrackQuality` | **C3** | Same pattern as R002 |
| **R009** FilterMissingPosition | `OpenSkyStateVector`, `TrackQuality` | insert `AuditEvent` | **C3** | Reads C3-produced `TrackQuality`; same pattern as R010 |
| **R012** FilterBadAltitude | `OpenSkyStateVector`, `TrackQuality` | insert `AuditEvent` | **C3** | Reads C3-produced `TrackQuality` |
| **R015** StaleContactClearsAlerts | `TrackQuality(staleAny)`, `Alert` | retract `Alert` | **C7** | Alert retract — safety alert lifecycle (C7) |
| **R017** NonAdsbSourceAudit | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit; same domain as C2 |
| **R018** TooFastForAltitude | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R020** TrackDegRangeAudit | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R022** SuddenSpeedJumpAudit | `KinematicDelta` | insert `AuditEvent` + `TrackQuality` | **C9** | Reads C9's `KinematicDelta`; now also produces `TrackQuality` (GAP-1 fix) |
| **R023** SuddenVerticalRateAudit | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R027** UnknownVelButMoving | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R028** UnknownTrackButMoving | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R031** SlowRotorcraftAudit | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R032** UAVAudit | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R033** GliderAudit | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R034** EmergencyVehicle | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R035** PointObstacle | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R039** OriginCountryNull | `OpenSkyStateVector` | insert `AuditEvent` | **C2** | Single-event audit |
| **R043** NoPairIfSameAircraft | `PairCandidate` | retract | **C1** | Operates on C1's `PairCandidate` |
| **R044** PairInhibitByCell | `PairCandidate`, `AlertInhibit` | retract | **C1** | Operates on C1's `PairCandidate` |
| **R045** PairInhibitByFlight | `PairCandidate`, `AlertInhibit` | retract | **C1** | Operates on C1's `PairCandidate` |
| **R046** PairInhibitByFlightB | `PairCandidate`, `AlertInhibit` | retract | **C1** | Operates on C1's `PairCandidate` |
| **R047** PairInhibitByPairKey | `PairCandidate`, `AlertInhibit` | retract | **C1** | Operates on C1's `PairCandidate` |
| **R049b** RetractConflictBadQuality | `ConflictCandidate`, `TrackQuality` | retract `ConflictCandidate` | **C1** | Modifies C1's `ConflictCandidate`; reads C3/C5/C9 `TrackQuality` |
| **R057** DowngradeVertSep | `ConflictCandidate`, `CpaMetrics` | modify `ConflictCandidate` | **C4** | Modifies C4-domain `ConflictCandidate` severity |
| **R076** HysteresisClear | `Alert`, `PairRiskState` | retract `Alert` | **C7** | Safety alert lifecycle |
| **R077** InhibitSafetyByPair | `Alert`, `AlertInhibit` | retract `Alert` | **C7** | Safety alert lifecycle |
| **R078** InhibitMustBeKnown | `AlertInhibit` | insert `AuditEvent` | **C2** | Generic audit |
| **R083** AlertPersists | `Params`, `Alert`, `AlertAck` | insert `AuditEvent` | **C7** | Alert lifecycle |
| **R084** AckSilences | `Alert`, `AlertAck` | modify `Alert` | **C7** | Alert lifecycle |
| **R085** ClearAfterAck | `Alert(ACK)`, `AlertAck` | retract `Alert` | **C7** | Alert lifecycle |
| **R086** InhibitByFlight | `Alert`, `AlertInhibit` | retract `Alert` | **C7** | Alert lifecycle |
| **R087** InhibitByPair | `Alert`, `AlertInhibit` | retract `Alert` | **C7** | Alert lifecycle |
| **R089** StatusUnavailable | `Status` | insert `AuditEvent` | **C2** | Generic audit |
| **R092** NoSpamNuisance | `Alert`, `Alert` | retract older `Alert` | **C7** | Alert dedup — safety lifecycle |
| **R094** ClearOldAdvisories | `Alert(TRAFFIC_ADVISORY)` | retract | **C8** | Traffic advisory lifecycle |
| **R095** ClearOldAcks | `AlertAck` | retract | **C7** | Ack cleanup |
| **R100** EndOfCycleMarker | (none) | no-op | **C2** | Catch-all placeholder |

---

## 3. Complete Cluster Composition After Assignment

| Cluster | Domain | Rule Count | Rules |
|:---:|:---|:---:|:---|
| **C1** | Core Pairing Pipeline | **12** | R000, R042, R043, R044, R045, R046, R047, R048, R049b, R050, R051, R052, R068b |
| **C2** | Data Quality & Grid Auditing | **30** | R006, R007, R008, R016, R017, R018, R019, R020, R023, R024, R025, R026, R027, R028, R029, R030, R031, R032, R033, R034, R035, R036, R037, R038, R039, R041, R053, R078, R089, R098, R100, R080 |
| **C3** | Stale Position Filtering | **7** | R001, R002, R004, R005, R009, R010, R012, R049 |
| **C4** | Conflict Classification & Streaks | **18** | R056, R057, R059, R061, R063, R064, R065, R067b, R068, R069, R070, R071, R072, R073, R074a, R074b, R074c, R099 |
| **C5** | Stale Contact Filtering | **2** | R003, R013 |
| **C6** | Alert Escalation | **6** | R058, R060, R062, R066, R067a, R079 |
| **C7** | Safety Alert Lifecycle | **14** | R014, R015, R075, R076, R077, R082, R083, R084, R085, R086, R087, R090, R092, R095, R096 |
| **C8** | Traffic Advisory Lifecycle | **5** | R081, R091, R093, R094, R097 |
| **C9** | Kinematics & Bad-Velocity Filter | **4** | R011, R021, R022, R055 |

**Total: 103 rules fully assigned.**

---

## 4. Cross-Cluster Dependency Analysis

### Fact-Type Flow Map

| Fact Type | Produced By | Consumed By (other clusters) |
|:---|:---|:---|
| `OpenSkyStateVector` | External | ALL clusters |
| `Params` | C1 (R000) | C3 (R002), C5 (R003), C4 (R056 etc.) |
| `GridCell` | C2 (R041) | C1 (R042, R050) |
| `TrackQuality` | C3 (R001,R002,R004,R005), C5 (R003), **C9 (R022,R055)** | C1 (R049b), C3 (R009,R010,R012,R049), C7 (R014,R015) |
| `PairCandidate` | C1 (R042) | C1 (R043–R049b, R050, R051, R052) |
| `KinematicDelta` | C9 (R021) | C9 (R022, R055) |
| `ConflictCandidate` | C1 (R068b→C4 R056,R069) | C4 (R057–R074c), C6 (R058–R079), C7 (R075,R082), C8 (R081) |
| `CpaMetrics` | C1 (R068b) | C4 (R069–R072), C2 (R080) |
| `PairRiskState` | C4 (R073) | C4 (R074a–c), C7 (R075,R082) |
| `Alert` | C6 (R060,R082), C7 (R075,R082), C8 (R081) | C7 (R076,R083–R087,R092,R095,R096), C8 (R091–R094,R097) |
| `AuditEvent` | C2, C3, C4, C6, C7, C8, C9 | Terminal — not consumed for decisions |

### Cluster Independence Summary

| Cluster | Independent? | Hard Dependencies | Via Fact Types |
|:---:|:---:|:---|:---|
| **C1** | 🟡 Soft | C2 (GridCell), C3/C5/C9 (TrackQuality) | `GridCell` (R042,R050), `TrackQuality` (R049b) |
| **C2** | 🟡 Soft | C1 (CpaMetrics via R080, ConflictCandidate via R066) | `CpaMetrics`, `ConflictCandidate` |
| **C3** | 🟡 Soft | C1 (Params via R002), C1 (PairCandidate via R049) | `Params` (static), `PairCandidate` (R049) |
| **C4** | 🔴 Hard | C1 | `ConflictCandidate`, `CpaMetrics` (all 18 rules) |
| **C5** | 🟡 Soft | C1 (Params) | `Params` (static singleton — effectively free) |
| **C6** | 🔴 Hard | C1, C4 | `ConflictCandidate` (all 6 rules), `PairRiskState` |
| **C7** | 🔴 Hard | C1, C4, C6 | `ConflictCandidate` (R075,R082), `PairRiskState` (C4), `Alert` (C6/C8) |
| **C8** | 🔴 Hard | C1, C4, C7 | `ConflictCandidate` (R081), `Alert` from C7 |
| **C9** | 🟡 Soft | None | `KinematicDelta` is self-contained |

> **New finding vs previous analysis:** C9 is no longer fully independent — it produces `TrackQuality` facts (via fixed R022/R055) that feed into C1 (R049b) and C3 (quality filters). However only the outputs flow outward; C9 itself reads no facts produced by other clusters.

---

## 5. Dependency Resolution Strategies

### Which clusters are self-sufficient?
- ✅ **C9** (4 rules): Self-sufficient — reads only `OpenSkyStateVector` and `KinematicDelta` (self-produced). Its `TrackQuality` outputs flow to C1/C3, but C9 doesn't need anything back.
- ✅ **C5** (2 rules): Practically independent — reads `OpenSkyStateVector` + `Params` (a static singleton inserted once).
- 🟡 **C3** (8 rules): Can be made independent by moving R049 (`SkipIfAnyTrackQualityBad`) to C1 — R049 reads `PairCandidate` which is a C1 fact. After this move, C3 only reads `OpenSkyStateVector` + `Params`.

### Core Production Chain required by dependent clusters
Any cluster reading `ConflictCandidate` (C4, C6, C7, C8) needs this entire chain duplicated:

| Stage | Rules | Count |
|:---|:---|:---:|
| Params | R000 | 1 |
| GridCell | R041 | 1 |
| TrackQuality flags | R001–R005 | 5 |
| PairCandidate lifecycle | R042–R052, R050 | 12 |
| CpaMetrics | R068b | 1 |
| ConflictCandidate production + classify | R056, R057–R072 | 17 |
| PairRiskState | R073 | 1 |
| **Total Core Chain** | | **38 rules** |

### Strategy A: Rule Duplication

| Cluster | Original | After Duplication | % of DRL |
|:---:|:---:|:---:|:---:|
| **C4** | 18 | 18 + 38 = **56** | **54.4%** |
| **C6** | 6 | 6 + 38 = **44** | **42.7%** |
| **C7** | 14 | 14 + 38 + 4 (Alert chain) = **56** | **54.4%** |
| **C8** | 5 | 5 + 38 + 4 (Alert chain) = **47** | **45.6%** |

**Duplication math:**
```
Sum of dependent clusters:  56+44+56+47 = 203 total rule slots (for 4 clusters sharing 103 unique rules)
Expansion factor:           203 / 103 = 1.97×
Average cluster size:       50 rules = 48.5% of entire DRL
```

### Strategy B: Cluster Merging (Dependency-Driven)

Tracing the merge cascade:
1. **C4** depends on **C1** → merge **C1+C4** (30 rules)
2. **C6** depends on **C1** and **C4** → merge **(C1+C4)+C6** (36 rules)
3. **C7** depends on **C1**, **C4**, **C6** → merge **(C1+C4+C6)+C7** (50 rules)
4. **C8** depends on **C1**, **C4**, **C7** → merge **(C1+C4+C6+C7)+C8** (55 rules)
5. **C2** depends on **C1** (CpaMetrics via R080, ConflictCandidate via R066) → merge **(C1+C4+C6+C7+C8)+C2** (83 rules)

**Result after all merges:**

| Segment | Rules | Flow % |
|:---|:---:|:---:|
| **Core Monolith** (C1+C2+C4+C6+C7+C8) | **≥ 83 rules** | ~96.6% |
| **C3** (after bridge move) | 7 rules | ~4.0% |
| **C5** (independent) | 2 rules | ~1.3% |
| **C9** (independent) | 4 rules | ~0.008% |

The three lightweight clusters (C3, C5, C9) together handle only **~5.3% of all information flow**. The monolith handles the remaining **~96.6%**.

---

## 6. Verdict

> [!IMPORTANT]
> **The corrected ruleset still cannot be effectively parallelized via cluster decomposition.**

The gap fixes improved semantic correctness, but did not change the fundamental topology. C1 now claims **77.27% of flow** (up from 72.99%), because the new R050 vertical gate is a structural operation tightly coupled to the PairCandidate chain.

### Comparison with Previous Analysis

| Metric | Pre-Fix Analysis | Post-Fix Analysis | Change |
|:---|:---:|:---:|:---|
| Total dependency edges | 1,011 | 1,093 | +8.1% (more accurate graph) |
| C1 flow share | 72.99% | 77.27% | +4.3% |
| Rules after merge | ≥79 | ≥83 | More rules captured in monolith |
| Independent clusters | C7, C9 | C5, C9 | C9 now produces TrackQuality |
| Duplication factor | 2.33× | 1.97× | Slightly better (C4 absorbed more) |
| Achievable parallelism | None | **None** | Unchanged |

The best achievable split remains: one **83-rule monolith** vs three trivial peripheral clusters (7+2+4 = 13 rules) handling an inconsequential fraction of the workload.
