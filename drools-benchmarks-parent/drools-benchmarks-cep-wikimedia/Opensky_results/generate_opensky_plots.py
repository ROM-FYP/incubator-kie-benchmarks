import pandas as pd
import matplotlib.pyplot as plt
import os

# Set the style
plt.style.use('default')
plt.rcParams['font.family'] = 'sans-serif'
plt.rcParams['axes.grid'] = True
plt.rcParams['grid.alpha'] = 0.5
plt.rcParams['grid.color'] = '#cccccc'

# Output directory
output_dir = 'plots_v2'
os.makedirs(output_dir, exist_ok=True)

# Colors and markers mapping (consistent with wikimedia)
colors = {
    'Sequential Baseline': '#000000',
    'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)': '#2ca02c',
    'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)': '#d62728',
    'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)': '#9467bd',
    'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)': '#8c564b',
    'Rule_cluster_parallel': '#e377c2'
}

markers = {
    'Sequential Baseline': 'o',
    'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)': '^',
    'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)': 'v',
    'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)': '<',
    'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)': '>',
    'Rule_cluster_parallel': '*'
}

# The architectures we want to plot (in this specific order for legend)
architectures = [
    'Sequential Baseline',
    'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
    'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
    'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
    'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
    'Rule_cluster_parallel'
]

# Column to Architecture mapping based on user request
col_mapping = {
    'Baseline': 'Sequential Baseline',
    'PE_t2': 'Built-in Parallel (threads=2, parallelMode=PARALLEL_EVALUATION)',
    'FP_t2': 'Built-in Parallel (threads=2, parallelMode=FULLY_PARALLEL)',
    'PE_t4': 'Built-in Parallel (threads=4, parallelMode=PARALLEL_EVALUATION)',
    'FP_t4': 'Built-in Parallel (threads=4, parallelMode=FULLY_PARALLEL)',
    'Cluster': 'Rule_cluster_parallel'
}

def format_dataset(val):
    return f"{int(val)//1000}K"

def plot_execution_time():
    df = pd.read_csv('opensky_fullreplay_msop.csv')
    df['split'] = df['Dataset'].apply(format_dataset)
    df['split'] = pd.Categorical(df['split'], categories=['400K', '800K', '1200K', '1600K'], ordered=True)
    df = df.sort_values('split')
    
    plt.figure(figsize=(12, 7))
    
    for col, arch in col_mapping.items():
        if col in df.columns:
            plt.plot(df['split'], df[col], marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            
    plt.title('Execution Time vs. Dataset Size', fontsize=14, pad=15)
    plt.ylabel('Mean Time per Operation (ms/op)', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    
    handles, labels = plt.gca().get_legend_handles_labels()
    order = [labels.index(arch) for arch in architectures if arch in labels]
    plt.legend([handles[idx] for idx in order], [labels[idx] for idx in order], title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/1_execution_time_line.png', dpi=300)
    plt.close()

def plot_throughput():
    df = pd.read_csv('opensky_fullreplay_throughput.csv')
    df['split'] = df['Dataset'].apply(format_dataset)
    df['split'] = pd.Categorical(df['split'], categories=['400K', '800K', '1200K', '1600K'], ordered=True)
    df = df.sort_values('split')
    
    plt.figure(figsize=(12, 7))
    
    for col, arch in col_mapping.items():
        if col in df.columns:
            plt.plot(df['split'], df[col], marker=markers.get(arch, 'o'), 
                     linewidth=2, markersize=8, color=colors.get(arch), label=arch)
            
    plt.title('Throughput Scaling', fontsize=14, pad=15)
    plt.ylabel('Events Processed per Second', fontsize=12)
    plt.xlabel('Dataset Size', fontsize=12)
    plt.ylim(bottom=0)
    
    handles, labels = plt.gca().get_legend_handles_labels()
    order = [labels.index(arch) for arch in architectures if arch in labels]
    plt.legend([handles[idx] for idx in order], [labels[idx] for idx in order], title='Architecture', bbox_to_anchor=(1.05, 1), loc='upper left')
    
    plt.tight_layout()
    plt.savefig(f'{output_dir}/2_throughput_scaling_line.png', dpi=300)
    plt.close()

if __name__ == '__main__':
    print("Generating Opensky plots...")
    plot_execution_time()
    plot_throughput()
    print(f"Opensky plots saved to {os.path.abspath(output_dir)}")
