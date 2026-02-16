# Rule Taxonomy Documentation

## Overview
This document describes the 70-rule taxonomy for Binance market data CEP benchmarking.

---

## Rule Categories

### 1. Price Movement Rules (20 rules)

#### 1.1 Price Spike Detection
- **Rule**: Detect X% price increase in Y seconds
- **Variations**: 1%, 2%, 3%, 5%, 10% thresholds
- **Time Windows**: 5s, 10s, 30s, 1m

#### 1.2 Price Drop Detection
- **Rule**: Detect X% price decrease in Y seconds
- **Variations**: Similar to spike detection

#### 1.3 Rapid Price Changes
- **Rule**: Multiple consecutive price movements
- **Patterns**: 3, 5, 7 consecutive increases/decreases

---

### 2. Volume Anomaly Rules (15 rules)

#### 2.1 Volume Surge
- **Rule**: Volume exceeds X times the moving average
- **Variations**: 2x, 3x, 5x, 10x average

#### 2.2 Volume Accumulation
- **Rule**: Sustained high volume over time window
- **Windows**: 1m, 5m, 15m

#### 2.3 Volume-Price Divergence
- **Rule**: Price up but volume down (or vice versa)

---

### 3. Temporal Pattern Rules (15 rules)

#### 3.1 Consecutive Movements
- **Rule**: N consecutive price increases/decreases
- **Variations**: 3, 5, 7 consecutive movements

#### 3.2 Alternating Patterns
- **Rule**: Up-down-up or down-up-down patterns

#### 3.3 Time-Based Sequences
- **Rule**: Specific patterns within time windows

---

### 4. Order Book Imbalance Rules (10 rules)

#### 4.1 Bid-Ask Imbalance
- **Rule**: Bid volume > X% of total volume
- **Thresholds**: 60%, 70%, 80%, 90%

#### 4.2 Spread Changes
- **Rule**: Spread widens/narrows by X%

#### 4.3 Large Orders
- **Rule**: Single order > X% of average

---

### 5. Multi-Symbol Correlation Rules (10 rules)

#### 5.1 Cross-Asset Movement
- **Rule**: BTC moves, ETH follows within X seconds
- **Pairs**: BTC-ETH, BTC-BNB, ETH-BNB

#### 5.2 Correlation Breakdown
- **Rule**: Historical correlation breaks

---

## Rule Template Format

### DRL Rule Example
```drl
rule "Price Spike 1% in 5s - BTCUSDT"
when
    $t1: TradeEvent(symbol == "BTCUSDT", $price1: price)
    $t2: TradeEvent(
        symbol == "BTCUSDT",
        price > ($price1 * 1.01),
        this after[0s, 5s] $t1
    )
then
    // Alert: 1% price increase detected
    insert(new PriceSpikeAlert($t1, $t2, "1%", "5s"));
end
```

---

## Scalability Strategy

### Generating 140 Rules
- Duplicate each rule with different thresholds
- Example: 1% → 2%, 3%, 5%, 10%

### Generating 280 Rules
- Add more symbols (BTC, ETH, BNB, SOL, ADA, etc.)
- Add more time windows (5s, 10s, 30s, 1m, 5m)

### Generating 560 Rules
- Combine threshold + time window + symbol variations

### Generating 1000+ Rules
- Add more complex combinations
- Add derived metrics (RSI, MACD, etc.)

---

## Rule Performance Characteristics

### Expected Activation Rates
- **Price Movement**: 5-10% of events
- **Volume Anomaly**: 2-5% of events
- **Temporal Patterns**: 1-3% of events
- **Order Book**: 10-15% of events
- **Correlation**: 1-2% of events

---

## Rule Files

### Base Taxonomy (70 rules)
- **File**: `src/main/resources/rules/taxonomy_base_70.drl`
- **Description**: (Paste your 70 rules here)

### Extended Taxonomy (Template)
- **File**: `src/main/resources/rules/taxonomy_extended.drl.ftl`
- **Description**: Freemarker template for generating scaled rule sets

---

## Notes
- Add specific notes about rule design
- Known limitations
- Performance considerations
