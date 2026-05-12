# Cluster Runtime Analysis — Full Distribution (4 Clusters)

> **Base:** Verified Infomap partition (I69 removed, 2026-04-06)
> **Updates:** Merged C3 (Trade Alpha) into C2. Added `UPD_LiqCount10s`, `UPD_TradeRate1s`. Distributed all fallback rules. Deleted 7 dead + 6 cross-cluster rules.

---

## Summary

| Cluster | Name | Cluster-Specific | Generic (shared) | Total |
|:-------:|------|:----------------:|:-----------------:|:-----:|
| **1** | Feed Health & Mode Transitions | **26** | 10 | **36** |
| **2** | Market Microstructure (Depth/Spread/Vol/Trade Alpha) | **52** | 10 | **62** |
| **3** | Liquidation Monitoring | **6** | 10 | **16** |
| **4** | Trade Rate | **3** | 10 | **13** |
| | **Total unique rules** | **87** | 10 | **97** |

> [!IMPORTANT]
> **All 4 clusters are fully independent — zero cross-cluster dependencies.** Generic validation rules are duplicated per session.

---

## Generic Rules (Duplicated in ALL Clusters)

10 stateless validation/cleanup rules — no cluster-specific state.

| # | Rule | Action |
|---|------|--------|
| 1 | `A01_MissingRequiredFields` | Retract invalid events |
| 2 | `A02_InvalidNumerics` | Retract NaN/Inf |
| 3 | `A03_TimestampSkewBound` | Emit RiskSignal |
| 4 | `A04_SymbolAllowlist` | Retract unknown symbols |
| 5 | `A06_PriceQtyPrecisionBounds` | Emit RiskSignal |
| 6 | `A07_DecodeErrorsQuarantine` | Retract decode errors |
| 7 | `A08_UnexpectedMessageType` | Retract unknown types |
| 8 | `B10_ReconnectStorm` | Retract reconnect events |
| 9 | `B16_LateEventRateHigh` | Emit RiskSignal |
| 10 | `CLEANUP_RetractProcessedEvent` | Retract non-LIQ/TRADE events |

---

## Cluster 1 — Feed Health & Mode Transitions (26 rules)

| # | Rule | Inputs | Outputs | New? |
|---|------|--------|---------|:----:|
| 1 | `B12_TradeActiveBookSilent` | FeedHealth, RiskConfig | FeedHealth | |
| 2 | `UPD_LastSeen_Trade` | MarketEvent, FeedHealth | FeedHealth | |
| 3 | `D32_TradesWhileBookStale` | MarketEvent, FeedHealth | RiskSignal | |
| 4 | `UPD_LastSeen_Depth` | MarketEvent, FeedHealth | FeedHealth | |
| 5 | `RECOVERY_ExitThrottledToNormal` | ModeState, FeedHealth, RiskConfig | ModeState | |
| 6 | `B13_BookActiveTradeSilent` | FeedHealth, RiskConfig | FeedHealth | |
| 7 | `I68_EnterThrottled_OnDegraded` | ModeState, FeedHealth | ModeState | |
| 8 | `C25_BookAgeStale` | FeedHealth, RiskConfig | RiskSignal | |
| 9 | `B14_StaleMark` | FeedHealth, RiskConfig | FeedHealth | |
| 10 | `UPD_LastSeen_Mark` | MarketEvent, FeedHealth | FeedHealth | |
| 11 | `G59_MarkStaleButMarketActive` | MarketEvent, FeedHealth | RiskSignal | |
| 12 | `BOOTSTRAP_ModeState` | RiskConfig | ModeState | ✅ |
| 13 | `BOOTSTRAP_FeedHealth` | RiskConfig | FeedHealth | ✅ |
| 14 | `A05_MonotonicPerStreamDetect` | FeedHealth, MarketEvent | FeedHealth | ✅ |
| 15 | `B09_HeartbeatMissing` | RiskConfig, FeedHealth | FeedHealth | ✅ |
| 16 | `B11_StreamSilenceButOpen` | RiskConfig, FeedHealth | RiskSignal | ✅ |
| 17 | `B15_StaleIndex` | RiskConfig, FeedHealth | FeedHealth | ✅ |
| 18 | `B17_OutOfOrderBurst` | RiskConfig, FeedHealth | FeedHealth | ✅ |
| 19 | `B18_PersistentBookGaps` | FeedHealth | RiskSignal | ✅ |
| 20 | `C26_BookSeqDiscontinuityUnrecovered` | FeedHealth | FeedHealth | ✅ |
| 21 | `D29_TradeTimestampRegression` | FeedHealth, MarketEvent | FeedHealth | ✅ |
| 22 | `UPD_LastSeen_Heartbeat` | MarketEvent, FeedHealth | FeedHealth | ✅ |
| 23 | `UPD_LastSeen_Index` | MarketEvent, FeedHealth | FeedHealth | ✅ |
| 24 | `G58_IndexStaleButMarkMoving` | FeedHealth, MarketEvent | RiskSignal | ✅ |
| 25 | `I70_EnterHalted_KillSwitchLatch` | ModeState, FeedHealth | ModeState | ✅ |
| 26 | `RECOVERY_ExitSafeToThrottled` | RiskConfig, ModeState | ModeState | ✅ |

