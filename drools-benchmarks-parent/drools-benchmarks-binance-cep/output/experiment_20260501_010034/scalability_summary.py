"""
Cross-dataset scalability summary.
Reads all per-dataset benchmark_summary.md files and produces
a unified comparison table showing how each architecture scales.
"""
import json, glob, sys, os, re

def main():
    root_dir = sys.argv[1]
    
    # Discover dataset subdirectories
    datasets = sorted([
        d for d in os.listdir(root_dir)
        if os.path.isdir(os.path.join(root_dir, d)) and d.startswith("split_")
    ])
    
    if not datasets:
        print("No dataset subdirectories found.")
        return
    
    print("")
    print("# Cross-Dataset Scalability Summary")
    print("")
    print(f"Experiment: {os.path.basename(root_dir)}")
    print(f"Datasets:   {', '.join(datasets)}")
    print("")
    
    # Collect results per architecture per dataset
    results = {}  # { arch_name: { dataset: { throughput, events_per_sec } } }
    
    for ds in datasets:
        ds_dir = os.path.join(root_dir, ds)
        result_files = sorted(glob.glob(os.path.join(ds_dir, "*_results.json")))
        
        for rf in result_files:
            try:
                with open(rf, "r") as f:
                    data = json.load(f)
                    if not isinstance(data, list): data = [data]
                    
                    for d in data:
                        full_name = d.get("benchmark", "")
                        short = full_name.split(".")[-2] if "." in full_name else full_name
                        
                        if "BinanceFullDatasetBenchmark" in short: short = "Baseline"
                        elif "BinanceBuiltinParallelBenchmark" in short: short = "Built-in Parallel"
                        elif "BinanceParallelBenchmark" in short: short = "Data-Parallel"
                        elif "BinanceClusterBenchmarkV3" in short: short = "Cluster V3"
                        
                        params = d.get("params", {})
                        threads = d.get("threads", 1)
                        label_parts = [short]
                        if threads > 1: label_parts.append(f"t{threads}")
                        for k, v in params.items():
                            if k not in ("dataFile", "dataset"): label_parts.append(f"{k}={v}")
                        arch_label = " ".join(label_parts)
                        
                        pm = d.get("primaryMetric", {})
                        score = pm.get("score")
                        
                        if arch_label not in results:
                            results[arch_label] = {}
                        results[arch_label][ds] = score
            except Exception:
                pass
    
    # Print scalability table
    header = "| Architecture |"
    sep = "| :--- |"
    for ds in datasets:
        # Extract event count from name (e.g., split_400k → 400K)
        count = ds.replace("split_", "").upper()
        header += f" {count} (ops/s) |"
        sep += " ---: |"
    header += " Trend |"
    sep += " :--- |"
    
    print(header)
    print(sep)
    
    for arch, ds_scores in sorted(results.items()):
        row = f"| **{arch}** |"
        scores = []
        for ds in datasets:
            s = ds_scores.get(ds)
            if s is not None:
                row += f" {s:.3f} |"
                scores.append(s)
            else:
                row += " N/A |"
        
        # Calculate trend (first vs last)
        if len(scores) >= 2 and scores[0] > 0:
            ratio = scores[-1] / scores[0]
            if ratio > 1.05:
                trend = f"↑ {ratio:.2f}×"
            elif ratio < 0.95:
                trend = f"↓ {ratio:.2f}×"
            else:
                trend = "→ stable"
        else:
            trend = "—"
        row += f" {trend} |"
        
        print(row)
    
    print("")

if __name__ == "__main__":
    main()
