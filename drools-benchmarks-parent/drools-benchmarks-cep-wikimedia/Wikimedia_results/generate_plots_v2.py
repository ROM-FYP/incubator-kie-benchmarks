import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os
import re

# Set the style
plt.style.use('default')
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['axes.grid'] = True
plt.rcParams['grid.alpha'] = 0.5
plt.rcParams['grid.color'] = '#cccccc'

# Output directory
output_dir = 'plots_v2'
os.makedirs(output_dir, exist_ok=True)

# Define the files and splits
splits = ['400K', '800K', '1200K', '1600K']
numeric_splits = [400000, 800000, 1200000, 1600000]

files = {
    '400K': 'benchmark_summary_400K.md',
    '800K': 'benchmark_summary_800K.md',
    '1200K': 'benchmark_summary_ 1200K.md',
    '1600K': 'benchmark_summary_1600K.md'
}

# Define the new architectures list
architectures = [
    'Sequential Baseline',
    'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
    'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
    'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
    'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
    'Rule_cluster_parallel'
]

# Generate distinct colors for 8 architectures
# We can use a colormap or specific hex colors
colors = {
    'Sequential Baseline': '#000000',
    'Built-in Parallel (parallelMode=PARALLEL_EVALUATION)': '#1f77b4',
    'Built-in Parallel (parallelMode=FULLY_PARALLEL)': '#ff7f0e',
    'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)': '#2ca02c',
    'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)': '#d62728',
    'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)': '#9467bd',
    'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)': '#8c564b',
    'Rule_cluster_parallel': '#e377c2'
}

markers = {
    'Sequential Baseline': 'o',
    'Built-in Parallel (parallelMode=PARALLEL_EVALUATION)': 's',
    'Built-in Parallel (parallelMode=FULLY_PARALLEL)': 'D',
    'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)': '^',
    'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)': 'v',
    'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)': '<',
    'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)': '>',
    'Rule_cluster_parallel': '*'
}

# Function to parse markdown tables
def parse_markdown_table(file_path, table_index):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Find all tables
    tables = re.findall(r'(\|(?:[^\n]+\|)+\n\|(?:[ :\-]+\|)+\n(?:\|(?:[^\n]+\|)+\n)+)', content)
    if len(tables) <= table_index:
        return None
    
    table_text = tables[table_index].strip().split('\n')
    header = [col.strip().replace('*', '') for col in table_text[0].split('|')[1:-1]]
    data = []
    for row in table_text[2:]:
        cols = [col.strip() for col in row.split('|')[1:-1]]
        data.append(cols)
        
    df = pd.DataFrame(data, columns=header)
    return df

# Data extraction
df_perf_list = []
df_timing_list = []
df_gc_list = []

for split in splits:
    file_path = files[split]
    
    # Table 3 is Performance Summary
    # Table 4 is Per-Iteration Timing
    # Table 5 is GC Detail
    
    perf = parse_markdown_table(file_path, 3)
    timing = parse_markdown_table(file_path, 4)
    gc = parse_markdown_table(file_path, 5)
    
    if perf is not None:
        perf['split'] = split
        df_perf_list.append(perf)
    if timing is not None:
        timing['split'] = split
        df_timing_list.append(timing)
    if gc is not None:
        gc['split'] = split
        df_gc_list.append(gc)

df_perf = pd.concat(df_perf_list, ignore_index=True)
df_timing = pd.concat(df_timing_list, ignore_index=True)
df_gc = pd.concat(df_gc_list, ignore_index=True)

# Clean architecture names
def clean_arch_name(name):
    name = name.replace('*', '').strip()
    if name == 'Cluster V3':
        return 'Rule_cluster_parallel'
    return name

df_perf['Architecture'] = df_perf['Architecture'].apply(clean_arch_name)
df_timing['Architecture'] = df_timing['Architecture'].apply(clean_arch_name)
df_gc['Architecture'] = df_gc['Architecture'].apply(clean_arch_name)