**Fact I/O:** IN: FeedHealth, ModeState, MarketEvent, RiskConfig → OUT: FeedHealth, ModeState, RiskSignal. ✅ Self-contained.

---

## Cluster 2 — Market Microstructure (52 rules)

Merged original C2 (Depth/Spread/MicroVol, 28 rules) + C3 (Trade Alpha, 2 rules) + 22 absorbed fallback rules. Merging C3 eliminates the `SignificantTrade` cross-cluster dependency.

| # | Rule | Inputs | Outputs | Source |
|---|------|--------|---------|:------:|
| 1 | `K77_Beta_AssessMicroVolRisk` | SpreadVelocityState, DepthState, MicroVolatilityRisk | MicroVolatilityRisk | C2 |
| 2 | `E33_SpreadComputeAndTier_Update` | BestBidAsk, SpreadState, RiskConfig | SpreadState | C2 |
| 3 | `K78_Beta_EmitMicroVolSignal` | MicroVolatilityRisk | RiskSignal, MicroVolatilityRisk | C2 |
| 4 | `E34_DepthTiering` | DepthState, RiskConfig | DepthState | C2 |
| 5 | `E41_LiquidityStressCombine` | SpreadState, DepthState | RiskSignal | C2 |
| 6 | `E36_SpreadBlowout` | SpreadState | RiskSignal | C2 |
| 7 | `BOOTSTRAP_MicroVolatilityRisk` | RiskConfig | MicroVolatilityRisk | C2 |
| 8 | `E35_DepthCollapse` | DepthState | RiskSignal | C2 |
| 9 | `K76_Beta_SpreadVelocity` | DepthUpdateTick, BestBidAsk, SpreadVelocityState | SpreadVelocityState | C2 |
| 10 | `DERIVE_BestBidAsk_Update` | MarketEvent, BestBidAsk | BestBidAsk | C2 |
| 11 | `K75_Alpha_DepthUpdate` | MarketEvent | DepthUpdateTick | C2 |
| 12 | `CLEANUP_RetractDepthUpdateTick` | DepthUpdateTick | DepthUpdateTick | C2 |
| 13 | `C21_TopJumpNoTrades` | MarketEvent, BestBidAsk, RiskConfig | RiskSignal | C2 |
| 14 | `BOOTSTRAP_SpreadVelocityState` | RiskConfig | SpreadVelocityState | C2 |
| 15 | `L81_Beta_AssessDislocation` | MarkDivergencePulsar, VolState, DislocationEscalation | DislocationEscalation | C2 |
| 16 | `L82_Beta_EmitDislocationSignal` | DislocationEscalation | RiskSignal, DislocationEscalation | C2 |
| 17 | `F43_VolTiering` | VolState, RiskConfig | VolState | C2 |
| 18 | `BOOTSTRAP_DislocationEscalation` | RiskConfig | DislocationEscalation | C2 |
| 19 | `L80_Beta_MarkDivergence` | MarkPriceTick, BestBidAsk, MarkDivergencePulsar | MarkDivergencePulsar | C2 |
| 20 | `L79_Alpha_MarkPriceUpdate` | MarketEvent | MarkPriceTick | C2 |
| 21 | `CLEANUP_RetractMarkPriceTick` | MarkPriceTick | MarkPriceTick | C2 |
| 22 | `BOOTSTRAP_MarkDivergencePulsar` | RiskConfig | MarkDivergencePulsar | C2 |
| 23 | `BOOTSTRAP_VolState` | RiskConfig | VolState | C2 |
| 24 | `F52_RegimeNormalizationEligibility` | VolState | RiskSignal | C2 |
| 25 | `E33_SpreadComputeAndTier_New` | BestBidAsk, SpreadState, RiskConfig | SpreadState | C2 |
| 26 | `DERIVE_BestBidAsk_New` | MarketEvent | BestBidAsk | C2 |
| 27 | `BOOTSTRAP_DepthState` | RiskConfig | DepthState | C2 |
| 28 | `E37_PersistentThinLiquidity` | DepthState | RiskSignal | C2 |
| 29 | `J71_Alpha_SignificantTrade` | MarketEvent | SignificantTrade | C3 |
| 30 | `CLEANUP_RetractSignificantTrade` | SignificantTrade | SignificantTrade | C3 |
| 31 | `C19_CrossedBook` | MarketEvent | RiskSignal | new |
| 32 | `C20_NegOrZeroSpreadPersistent` | BestBidAsk | RiskSignal | new |
| 33 | `D27_TradePriceOutOfBandVsMid` | RiskConfig, BestBidAsk, MarketEvent | RiskSignal | new |
| 34 | `D28_TradeSizeOutlier` | MarketEvent | RiskSignal | new |
| 35 | `E38_ImbalanceComputeTier_New` | DepthState | ImbalanceState | new |
| 36 | `E38_ImbalanceComputeTier_Update` | DepthState, ImbalanceState | ImbalanceState | new |
| 37 | `E39_ImbalancePersistence` | ImbalanceState | RiskSignal | new |
| 38 | `E40_ImbalanceFlipFlop` | ImbalanceState | RiskSignal | new |
| 39 | `F47_VolSpike` | VolState | RiskSignal | new |
| 40 | `F48_VolPersistenceHigh` | VolState | RiskSignal | new |
| 41 | `F50_BookDrivenMove` | SpreadState, DepthState, VolState | RiskSignal | new |
| 42 | `F51_RegimeShiftToSafe` | VolState | RiskSignal | new |
| 43 | `G53_MarkIndexDivergence_New` | RiskConfig, BestBidAsk, MarketEvent | MarkIndexState | new |
| 44 | `G53_MarkIndexDivergence_Update` | RiskConfig, BestBidAsk, MarketEvent, MarkIndexState | MarkIndexState | new |
| 45 | `G54_ComputeMarkMidDivergence` | MarkIndexState | RiskSignal | new |
| 46 | `G55_DislocationPersistence` | RiskConfig, MarkIndexState | RiskSignal | new |
| 47 | `G56_DislocationPlusThinBook` | MarkIndexState, DepthState | RiskSignal | new |
| 48 | `G60_SuddenDivReversalFlag` | MarkIndexState | RiskSignal | new |
| 49 | `BOOTSTRAP_TradeSweepImpact` | RiskConfig | TradeSweepImpact | new |
| 50 | `BOOTSTRAP_MicrostructureStress` | RiskConfig | MicrostructureStress | new |
| 51 | `J72_Beta_ComputeSweepImpact` | SignificantTrade, BestBidAsk, TradeSweepImpact | TradeSweepImpact | new |
| 52 | `J73_Beta_AssessMicroStress` | TradeSweepImpact, SpreadState, MicrostructureStress | MicrostructureStress | new |
| 53 | `J74_Beta_EmitStressSignal` | MicrostructureStress | RiskSignal, MicrostructureStress | new |

