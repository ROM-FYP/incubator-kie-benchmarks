# Cluster-Level Parallel Execution Report

*Generated: Sun Apr 05 13:20:00 IST 2026*

## 1. Methodology

- Hybrid static-dynamic partitioning using Infomap community detection
- 6 Infomap clusters + 1 fault-tolerant fallback session
- LHS-parsed eventType routing (barrier-free BlockingQueue workers)
- Dataset: 1000 events, 1 symbols

## 2. Cluster Configuration

| Cluster | Name | Original | Duplicated | Total | Event Types |
|---------|------|----------|------------|-------|-------------|
| 1 | Feed Health & Mode Transitions | 14 | +0 | 14 | [DEPTH] |
| 2 | Depth/Spread | 26 | +0 | 26 | [DEPTH, TRADE, MARK] |
| 3 | Trade Alpha | 2 | +0 | 2 | [TRADE] |
| 5 | Liquidation | 3 | +0 | 3 | [] |
| 6 | Trade Rate | 2 | +0 | 2 | [] |
| F | Fallback | 48 | 0 | 48 | ALL (fault-tolerant) |

- Infomap coverage: 47/110 rules (43%)
- Bridge rules duplicated: 0 (0% overhead on clusters)
- Fallback coverage: 48/110 rules (44%)

## 3. Routing Table

| Event Type | Cluster Sessions | + Fallback | Total Sessions |
|------------|-----------------|------------|----------------|
| DEPTH | [1, 2] | always | 3 |
| TRADE | [2, 3] | always | 3 |
| MARK | [2] | always | 2 |

## 4. Performance Results

| Metric | Single Session | Cluster (8T) |
|--------|---------------|               |
| Events/sec | 4098.36 | 11111.11 |
| Duration (ms) | 244 | 90 |
| Speedup | 1.00x | 2.71x |

## 5. Correctness Validation

- Baseline rules fired: 4998
- Cluster + Fallback fired: 4998 (≥ baseline due to bridge duplication)
- Correctness: **PASS ✅**

## 6. Fallback Analysis (key research metric)

| Metric | Value |
|--------|-------|
| Fallback rules fired | 3002 |
| Total rules fired | 4998 |
| Fallback % of total | 60.1% |
| Cluster-only fired | 1996 |

> If fallback fires <5% of total activations, Infomap clustering
> captured the dominant execution paths. If >20%, the clustering
> missed significant rule interactions and needs refinement.

## 7. Per-Cluster Breakdown

| Cluster | Events Recv | Fired | Avg μs/event |
|---------|-------------|-------|--------------|
| Cluster 1 | 0 | 0 | — |
| Cluster 2 | 1000 | 0 | — |
| Cluster 3 | 1000 | 1996 | — |
| Cluster 5 | 0 | 0 | — |
| Cluster 6 | 0 | 0 | — |
| Fallback | 1000 | 3002 | — |

## 8. Conclusions

- Speedup: **2.71x**
- Infomap quality: **NEEDS REFINEMENT** — fallback fired 60.1%, significant rule interactions missed by clustering
- Architecture: barrier-free BlockingQueue workers (no per-event sync)
- Thread pool: 8 threads for 6 sessions
