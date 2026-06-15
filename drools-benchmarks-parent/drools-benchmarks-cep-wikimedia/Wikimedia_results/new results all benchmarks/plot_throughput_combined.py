"""
Combined Throughput Line Plot (4 benchmarks in 2×2 grid)
=========================================================
Reads  : binance_throughput.csv, opensky_fullreplay_throughput.csv,
         wikimedia_throughput.csv, bgp_routing_throughput.csv  (same directory)
Outputs: combined_throughput_line.png   (same directory)
"""

import os
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

# -- paths -----------------------------------------------------------------
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT_PATH   = os.path.join(SCRIPT_DIR, "combined_throughput_line.png")

CSV_FILES = {
    "OpenSky":      os.path.join(SCRIPT_DIR, "opensky_fullreplay_throughput.csv"),
    "Wikimedia":    os.path.join(SCRIPT_DIR, "wikimedia_throughput.csv"),
    "Binance":      os.path.join(SCRIPT_DIR, "binance_throughput.csv"),
    "BGP Routing":  os.path.join(SCRIPT_DIR, "bgp_routing_throughput.csv"),
}

# -- column -> legend mapping per benchmark --------------------------------
# Each benchmark CSV has slightly different column names for the baseline
# and cluster columns, but the PE/FP columns are consistent.
#
# OpenSky columns:   Baseline, PE_t1, PE_t2, PE_t4, FP_t1, FP_t2, FP_t4, Cluster_2, Cluster_9
# Wikimedia columns: Sequential, PE_t1, FP_t1, PE_t2, FP_t2, PE_t4, FP_t4, Cluster_4, Cluster_2
# Binance columns:   Sequential, PE_t1, FP_t1, PE_t2, FP_t2, PE_t4, FP_t4, Cluster_2, Cluster_5
#
# We SKIP PE_t1 and FP_t1 as requested.

COLUMN_MAP = {
    "OpenSky": {
        "Baseline":  "Sequential Baseline",
        "PE_t2":     "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)",
        "PE_t4":     "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)",
        "FP_t2":     "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)",
        "FP_t4":     "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)",
        "Cluster_2": "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
        "Cluster_9": "Rule-Clustered Architecture\n(Rule Duplication Only)",
    },
    "Wikimedia": {
        "Sequential": "Sequential Baseline",
        "PE_t2":      "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)",
        "PE_t4":      "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)",
        "FP_t2":      "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)",
        "FP_t4":      "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)",
        "Cluster_2":  "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
        "Cluster_4":  "Rule-Clustered Architecture\n(Rule Duplication Only)",
    },
    "Binance": {
        "Sequential": "Sequential Baseline",
        "PE_t2":      "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)",
        "PE_t4":      "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)",
        "FP_t2":      "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)",
        "FP_t4":      "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)",
        "Cluster_2":  "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
        "Cluster_5":  "Rule-Clustered Architecture\n(Rule Duplication Only)",
    },
    "BGP Routing": {
        "Sequential": "Sequential Baseline",
        "PE_t2":      "Built-in Parallel (threads=2, mode=PARALLEL_EVAL)",
        "PE_t4":      "Built-in Parallel (threads=4, mode=PARALLEL_EVAL)",
        "FP_t2":      "Built-in Parallel (threads=2, mode=FULLY_PARALLEL)",
        "FP_t4":      "Built-in Parallel (threads=4, mode=FULLY_PARALLEL)",
        "Cluster_2":  "Rule-Clustered Architecture\n(Rule Duplication + Cluster Merging)",
        "Cluster_6":  "Rule-Clustered Architecture\n(Rule Duplication Only)",
    },
}

# -- visual style per legend label (identical to peak-heap plot) -----------
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

# Deterministic draw / legend order
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

# -- load data -------------------------------------------------------------
data = {}
for bench, path in CSV_FILES.items():
    df = pd.read_csv(path)
    df.columns = df.columns.str.strip()
    # Convert dataset column to K-units for display (400000 -> 400)
    df["Size_K"] = df["Dataset"] // 1000
    data[bench] = df

# -- plot ------------------------------------------------------------------
fig, axes = plt.subplots(2, 2, figsize=(18, 13), sharey=False)
axes = axes.flatten()
fig.patch.set_facecolor("white")

legend_handles = {}

for ax, bench in zip(axes, BENCHMARK_ORDER):
    df = data[bench]
    col_map = COLUMN_MAP[bench]

    ax.set_title(BENCHMARK_TITLES[bench], fontsize=14, fontweight="bold", pad=10)
    ax.set_xlabel("Dataset Size", fontsize=12)
    ax.set_ylabel("Throughput (events/sec)", fontsize=12)
    ax.grid(True, linestyle="--", alpha=0.35)
    ax.set_facecolor("#fafafa")

    for label in LABEL_ORDER:
        # Find the CSV column that maps to this label for this benchmark
        col = None
        for c, l in col_map.items():
            if l == label:
                col = c
                break
        if col is None or col not in df.columns:
            continue

        sorted_df = df.sort_values("Size_K")
        style = STYLE[label]
        line, = ax.plot(
            sorted_df["Size_K"], sorted_df[col],
            label=label, **style
        )
        if label not in legend_handles:
            legend_handles[label] = line

    # Format x-axis as "400K", "800K", ...
    ax.set_xticks(sorted(df["Size_K"].unique()))
    ax.set_xticklabels([f"{v}K" for v in sorted(df["Size_K"].unique())], fontsize=10)
    ax.tick_params(axis="y", labelsize=10)

# -- shared legend below the plots -----------------------------------------
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
    "Throughput vs. Dataset Size — All Benchmarks",
    fontsize=16, fontweight="bold", y=0.99,
)

plt.tight_layout(rect=[0, 0.12, 1, 0.95])
plt.savefig(OUT_PATH, dpi=200, bbox_inches="tight", facecolor="white")
print(f"Saved to {OUT_PATH}")