**Fact I/O:** IN: MarketEvent, RiskConfig + 14 internal state facts → OUT: 15 state facts + RiskSignal. ✅ Self-contained.

---

## Cluster 3 — Liquidation Monitoring (6 rules)

| # | Rule | Inputs | Outputs | New? |
|---|------|--------|---------|:----:|
| 1 | `BOOTSTRAP_LiquidationStats` | RiskConfig | LiquidationStats | |
| 2 | `UPD_LiqCount10s` | LiquidationStats, MarketEvent(LIQ) | LiquidationStats | ✅ |
| 3 | `H61_LiqTiering` | LiquidationStats, RiskConfig | LiquidationStats | |
| 4 | `H62_LiqBurstJump` | RiskConfig, LiquidationStats | RiskSignal | ✅ |
| 5 | `H66_CascadePersistenceEscalate` | LiquidationStats | RiskSignal | ✅ |
| 6 | `H67_CascadeCooldownEligibility` | LiquidationStats | RiskSignal | |

**Fact I/O:** IN: MarketEvent(LIQ), RiskConfig → OUT: LiquidationStats, RiskSignal. ✅ Self-contained.

---

## Cluster 4 — Trade Rate (3 rules)

| # | Rule | Inputs | Outputs | New? |
|---|------|--------|---------|:----:|
| 1 | `BOOTSTRAP_TradeStats` | RiskConfig | TradeStats | |
| 2 | `UPD_TradeRate1s` | TradeStats, MarketEvent(TRADE) | TradeStats | ✅ |
| 3 | `D30_TradeRateTiering` | TradeStats, RiskConfig | TradeStats | |

