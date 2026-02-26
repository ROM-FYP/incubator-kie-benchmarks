# OpenSky Air-Trafficking Benchmark — Rule Documentation

This document explains the Drools rules defined in `airTraffick_rules.drl`, which implement a simplified Short-Term Conflict Alert (STCA) system over live OpenSky Network ADS-B data. It covers the fact model (object types), the purpose of each rule group, and a rule-by-rule description of R000–R055.

---

## 1. Object Types (Fact Model)

### `OpenSkyStateVector`
**Role:** The primary input fact. One instance is inserted per aircraft per snapshot.

Represents the ADS-B state broadcast received from a single aircraft at a point in time. Key fields:

| Field | Type | Meaning |
|-------|------|---------|
| `icao24` | `String` | Permanent 24-bit hex transponder ID (aircraft "VIN") |
| `callsign` | `String` | 8-char flight identifier set by crew per flight |
| `originCountry` | `String` | Country of aircraft registration |
| `lat`, `lon` | `Double` | WGS-84 position in degrees |
| `baroAltitudeM` | `Double` | Barometric altitude in metres |
| `geoAltitudeM` | `Double` | GPS geometric altitude in metres |
| `velocityMps` | `Double` | Ground speed in metres per second |
| `trueTrackDeg` | `Double` | Heading in degrees clockwise from true North (0–360) |
| `verticalRateMps` | `Double` | Climb (+) / descent (−) rate in m/s |
| `onGround` | `Boolean` | Transponder ground-bit flag |
| `lastContact` | `Long` | Unix epoch (seconds) of the most recent ADS-B reception |
| `timePosition` | `Long` | Unix epoch (seconds) of the most recent position fix |
| `squawk` | `String` | 4-digit octal transponder code |
| `spi` | `Boolean` | Special Position Identification pulse (pilot-activated) |
| `positionSource` | `Integer` | 0=ADS-B, 1=ASTERIX, 2=MLAT, 3=FLARM |
| `category` | `Integer` | ADS-B emitter category (e.g. 8=rotorcraft, 14=UAV) |
| `snapshotTime` | `long` | Epoch of the data snapshot that produced this fact |

Annotated `@Role(EVENT) @Expires("10s")` — treated as a time-windowed CEP event that expires 10 seconds after insertion when the pseudo clock advances.

---

### `Params`
**Role:** Singleton configuration fact.

Inserted once by R000 at session start. Holds all tunable thresholds used by other rules:

| Field | Default | Meaning |
|-------|---------|---------|
| `gridDeg` | 0.25 | Grid cell size in degrees |
| `stalePosSec` | 20 | Seconds before a position fix is considered stale |
| `staleAnySec` | 30 | Seconds before any contact is considered stale |
| `nearDistNm` | 10.0 | Pairing range in nautical miles |
| `warnDistNm` | 5.0 | Horizontal distance threshold for WARN severity |
| `alertDistNm` | 3.0 | Horizontal distance threshold for ALERT severity |
| `vertMinFt` | 1000.0 | Vertical separation minimum in feet |
| `warnTtcSec` | 90.0 | Time-to-collision threshold for WARN |
| `alertTtcSec` | 45.0 | Time-to-collision threshold for ALERT |
| `persistSec` | 15.0 | Seconds a conflict must persist before raising an Alert |

---

### `TrackQuality`
**Role:** Per-aircraft data quality flag fact. One instance per quality issue detected.

Inserted by rules R001–R005 when bad or stale data is detected for an aircraft. Used downstream by R049 to gate aircraft out of conflict pairing.

| Field | Meaning |
|-------|---------|
| `icao24` | Aircraft this flag applies to |
| `stalePos` | Position fix older than `stalePosSec` |
| `staleAny` | Any contact older than `staleAnySec` |
| `missingPos` | `lat` or `lon` is null |
| `badAlt` | Altitude is physically implausible (< −200m) |
| `badVel` | Velocity is negative |
| `reason` | Human-readable description |

---

### `AuditEvent`
**Role:** Structured log entry inserted into working memory for observability.

Does not influence rule execution directly — it's a record of what happened and why. Consumed externally (e.g. benchmark output analysis).

| Field | Meaning |
|-------|---------|
| `kind` | Category: `DATA_QUALITY`, `DATA_ANOMALY`, `KINEMATICS`, `FILTER`, `SURVEILLANCE`, `OPS_PATTERN`, `PAIR_READY`, `TRACK_QUALITY` |
| `subject` | icao24 or pair key (e.g. `"3c4b12\|4acb45"`) |
| `atSec` | Epoch second of the event |
| `detail` | Human-readable description |

Annotated `@Role(EVENT) @Expires("30s")`.

---

### `KinematicDelta`
**Role:** Computed rate-of-change between two consecutive state vectors for the same aircraft.

Inserted by R021. Consumed by R022 and R055.

| Field | Meaning |
|-------|---------|
| `icao24` | Aircraft |
| `dVel` | Velocity change (m/s) between two snapshots |
| `dAlt` | Altitude change (m) — currently unused |
| `dTrack` | Track angle change (°) — currently unused |
| `dt` | Time delta (seconds) between the two snapshots |

Annotated `@Role(EVENT) @Expires("10s")`.

---

### `GridCell`
**Role:** Spatial index entry — maps one aircraft to one grid cell.

Inserted by R041 for each aircraft with a valid position. Used by R042 to pair aircraft in the same cell.

| Field | Meaning |
|-------|---------|
| `cellId` | String key `"gx_gy"` (e.g. `"205_34"`) |
| `icao24` | Aircraft assigned to this cell |

Annotated `@Role(EVENT) @Expires("10s")`.

---

### `PairCandidate`
**Role:** Represents an ordered pair of aircraft that share a grid cell and should be evaluated for conflict.

Inserted by R042. Progressively filtered by R043–R051. Surviving instances reach R056 for conflict geometry computation.

| Field | Meaning |
|-------|---------|
| `a` | First icao24 (lexicographically smaller) |
| `b` | Second icao24 |
| `cellId` | Grid cell where they co-located |

---

### `AlertInhibit`
**Role:** Operator-configured suppression entry.

Prevents conflict alerts from being raised for specific aircraft, pairs, or airspace zones. Matched by R044–R047.

| Field | Meaning |
|-------|---------|
| `kind` | `"AIRSPACE"`, `"FLIGHT"`, or `"FLIGHTPAIR"` |
| `key` | Cell ID, icao24, or `"a\|b"` pair key |
| `enabled` | Whether the inhibit is active |

---

### `ConflictCandidate`
**Role:** Represents a pair that has been assessed geometrically and found to be potentially in conflict.

Produced by R056 (and severity-upgraded by R057/R058). Contains horizontal distance, time-to-collision, severity level, and vertical separation.

---

### `PairRiskState`
**Role:** Persistence tracker for a conflict pair.

One instance per pair. Stores how long the pair has been in conflict. Used by rules R073–R080 to apply hysteresis — alerts are only raised after a conflict persists for `persistSec` seconds, preventing false alerts from momentary data noise.

---

### `Alert`
**Role:** A confirmed, persistent conflict alert raised to the operator.

