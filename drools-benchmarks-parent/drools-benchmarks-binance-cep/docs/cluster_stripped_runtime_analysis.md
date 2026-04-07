# Cluster Runtime Analysis — Verified Infomap Partition (I69 Removed)

> **Infomap re-run:** `./Infomap binance_rule_graph.net . --clu --ftree` (2026-04-06)  
> **Change:** Removed synthetic rule `I69_EnterSafe_OnLiquidityStress` from dependency graph  
> **Result:** 5 top modules (down from 6), codelength 2.049 bits, 44.8% savings  
> **ftree:** [binance_rule_graph_no_i69.ftree](file:///home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/clusters/binance_rule_graph_no_i69.ftree)

---

## Summary

| Cluster | Name | Rules | Self-Contained? |
|:-------:|------|:-----:|:---------------:|
| **1** | Feed Health & Mode Transitions | **11** | ✅ Yes |
| **2** | Depth / Spread / Micro-Volatility | **28** | ✅ Yes |
| **3** | Trade Alpha | **2** | ✅ Yes |
| **4** | Liquidation Monitoring | **3** | ✅ Yes |
| **5** | Trade Rate | **2** | ✅ Yes |
| | **Total** | **46** | **Zero cross-cluster deps** |

> [!IMPORTANT]
> **All 5 clusters are fully independent.** Each cluster's consumed facts are either externally injected (MarketEvent, RiskConfig) or produced internally. No cluster needs another cluster's output.

---

## What Changed: Old Cluster 4 Dissolved

I69 was the sole bridge node connecting two disconnected subgraphs inside old Cluster 4. Without it, Infomap naturally absorbed each subgraph into its parent cluster:

```
OLD (6 clusters)                        NEW (5 clusters)
─────────────────                       ─────────────────
Cluster 4:                              
  BBA_New ─────────────────────────→  Cluster 2 (path 2:4)
  E33_Spread_New ──────────────────→  Cluster 2 (path 2:4)
  I68_EnterThrottled ──────────────→  Cluster 1 (path 1:2:4)
  I69_EnterSafe ───────────────────→  REMOVED (synthetic)
  RECOVERY_ExitThrottled ──────────→  Cluster 1 (path 1:2:3)
```

---

## Cluster 1 — Feed Health & Mode Transitions (11 rules)

Contains all `FeedHealth` god object rules plus the `ModeState` transition rules that were absorbed from old Cluster 4.

| # | Path | Rule | Inputs | Outputs |
|---|------|------|--------|---------|
| 1 | 1:1:1 | `B12_TradeActiveBookSilent` | FeedHealth, RiskConfig | FeedHealth |
| 2 | 1:1:2 | `UPD_LastSeen_Trade` | MarketEvent, FeedHealth | FeedHealth |
| 3 | 1:1:3 | `D32_TradesWhileBookStale` | MarketEvent, FeedHealth | RiskSignal |
| 4 | 1:2:1 | `UPD_LastSeen_Depth` | MarketEvent, FeedHealth | FeedHealth |
| 5 | 1:2:2 | `RECOVERY_ExitThrottledToNormal` | ModeState, FeedHealth, RiskConfig | ModeState |
| 6 | 1:2:3 | `B13_BookActiveTradeSilent` | FeedHealth, RiskConfig | FeedHealth |
| 7 | 1:2:4 | `I68_EnterThrottled_OnDegraded` | ModeState, FeedHealth | ModeState |
| 8 | 1:2:5 | `C25_BookAgeStale` | FeedHealth, RiskConfig | RiskSignal |
| 9 | 1:3:1 | `B14_StaleMark` | FeedHealth, RiskConfig | FeedHealth |
| 10 | 1:3:2 | `UPD_LastSeen_Mark` | MarketEvent, FeedHealth | FeedHealth |
| 11 | 1:3:3 | `G59_MarkStaleButMarketActive` | MarketEvent, FeedHealth | RiskSignal |

**Fact I/O:** IN: FeedHealth, ModeState, MarketEvent, RiskConfig → OUT: FeedHealth, ModeState, RiskSignal

> All consumed facts are either self-produced (FeedHealth ↔ modify, ModeState ↔ modify) or external (MarketEvent, RiskConfig). ✅ Self-contained.

---

## Cluster 2 — Depth / Spread / Micro-Volatility (28 rules)

The largest cluster. Contains the full depth/spread pipeline, micro-volatility forward chain (K75→K78), mark divergence chain (L79→L82), and the two absorbed bootstrap inserts from old Cluster 4.

| # | Path | Rule | Inputs | Outputs |
|---|------|------|--------|---------|
| 1 | 2:1:1 | `K77_Beta_AssessMicroVolRisk` | SpreadVelocityState, DepthState, MicroVolatilityRisk | MicroVolatilityRisk |
| 2 | 2:1:2 | `E33_SpreadComputeAndTier_Update` | BestBidAsk, SpreadState, RiskConfig | SpreadState |
| 3 | 2:1:3 | `K78_Beta_EmitMicroVolSignal` | MicroVolatilityRisk | RiskSignal, MicroVolatilityRisk |
| 4 | 2:1:4 | `E34_DepthTiering` | DepthState, RiskConfig | DepthState |
| 5 | 2:1:5 | `E41_LiquidityStressCombine` | SpreadState, DepthState | RiskSignal |
| 6 | 2:1:6 | `E36_SpreadBlowout` | SpreadState | RiskSignal |
| 7 | 2:1:7 | `BOOTSTRAP_MicroVolatilityRisk` | MicroVolatilityRisk, RiskConfig | MicroVolatilityRisk |
| 8 | 2:1:8 | `E35_DepthCollapse` | DepthState | RiskSignal |
| 9 | 2:2:1 | `K76_Beta_SpreadVelocity` | DepthUpdateTick, BestBidAsk, SpreadVelocityState | SpreadVelocityState |
| 10 | 2:2:2 | `DERIVE_BestBidAsk_Update` | MarketEvent, BestBidAsk | BestBidAsk |
| 11 | 2:2:3 | `K75_Alpha_DepthUpdate` | MarketEvent | DepthUpdateTick |
| 12 | 2:2:4 | `CLEANUP_RetractDepthUpdateTick` | DepthUpdateTick | DepthUpdateTick |
| 13 | 2:2:5 | `C21_TopJumpNoTrades` | MarketEvent, BestBidAsk, RiskConfig | RiskSignal |
| 14 | 2:2:6 | `BOOTSTRAP_SpreadVelocityState` | SpreadVelocityState, RiskConfig | SpreadVelocityState |
| 15 | 2:3:1:1 | `L81_Beta_AssessDislocation` | MarkDivergencePulsar, VolState, DislocationEscalation | DislocationEscalation |
| 16 | 2:3:1:2 | `L82_Beta_EmitDislocationSignal` | DislocationEscalation | RiskSignal, DislocationEscalation |
| 17 | 2:3:1:3 | `F43_VolTiering` | VolState, RiskConfig | VolState |
| 18 | 2:3:1:4 | `BOOTSTRAP_DislocationEscalation` | DislocationEscalation, RiskConfig | DislocationEscalation |
| 19 | 2:3:2:1 | `L80_Beta_MarkDivergence` | MarkPriceTick, BestBidAsk, MarkDivergencePulsar | MarkDivergencePulsar |
| 20 | 2:3:2:2 | `L79_Alpha_MarkPriceUpdate` | MarketEvent | MarkPriceTick |
| 21 | 2:3:2:3 | `CLEANUP_RetractMarkPriceTick` | MarkPriceTick | MarkPriceTick |
| 22 | 2:3:2:4 | `BOOTSTRAP_MarkDivergencePulsar` | MarkDivergencePulsar, RiskConfig | MarkDivergencePulsar |
| 23 | 2:3:3:1 | `BOOTSTRAP_VolState` | VolState, RiskConfig | VolState |
| 24 | 2:3:3:2 | `F52_RegimeNormalizationEligibility` | VolState | RiskSignal |
| 25 | 2:4:1 | `E33_SpreadComputeAndTier_New` | BestBidAsk, SpreadState, RiskConfig | SpreadState |
| 26 | 2:4:2 | `DERIVE_BestBidAsk_New` | MarketEvent, BestBidAsk | BestBidAsk |
| 27 | 2:5:1 | `BOOTSTRAP_DepthState` | DepthState, RiskConfig | DepthState |
| 28 | 2:5:2 | `E37_PersistentThinLiquidity` | DepthState | RiskSignal |

**Fact I/O:** IN: MarketEvent, RiskConfig + 10 internal state facts → OUT: 11 state facts + RiskSignal

> Every non-external fact consumed is also produced internally via `modify()` or `insert()`. ✅ Self-contained.

---

## Cluster 3 — Trade Alpha (2 rules)

| # | Path | Rule | Inputs | Outputs |
|---|------|------|--------|---------|
| 1 | 3:1 | `CLEANUP_RetractSignificantTrade` | SignificantTrade | SignificantTrade |
| 2 | 3:2 | `J71_Alpha_SignificantTrade` | MarketEvent | SignificantTrade |

**Fact I/O:** IN: MarketEvent → OUT: SignificantTrade. ✅ Self-contained.

---

## Cluster 4 — Liquidation Monitoring (3 rules)

| # | Path | Rule | Inputs | Outputs |
|---|------|------|--------|---------|
| 1 | 4:1 | `BOOTSTRAP_LiquidationStats` | LiquidationStats, RiskConfig | LiquidationStats |
| 2 | 4:2 | `H61_LiqTiering` | LiquidationStats, RiskConfig | LiquidationStats |
| 3 | 4:3 | `H67_CascadeCooldownEligibility` | LiquidationStats | RiskSignal |

**Fact I/O:** IN: LiquidationStats, RiskConfig → OUT: LiquidationStats, RiskSignal. ✅ Self-contained.

---

## Cluster 5 — Trade Rate (2 rules)

| # | Path | Rule | Inputs | Outputs |
|---|------|------|--------|---------|
| 1 | 5:1 | `BOOTSTRAP_TradeStats` | TradeStats, RiskConfig | TradeStats |
| 2 | 5:2 | `D30_TradeRateTiering` | TradeStats, RiskConfig | TradeStats |

**Fact I/O:** IN: TradeStats, RiskConfig → OUT: TradeStats. ✅ Self-contained.

---

## Cross-Cluster Independence Matrix

| | C1 (11) | C2 (28) | C3 (2) | C4 (3) | C5 (2) |
|---|:---:|:---:|:---:|:---:|:---:|
| **C1** | — | ✅ | ✅ | ✅ | ✅ |
| **C2** | ✅ | — | ✅ | ✅ | ✅ |
| **C3** | ✅ | ✅ | — | ✅ | ✅ |
| **C4** | ✅ | ✅ | ✅ | — | ✅ |
| **C5** | ✅ | ✅ | ✅ | ✅ | — |

> [!IMPORTANT]
> **Zero cross-cluster dependencies confirmed.** The only shared facts across sessions are the externally-injected `MarketEvent` (from orchestrator) and `RiskConfig` (bootstrapped per symbol at session creation).

---

## Unclustered Rules (Fallback Candidates)

The ftree covers **46 of the 109 rules** (after removing I69) in `taxonomy.drl`. The remaining **63 rules** are not in any Infomap cluster:

- **Section A:** Ingestion/validation (A01–A08)
- **Section B:** B10, B11, B15–B18 (non-FeedHealth-modify feed rules)
- **Section C:** C19–C24, C26 (order book validation)
- **Section D:** D27–D29, D31 (trade tape sanity)
- **Section E:** E38–E40, E42 (imbalance, market impact)
- **Section F:** F44–F51 (volatility signals)
- **Section G:** G53–G57, G60 (mark-index divergence)
- **Section H:** H62–H66 (liquidation signals)
- **Section I:** I70 (kill-switch latch)
- **Section J:** J72–J74 (trade sweep impact chain)
- **Bootstraps:** TradeSweepImpact, MicrostructureStress, ModeState, FeedHealth
- **Cleanup:** CLEANUP_RetractProcessedEvent
- **Recovery:** RECOVERY_ExitSafeToThrottled

> [!NOTE]
> These rules were not captured by Infomap because they had low or zero edge weight in the runtime dependency graph — either they never fired during the trace, or they were generic stateless filters that don't participate in dense fact-flow clusters.