**Fact I/O:** IN: MarketEvent(TRADE), RiskConfig → OUT: TradeStats. ✅ Self-contained.

---

## Cross-Cluster Independence Matrix

| | C1 (36) | C2 (62) | C3 (16) | C4 (13) |
|---|:---:|:---:|:---:|:---:|
| **C1** | — | ✅ | ✅ | ✅ |
| **C2** | ✅ | — | ✅ | ✅ |
| **C3** | ✅ | ✅ | — | ✅ |
| **C4** | ✅ | ✅ | ✅ | — |

> **Zero cross-cluster dependencies confirmed.**

---

## Event Routing Table

| Event Type | C1 | C2 | C3 | C4 |
|------------|:--:|:--:|:--:|:--:|
| **DEPTH** | ✅ | ✅ | | |
| **TRADE** | ✅ | ✅ | | ✅ |
| **MARK** | ✅ | ✅ | | |
| **INDEX** | ✅ | ✅ | | |
| **HEARTBEAT** | ✅ | | | |
| **LIQ** | | | ✅ | |

---

## Deleted Rules

**Cross-cluster (6):** `E42_MarketImpactRiskCombine`, `F49_TradeDrivenMove`, `G57_DislocationPlusTradeBurst`, `H63_LiqPlusVolSpike`, `H64_LiqPlusDepthCollapse`, `H65_LiqPlusDislocation`

**Dead (7):** `C22_TopSizesInvalid`, `C23_BookIncompletePartialDepth`, `C24_ExcessiveDepthUpdateRate`, `D31_LargeTradeCluster`, `F44_PriceJump`, `F45_Whipsaw`, `F46_SustainedTrend`