Produced by R082+ after `PairRiskState` confirms persistence. Carries severity, timestamps, and acknowledgement state.

---

## 2. Rule Groups Overview

| Rules | Group | Purpose |
|-------|-------|---------|
| R000 | Initialisation | Insert singleton `Params` fact |
| R001–R005 | Data Quality Flags | Insert `TrackQuality` facts for bad/stale data |
| R006–R020 | Data Quality Audits | Insert `AuditEvent` for anomalies (audit-only, not gating) |
| R021–R039 | Kinematics & Anomaly | Cross-snapshot deltas, kinematic anomaly detection |
| R041–R055 | Grid / Pair Candidates | Spatial indexing and conflict candidate generation |
| R056–R080 | Conflict Detection | Geometry computation, severity classification |
| R081–R095 | Alert Lifecycle | Persistence, escalation, inhibition, acknowledgement |
| R096–R100 | Performance / Audit | Benchmarking hooks and audit recording |

---

## 3. Rule-by-Rule Reference (R000–R055)

---

### R000 — `R000_LoadDefaultParams`
**Group:** Initialisation | **Salience:** 1000

**What it does:** Inserts the singleton `Params` fact with default configuration values at session start.

**Why it exists:** Using `not Params()` prevents re-insertion on subsequent `fireAllRules()` calls. High salience (1000) guarantees it fires before any rule that reads `Params`. This is the standard Drools bootstrap pattern for session-scoped configuration.

---

### R001 — `R001_StalePosFlag`
**Group:** Data Quality

**What it does:** Inserts `TrackQuality(stalePos=true)` when the GPS position fix (`timePosition`) is older than `stalePosSec` (20s) or is null.

**Why it exists:** A position timestamp older than 20 seconds means the displayed aircraft position could be kilometres from the actual position. Aircraft with stale positions are excluded from conflict detection by R049 and their conflict alerts are cleared by R014.

---

### R002 — `R002_MissingPositionFlag`
**Group:** Data Quality

**What it does:** Inserts `TrackQuality(missingPos=true)` when `lat` or `lon` is null.

**Why it exists:** Conflict detection is purely geometry-based. An aircraft with no position cannot participate in any proximity calculation. This flag excludes it from pairing via R049.

---

### R003 — `R003_StaleContactFlag`
**Group:** Data Quality

**What it does:** Inserts `TrackQuality(staleAny=true)` when `lastContact` is null or older than `staleAnySec` (30s).

**Why it exists:** `lastContact` is the last time *any* ADS-B message was received from this aircraft, not just a position. If contact is lost for more than 30s the aircraft may have left the coverage area, powered off its transponder, or crashed. It is immediately excluded from pairing and its alerts are cleared by R015.

---

### R004 — `R004_BadAltitudeFlag`
**Group:** Data Quality

**What it does:** Inserts `TrackQuality(badAlt=true)` when `baroAltitudeM` or `geoAltitudeM` is below −200m.

**Why it exists:** −200m is below the lowest point on Earth (Dead Sea, −430m is the limit of plausible aircraft altitude). Negative altitudes beyond this indicate corrupted transponder data. Without valid altitude, vertical separation cannot be computed.

---

### R005 — `R005_BadVelocityFlag`
**Group:** Data Quality

**What it does:** Inserts `TrackQuality(badVel=true)` when `velocityMps` is negative.

**Why it exists:** Ground speed cannot be negative — it is a scalar magnitude. A negative value indicates a corrupted or uninitialized transponder field. Without valid speed, time-to-collision calculations are meaningless.

---

### R006 — `R006_OnGroundButHighSpeed`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_ANOMALY", ...)` when `onGround == true` but `velocityMps > 120` m/s.

**Why it exists:** 120 m/s (~233 knots) significantly exceeds any ground speed limit. This combination indicates the aircraft is in takeoff rotation but the squat switch hasn't updated the ground bit yet. Logged as an anomaly but the aircraft is not excluded from conflict detection — it is real traffic in a critical phase of flight.

---

### R007 — `R007_NullCallsignAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", ...)` when `callsign` is null.

**Why it exists:** A missing callsign reduces traceability. Some transponders never populate this field. Logged for analytics; does not affect conflict detection since `icao24` is used as the track identifier.

---

### R008 — `R008_SpiSetAudit` *(if present)*
**Group:** Surveillance Audit

**What it does:** Logs when the SPI (Special Position Identification) pulse is active.

**Why it exists:** SPI is set by the pilot pressing the "IDENT" button on request from ATC, or is automatically set in distress modes. Worth logging as a surveillance event.

---

### R009 — `R009_FilterTracksMissingPositionFromPairing`
**Group:** Quality Gate Audit

**What it does:** Inserts `AuditEvent("FILTER", ...)` when an aircraft has `TrackQuality(missingPos=true)`.

**Why it exists:** Provides an explicit, traceable audit record every time an aircraft is excluded from conflict pairing due to missing position. The actual exclusion is enforced by R049; this rule makes the reason visible in the audit log.

---

### R010 — `R010_FilterTracksStalePositionFromPairing`
**Group:** Quality Gate Audit

**What it does:** Inserts `AuditEvent("FILTER", ...)` when an aircraft has `TrackQuality(stalePos=true)`.

**Why it exists:** Same as R009 — provides an audit trail for stale-position exclusions.

---

### R011 — `R011_FilterTracksBadVelocityFromPairing`
**Group:** Quality Gate Audit

**What it does:** Inserts `AuditEvent("FILTER", ...)` when an aircraft has `TrackQuality(badVel=true)`.

**Why it exists:** Same pattern — audit trail for bad-velocity exclusions.

---

### R012 — `R012_FilterTracksBadAltitudeFromPairing`
**Group:** Quality Gate Audit

**What it does:** Inserts `AuditEvent("FILTER", ...)` when an aircraft has `TrackQuality(badAlt=true)`.

**Why it exists:** Same pattern — audit trail for bad-altitude exclusions.

---

### R013 — `R013_FilterStaleContactFromPairing`
**Group:** Quality Gate Audit

**What it does:** Inserts `AuditEvent("FILTER", ...)` when an aircraft has `TrackQuality(staleAny=true)`.

**Why it exists:** Completes the five-flag quality gate audit trail — one rule per flag (R009–R013). R003 sets the `staleAny` flag when `lastContact` is too old; R013 logs the corresponding exclusion event.

---

### R014 — `R014_StalePosClearsOldAlerts`
**Group:** Alert Cleanup

**What it does:** Retracts an `Alert` when one of its aircraft has `TrackQuality(stalePos=true)`.

**Why it exists:** An alert based on a position older than 20 seconds is unreliable — the aircraft may have moved several kilometres. Clearing the alert prevents misleading the controller into believing a separation violation is still occurring when the data no longer supports it.

---

### R015 — `R015_StaleContactClearsOldAlerts`
**Group:** Alert Cleanup

**What it does:** Retracts an `Alert` when one of its aircraft has `TrackQuality(staleAny=true)`.

**Why it exists:** Loss of contact for more than 30 seconds means the aircraft has effectively disappeared from surveillance. Any previous alert for that aircraft is no longer actionable and must be cleared.

