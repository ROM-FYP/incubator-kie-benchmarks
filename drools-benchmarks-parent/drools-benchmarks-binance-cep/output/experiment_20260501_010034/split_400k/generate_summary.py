import json, glob, sys, os

def format_name(bench_obj):
    full_name = bench_obj.get("benchmark", "")
    short_name = full_name.split(".")[-2] if "." in full_name else full_name
    
    if "Binance" in short_name:
        if "BinanceFullDatasetBenchmark" in short_name: short_name = "Sequential Baseline"
        elif "BinanceBuiltinParallelBenchmark" in short_name: short_name = "Built-in Parallel"
        elif "BinanceParallelBenchmark" in short_name: short_name = "Data-Parallel"
        elif "BinanceClusterBenchmarkV3" in short_name: short_name = "Cluster V3"
            
    params = bench_obj.get("params", {})
    threads = bench_obj.get("threads", 1)
    
    param_list = []
    if threads > 1:
        param_list.append(f"threads={threads}")
        
    for k, v in params.items():
        param_list.append(f"{k}={v}")
        
    if param_list:
        param_str = ", ".join(param_list)
        return f"{short_name} ({param_str})"
    return short_name

def format_val(val, fmt="{:.2f}"):
    if val is None or val == "NaN": return "N/A"
    try: return fmt.format(float(val))
    except (ValueError, TypeError): return str(val)

def main():
    import re
    target_dir = sys.argv[1]
    dataset_name = os.path.basename(target_dir)
    
    print("")
    print(f"### Benchmark Performance Summary — {dataset_name}")
    print("| Architecture | Throughput (ops/s) | Events/s | Total Events | Rules Fired | Error Margin | Alloc Rate (MB/s) | Mem per Op (B/op) | GC Count | GC Time (ms) |")
    print("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |")
    
    files = sorted(glob.glob(os.path.join(target_dir, "*_results.json")))
    for file in files:
        try:
            # Parse associated log file for events and rules fired
            log_file = file.replace("_results.json", "_output.log")
            events_count = 0
            rules_fired = 0
            
            if os.path.exists(log_file):
                with open(log_file, "r") as lf:
                    log_text = lf.read()
                    
                    # Extract Events
                    m_ev1 = re.search(r"Events:\s*(\d+)", log_text)
                    m_ev2 = re.search(r"Total events per invocation:\s*(\d+)", log_text)
                    if m_ev1: events_count = int(m_ev1.group(1))
                    elif m_ev2: events_count = int(m_ev2.group(1))
                    
                    # Extract Rules Fired
                    m_rf = re.search(r"(?:Rules fired|Fired):\s*(\d+)", log_text)
                    if m_rf: rules_fired = int(m_rf.group(1))
            
            with open(file, "r") as f:
                data = json.load(f)
                if not isinstance(data, list): data = [data]
                for d in data:
                    name = format_name(d)
                    pm = d.get("primaryMetric", {})
                    sm = d.get("secondaryMetrics", {})
                    
                    thrpt = pm.get("score")
                    err = pm.get("scoreError")
                    
                    events_per_sec = (thrpt * events_count) if thrpt is not None and events_count > 0 else None
                    
                    alloc_r = sm.get("gc.alloc.rate", {}).get("score")
                    alloc_norm = sm.get("gc.alloc.rate.norm", {}).get("score")
                    gc_c = sm.get("gc.count", {}).get("score")
                    gc_t = sm.get("gc.time", {}).get("score")
                    
                    ev_str = f"{events_count:,}" if events_count > 0 else "N/A"
                    rf_str = f"{rules_fired:,}" if rules_fired > 0 else "N/A"
                    eps_str = f"{events_per_sec:,.0f}" if events_per_sec is not None else "N/A"
                    
                    row = f"| **{name}** | {format_val(thrpt, '{:.3f}')} | {eps_str} | {ev_str} | {rf_str} | ± {format_val(err, '{:.4f}')} | {format_val(alloc_r, '{:.2f}')} | {format_val(alloc_norm, '{:.2e}')} | {format_val(gc_c, '{:.0f}')} | {format_val(gc_t, '{:.1f}')} |"
                    print(row)
        except Exception:
            filename = os.path.basename(file)
            print(f"| **Error parsing {filename}** | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |")
    print("")

if __name__ == "__main__":
    main()
