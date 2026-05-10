# Placeholder for DRL Rule Files

## Instructions
Place your DRL rule files here.

## Expected Files

### taxonomy_base_70.drl
The base 70-rule taxonomy file. This should contain:
- 20 Price Movement rules
- 15 Volume Anomaly rules
- 15 Temporal Pattern rules
- 10 Order Book Imbalance rules
- 10 Multi-Symbol Correlation rules

### taxonomy_extended.drl.ftl
Freemarker template for generating scaled rule sets (140, 280, 560, 1000+ rules).

## Rule Categories

1. **Price Movement** (20 rules)
   - Price spike detection
   - Price drop detection
   - Rapid price changes

2. **Volume Anomaly** (15 rules)
   - Volume surge detection
   - Volume accumulation
   - Volume-price divergence

3. **Temporal Patterns** (15 rules)
   - Consecutive movements
   - Alternating patterns
   - Time-based sequences

4. **Order Book Imbalance** (10 rules)
   - Bid-ask imbalance
   - Spread changes
   - Large orders

5. **Multi-Symbol Correlation** (10 rules)
   - Cross-asset movements
   - Correlation breakdowns
   - Leading/lagging indicators