---

### R016 — `R016_CategoryUnknownAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", ...)` when `category` is null or 0 (unknown emitter type).

**Why it exists:** Most Mode S transponders don't populate the ADS-B emitter category field, leaving it at 0. Logging these tracks allows analytics on how many aircraft have unknown classification. Category information is used by R031–R035 for operational pattern detection.

---

### R017 — `R017_NonAdsbPositionSourceAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", ...)` when `positionSource` is non-null and not 0 (i.e. not ADS-B — could be MLAT=2, ASTERIX=1, or FLARM=3).

**Why it exists:** Non-ADS-B position sources have higher uncertainty. MLAT in particular relies on multilateration from multiple ground receivers and can produce position errors of hundreds of metres. Knowing which tracks are MLAT-derived is important for tuning false-alert rates. Only fires for the minority of non-ADS-B tracks.

---

### R018 — `R018_TooFastForAltitudeBandAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_ANOMALY", ...)` when `geoAltitudeM < 300` and `velocityMps > 250`.

**Why it exists:** 250 m/s (~486 knots) at below 300m is physically implausible for normal operations and exceeds ICAO speed limits below 10,000 ft. Either the reported altitude is wrong (most likely) or the speed is corrupted. Logged as an anomaly; aircraft not excluded since it is still real traffic.

---

### R019 — `R019_NullAltitudesAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", ...)` when *both* `baroAltitudeM` and `geoAltitudeM` are null.

**Why it exists:** A single null altitude is common (e.g. older Mode C transponders omit GPS altitude). Both null simultaneously means the system has *no* vertical position — vertical separation cannot be computed at all for this track, making conflict detection unreliable.

---

### R020 — `R020_TrackDegRangeAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_ANOMALY", ...)` when `trueTrackDeg` is outside the valid range [0, 360).

**Why it exists:** A compass bearing must be in [0°, 360°). Values outside this range indicate a transponder firmware bug or a data pipeline arithmetic error. An invalid heading makes trajectory projection (used in TTC computation) unreliable.

---

### R021 — `R021_DeltaVelocityOver10s`
**Group:** Kinematics

**What it does:** Joins two state vectors for the same aircraft where the newer is at most 15 seconds later than the older. Computes `dVel = velocityNew − velocityOld` and inserts a `KinematicDelta` fact.

**Why it exists:** A single snapshot gives instantaneous speed; comparing two consecutive snapshots gives acceleration. The `KinematicDelta` is then consumed by R022 (sudden speed jump audit) and R055 (high acceleration audit). The 15-second window ensures only consecutive snapshots are compared — wider windows produce meaningless deltas. The time guard is expressed as `($t1 - $t0) <= 15` in the `when` clause so Rete filters the cross-join efficiently.

---

### R022 — `R022_SuddenSpeedJumpAudit`
**Group:** Kinematics Audit

**What it does:** Inserts `AuditEvent("KINEMATICS", "sudden speed jump")` when a `KinematicDelta` shows `|dVel| > 80 m/s`.

**Why it exists:** A speed change of 80 m/s (~155 knots) within 15 seconds is physically impossible for commercial aircraft (which accelerate at 0.05–0.15g). In practice this fires exclusively on corrupted ADS-B data — GPS switching between receiver modes mid-flight, MLAT receiver errors, or transponder cold-restarts. Useful for identifying noisy receivers in the dataset.

---

### R023 — `R023_SuddenVerticalRateAudit`
**Group:** Kinematics Audit

**What it does:** Inserts `AuditEvent("KINEMATICS", "extreme vertical rate")` when `|verticalRateMps| > 40`.

**Why it exists:** 40 m/s (~7,900 ft/min) of climb or descent is beyond the structural limits of commercial aircraft at cruise. This value is read directly from the transponder (no cross-snapshot join needed), making this rule an O(n) single-fact check. It fires almost exclusively on GPS altitude glitches causing spurious rate readings.

---

### R024 — `R024_SuddenTurnRateApproxAudit`
**Group:** Kinematics Audit

**What it does:** Computes the smallest angle between two consecutive `trueTrackDeg` values using the shortest-angle formula, within a 15-second window. Fires if the turn rate exceeds 10°/s.

**Why it exists:** 10°/s is an aggressive turn for any commercial aircraft (standard rate turn is 3°/s). A rate above this in ADS-B data almost always signals heading data corruption. The shortest-angle formula (`min(|Δtrack|, 360 − |Δtrack|)`) handles the 0°/360° wraparound correctly (e.g. a heading change from 355° to 5° is 10°, not 350°). The entire threshold logic lives in `eval()` in the `when` clause — no if-blocks in `then`.

---

### R025 — `R025_AltitudeJumpAudit`
**Group:** Kinematics Audit

**What it does:** Inserts `AuditEvent("KINEMATICS", "altitude jump")` when `geoAltitudeM` changes by more than 1,200m between two snapshots within 15 seconds.

**Why it exists:** The fastest legitimate commercial climb rate is ~20 m/s → at most 300m over 15 seconds. A 1,200m change must be a bad GPS fix, MLAT position outlier, or receiver multipath error. GPS geometric altitude is used (not barometric) because baro altitude intrinsically varies with atmospheric pressure and cannot be compared snapshot-to-snapshot as reliably. The altitude difference check is expressed directly in the `$s0` pattern constraint for efficient Rete evaluation.

---

### R026 — `R026_OnGroundStateFlipAudit`
**Group:** Kinematics Audit

**What it does:** Inserts `AuditEvent("KINEMATICS", "onGround flip")` when the `onGround` boolean changes between two consecutive snapshots within 15 seconds.

**Why it exists:** An `onGround` flip from `true → false` marks takeoff and from `false → true` marks landing — both are normal and expected events. However in the OpenSky network, this flag is often noisy: aircraft rolling on the runway can oscillate between `true` and `false` across several snapshots. Logging every flip allows post-hoc analysis of sensor noise vs real flight phase transitions.

---

### R027 — `R027_UnknownVelButMovingAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", "airborne but velocity null")` when `onGround == false` and `velocityMps == null`.

**Why it exists:** An airborne aircraft should always report ground speed. A null velocity while airborne indicates a partially-functional transponder or a one-snapshot ADS-B stream dropout. Without velocity, TTC calculations for this aircraft are unreliable.

---

### R028 — `R028_UnknownTrackButMovingAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", "moving but track null")` when `trueTrackDeg == null` and `velocityMps > 30`.

**Why it exists:** An aircraft moving at more than 30 m/s (~58 knots) should have a defined heading. A null heading prevents trajectory projection — the system cannot predict where the aircraft will be in 90 seconds. The 30 m/s threshold avoids false positives for hovering helicopters or slow ground vehicles.

---

### R029 — `R029_ClimbWhileOnGroundAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_ANOMALY", "vertical rate while on ground")` when `onGround == true` and `|verticalRateMps| > 3`.