# Convert string values to float where applicable
df_perf['Time (ms/op)'] = pd.to_numeric(df_perf['Time (ms/op)'], errors='coerce')
df_perf['Events/s'] = pd.to_numeric(df_perf['Events/s'].str.replace(',', ''), errors='coerce')
df_perf['Mem/Op (B)'] = pd.to_numeric(df_perf['Mem/Op (B)'], errors='coerce')

df_timing['Mean'] = pd.to_numeric(df_timing['Mean'], errors='coerce')
df_timing['Stdev'] = pd.to_numeric(df_timing['Stdev'], errors='coerce')

df_gc['GC Time (total ms)'] = pd.to_numeric(df_gc['GC Time (total ms)'], errors='coerce')
df_gc['GC Count (total)'] = pd.to_numeric(df_gc['GC Count (total)'], errors='coerce')

df_perf['split'] = pd.Categorical(df_perf['split'], categories=splits, ordered=True)
df_timing['split'] = pd.Categorical(df_timing['split'], categories=splits, ordered=True)
df_gc['split'] = pd.Categorical(df_gc['split'], categories=splits, ordered=True)

# 1. Execution Time vs. Dataset Size (Line Chart)
def plot_execution_time():
    plt.figure(figsize=(12, 7))
    for arch in architectures:
        data = df_perf[df_perf['Architecture'] == arch].sort_values('split')
        if not data.empty:
            plt.plot(data['split'], data['Time (ms/op)'], marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            
    plt.title('Execution Time vs. Dataset Size', fontsize=14, pad=15)
    plt.ylabel('Mean Time per Operation (ms/op)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/1_execution_time_line.png', dpi=300)
    plt.close()

# 2. Throughput Scaling (Line Chart)
def plot_throughput():
    plt.figure(figsize=(12, 7))
    for arch in architectures:
        data = df_perf[df_perf['Architecture'] == arch].sort_values('split')
        if not data.empty:
            plt.plot(data['split'], data['Events/s'], marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            
    plt.title('Throughput Scaling', fontsize=14, pad=15)
    plt.ylabel('Events Processed per Second', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.ylim(bottom=0)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/2_throughput_scaling_line.png', dpi=300)
    plt.close()

# 3. Memory per Operation (Line Chart)
def plot_memory():
    plt.figure(figsize=(12, 7))
    for arch in architectures:
        data = df_perf[df_perf['Architecture'] == arch].sort_values('split')
        if not data.empty:
            plt.plot(data['split'], data['Mem/Op (B)'] / 1e9, marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            
    plt.title('Memory per Operation', fontsize=14, pad=15)
    plt.ylabel('Heap Allocated per Full Run (GB)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/3_memory_per_operation_line.png', dpi=300)
    plt.close()

# 4. GC Overhead (Line Chart)
def plot_gc_overhead():
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10), sharex=True)
    
    for arch in architectures:
        data = df_gc[df_gc['Architecture'] == arch].sort_values('split')
        if not data.empty:
            ax1.plot(data['split'], data['GC Time (total ms)'], marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            ax2.plot(data['split'], data['GC Count (total)'], marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            
    ax1.set_ylabel('Total GC Time (ms)', fontsize=12)
    ax1.set_title('GC Overhead (Time and Count)', fontsize=14, pad=15)
    
    ax2.set_xlabel('Dataset Size', fontsize=12)
    ax2.set_ylabel('GC Count', fontsize=12)
    
    # Legend for the figure
    lines, labels = ax1.get_legend_handles_labels()
    fig.legend(lines, labels, title='Architecture', bbox_to_anchor=(1.05, 0.95), loc='upper left')
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/4_gc_overhead_line.png', dpi=300)
    plt.close()

# 5. Execution Time Variability (Bar Chart, Subset)
def plot_variability():
    plt.figure(figsize=(10, 6))
    
    subset_archs = [
        'Sequential Baseline',
        'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
        'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
        'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
        'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
        'Rule_cluster_parallel'
    ]
    
    data_subset = df_timing[df_timing['Architecture'].isin(subset_archs)]
    pivot_mean = data_subset.pivot(index='split', columns='Architecture', values='Mean')
    pivot_stdev = data_subset.pivot(index='split', columns='Architecture', values='Stdev')
    
    pivot_mean = pivot_mean[subset_archs]
    pivot_stdev = pivot_stdev[subset_archs]
    
    plot_colors = [colors.get(col) for col in subset_archs]
    
    pivot_mean.plot(kind='bar', yerr=pivot_stdev, capsize=4, ax=plt.gca(), 
                    color=plot_colors, width=0.8, error_kw=dict(ecolor='black', lw=1.5, capthick=1.5))
    
    plt.title('Execution Time Variability (Mean ± Stdev)', fontsize=14, pad=15)
    plt.ylabel('Time per Operation (ms/op)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.xticks(rotation=0)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/5_time_variability.png', dpi=300)
    plt.close()

# 7. Time Scaling Linear Fit (Line/Scatter)
def plot_time_scaling():
    plt.figure(figsize=(12, 7))
    
    x_numeric = np.array(numeric_splits) / 1000 # thousands
    
    for arch in architectures:
        data = df_perf[df_perf['Architecture'] == arch].sort_values('split')
        if not data.empty:
            y = data['Time (ms/op)'].values
            
            plt.scatter(x_numeric, y, color=colors.get(arch), s=60, marker=markers.get(arch, 'o'), label=arch)
            
            # Linear regression using numpy
            if len(x_numeric) == len(y):
                slope, intercept = np.polyfit(x_numeric, y, 1)
                line = slope * x_numeric + intercept
                
                plt.plot(x_numeric, line, color=colors.get(arch), linestyle='--', alpha=0.7)

    plt.title('Time Scaling - Linear Fit', fontsize=14, pad=15)
    plt.ylabel('Execution Time (ms/op)', fontsize=12)
    plt.xlabel('Event Count (Thousands)', fontsize=12)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/7_time_scaling_fit.png', dpi=300)
    plt.close()

# 8. Peak Heap Size (Line Chart)
def plot_peak_heap():
    try:
        df_heap_full = pd.read_csv('peak_heap_summary.csv')
    except Exception as e:
        print("Could not read peak_heap_summary.csv:", e)
        return
        
    heap_arch_mapping = {
        'Vanilla single threaded': 'Sequential Baseline',
        'PARALLEL_EVAL (t2)': 'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
        'FULLY_PARALLEL (t2)': 'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
        'PARALLEL_EVAL (t4)': 'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
        'FULLY_PARALLEL (t4)': 'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
        'Alpha Routed (cluster)': 'Rule_cluster_parallel',
        'Cluster V3 (data-parallel)': 'Rule_cluster_parallel'
    }
    
    benchmarks = ['Wikimedia', 'OpenSky', 'Binance']
    fig, axes = plt.subplots(1, 3, figsize=(18, 4))
    
    for idx, benchmark in enumerate(benchmarks):
        df_heap = df_heap_full[df_heap_full['Benchmark'] == benchmark].copy()
        ax = axes[idx]
        
        if df_heap.empty:
            ax.set_title(f'{benchmark} (No Data)')
            continue
            
        df_heap['Architecture'] = df_heap['Configuration'].map(heap_arch_mapping)
        df_heap['Dataset'] = pd.Categorical(df_heap['Dataset'], categories=splits, ordered=True)
        
        for arch in architectures:
            data = df_heap[df_heap['Architecture'] == arch].sort_values('Dataset')
            if not data.empty:
                ax.plot(data['Dataset'], data['Peak Heap (MB)'], marker=markers.get(arch, 'o'), 
                         linewidth=3, markersize=10, color=colors.get(arch), label=arch)
                
        ax.set_xlabel('Dataset Size', fontsize=18)
        ax.tick_params(axis='both', which='major', labelsize=16)
        if idx == 0:
            ax.set_ylabel('Peak Heap Size (MB)', fontsize=18)
            
    plt.suptitle('Peak Heap vs. Dataset Size', fontsize=24, y=0.95)
    plt.tight_layout()
    plt.subplots_adjust(wspace=0.3)
    plt.savefig(f'{output_dir}/8_peak_heap_line_combined.png', dpi=300, bbox_inches='tight')
    plt.close()


# 9. Execution Time Combined
def plot_execution_time_combined():
    import pandas as pd
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(1, 3, figsize=(18, 4))
    benchmarks = ['Wikimedia', 'OpenSky', 'Binance']
    
    # Wikimedia
    df_wiki = df_perf[['split', 'Architecture', 'Time (ms/op)']].copy()
    df_wiki['Benchmark'] = 'Wikimedia'
    
    # OpenSky
    try:
        df_os = pd.read_csv('opensky_fullreplay_msop.csv')
        df_os_melt = df_os.melt(id_vars=['Dataset'], var_name='ArchShort', value_name='Time (ms/op)')
        dataset_map = {400000: '400K', 800000: '800K', 1200000: '1200K', 1600000: '1600K'}
        df_os_melt['split'] = df_os_melt['Dataset'].map(dataset_map)
        arch_map_os = {
            'Baseline': 'Sequential Baseline',
            'PE_t2': 'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
            'FP_t2': 'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
            'PE_t4': 'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
            'FP_t4': 'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
            'Cluster': 'Rule_cluster_parallel'
        }
        df_os_melt['Architecture'] = df_os_melt['ArchShort'].map(arch_map_os)
        df_os_melt = df_os_melt.dropna(subset=['Architecture'])
        df_os_melt['Benchmark'] = 'OpenSky'
    except Exception as e:
        print("Error reading OpenSky time:", e)
        df_os_melt = pd.DataFrame()
        
    # Binance
    try:
        df_bin = pd.read_csv('performance_summary.csv')
        arch_map_bin = {
            'Sequential Baseline': 'Sequential Baseline',
            'PARALLEL_EVALUATION': 'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
            'FULLY_PARALLEL': 'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
            'Cluster V3': 'Rule_cluster_parallel'
        }
        df_bin['Architecture'] = df_bin['architecture'].map(arch_map_bin)
        df_bin['Time (ms/op)'] = df_bin['time_ms_op']
        df_bin['Benchmark'] = 'Binance'
    except Exception as e:
        print("Error reading Binance time:", e)
        df_bin = pd.DataFrame()
        
    df_combined = pd.concat([df_wiki, df_os_melt, df_bin], ignore_index=True)
    df_combined['split'] = pd.Categorical(df_combined['split'], categories=splits, ordered=True)
    
    for idx, benchmark in enumerate(benchmarks):
        df_bench = df_combined[df_combined['Benchmark'] == benchmark]
        ax = axes[idx]
        
        for arch in architectures:
            data = df_bench[df_bench['Architecture'] == arch]
            if not data.empty:
                data = data.groupby('split', observed=False)['Time (ms/op)'].mean().reset_index().dropna()
                ax.plot(data['split'], data['Time (ms/op)'] / 1000.0, marker=markers.get(arch, 'o'), 
                         linewidth=3, markersize=10, color=colors.get(arch), label=arch)
                
        ax.set_xlabel('Dataset Size', fontsize=18)
        ax.tick_params(axis='both', which='major', labelsize=16)
        if idx == 0:
            ax.set_ylabel('Execution Time (s)', fontsize=18)
            
    plt.suptitle('Execution Time vs. Dataset Size', fontsize=24, y=0.95)
    plt.tight_layout()
    plt.subplots_adjust(wspace=0.3)
    plt.savefig(f'{output_dir}/9_execution_time_combined.png', dpi=300, bbox_inches='tight')
    plt.close()

# 10. Throughput Combined
def plot_throughput_combined():
    import pandas as pd
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(1, 3, figsize=(18, 4))
    benchmarks = ['Wikimedia', 'OpenSky', 'Binance']
    
    # Wikimedia
    df_wiki = df_perf[['split', 'Architecture', 'Events/s']].copy()
    df_wiki['Benchmark'] = 'Wikimedia'
    
    # OpenSky
    try:
        df_os = pd.read_csv('opensky_fullreplay_throughput.csv')
        df_os_melt = df_os.melt(id_vars=['Dataset'], var_name='ArchShort', value_name='Events/s')
        dataset_map = {400000: '400K', 800000: '800K', 1200000: '1200K', 1600000: '1600K'}
        df_os_melt['split'] = df_os_melt['Dataset'].map(dataset_map)
        arch_map_os = {
            'Baseline': 'Sequential Baseline',
            'PE_t2': 'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
            'FP_t2': 'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
            'PE_t4': 'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
            'FP_t4': 'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
            'Cluster': 'Rule_cluster_parallel'
        }
        df_os_melt['Architecture'] = df_os_melt['ArchShort'].map(arch_map_os)
        df_os_melt = df_os_melt.dropna(subset=['Architecture'])
        df_os_melt['Benchmark'] = 'OpenSky'
    except Exception as e:
        print("Error reading OpenSky throughput:", e)
        df_os_melt = pd.DataFrame()
        
    # Binance
    try:
        df_bin = pd.read_csv('performance_summary.csv')
        arch_map_bin = {
            'Sequential Baseline': 'Sequential Baseline',
            'PARALLEL_EVALUATION': 'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
            'FULLY_PARALLEL': 'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
            'Cluster V3': 'Rule_cluster_parallel'
        }
        df_bin['Architecture'] = df_bin['architecture'].map(arch_map_bin)
        df_bin['Events/s'] = df_bin['events_per_s']
        df_bin['Benchmark'] = 'Binance'
    except Exception as e:
        print("Error reading Binance throughput:", e)
        df_bin = pd.DataFrame()
        
    df_combined = pd.concat([df_wiki, df_os_melt, df_bin], ignore_index=True)
    df_combined['split'] = pd.Categorical(df_combined['split'], categories=splits, ordered=True)
    
    for idx, benchmark in enumerate(benchmarks):
        df_bench = df_combined[df_combined['Benchmark'] == benchmark]
        ax = axes[idx]
        
        for arch in architectures:
            data = df_bench[df_bench['Architecture'] == arch]
            if not data.empty:
                data = data.groupby('split', observed=False)['Events/s'].mean().reset_index().dropna()
                ax.plot(data['split'], data['Events/s'], marker=markers.get(arch, 'o'), 
                         linewidth=3, markersize=10, color=colors.get(arch), label=arch)
                
        ax.set_xlabel('Dataset Size', fontsize=18)
        ax.tick_params(axis='both', which='major', labelsize=16)
        if idx == 0:
            ax.set_ylabel('Throughput (Events/s)', fontsize=18)
            
    plt.suptitle('Throughput vs. Dataset Size', fontsize=24, y=0.95)
    plt.tight_layout()
    plt.subplots_adjust(wspace=0.3)
    plt.savefig(f'{output_dir}/10_throughput_combined.png', dpi=300, bbox_inches='tight')
    plt.close()

if __name__ == '__main__':
    print("Generating plots directly from Markdown...")
    plot_execution_time()
    plot_throughput()
    plot_memory()
    plot_gc_overhead()
    plot_variability()
    plot_time_scaling()
    plot_peak_heap()
    plot_execution_time_combined()
    plot_throughput_combined()
    
    # Save CSVs of the parsed data
    df_perf.to_csv(f'{output_dir}/parsed_performance_summary.csv', index=False)
    df_timing.to_csv(f'{output_dir}/parsed_per_iteration_timing.csv', index=False)
    df_gc.to_csv(f'{output_dir}/parsed_gc_detail.csv', index=False)
    
    print(f"All plots and CSVs saved to {os.path.abspath(output_dir)}")
