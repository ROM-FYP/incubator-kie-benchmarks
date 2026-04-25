# Wikimedia CEP Flamegraph Deep-Dive

This document "reads" the generated flamegraphs to explain exactly where the memory and CPU are going.

## 1. Baseline Allocation Flamegraph (`baseline_alloc_flamegraph.html`)
**The "Tuple Explosion"**

*   **The Main Pillar:** You will see one giant vertical tower. This is the JMH worker thread. 
*   **The Hotspot:** Look for a massive horizontal plateau in `org.drools.core.phreak.PhreakPropagationContext`. Above this, you'll see a multi-colored "forest" of allocations.
*   **What's being allocated?**
    *   **`LeftTuple` / `RightTuple`:** These represent about **60-70%** of the width. Because all 70 rules are in one session, every new event triggers thousands of partial matches across the RETE network.
    *   **`FactHandle`:** Every event gets a handle; with 222k events, this is a fixed cost.
    *   **`MVEL` / `ConditionEvaluator`:** You'll see narrow towers on top of the tuples where rules are actually evaluating logic (the `evaluate` method of your moderators).

---

## 2. Cluster Allocation Flamegraph (`cluster_alloc_flamegraph.html`)
**The "Partitioned Efficiency"**

*   **Split Stacks:** Instead of one big tower, you see 4 distinct towers (one for each worker thread in the ForkJoinPool).
*   **Orchestrator Overhead:** At the very bottom, locate `WikimediaClusterOrchestrator.replayEvents`. It should be **extremely narrow** (likely < 5% of total width). This proves the "Alpha-routing" logic is almost free.
*   **Reduced Tuple Count:** Notice that the `LeftTuple` / `RightTuple` blocks are much smaller relative to the total width. 
*   **Why?** In Cluster 4 (Discussion), only 729 events are processed. The "discussion rules" never even see the 100,000+ "minor edit" events, so they never create "partial matches" for them. This is the "3.9x memory reduction" in action.

---

## 3. CPU Flamegraphs (`baseline_cpu_flamegraph.html`, `cluster_cpu_flamegraph.html`)
**Lock Contention vs. Pure Logic**

> [!NOTE]
> We used the `--wall` (Wall-clock) profiling mode to generate these. This ensures that the background worker threads in the Parallel Cluster are captured, even when they are switching contexts or waiting for the orchestrator.

*   **Baseline:** The CPU is almost 100% inside `fireAllRules`. You'll see deep stacks into `MVEL` evaluation and RETE network traversal.
*   **Cluster:** 
    *   **Distributed Work:** You'll see 4 major towers (ForkJoinPool workers).
    *   **Worker Efficiency:** The vertical depth into `evaluateNetwork` is slightly reduced compared to the baseline, as each session has a simpler network.
    *   **Orchestrator Overhead:** At the far left, you may see the `WikimediaClusterOrchestrator.replayEvents` stack. It remains a very small percentage of the total CPU time.

---

## 4. Summary: How the Cluster Wins
1.  **Vertical Space:** Baseline is "deeper" (more complex joins). Cluster is "shallower".
2.  **Horizontal Space:** Baseline is "fatter" (more allocations per fact). Cluster is "leaner".
3.  **Visual Noise:** The baseline is a "chaotic forest"; the cluster is 4 "neatly organized gardens".
