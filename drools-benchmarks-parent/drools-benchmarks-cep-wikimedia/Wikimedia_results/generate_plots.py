import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import os

# Set the style
plt.style.use('default')
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['axes.grid'] = True
plt.rcParams['grid.alpha'] = 0.5
plt.rcParams['grid.color'] = '#cccccc'

# Output directory
output_dir = 'plots'
os.makedirs(output_dir, exist_ok=True)

# Colors
colors = {
    'Sequential Baseline': '#58a6ff',
    'PARALLEL_EVALUATION': '#3fb950',
    'FULLY_PARALLEL': '#d2a8ff',
    'Cluster V3': '#ff7b72'
}

cluster_colors = {
    'C1 (Minor)': '#f78166',
    'C2 (Bot)': '#79c0ff',
    'C3 (Content+Vandalism)': '#56d364',
    'C4 (Discussion)': '#e3b341'
}

splits_order = ['400K', '800K', '1200K', '1600K']
numeric_splits = [400000, 800000, 1200000, 1600000]

# Load Data
df_perf = pd.read_csv('performance_summary.csv')
df_timing = pd.read_csv('per_iteration_timing.csv')
df_gc = pd.read_csv('gc_detail.csv')
df_cluster = pd.read_csv('cluster_breakdown.csv')

# Average out the multiple runs for parallel architectures
def aggregate_runs(df):
    return df.groupby(['split', 'architecture']).mean(numeric_only=True).reset_index()

df_perf_agg = aggregate_runs(df_perf)
df_timing_agg = aggregate_runs(df_timing)
df_gc_agg = aggregate_runs(df_gc)

# Sort splits categorically
df_perf_agg['split'] = pd.Categorical(df_perf_agg['split'], categories=splits_order, ordered=True)
df_perf_agg = df_perf_agg.sort_values('split')

df_timing_agg['split'] = pd.Categorical(df_timing_agg['split'], categories=splits_order, ordered=True)
df_timing_agg = df_timing_agg.sort_values('split')

df_gc_agg['split'] = pd.Categorical(df_gc_agg['split'], categories=splits_order, ordered=True)
df_gc_agg = df_gc_agg.sort_values('split')


# 1. Execution Time vs. Dataset Size
def plot_execution_time():
    plt.figure(figsize=(10, 6))
    
    pivot = df_perf_agg.pivot(index='split', columns='architecture', values='time_ms_op')
    pivot = pivot[['Sequential Baseline', 'PARALLEL_EVALUATION', 'FULLY_PARALLEL', 'Cluster V3']]
    
    ax = pivot.plot(kind='bar', color=[colors[col] for col in pivot.columns], ax=plt.gca(), width=0.8, edgecolor='none')
    
    plt.title('Execution Time vs. Dataset Size', fontsize=14, pad=15)
    plt.ylabel('Mean Time per Operation (ms/op)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.xticks(rotation=0)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/1_execution_time.png', dpi=300)
    plt.close()