**Why it exists:** A vertical rate above 3 m/s (~590 ft/min) while on the ground is physically implausible except during the instant of rotation at takeoff. This fires when the squat switch hasn't updated the `onGround` flag yet but the aircraft has already rotated. Audit-only; aircraft is not excluded since it is in a real and critical phase of flight. Companion to R006 which checks horizontal speed under the same condition.

---

### R030 — `R030_BaroGeoDisagreeAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_ANOMALY", "baro vs geo altitude disagree")` when both altitude fields are present but differ by more than 600m.

**Why it exists:** Some disagreement between barometric and geometric altitude is normal (atmospheric pressure variations, altimeter settings). But 600m of disagreement exceeds what any realistic atmospheric effect or altimeter setting could produce — it signals MLAT position error, bad GPS fix, or wrong altimeter setting (QNE vs QNH confusion). Without knowing which altitude to trust, vertical separation calculations for this aircraft are uncertain.

---

### R031 — `R031_SlowHoveringRotorcraftAudit`
**Group:** Operational Pattern

**What it does:** Inserts `AuditEvent("OPS_PATTERN", "rotorcraft low speed airborne")` when `category == 8` (helicopter), `velocityMps < 10`, and `onGround == false`.

**Why it exists:** A hovering helicopter is essentially stationary traffic — a fundamentally different threat model from converging fixed-wing aircraft. Standard STCA separation minima (3 NM / 1000 ft) were designed for en-route traffic. `OPS_PATTERN` events give downstream logic (or operators) the context to apply different alerting logic for rotorcraft operations.

---

### R032–R035
**Group:** Operational Pattern

These four rules follow the same form: match a specific ADS-B emitter `category` and insert `AuditEvent("OPS_PATTERN", ...)`.

| Rule | Category | Pattern logged |
|------|----------|----------------|
| R032 | 14 (UAV/drone) | UAV detected — separation minima may not apply |
| R033 | 9 (glider/sailplane) | Glider detected — may have no engine, unpowered performance |
| R034 | 16 (surface emergency vehicle) | Emergency vehicle on field |
| R035 | 18 (point obstacle) | Fixed obstacle transmitting ADS-B |

None of these gate the aircraft out of conflict detection; they provide situational awareness context.

---

### R036 — `R036_SquawkChangedAudit`
**Group:** Surveillance Audit

**What it does:** Inserts `AuditEvent("SURVEILLANCE", "squawk changed A->B")` when the 4-digit transponder code changes between two snapshots within 60 seconds.

**Why it exists:** A squawk change is operationally significant:
- Normal code → `7700` = emergency declared
- Normal code → `7600` = radio failure
- Normal code → `7500` = hijacking
- Any other change = ATC reassignment

The 60-second window (wider than kinematics rules) accounts for the time a pilot takes to dial in a new squawk. The audit log captures both the old and new code for full traceability.

---

### R037 — `R037_SpiSetAudit`
**Group:** Surveillance Audit

**What it does:** Inserts `AuditEvent("SURVEILLANCE", "SPI set")` when `spi == true`.

**Why it exists:** SPI (Special Position Identification) is activated by pressing the "IDENT" button on the transponder — usually at ATC request to confirm identity, or automatically in emergency transponder modes. It is a direct communication channel between pilot and controller worth logging in the audit trail.

---

### R038 — `R038_CallsignWhitespaceAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", "callsign blank")` when `callsign != null` but consists entirely of whitespace.

**Why it exists:** ADS-B callsign fields are padded to exactly 8 characters. When a crew hasn't set a callsign, some transponders broadcast 8 space characters instead of null. R007 catches null callsigns; R038 catches this "blank but non-null" variant. Together they cover the two most common "no callsign" encodings in real transponder data.

---

### R039 — `R039_OriginCountryMissingAudit`
**Group:** Data Quality Audit

**What it does:** Inserts `AuditEvent("DATA_QUALITY", "origin country null")` when `originCountry == null`.

**Why it exists:** `originCountry` is derived from the `icao24` prefix (country allocation is standardised by ICAO). A null value indicates an unregistered or spoofed transponder code. Does not affect conflict detection; logged for analytics.

---

### R041 — `R041_AssignGridCell`
**Group:** Grid / Pair Candidates

**What it does:** Computes a grid cell key `"gx_gy"` for each aircraft with a known position using `Math.floor(lat / gridDeg)` and `Math.floor(lon / gridDeg)`. Inserts a `GridCell` fact mapping the aircraft to that cell.

**Why it exists:** Naïve all-vs-all pairing of 630 aircraft would require evaluating ~198,000 pairs. The grid reduces this to only aircraft within the same 0.25° × 0.25° cell (roughly 18–28 km depending on latitude). Two aircraft more than one cell apart are guaranteed to be far enough apart that no STCA threshold could be exceeded, so they can be safely skipped.

---

### R042 — `R042_PairWithinCell`
**Group:** Grid / Pair Candidates

**What it does:** Joins two `GridCell` facts sharing the same `cellId` and inserts a `PairCandidate(a, b, cellId)`. The `eval($a.compareTo($b) < 0)` guard ensures each pair is created exactly once (ordered by `icao24` lexicographic order, preventing both `(A,B)` and `(B,A)` being created).

**Why it exists:** This is the core spatial proximity filter. Only aircraft in the same cell are candidates for conflict evaluation. The deduplication guard prevents duplicate `ConflictCandidate` facts downstream.

---

### R043 — `R043_NoPairIfSameAircraft`
**Group:** Grid / Pair Candidates

**What it does:** Retracts a `PairCandidate` where `a == b` (self-pair).

**Why it exists:** R042's `compareTo < 0` guard already prevents self-pairs (`compareTo(self) == 0`), so this rule should never fire in practice. It exists as a defensive guard — if R042 is ever modified and loses the deduplication constraint, R043 ensures self-pairs are cleaned up immediately rather than propagating into conflict detection.

---

### R044 — `R044_PairInhibitByCell`
**Group:** Pair Candidate Filter

**What it does:** Retracts a `PairCandidate` when an `AlertInhibit(kind="AIRSPACE", key=cellId, enabled=true)` exists for its grid cell.

**Why it exists:** Operators can suppress conflict alerts for entire airspace zones — e.g. military exercise areas, firing ranges, or temporarily reserved airspace where standard STCA separation minima don't apply. This rule removes all pairs in that zone before any further processing.

---

### R045 & R046 — `R045_PairInhibitByFlightA` / `R046_PairInhibitByFlightB`
**Group:** Pair Candidate Filter

**What they do:** Retract a `PairCandidate` when either aircraft A (R045) or aircraft B (R046) has an individual `AlertInhibit(kind="FLIGHT", ...)`.

**Why two separate rules:** Drools cannot efficiently OR-match a constraint against two different bound variables from the same pattern in a single rule. Splitting into two rules keeps the matching clean and fully indexed.

**Why they exist:** Operators can individually suppress specific aircraft — e.g. known test transponders, training aircraft operating under special VFR, or aircraft whose transponder data is persistently bad.

---

### R047 — `R047_PairInhibitByFlightPair`
**Group:** Pair Candidate Filter

**What it does:** Retracts a `PairCandidate` when a specific pair `AlertInhibit(kind="FLIGHTPAIR", key="a|b")` exists.

