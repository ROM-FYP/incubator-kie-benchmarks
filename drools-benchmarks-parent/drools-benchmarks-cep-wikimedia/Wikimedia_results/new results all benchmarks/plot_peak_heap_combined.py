"""
Combined Peak Heap Line Plot (4 benchmarks in 2×2 grid)
=========================================================
Reads  : peak_heap_summary copy.csv   (same directory)
Outputs: combined_peak_heap_line.png   (same directory)
"""

import os
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

# ── paths ────────────────────────────────────────────────────────────────
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
CSV_PATH   = os.path.join(SCRIPT_DIR, "peak_heap_summary copy.csv")
OUT_PATH   = os.path.join(SCRIPT_DIR, "combined_peak_heap_line.png")

# ── legend mapping ───────────────────────────────────────────────────────
# key  = (benchmark, configuration)  →  unified legend label
# We build the map per-benchmark for the cluster-based architectures,
# and use a common map for the built-in ones.

COMMON_MAP = {
    "Vanilla single threaded":  "Sequential Baseline",
    "PARALLEL_EVAL (t4)":       "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)",
    "PARALLEL_EVAL (t2)":       "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)",
    "FULLY_PARALLEL (t4)":      "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)",
    "FULLY_PARALLEL (t2)":      "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)",
}

# Cluster architectures that map to common names across benchmarks
CLUSTER_MERGED = {
    ("OpenSky",      "Alpha Routed (2 clusters)"):  "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
    ("Wikimedia",    "Alpha Routed (2 clusters)"):  "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
    ("Binance",      "Cluster V3 (2 clusters)"):    "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
    ("BGP Routing",  "Alpha Routed (2 clusters)"):  "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
}

CLUSTER_DUP_ONLY = {
    ("OpenSky",      "Alpha Routed (9 clusters)"):  "Rule-Clustered Architecture\n(Rule Duplication Only)",
    ("Wikimedia",    "Alpha Routed (4 clusters)"):  "Rule-Clustered Architecture\n(Rule Duplication Only)",
    ("Binance",      "Cluster V3 (5 clusters)"):    "Rule-Clustered Architecture\n(Rule Duplication Only)",
    ("BGP Routing",  "Alpha Routed (6 clusters)"):  "Rule-Clustered Architecture\n(Rule Duplication Only)",
}


def resolve_label(benchmark: str, config: str) -> str:
    """Return the unified legend label for a (benchmark, config) pair."""
    if config in COMMON_MAP:
        return COMMON_MAP[config]
    key = (benchmark, config)
    if key in CLUSTER_MERGED:
        return CLUSTER_MERGED[key]
    if key in CLUSTER_DUP_ONLY:
        return CLUSTER_DUP_ONLY[key]
    return config  # fallback – should not happen


# ── visual style per legend label ────────────────────────────────────────
STYLE = {
    "Sequential Baseline": dict(
        color="#1a1a1a", marker="o", linewidth=2.4, markersize=8, linestyle="-"
    ),
    "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)": dict(
        color="#2ca02c", marker="^", linewidth=2.0, markersize=8, linestyle="-"
    ),
    "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)": dict(
        color="#6a3d9a", marker="<", linewidth=2.0, markersize=8, linestyle="-"
    ),
    "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)": dict(
        color="#e31a1c", marker="v", linewidth=2.0, markersize=8, linestyle="-"
    ),
    "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)": dict(
        color="#b15928", marker="s", linewidth=2.0, markersize=8, linestyle="-"
    ),
    "Rule-Clustered Architecture\n(Rule Duplication Only)": dict(
        color="#ff7f00", marker="D", linewidth=2.2, markersize=8, linestyle="--"
    ),
    "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)": dict(
        color="#e377c2", marker="*", linewidth=2.2, markersize=9, linestyle="--"
    ),
}

# Deterministic draw order (so the legend is consistent)
LABEL_ORDER = [
    "Sequential Baseline",
    "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)",
    "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)",
    "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)",
    "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)",
    "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
    "Rule-Clustered Architecture\n(Rule Duplication Only)",
]

BENCHMARK_ORDER = ["OpenSky", "Wikimedia", "Binance", "BGP Routing"]
BENCHMARK_TITLES = {
    "OpenSky":      "OpenSky (Air-Traffic)",
    "Wikimedia":    "Wikimedia (Recent-Changes)",
    "Binance":      "Binance (Crypto-Ticker)",
    "BGP Routing":  "BGP Routing (Network-Events)",
}

# ── load data ────────────────────────────────────────────────────────────
df = pd.read_csv(CSV_PATH)
df.columns = df.columns.str.strip()

# Parse dataset sizes to numeric (e.g. "400K" → 400)
df["Size_K"] = df["Dataset"].str.replace("K", "", regex=False).astype(int)

# Add unified label column
df["Label"] = df.apply(lambda r: resolve_label(r["Benchmark"], r["Configuration"]), axis=1)

# ── plot ─────────────────────────────────────────────────────────────────
fig, axes = plt.subplots(2, 2, figsize=(18, 13), sharey=False)
axes = axes.flatten()
fig.patch.set_facecolor("white")

legend_handles = {}  # label → handle (collect once for shared legend)

for ax, bench in zip(axes, BENCHMARK_ORDER):
    sub = df[df["Benchmark"] == bench]
    ax.set_title(BENCHMARK_TITLES[bench], fontsize=14, fontweight="bold", pad=10)
    ax.set_xlabel("Dataset Size", fontsize=12)
    ax.set_ylabel("Peak Heap (MB)", fontsize=12)
    ax.grid(True, linestyle="--", alpha=0.35)
    ax.set_facecolor("#fafafa")

    for label in LABEL_ORDER:
        grp = sub[sub["Label"] == label].sort_values("Size_K")
        if grp.empty:
            continue
        style = STYLE[label]
        line, = ax.plot(
            grp["Size_K"], grp["Peak Heap (MB)"],
            label=label, **style
        )
        if label not in legend_handles:
            legend_handles[label] = line

    # Format x-axis as "400K", "800K", …
    ax.set_xticks(sorted(sub["Size_K"].unique()))
    ax.set_xticklabels([f"{v}K" for v in sorted(sub["Size_K"].unique())], fontsize=10)
    ax.tick_params(axis="y", labelsize=10)

# ── shared legend below the plots ────────────────────────────────────────
ordered_handles = [legend_handles[l] for l in LABEL_ORDER if l in legend_handles]
ordered_labels  = [l for l in LABEL_ORDER if l in legend_handles]

fig.legend(
    ordered_handles, ordered_labels,
    loc="lower center",
    ncol=3,
    fontsize=9.5,
    frameon=True,
    fancybox=True,
    shadow=False,
    borderpad=0.8,
    columnspacing=1.5,
    handlelength=2.5,
)

fig.suptitle(
    "Peak Heap Memory vs. Dataset Size — All Benchmarks",
    fontsize=16, fontweight="bold", y=0.99,
)

plt.tight_layout(rect=[0, 0.12, 1, 0.95])
plt.savefig(OUT_PATH, dpi=200, bbox_inches="tight", facecolor="white")
print(f"\nSaved to {OUT_PATH}")
