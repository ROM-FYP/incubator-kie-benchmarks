# Binance CEP Benchmark — Rule Taxonomy

> Accurate description of `taxonomy.drl` as implemented.
> **Total rules: 103** (verified via `grep -c "^rule "`)

---

## Rule Categorization

Each rule belongs to exactly one category:

| Category | Code | Description |
|----------|------|-------------|
| **Infrastructure** | INFRA | Bootstrap state facts, retract consumed events, timestamp bookkeeping |
| **Derived Metrics** | DERIVE | Compute and maintain singleton derived state (BestBidAsk, SpreadState, etc.) |
| **Alert / Signal** | ALERT | Single/few-condition rules that emit to `channels["alerts"]` |
| **CEP Pattern** | CEP | Rules using temporal operators, sliding windows, or negation patterns |
| **Multi-fact Join** | JOIN | Rules joining 2+ fact types without temporal operators |
| **State Machine** | FSM | Mode transitions and recovery logic |
| **Forward Chain** | CHAIN | Explicit chaining sequences (J, K, L, M sections) |

---

## Rule Count by Category

| Category | Count | Avg Conditions/Rule | WM Operations |
|----------|------:|--------------------:|---------------|
| INFRA    |    21 | 2.0 | insert / retract / modify |
| DERIVE   |     4 | 3.0 | insert / modify |
| ALERT    |    28 | 1.5 | channel send only |
| CEP      |     9 | 2.5 | modify / channel send |
| JOIN     |     8 | 3.2 | channel send / modify |
| FSM      |     4 | 2.5 | modify |
| CHAIN    |    16 | 2.8 | insert / modify / channel send |
| **TOTAL**| **103** | | |

> CEP rules: UPD_TradeRate1s, UPD_LiqCount10s, C21_TopJumpNoTrades, M83–M87

---

## Section-by-Section Breakdown

### INFRA — Infrastructure Rules (21)

#### Bootstrap (12) — salience 1000
Insert initial singleton state facts at session start.

| Rule | Inserts |
|------|---------|
| `BOOTSTRAP_ModeState` | `ModeState` |
| `BOOTSTRAP_FeedHealth` | `FeedHealth` |
| `BOOTSTRAP_DepthState` | `DepthState` |
| `BOOTSTRAP_TradeStats` | `TradeStats` |
| `BOOTSTRAP_VolState` | `VolState` |
| `BOOTSTRAP_LiquidationStats` | `LiquidationStats` |
| `BOOTSTRAP_TradeSweepImpact` | `TradeSweepImpact` |
| `BOOTSTRAP_MicrostructureStress` | `MicrostructureStress` |
| `BOOTSTRAP_SpreadVelocityState` | `SpreadVelocityState` |
| `BOOTSTRAP_MicroVolatilityRisk` | `MicroVolatilityRisk` |
| `BOOTSTRAP_MarkDivergencePulsar` | `MarkDivergencePulsar` |
| `BOOTSTRAP_DislocationEscalation` | `DislocationEscalation` |

#### Cleanup (4) — salience -1000
Retract consumed event tickets after processing.

| Rule | Retracts |
|------|----------|
| `CLEANUP_RetractProcessedEvent` | `MarketEvent` |
| `CLEANUP_RetractSignificantTrade` | `SignificantTrade` |
| `CLEANUP_RetractDepthUpdateTick` | `DepthUpdateTick` |
| `CLEANUP_RetractMarkPriceTick` | `MarkPriceTick` |

#### Timestamp Updates (5)
Update `FeedHealth.lastSeenMs` per stream type for staleness detection.

| Rule |
|------|
| `UPD_LastSeen_Heartbeat` |
| `UPD_LastSeen_Depth` |
| `UPD_LastSeen_Trade` |
| `UPD_LastSeen_Mark` |
| `UPD_LastSeen_Index` |

---

### Section A — Data Quality / Validation (8 ALERT rules)
Validate incoming `MarketEvent` fields; fire on malformed data.

`A01_MissingRequiredFields`, `A02_InvalidNumerics`, `A03_TimestampSkewBound`,
`A04_SymbolAllowlist`, `A05_MonotonicPerStreamDetect`, `A06_PriceQtyPrecisionBounds`,
`A07_DecodeErrorsQuarantine`, `A08_UnexpectedMessageType`

---

### Section B — Feed Health / Staleness (10 rules: ALERT + INFRA)
Detect cross-stream anomalies using `FeedHealth` timestamps.