**Why it exists:** The finest-grained inhibit level — suppresses one specific aircraft pair. Used for formation flying (e.g. display teams that intentionally fly very close) or escort scenarios where separation minima are contractually waived.

---

### R048 — `R048_SkipBothOnGroundPairs`
**Group:** Pair Candidate Filter

**What it does:** Retracts a `PairCandidate` when *both* aircraft report `onGround == true`.

**Why it exists:** STCA is an en-route / terminal separation tool. Surface movement (taxiing, gate pushback) is handled by separate Surface Movement Guidance and Control Systems (SMGCS). If only *one* aircraft is on the ground, the pair is kept — a taxiing aircraft near a rotating aircraft on final approach is still a concern.

---

### R049 — `R049_SkipBadQualityPairs`
**Group:** Pair Candidate Filter (Quality Gate)

**What it does:** Retracts a `PairCandidate` if either aircraft has any active `TrackQuality` flag (`missingPos`, `stalePos`, `badAlt`, or `badVel`).

**Why it exists:** This is the enforcement point for all the quality flags set by R001–R005. A pair where either aircraft has bad data cannot produce a reliable conflict assessment — the geometry would be wrong, the time-to-collision meaningless, or the altitude comparison impossible. Retracting early prevents these pairs from consuming any further computation in R056+.

---

### R051 — `R051_PairDedupGuard`
**Group:** Pair Candidate Filter

**What it does:** Retracts one of two `PairCandidate` facts that share the same pair key `(a+"|"+b)`.

**Why it exists:** An aircraft near a grid cell boundary can be assigned to two adjacent cells by R041, causing R042 to produce two identical `PairCandidate(A,B)` facts from different cells. This rule removes the duplicate. The choice of which to retract is non-deterministic (both carry the same aircraft IDs), which is acceptable since they represent the same proximity relationship.

---

### R052 — `R052_FinalPairReadyForConflictCheck`
**Group:** Pair Candidate Audit

**What it does:** Inserts `AuditEvent("PAIR_READY", ...)` for every `PairCandidate` that survived all filter rules (R043–R051).

**Why it exists:** This is the observability checkpoint immediately before conflict detection. The count of `PAIR_READY` events per cycle directly shows the engine workload — how many pairs are entering R056 geometry computation each snapshot. This is valuable for performance tuning and understanding traffic density.

---

### R053 — `R053_BusyAirspaceCellAudit`
**Group:** Grid / Operational Pattern

**What it does:** Uses `accumulate(GridCell(cellId == $cell), count(1))` to count aircraft in each cell. Inserts `AuditEvent("OPS_PATTERN", ...)` when a cell contains 5 or more aircraft.

**Why it exists:** A grid cell with 5+ aircraft represents a complex traffic cluster — potentially an arrival fix, holding stack, or busy terminal area. This is operationally significant: standard pairwise STCA may miss systemic conflicts in high-density clusters. Flagging busy cells allows operators to apply heightened vigilance or alternative traffic management.

---

### R055 — `R055_HighAccelerationAudit`
**Group:** Kinematics Audit

**What it does:** Reads `dVel` and `dt` from a `KinematicDelta` and inserts `AuditEvent("KINEMATICS", "high linear acceleration Xm/s2")` when `|dVel| / dt > 5 m/s²`.

**Why it exists:** 5 m/s² (~0.5g) sustained linear acceleration is beyond the structural capability of commercial aircraft in level flight, but achievable by military fast jets or is a signal of MLAT position interpolation error. This is distinct from R022 (which checks raw `|dVel| > 80 m/s` — almost always data corruption). R055 catches *rate* of change, which can flag aggressive but physically plausible military manoeuvres or moderate-but-sustained sensor artifacts.

---

---

## 4. Rule-by-Rule Reference (R056–R100)

---

### R056 — `R056_BuildConflictCandidateBasic`
**Group:** Conflict Detection | **Inputs:** `Params`, `PairCandidate`, two `OpenSkyStateVector`

**What it does:** The core geometry rule. For each `PairCandidate(a, b)` that survived all filters, it:
1. Computes the **haversine great-circle distance** (`d`) between the two aircraft in metres
2. Computes **vertical separation** (`vert`) from whichever altitude field is available (geo preferred, baro as fallback)
3. Determines **`closing`** — whether the aircraft are within the `nearDistNm` range
4. Computes a simplified **time-to-collision** (`ttc`) using the scalar difference in ground speeds (`rel`) and current distance: `ttc = d / rel`
5. Inserts a `ConflictCandidate(a, b, d, vert, closing, ttc, "INFO")`

**Why it exists:** This is the single rule that transforms the pair candidate (two IDs and a cell) into a geometric assessment with actual distance and time values. Severity is always seeded as `"INFO"` — all classification to WARN/ALERT is handled declaratively by the downstream rules R057–R062 via `modify()`, keeping R056's responsibility purely geometric.

---

### R057 — `R057_DowngradeIfVerticalSeparationLarge`
**Group:** Conflict Severity Classification

**What it does:** Calls `modify($c){ setSeverity("INFO"); }` when `vertSepM > (vertMinFt * 0.3048 * 2.0)` — i.e., when vertical separation exceeds **twice the minimum vertical separation** (default: 2 × 1000 ft = ~610m).

**Why it exists:** Horizontal proximity alone is not sufficient to declare a conflict — aircraft at different flight levels are separated vertically and pose no risk. This rule is the "vertical filter": if the aircraft are well clear vertically, the severity is dropped back to INFO regardless of horizontal distance or TTC. The 2× multiplier gives a safety buffer accounting for altitude measurement uncertainty.

---

### R058 — `R058_UpgradeIfVeryClose`
**Group:** Conflict Severity Classification

**What it does:** Upgrades any non-ALERT `ConflictCandidate` to `"ALERT"` when `distM < metersFromNm(1.5)` — i.e., less than **1.5 NM** horizontal separation.

**Why it exists:** 1.5 NM is below any safe separation minima in both en-route (5 NM) and terminal (3 NM) airspace. At this distance a conflict is unambiguous regardless of the TTC estimate. This rule overrides anything R057 may have set — extremely close separation takes unconditional priority.

---

### R059 — `R059_WarnIfBelowPrototypeRadarMinima`
**Group:** Conflict Severity Classification

**What it does:** Upgrades an `"INFO"` `ConflictCandidate` to `"WARN"` when `distM < metersFromNm(3.0)` (less than **3 NM**).

**Why it exists:** 3 NM is the standard terminal radar separation minimum. Aircraft within 3 NM but not yet in R058's 1.5 NM emergency zone are in a developing conflict situation that requires a warning to the controller. The `severity == "INFO"` guard ensures this rule only promotes; it never demotes.

---

### R060 — `R060_AlertIfBelowPrototypeRadarMinima`
**Group:** Conflict Severity Classification

**What it does:** Upgrades a `"WARN"` `ConflictCandidate` to `"ALERT"` when `distM < metersFromNm(3.0)`.