# 2. Throughput Scaling
def plot_throughput():
    plt.figure(figsize=(10, 6))
    
    for arch, color in colors.items():
        data = df_perf_agg[df_perf_agg['architecture'] == arch]
        plt.plot(data['split'], data['events_per_s'], marker='o', linewidth=2, markersize=8, color=color, label=arch)
        
    plt.title('Throughput Scaling', fontsize=14, pad=15)
    plt.ylabel('Events Processed per Second', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.ylim(bottom=0)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/2_throughput_scaling.png', dpi=300)
    plt.close()

# 3. Memory per Operation
def plot_memory():
    plt.figure(figsize=(10, 6))
    
    target_archs = ['Sequential Baseline', 'Cluster V3']
    data = df_perf_agg[df_perf_agg['architecture'].isin(target_archs)].copy()
    data['mem_gb'] = data['mem_op_bytes'] / 1e9
    
    pivot = data.pivot(index='split', columns='architecture', values='mem_gb')
    pivot = pivot[target_archs]
    
    pivot.plot(kind='bar', color=[colors[col] for col in pivot.columns], ax=plt.gca(), width=0.6)
    
    plt.title('Memory per Operation', fontsize=14, pad=15)
    plt.ylabel('Heap Allocated per Full Run (GB)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.xticks(rotation=0)
    plt.legend(title='Architecture')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/3_memory_per_operation.png', dpi=300)
    plt.close()

# 4. GC Overhead
def plot_gc_overhead():
    fig, ax1 = plt.subplots(figsize=(10, 6))
    ax2 = ax1.twinx()
    
    target_archs = ['Sequential Baseline', 'Cluster V3']
    data = df_gc_agg[df_gc_agg['architecture'].isin(target_archs)]
    
    pivot_time = data.pivot(index='split', columns='architecture', values='gc_time_total_ms')
    pivot_count = data.pivot(index='split', columns='architecture', values='gc_count_total')
    
    pivot_time = pivot_time[target_archs]
    pivot_count = pivot_count[target_archs]
    
    x = np.arange(len(splits_order))
    width = 0.35
    
    ax1.bar(x - width/2, pivot_time['Sequential Baseline'], width, label='GC Time - Seq', color=colors['Sequential Baseline'], alpha=0.8)
    ax1.bar(x + width/2, pivot_time['Cluster V3'], width, label='GC Time - Cluster V3', color=colors['Cluster V3'], alpha=0.8)
    
    ax2.plot(x, pivot_count['Sequential Baseline'], color=colors['Sequential Baseline'], marker='o', linestyle='--', linewidth=2, markersize=8, label='GC Count - Seq')
    ax2.plot(x, pivot_count['Cluster V3'], color=colors['Cluster V3'], marker='s', linestyle='--', linewidth=2, markersize=8, label='GC Count - Cluster V3')
    
    ax1.set_xlabel('Dataset Size', fontsize=12)
    ax1.set_ylabel('Total GC Time (ms)', fontsize=12)
    ax2.set_ylabel('GC Count', fontsize=12)
    
    ax1.set_xticks(x)
    ax1.set_xticklabels(splits_order)
    
    plt.title('GC Overhead (Time and Count)', fontsize=14, pad=15)
    
    lines_1, labels_1 = ax1.get_legend_handles_labels()
    lines_2, labels_2 = ax2.get_legend_handles_labels()
    ax1.legend(lines_1 + lines_2, labels_1 + labels_2, loc='upper left', bbox_to_anchor=(1.1, 1))
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/4_gc_overhead.png', dpi=300)
    plt.close()

# 5. Execution Time Variability
def plot_variability():
    plt.figure(figsize=(10, 6))
    
    pivot_mean = df_timing_agg.pivot(index='split', columns='architecture', values='mean_ms')
    pivot_stdev = df_timing_agg.pivot(index='split', columns='architecture', values='stdev_ms')
    
    archs = ['Sequential Baseline', 'PARALLEL_EVALUATION', 'FULLY_PARALLEL', 'Cluster V3']
    pivot_mean = pivot_mean[archs]
    pivot_stdev = pivot_stdev[archs]
    
    pivot_mean.plot(kind='bar', yerr=pivot_stdev, capsize=4, ax=plt.gca(), color=[colors[col] for col in archs], width=0.8, error_kw=dict(ecolor='black', lw=1.5, capthick=1.5))
    
    plt.title('Execution Time Variability (Mean ± Stdev)', fontsize=14, pad=15)
    plt.ylabel('Time per Operation (ms/op)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.xticks(rotation=0)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/5_time_variability.png', dpi=300)
    plt.close()

# 6. Cluster V3 Workload
def plot_cluster_workload():
    plt.figure(figsize=(10, 6))
    
    df_cluster['split'] = pd.Categorical(df_cluster['split'], categories=splits_order, ordered=True)
    pivot = df_cluster.pivot(index='split', columns='cluster', values='rules_fired')
    pivot = pivot / 1e6 # millions
    
    clusters = ['C1 (Minor)', 'C2 (Bot)', 'C3 (Content+Vandalism)', 'C4 (Discussion)']
    pivot = pivot[clusters]
    
    pivot.plot(kind='bar', stacked=True, color=[cluster_colors[c] for c in clusters], ax=plt.gca(), width=0.6)
    
    plt.title('Cluster V3 - Per-Session Rules Fired', fontsize=14, pad=15)
    plt.ylabel('Rules Fired (Millions)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.xticks(rotation=0)
    plt.legend(title='Cluster', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/6_cluster_workload.png', dpi=300)
    plt.close()

# 7. Time Scaling Linear Fit
def plot_time_scaling():
    plt.figure(figsize=(10, 6))
    
    x_numeric = np.array(numeric_splits) / 1000 # thousands
    
    for arch in ['Sequential Baseline', 'PARALLEL_EVALUATION', 'FULLY_PARALLEL', 'Cluster V3']:
        data = df_perf_agg[df_perf_agg['architecture'] == arch].sort_values('split')
        y = data['time_ms_op'].values
        
        plt.scatter(x_numeric, y, color=colors[arch], s=60, label=arch)
        
        # Linear regression using numpy
        slope, intercept = np.polyfit(x_numeric, y, 1)
        line = slope * x_numeric + intercept
        
        plt.plot(x_numeric, line, color=colors[arch], linestyle='--', alpha=0.7)

    plt.title('Time Scaling - Linear Fit', fontsize=14, pad=15)
    plt.ylabel('Execution Time (ms/op)', fontsize=12)
    plt.xlabel('Event Count (Thousands)', fontsize=12)
    plt.legend(title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    plt.savefig(f'{output_dir}/7_time_scaling_fit.png', dpi=300)
    plt.close()

if __name__ == '__main__':
    print("Generating plots...")
    plot_execution_time()
    plot_throughput()
    plot_memory()
    plot_gc_overhead()
    plot_variability()
    plot_cluster_workload()
    plot_time_scaling()
    print(f"All plots saved to {os.path.abspath(output_dir)}")