`B09_HeartbeatMissing`, `B10_ReconnectStorm`, `B11_StreamSilenceButOpen`,
`B12_TradeActiveBookSilent`, `B13_BookActiveTradeSilent`, `B14_StaleMark`,
`B15_StaleIndex`, `B16_LateEventRateHigh`, `B17_OutOfOrderBurst`,
`B18_PersistentBookGaps`

---

### Section C — Cross-field Sanity Checks (5 rules: ALERT + CEP)
Validate structural consistency of order book data.

| Rule | Category | Notes |
|------|----------|-------|
| `C19_CrossedBook` | ALERT | Bid > Ask |
| `C20_NegativeOrZeroSpreadPersistent` | ALERT | Spread <= 0 |
| `C21_TopJumpNoTrades` | **CEP** | Negation: price jump with no trades in interval |
| `C25_BookAgeStale` | ALERT | Depth not updated |
| `C26_BookSequenceDiscontinuityUnrecovered` | ALERT | Gap in book sequence |

---

### Section D — Trade Anomaly / Rate Rules (4 rules: ALERT + CEP)

| Rule | Category | Notes |
|------|----------|-------|
| `D27_TradePriceOutOfBandVsMid` | ALERT | Trade far from mid |
| `D28_TradeSizeOutlier` | ALERT | Outlier trade size |
| `D29_TradeTimestampRegression` | ALERT | Late/OOO trade |
| `UPD_TradeRate1s` | **CEP** | `accumulate over window:time(1s)` |
| `D30_TradeRateTiering` | DERIVE | Tiers from trade rate |
| `D32_TradesWhileBookStale` | JOIN | Cross-stream trade/book join |

---

### Section E — Spread, Depth & Liquidity (10 rules: DERIVE + ALERT + JOIN)

| Rule | Category |
|------|----------|
| `E33_SpreadComputeAndTier_New` | DERIVE |
| `E33_SpreadComputeAndTier_Update` | DERIVE |
| `E34_DepthTiering` | DERIVE |
| `E35_DepthCollapse` | ALERT |
| `E36_SpreadBlowout` | ALERT |
| `E37_PersistentThinLiquidity` | ALERT |
| `E38_ImbalanceComputeTier_New` | DERIVE |
| `E38_ImbalanceComputeTier_Update` | DERIVE |
| `E39_ImbalancePersistence` | ALERT |
| `E40_ImbalanceFlipFlop` | ALERT |
| `E41_LiquidityStressCombine` | JOIN |

---

### Section F — Volatility & Regime (6 rules: DERIVE + ALERT + JOIN)

`F43_VolTiering` (DERIVE), `F47_VolSpike` (ALERT), `F48_VolPersistenceHigh` (ALERT),
`F50_BookDrivenMove` (JOIN), `F51_RegimeShiftToSafe` (FSM), `F52_RegimeNormalizationEligibility` (JOIN)

---

### Section G — Mark/Index Dislocation (8 rules: DERIVE + ALERT)

`G53_MarkIndexDivergence_New` (DERIVE), `G53_MarkIndexDivergence_Update` (DERIVE),
`G54_ComputeMarkMidDivergence` (DERIVE), `G55_DislocationPersistence` (ALERT),
`G56_DislocationPlusThinBook` (JOIN), `G58_IndexStaleButMarkMoving` (ALERT),
`G59_MarkStaleButMarketActive` (ALERT), `G60_SuddenDivergenceReversalFlag` (ALERT)

---

### Section H — Liquidation & Cascade Stress (3 rules: CEP + ALERT)

| Rule | Category | Notes |
|------|----------|-------|
| `UPD_LiqCount10s` | **CEP** | `accumulate over window:time(10s)` |
| `H61_LiqTiering` | DERIVE | Tiers from liq count |
| `H62_LiqBurstJump` | ALERT | Liq burst detection |
| `H66_CascadePersistenceEscalate` | ALERT | Sustained cascade |
| `H67_CascadeCooldownEligibility` | JOIN | Multi-condition cooldown |

---

### Section I — Mode / Kill-switch (2 rules: FSM)

`I68_EnterThrottled_OnDegraded`, `I70_EnterHalted_KillSwitchLatch`

#### Recovery Rules (2 rules: FSM)

`RECOVERY_ExitSafeToThrottled`, `RECOVERY_ExitThrottledToNormal`

---