**Why it exists:** If a pair was already at WARN severity and is still inside the 3 NM bubble, the situation has persisted without resolution and should escalate to ALERT. Together with R059 this forms a two-step distance-based escalation ladder: INFO → WARN (R059) → ALERT (R060), where each step requires the rule to fire once on the same geometry.

---

### R061 — `R061_WarnIfTTCWithinWarningTime`
**Group:** Conflict Severity Classification

**What it does:** Upgrades `"INFO"` → `"WARN"` when `ttcSec < warnTtcSec` (default **90 seconds**).

**Why it exists:** Distance alone is insufficient for aircraft on converging headings — two aircraft 8 NM apart but closing at a combined 600 knots will be in conflict within 90 seconds. TTC-based alerting provides **time-to-collision lead time**, which is the fundamental metric in STCA. This is the time-domain counterpart to R059's distance-domain promotion.

---

### R062 — `R062_AlertIfTTCWithinAlertTime`
**Group:** Conflict Severity Classification

**What it does:** Upgrades any non-ALERT `ConflictCandidate` to `"ALERT"` when `ttcSec < alertTtcSec` (default **45 seconds**).

**Why it exists:** 45 seconds of TTC represents a genuine emergency — standard ATC separation instructions take 30–60 seconds to execute. At this lead time the conflict has reached the point where immediate controller action is required. The `severity != "ALERT"` guard prevents a redundant `modify()` on already-ALERT candidates.

---

### R063 — `R063_SuppressIfNotClosingAndFar`
**Group:** Conflict Filtering

**What it does:** Retracts a `ConflictCandidate` when `closing == false` AND `distM > metersFromNm(nearDistNm)` (default > **10 NM**).

**Why it exists:** Aircraft that are already far apart and not converging are definitively not in conflict — they are diverging. The `PairCandidate` arose because they shared a grid cell at the time of evaluation, but geometry shows they are moving apart. Retracting early saves R065–R082 from processing irrelevant candidates.

---

### R064 — `R064_SuppressIfAnyOnGround`
**Group:** Conflict Filtering

**What it does:** Retracts a `ConflictCandidate` when either aircraft `a` or `b` reports `onGround == true`.

**Why it exists:** Unlike R048 (which filters `PairCandidate` where *both* are on the ground), this rule catches the case where only one of the pair is on the ground. An aircraft that has just landed and is rolling down the runway should not trigger an en-route STCA against a departing aircraft. STCA is an airborne separation tool.

---

### R065 — `R065_ConflictCandidateAudit_WARN`
**Group:** Conflict Audit

**What it does:** Inserts `AuditEvent("CONFLICT_WARN", pairKey, ...)` recording the distance in NM and TTC for every WARN-severity `ConflictCandidate`.

**Why it exists:** Creates a structured audit trail of every WARN event with its geometry values, enabling post-run analysis of how many warnings were generated, their distribution of distances and TTC values, and their mapping to aircraft pairs.

---

### R066 — `R066_ConflictCandidateAudit_ALERT`
**Group:** Conflict Audit

**What it does:** Same as R065 but for `"ALERT"` severity — inserts `AuditEvent("CONFLICT_ALERT", ...)`.

**Why it exists:** Provides separate, filterable audit records for ALERT-level conflicts. Keeping WARN and ALERT audit rules separate allows consumers of the `AuditEvent` stream to subscribe to only the severity level they need.

---

### R067a & R067b — `R067a_DedupeConflictCandidates_KeepALERT` / `R067b_DedupeConflictCandidates_KeepFirst`
**Group:** Conflict De-duplication

**What they do:** When two `ConflictCandidate` facts exist for the same pair key `(a|b)`:
- **R067a** retracts the non-ALERT one when a same-pair ALERT exists (keep the higher severity)
- **R067b** retracts the second one when neither is ALERT (keep the first)

**Why two rules:** An `if/else` cannot be expressed as a single pattern match cleanly in Drools. The two-rule split keeps each case declarative and independently indexable. This was the same pattern addressed earlier with the R067 rewrite from the previous chat session.

**Why they exist:** A pair can accumulate two `ConflictCandidate` facts if both R056 and R069 fire for the same pair (R056 using simple distance/speed; R069 using the richer `CpaMetrics`). The dedup rules ensure exactly one `ConflictCandidate` proceeds to alerting, with the higher severity winning.

---

### R068 — `R068_ClearOldConflictCandidates`
**Group:** Conflict Cleanup

**What it does:** Retracts every `ConflictCandidate` unconditionally.

**Why it exists:** `ConflictCandidate` facts are transient — they represent the conflict state for *this snapshot cycle*. After R065/R066 have audited them and R075/R081/R082 have raised any necessary `Alert` facts, the candidates should not linger across cycles. This ensures the working memory stays bounded.

---

### R069 — `R069_CreateConflictFromCpaMetrics`
**Group:** CPA-based Conflict Detection

**What it does:** If a `CpaMetrics` fact exists for a pair that is actively closing (`closing == true`, `tCpaSec > 0`), inserts a new `ConflictCandidate` using the **CPA distance** (`dCpaM`) and **CPA time** (`tCpaSec`) — the predicted closest-approach values rather than current distance.

**Why it exists:** The simple TTC in R056 (`d / speed_difference`) is a rough linear approximation that ignores the directional headings of the aircraft. `CpaMetrics` provides the full vector-based closest point of approach computation. A pair might currently be 8 NM apart but CPA analysis shows they will be 0.8 NM apart in 45 seconds — R069 captures this threat that R056 would miss.

---

### R070 — `R070_SuppressIfNotClosingOrPastCPA`
**Group:** CPA-based Filtering

**What it does:** Retracts a `ConflictCandidate` that has a matching `CpaMetrics` where the pair is not closing or has already passed CPA (`tCpaSec ≤ 0`).

**Why it exists:** A pair past their CPA is diverging — the closest approach already happened and they are now separating. The threat is over. This is the CPA-domain equivalent of R063 (suppress if not closing).

---

### R071 & R072 — `R071_UpgradeToWARN_UsingCPA` / `R072_UpgradeToALERT_UsingCPA`
**Group:** CPA-based Severity Classification

**What they do:** Read `dCpaM` and `tCpaSec` from `CpaMetrics` and apply the same distance/TTC thresholds as R059–R062, but against **future CPA values** rather than current values.

**Why they exist:** The CPA-based distances and times are more accurate than the simple approximations used by R059–R062. These rules allow the `ConflictCandidate` severity to escalate based on *predicted* geometry — the aircraft may be 6 NM apart now but CPA shows 2 NM in 60 seconds, which R071 upgrades to WARN.

---

### R073 — `R073_InitPairRiskState`
**Group:** Persistence / Hysteresis

**What it does:** Inserts a `PairRiskState` the first time a `ConflictCandidate` appears for a new pair key, initialising streaks to zero.

**Why it exists:** `PairRiskState` is the persistent per-pair memory that survives across snapshots. R073 is the bootstrap: the `not PairRiskState(key == $k)` guard ensures it fires exactly once per pair lifetime. Without this initialisation fact, the streak-counting rules R074a/b/c have nothing to `modify()`.

---

