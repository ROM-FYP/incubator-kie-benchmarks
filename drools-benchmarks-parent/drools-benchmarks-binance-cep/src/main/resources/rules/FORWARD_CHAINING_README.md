# Forward Chaining Architecture in Binance CEP

This document explains the advanced **4-level forward chaining** mechanics injected into the `taxonomy.drl` benchmark ruleset. These sections evaluate `MarketEvent`s (specifically `TRADE`, `DEPTH`, and `MARK`) through a multi-stage Rete network before emitting actionable feature signals. 

These rules simulate realistic computational complexities found in modern HFT risk engines.

## Key Design Principles

1. **Forward Chaining Depth**: Events trigger subsequent rules through exactly 4 stages: `Alpha Filter -> Delta Calculation -> Stress Assessment -> Emitting Flag`.
2. **Memory Safe Lifecycle**: Temporary alpha proxy events (e.g., `SignificantTrade`, `DepthUpdateTick`, `MarkPriceTick`) are immediately manually retracted after downstream processing or naturally expire (`@expires(1s)`). This guarantees a constant and optimized memory footprint during intensive `JMH` throughput benchmarks over large datasets.
3. **State Singletons**: Intermediate feature aggregators (e.g., `TradeSweepImpact`, `MicrostructureStress`, `SpreadVelocityState`, `MarkDivergencePulsar`) are declared as per-symbol singletons initialized by rule bootstraps. State updates exclusively use the `modify($fact)` block to surgically notify the Drools RETE engine of changes without costly re-insertions.

## Implemented Rule Chains

### 1. Section J: Significant Trade Sweep (TRADE)
Evaluates the immediate price impact of high-volume trades against the resting order book's mid-price.
- **Level 1 (Alpha)**: Validates `MarketEvent(TRADE)` volume thresholds -> Inserts `SignificantTrade`.
- **Level 2 (Beta Join)**: Joins with `BestBidAsk` -> Computes basis point impact -> Updates `TradeSweepImpact`.
- **Level 3 (Beta Join)**: Joins with `SpreadState` -> Elevates `MicrostructureStress` state to `ELEVATED`.
- **Level 4 (Signal)**: Matches `ELEVATED` state -> Emits `J74_MicrostructureStress` `RiskSignal` and resets state.

### 2. Section K: Micro-Volatility Burst (DEPTH)
Detects rapid sequential order book thrashing and assesses micro-spread velocity changes.
- **Level 1 (Alpha)**: Validates `MarketEvent(DEPTH)` boundaries -> Inserts `DepthUpdateTick`.
- **Level 2 (Beta Join)**: Joins with `BestBidAsk` -> Calculates derivative velocity -> Updates `SpreadVelocityState`.
- **Level 3 (Beta Join)**: Joins with `DepthState` -> Activates `MicroVolatilityRisk`.
- **Level 4 (Signal)**: Matches `ACTIVE` state -> Emits `K78_MicroVolatilityBurst` `RiskSignal` and resets state.

### 3. Section L: Mark Price Dislocation (MARK)
Watches the incoming Binance synthetic Mark Price and evaluates basis-point divergence against our local top-of-book Mid Price to flag systemic routing dangers.
- **Level 1 (Alpha)**: Validates `MarketEvent(MARK)` -> Inserts `MarkPriceTick`.
- **Level 2 (Beta Join)**: Joins with `BestBidAsk` -> Computes divergence variance -> Updates `MarkDivergencePulsar`.
- **Level 3 (Beta Join)**: Joins with `VolState` -> Triggers `DislocationEscalation`.
- **Level 4 (Signal)**: Matches `TRIGGERED` state -> Emits `L82_MarkDislocationDanger` `RiskSignal` and resets state.