### Section J — Forward Chain: Trade Significance (4 rules: CHAIN)
4-level explicit forward chain on `SignificantTrade` → `TradeSweepImpact` →
`MicrostructureStress` → signal.

| Rule | Depth | Inserts/Modifies |
|------|------:|-----------------|
| `J71_Alpha_SignificantTrade` | 1 | inserts `SignificantTrade` |
| `J72_Beta_ComputeSweepImpact` | 2 | modifies `TradeSweepImpact` |
| `J73_Beta_AssessMicrostructureStress` | 3 | modifies `MicrostructureStress` |
| `J74_Beta_EmitStressSignal` | 4 | channel send |

---

### Section K — Forward Chain: Depth/Spread Velocity (4 rules: CHAIN)
4-level chain on `DepthUpdateTick` → `SpreadVelocityState` → `MicroVolatilityRisk` → signal.

| Rule | Depth |
|------|------:|
| `K75_Alpha_DepthUpdate` | 1 |
| `K76_Beta_SpreadVelocity` | 2 |
| `K77_Beta_AssessMicroVolRisk` | 3 |
| `K78_Beta_EmitMicroVolSignal` | 4 |

---

### Section L — Forward Chain: Mark Price Dislocation (4 rules: CHAIN)
4-level chain on `MarkPriceTick` → `MarkDivergencePulsar` → `DislocationEscalation` → signal.

| Rule | Depth |
|------|------:|
| `L79_Alpha_MarkPriceUpdate` | 1 |
| `L80_Beta_MarkDivergence` | 2 |
| `L81_Beta_AssessDislocation` | 3 |
| `L82_Beta_EmitDislocationSignal` | 4 |

---

### Section M — Temporal CEP Patterns (5 rules: CEP) ← *added for RuleML+RR*
Rules exercising Drools temporal operators absent from sections A-L.

| Rule | Temporal Operator | Window / Condition |
|------|-------------------|--------------------|
| `M83_SpreadReactionAfterSignificantTrade` | eval-temporal join | Spread widens within 5s of trade |
| `M84_DepthCollapseAfterSpreadBlowout` | eval-temporal sequence | Depth collapses within 10s of spread blowout |
| `M85_TradeBurstCountWindow` | `accumulate over window:length(20)` | ≥5 significant trades in last 20 events |
| `M86_PersistentMarkDislocationNoRecovery` | temporal negation | Dislocation with no LOW recovery |
| `M87_CompoundSystemicRisk` | multi-chain join | J-chain + K-chain outputs within 10s |

---

## Conditions-per-Rule Distribution

| Conditions | Rule Count | Examples |
|:----------:|:---------:|---------|
| 1 | ~22 | Most ALERT rules (single state check) |
| 2 | ~35 | Most INFRA, simple JOIN rules |
| 3 | ~30 | Most CHAIN, complex ALERT rules |
| 4+ | ~16 | `E41_LiquidityStressCombine`, `G56`, `M85`, `M87` |

**Average:** ~2.3 conditions/rule  
**Maximum:** 5 conditions (`E41_LiquidityStressCombine`)

---

## Event Type Distribution

Benchmarks use `MarketEvent` discriminated by `eventType` field:

| eventType | Typical % of stream | Consumer Rules |
|-----------|-------------------:|----------------|
| `DEPTH`   | ~45% | Sections B, C, E, K |
| `TRADE`   | ~40% | Sections B, D, J |
| `MARK`    | ~10% | Sections B, G, L |
| `INDEX`   | ~3%  | Section G |
| `LIQ`     | ~1%  | Section H |
| `HEARTBEAT` | ~1% | Section B |
| `DECODE_ERROR` | <0.1% | Section A |

---

## Key Structural Properties

| Property | Value |
|----------|-------|
| Total rules | **103** |
| Active CEP rules (windows/temporal) | **7** (UPD_TradeRate1s, UPD_LiqCount10s, C21, M83–M87) |
| Max forward-chaining depth | **4** (sections J, K, L) |
| Distinct fact types (Java) | **13** model classes |
| Distinct fact types (DRL-declared) | **9** (SignificantTrade, DepthUpdateTick, etc.) |
| `@expires` annotations | **4** (MarketEvent: 60s; ticks: 1s) |
| Rules with `modify` (WM update) | **~30** |
| Rules with `insert` (new facts) | **~16** |
| Rules with `retract` (WM cleanup) | **4** |
| Rules emitting to `channels["alerts"]` | **~55** |