### R074a, R074b, R074c — `R074a_UpdateStreaks_ALERT` / `R074b_UpdateStreaks_WARN` / `R074c_UpdateStreaks_INFO`
**Group:** Persistence / Hysteresis

**What they do:** For each snapshot cycle, exactly one of these fires per active `ConflictCandidate`+`PairRiskState` pair:
- **R074a** (ALERT): increments `alertStreak`, resets `warnStreak` and `safeStreak` to 0
- **R074b** (WARN): increments `warnStreak`, resets the others
- **R074c** (INFO): increments `safeStreak`, resets the others

**Why three rules:** The original R074 used a single `if/else if/else` block inside `then` with a `modify`. This is the anti-pattern addressed throughout these rules — a single rule with branching logic means the Rete engine can't distinguish the three cases. Splitting into three rules with severity constraints on the `ConflictCandidate` pattern keeps each case declarative and lets the engine index on `severity` efficiently.

**Why they exist:** These build the "streak counter" — how many consecutive snapshots a pair has been at each severity level. This is the input to the hysteresis rules R075 and R076.

---

### R075 — `R075_RaiseAlertsOnlyAfterPersistence`
**Group:** Persistence / Hysteresis

**What it does:** Inserts `Alert(type="SAFETY_ALERT")` only when `alertStreak >= 2` — i.e., the pair has been in ALERT state for at least **two consecutive snapshots**.

**Why it exists:** Single-snapshot ALERT events are often data artefacts — a GPS outlier, MLAT position error, or squawk glitch can produce a momentary false geometry. Requiring two consecutive ALERT snapshots (~10–20 seconds apart depending on feed rate) acts as a temporal filter that dramatically reduces nuisance alerts from data noise while still catching real conflicts quickly enough for ATC response.

---

### R076 — `R076_Hysteresis_ClearOnlyAfterSafePersistence`
**Group:** Persistence / Hysteresis

**What it does:** Retracts an `Alert(type="SAFETY_ALERT")` only when `safeStreak >= 3` — three consecutive snapshots of safe geometry.

**Why it exists:** This is the "latch-off" hysteresis. A real conflict that briefly looks safe for one snapshot (e.g., due to a position update) should not clear the alert prematurely. The controller needs confidence that the situation is fully resolved. Three consecutive safe snapshots (~30 seconds of safe data) provides this confidence.

---

### R077 — `R077_InhibitSafetyAlertsByPair`
**Group:** Alert Inhibition

**What it does:** Retracts a `SAFETY_ALERT` Alert and inserts an `AuditEvent("INHIBIT_EFFECT", ...)` when a matching `AlertInhibit(kind="FLIGHTPAIR")` exists.

**Why it exists:** Fine-grained pair-level inhibition for the most critical alert type. Records the suppression as an `INHIBIT_EFFECT` audit event so operators have a traceable log of when and why a safety alert was suppressed.

---

### R078 — `R078_InhibitMustBeKnown_Audit`
**Group:** Alert Inhibition

**What it does:** Inserts `AuditEvent("INHIBIT_SET", ...)` for every active `AlertInhibit`.

**Why it exists:** Provides visibility into what inhibits are currently loaded in the session. An operator reviewing the audit log can confirm which aircraft, pairs, or airspace zones are suppressed and detect any inadvertent inhibits.

---

### R079 — `R079_MultiConflictHotspotEscalation`
**Group:** Hotspot Detection

**What it does:** Uses a three-way join on `ConflictCandidate(severity=="ALERT", a==$x)` to detect an aircraft involved in **three or more simultaneous ALERT conflicts**. Inserts `AuditEvent("HOTSPOT", ...)`.

**Why it exists:** A single aircraft involved in multiple simultaneous conflicts signals a localised high-density traffic problem — a blocked arrival fix, holding stack collapse, or airspace design issue. Standard pairwise STCA doesn't surface this systemic pattern. The hotspot audit event is the hook for supervisory alerting or traffic flow management escalation.

---

### R080 — `R080_RecordCPAForAnalysis`
**Group:** CPA Analysis Hook

**What it does:** Inserts `AuditEvent("CPA_METRICS", ...)` for every `CpaMetrics` fact, recording `tCPA`, `dCPA_m`, and `vertAtCPA_m`.

**Why it exists:** Pure observability — logs the CPA computation results so that post-benchmark analysis can correlate CPA values with raised alerts, evaluate the quality of the TTC approximation in R056 vs. full CPA in R069, and tune alert thresholds based on real-world data distributions.

---

### R081 — `R081_RaiseTrafficAdvisoryOnWARN`
**Group:** Alert Lifecycle

**What it does:** Inserts `Alert(type="TRAFFIC_ADVISORY", severity="WARN")` when a WARN `ConflictCandidate` exists and no TRAFFIC_ADVISORY already exists for that pair.

**Why it exists:** TRAFFIC_ADVISORY is the lighter-weight alert — informing the controller that the situation requires monitoring but is not yet an emergency. The `not Alert(...)` guard is idempotent: the advisory is raised once and persists until explicitly cleared.

---

### R082 — `R082_RaiseSafetyAlertOnALERT`
**Group:** Alert Lifecycle

**What it does:** Inserts `Alert(type="SAFETY_ALERT", severity="ALERT")` when an ALERT `ConflictCandidate` exists and no SAFETY_ALERT already exists for that pair.

**Why it exists:** This is the primary safety alerting rule — the one that puts an actionable conflict on the controller's screen. Like R081, the `not Alert(...)` guard prevents duplicate alerts per pair.

---

### R083 — `R083_AlertPersistsWhileConditionExists`
**Group:** Alert Lifecycle

**What it does:** Matches `Alert` facts that are older than `persistSec` (15 seconds) and have no `AlertAck`. The `then` block is intentionally empty (`// keep alert`).

**Why it exists:** This is a **no-action persistence rule** — its purpose is to express the *intent* that alerts should remain active beyond `persistSec` without being retracted. In a real STCA system this might instead drive a periodic re-notification. As documented: the empty `then` is intentional; the rule's existence documents the persistence policy rather than implementing anything imperative.

---

### R084 — `R084_AckSilencesAudibleEquivalent`
**Group:** Alert Acknowledgement

**What it does:** Sets `severity = "ACK"` on an `Alert` when a matching `AlertAck` exists.

**Why it exists:** When a controller acknowledges an alert it should be silenced (no repeat audio/visual) but not yet cleared — the conflict may still exist. The `"ACK"` severity is the "silenced but acknowledged" state, distinct from `"WARN"` or `"ALERT"`. The alert only gets physically retracted by R085 after a further timeout.

---

### R085 — `R085_ClearAlertAfterAckAndNoNewConflicts`
**Group:** Alert Acknowledgement

**What it does:** Retracts an ACK-severity `Alert` if the corresponding `AlertAck` was received more than **30 seconds** ago.

**Why it exists:** After acknowledgement, the controller has been informed. If the geometry doesn't re-escalate within 30 seconds after ack, the alert is definitively cleared. This prevents acknowledged alerts from lingering indefinitely in working memory.

---

### R086 — `R086_InhibitAlertsByFlight`
**Group:** Alert Inhibition

