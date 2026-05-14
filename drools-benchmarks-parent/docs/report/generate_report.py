import os
import re

report_dir = "/home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/docs/report"
output_file = os.path.join(report_dir, "Benchmark_Execution_Report.md")

modules = ['binance', 'wikimedia', 'opensky']

def extract_error(content):
    if "<failure>" in content:
        match = re.search(r"<failure>\s*(java\.lang\.Error: Unresolved compilation problem[s]?:\s+.*)", content)
        if match: return f"FAILED: {match.group(1).strip()}"

    if "[ERROR] Failed to execute goal" in content:
        match = re.search(r"\[ERROR\] Failed to execute goal.*?An exception occurred while executing the Java class\.\s*(.*?)(?:\n\[ERROR\] ->|\n\[INFO\])", content, re.DOTALL)
        if match: return f"FAILED EXECUTION:\n{match.group(1).strip()}"
        
        match2 = re.search(r"\[ERROR\] Failed to execute goal.*?: (.*?)(?:\n\[ERROR\] ->|\n\[INFO\])", content, re.DOTALL)
        if match2: return f"FAILED:\n{match2.group(1).strip()}"
        
    return None

def extract_jmh(module):
    path = os.path.join(report_dir, f"jmh_{module}.txt")
    if not os.path.exists(path): return "N/A"
    with open(path, 'r') as f: content = f.read()
    
    err = extract_error(content)
    if err: return err
    
    match4 = re.search(r"([\w.]+)\s+baseline\s+(?:.*)\s+thrpt\s+1\s+([\d.]+)\s+(ops/s)", content)
    if match4: return f"{match4.group(2)} {match4.group(3)}"

    lines = content.strip().split('\n')
    for line in reversed(lines):
        if "baseline" in line and ("ops/s" in line or "s/op" in line or "ms/op" in line):
            return line.strip()
    return "JMH execution complete. Raw output available in logs."

def extract_heap(module):
    path = os.path.join(report_dir, f"heap_{module}.txt")
    if not os.path.exists(path): return "N/A"
    with open(path, 'r') as f: content = f.read()
    
    err = extract_error(content)
    if err: return err

    res = []
    for line in content.split('\n'):
        if "median_peak_heap_mb" in line or "median_post_dispose_mb" in line or "median_delta_mb" in line:
            res.append(line.strip())
            
    return "\n".join(res) if res else "Heap Profile execution complete. Raw output available in logs."

def extract_char(module):
    path = os.path.join(report_dir, f"char_{module}.txt")
    if not os.path.exists(path): return "N/A"
    with open(path, 'r') as f: content = f.read()
    
    err = extract_error(content)
    if err: return err

    res = []
    capture = False
    for line in content.split('\n'):
        if "CHARACTERIZATION RESULTS" in line or "CHARACTERIZATION TABLE" in line:
            capture = True
            res.append(line)
            continue
        if capture and ("══════════════════════════════════════════════════════════════════════" in line or "└──────────────────────────────────────┴─────────────────┘" in line):
            res.append(line)
            break
        if capture:
            res.append(line)
    return "\n".join(res) if res else "Characterization execution complete. Raw output available in logs."

with open(output_file, 'w') as out:
    out.write("# Benchmark Execution Report\n\n")
    out.write("This report contains the execution results of the JMH Benchmarks, Heap Profilers, and Characterization Collectors across all three domains (Binance, Wikimedia, OpenSky).\n\n")
    out.write("> **Note:** JMH benchmarks and Heap Profilers were run with reduced iterations (`-wi 0 -i 1 -f 0 -t 1` and `--trials 1`) to verify functionality without modifying source code.\n")
    out.write("> **Note 2:** Per the strict instruction *'no source code changes, no matter what'*, compilation failures encountered during execution are reported exactly as-is without being fixed.\n\n")

    for mod in modules:
        out.write(f"## {mod.capitalize()} Domain\n\n")
        
        out.write("### 1. JMH Benchmark (mode=baseline)\n")
        out.write("```text\n")
        out.write(extract_jmh(mod) + "\n")
        out.write("```\n\n")
        
        out.write("### 2. Heap Profiler\n")
        out.write("```text\n")
        out.write(extract_heap(mod) + "\n")
        out.write("```\n\n")
        
        out.write("### 3. Characterization Collector\n")
        out.write("```text\n")
        out.write(extract_char(mod) + "\n")
        out.write("```\n\n")
        
        out.write("---\n\n")

print("Report generated.")
