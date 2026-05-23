import json
import re
import os
import glob

def extract_metrics(base_dir, project):
    results = {}
    
    char_files = {
        'Base': glob.glob(f'{base_dir}/{project}/*baseline*.txt') + glob.glob(f'{base_dir}/{project}/*char_baseline*.txt'),
        'Med': glob.glob(f'{base_dir}/{project}/*cov50*.txt'),
        'Low': glob.glob(f'{base_dir}/{project}/*cov25*.txt')
    }
    
    for variant, files in char_files.items():
        if not files:
            continue
        file = files[0]
        results[variant] = {}
        with open(file, 'r', encoding='utf-8') as f:
            content = f.read()
            cov_match = re.search(r'Rule coverage on dataset\s*│\s*([\d.]+)', content)
            rf_match = re.search(r'Rules fired per event\s*│\s*([\d.]+)', content)
            wm_match = re.search(r'Peak WM size \(facts\)\s*│\s*([\d.]+)', content)
            
            if cov_match: results[variant]['Coverage'] = cov_match.group(1)
            if rf_match: results[variant]['Rules_Fired'] = rf_match.group(1)
            if wm_match: results[variant]['Peak_WM'] = wm_match.group(1)

    jmh_files = {
        'Base': glob.glob(f'{base_dir}/{project}/results/jmh_*baseline.json'),
        'Med': glob.glob(f'{base_dir}/{project}/results/jmh_*cov50.json'),
        'Low': glob.glob(f'{base_dir}/{project}/results/jmh_*cov25.json')
    }
    
    for variant, files in jmh_files.items():
        if not files:
            continue
        file = files[0]
        with open(file, 'r', encoding='utf-8') as f:
            data = json.load(f)
            score = data[0]['primaryMetric']['score']
            throughput = score * 1600000
            latency = 1.0 / score
            results[variant]['Throughput'] = round(throughput)
            results[variant]['Latency'] = round(latency, 1)
            
    gc_files = {
        'Base': glob.glob(f'{base_dir}/{project}/results/gc/gc_*baseline.log'),
        'Med': glob.glob(f'{base_dir}/{project}/results/gc/gc_*cov50.log'),
        'Low': glob.glob(f'{base_dir}/{project}/results/gc/gc_*cov25.log')
    }
    
    for variant, files in gc_files.items():
        if not files:
            continue
        file = files[0]
        with open(file, 'r', encoding='utf-8') as f:
            content = f.read()
            # G1GC heap usage at exit "garbage-first heap   total 4227072K, used 73326K"
            heap_match = re.search(r'used (\d+)K', content)
            if heap_match:
                mb = int(heap_match.group(1)) // 1024
                results[variant]['Peak_Heap_MB'] = mb
                
    return results

print("=== AIRTRAFFIC ===")
at = extract_metrics(r'c:\Users\DELL\incubator-kie-benchmarks\paper\results\opensky-20260523T055728Z-3-001', 'opensky')
for v, d in at.items(): print(f"{v}: {d}")

print("\n=== WIKIMEDIA ===")
wiki = extract_metrics(r'c:\Users\DELL\incubator-kie-benchmarks\paper\results\wikimedia-20260523T055710Z-3-001', 'wikimedia')
for v, d in wiki.items(): print(f"{v}: {d}")