**What it does:** Retracts any `Alert` where either aircraft `a` or `b` has an individual `AlertInhibit(kind="FLIGHT")`.

**Why it exists:** Individual flight inhibition at the alert level — the equivalent of R045/R046 (which filtered `PairCandidate` earlier in the pipeline) but applied here to already-raised `Alert` facts. Necessary because inhibits can be added dynamically while alerts are already active.

---

### R087 — `R087_InhibitAlertsByPair`
**Group:** Alert Inhibition

**What it does:** Retracts any `Alert` for a pair when a `AlertInhibit(kind="FLIGHTPAIR")` exists for that pair.

**Why it exists:** Pair-level inhibition at the alert level — the complement to R047 (which filtered `PairCandidate`) but acting on live `Alert` facts. Handles the case where a formation flight inhibit is configured after alerts are already raised for that pair.

---

### R088 — `R088_InhibitionMustBeKnown_Audit`
**Group:** Alert Inhibition Audit

**What it does:** Inserts `AuditEvent("INHIBIT_SET", ...)` for every enabled `AlertInhibit`. Duplicate of R078.

**Why it exists:** R078 and R088 are structurally identical. R078 is in the R077-block (around `SAFETY_ALERT` inhibition) and R088 is in the R081+ alerting policy block. This appears to be a redundant rule from incremental rule authoring. It produces duplicate `INHIBIT_SET` audit events. A future cleanup could merge these.

---

### R089 — `R089_StatusWhenNotAvailable`
**Group:** Status / Availability

**What it does:** Inserts `AuditEvent("STATUS", "STCA_UNAVAILABLE", ...)` when `Status(stcaAvailable == false)`.

**Why it exists:** A `Status` fact is a session-level signal that the STCA system itself is degraded or unavailable (e.g., sensor feed down, processing overload). When this is declared, an audit event is emitted so that downstream monitors know to display a system-wide unavailability indicator to controllers rather than silently producing no alerts.

---

### R090 — `R090_SafetyAlertPriorityAudit`
**Group:** Alert Audit

**What it does:** Inserts `AuditEvent("PRIORITY", ...)` for every active `SAFETY_ALERT`.

**Why it exists:** Provides a per-cycle count of active safety alerts in the audit log — useful for benchmarking to measure how many simultaneous SAFETY_ALERT facts exist across the full snapshot history.

---

### R091 — `R091_TrafficAdvisoryAudit`
**Group:** Alert Audit

**What it does:** Inserts `AuditEvent("ADVISORY", ...)` for every active `TRAFFIC_ADVISORY` Alert.

**Why it exists:** Same purpose as R090 but for the lighter-weight advisory tier. Together R090 and R091 maintain a continuous audit stream of the alert population.

---

### R092 — `R092_DoNotSpamNuisanceAlerts`
**Group:** Nuisance Reduction

**What it does:** Retracts one of two `Alert` facts for the same pair `(a, b)` if they were both created within **10 seconds** of each other.

**Why it exists:** Prevents alert duplication storms during rapid `fireAllRules()` cycles where the same pair might trigger both R081 and R082, or trigger R082 multiple times across overlapping `ConflictCandidate` facts from R056 and R069. Acts as a deduplication guard at the `Alert` level.

---

### R093 — `R093_EscalateFromAdvisoryToSafetyAlert`
**Group:** Alert Escalation

**What it does:** Retracts the `TRAFFIC_ADVISORY` for a pair when a `SAFETY_ALERT` exists for the same pair.

**Why it exists:** Once a pair has escalated to `SAFETY_ALERT`, the lower-tier `TRAFFIC_ADVISORY` is superseded. Keeping both in working memory is redundant and would produce duplicate audit events (R090 and R091 both firing for the same pair). This cleanup rule removes the advisory, leaving only the safety alert.

---

### R094 — `R094_ClearOldAdvisories`
**Group:** Alert Lifecycle Cleanup

**What it does:** Retracts a `TRAFFIC_ADVISORY` Alert that is more than **120 seconds** old.

**Why it exists:** A traffic advisory that has been present for 2 minutes without escalating to a safety alert and without being acknowledged most likely reflects a situation that resolved itself. Clearing it prevents stale advisories accumulating in working memory across many snapshot cycles.

---

### R095 — `R095_ClearOldAcks`
**Group:** Alert Lifecycle Cleanup

**What it does:** Retracts an `AlertAck` fact that is more than **600 seconds** (10 minutes) old.

**Why it exists:** `AlertAck` facts are inserted by external operators and are never retracted by the controller — only by timeout. 10 minutes is a reasonable window: if the same conflict re-occurs more than 10 minutes after acknowledgement it should surface as a fresh alert rather than being silenced by a stale ack.

---

### R096 — `R096_RecordEverySafetyAlert`
**Group:** Performance / Recording Hooks

**What it does:** Inserts `AuditEvent("RECORD_ALERT", ...)` for every active `SAFETY_ALERT`.

**Why it exists:** Provides a dedicated `RECORD_ALERT` stream in the audit log that is easy to filter separately from `PRIORITY` events (R090). Useful for benchmark output post-processing to count and characterise raised safety alerts.

---

### R097 — `R097_RecordEveryTrafficAdvisory`
**Group:** Performance / Recording Hooks

**What it does:** Inserts `AuditEvent("RECORD_ADVISORY", ...)` for every active `TRAFFIC_ADVISORY`.

**Why it exists:** Same purpose as R096 for the advisory tier. Together R096/R097 give a clean, filterable record of all raised alerts suitable for benchmark output analysis.

---

### R098 — `R098_PerformanceCountersPlaceholder`
**Group:** Performance Hooks

**What it does:** Matches every `AuditEvent()`. The `then` block is empty with a comment: `// host can aggregate counts`.

**Why it exists:** This is a **deliberate no-op placeholder**. Its purpose is to fire on every `AuditEvent` so that external benchmark tooling (e.g., a `RuleRuntimeEventListener`) can observe aggregate activation counts. The empty body means the rule contributes to the Drools activation count metric without changing working memory state, making it useful as a throughput counter.

---

### R099 — `R099_IgnoreNonJustifiedAlertPlaceholder`
**Group:** Performance Hooks

**What it does:** Matches `AuditEvent(kind == "CONFLICT_WARN")`. Empty `then` block.

**Why it exists:** Another intentional no-op placeholder. Represents the conceptual location where a future rule would handle WARN-level conflict audit events (e.g., routing them to a display system or persistence layer). Currently fires and does nothing — serves as a benchmark activation hook similar to R098.

---

### R100 — `R100_EndOfCycleMarker`
**Group:** Performance Hooks

**What it does:** Matches with an empty `when` clause (fires unconditionally every `fireAllRules()` call). Prints `[DRL] R100_EndOfCycleMarker FIRED`.

**Why it exists:** A session-lifecycle marker that fires exactly once per `fireAllRules()` invocation, providing a clear delimiter in the debug log output between snapshot cycles. In a streaming deployment this would signal the end of one processing epoch. In JMH benchmarking it confirms that at least one rule fired during each measurement period.

---

*End of rule documentation. All rules R000–R100 documented.*
