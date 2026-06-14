#!/bin/bash
# Exit immediately if a command exits with a non-zero status
set -e

# Target JAR and Results Directory relative to the execution root (drools-benchmarks-ripe-ris-cep)
JAR_PATH="target/drools-benchmarks-ripe-ris-cep.jar"
RESULTS_DIR="results"

# Verify that the compiled shaded JAR exists before starting
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: Shaded JAR not found at $JAR_PATH."
    echo "Please build the project first using: mvn clean package -DskipTests"
    exit 1
fi

# Create results directory if it doesn't exist
mkdir -p "$RESULTS_DIR"

# Define 4 architectures: "RunnerClassName:friendly_name"
ARCHITECTURES=(
    "RipeRisSequentialJmhRunner:sequential"
    "RipeRisParallelEvalJmhRunner:parallel_eval"
    "RipeRisFullyParallelJmhRunner:fully_parallel"
    "RipeRisClusterJmhRunner:cluster"
)

# Define 4 data levels: "EnvKey:friendly_data_count"
DATASETS=(
    "RIPERIS_DATA_FILE_0_4M:400K"
    "RIPERIS_DATA_FILE_0_8M:800K"
    "RIPERIS_DATA_FILE_1_2M:1200K"
    "RIPERIS_DATA_FILE_1_6M:1600K"
)

echo "=============================================================="
echo "Starting RIPE RIS CEP Benchmark Suite (16 combinations)"
echo "Warmup: 2 iterations | Measurement: 5 iterations | Forks: 1"
echo "Results target: $RESULTS_DIR/"
echo "=============================================================="
echo ""

# Loop over architectures and datasets to run all 16 combinations one by one
for arch_entry in "${ARCHITECTURES[@]}"; do
    # Split the entry by colon
    runner_class="${arch_entry%%:*}"
    arch_name="${arch_entry#*:}"

    for dataset_entry in "${DATASETS[@]}"; do
        # Split the entry by colon
        param_value="${dataset_entry%%:*}"
        data_count="${dataset_entry#*:}"

        output_file="${RESULTS_DIR}/${arch_name}_${data_count}.json"

        echo "--------------------------------------------------------------"
        echo "Running Combination:"
        echo "  Architecture : $arch_name ($runner_class)"
        echo "  Data Size    : $data_count ($param_value)"
        echo "  Output File  : $output_file"
        echo "--------------------------------------------------------------"

        # Execute JMH benchmark via shaded JAR
        # -wi 2  : 2 warmup iterations
        # -i 5   : 5 measurement iterations
        # -f 1   : 1 fork (runs on a dedicated JVM)
        # -prof gc : Attach GC/allocation profiler to record memory metrics
        # The runner class will write the custom, clean results JSON to: $output_file
        # We redirect the standard console output to a log file in results/
        if ! java -Djmh.ignoreLock=true -Dwarmup.iterations=2 -jar "$JAR_PATH" "$runner_class" \
            -p dataFile="$param_value" \
            -wi 2 -i 5 -f 1 \
            -prof gc \
            > "${output_file%.json}.log" 2>&1; then
            echo ""
            echo "=============================================================="
            echo "ERROR: Benchmark run failed for combination:"
            echo "  Architecture : $arch_name ($runner_class)"
            echo "  Data Size    : $data_count ($param_value)"
            echo "  Log File     : ${output_file%.json}.log"
            echo "=============================================================="
            echo "Last 30 lines of log output:"
            echo "--------------------------------------------------------------"
            tail -n 30 "${output_file%.json}.log"
            echo "--------------------------------------------------------------"
            exit 1
        fi

        echo "Finished combination. Saved results to $output_file"
        echo ""
    done
done

echo "=============================================================="
echo "All 16 benchmark combinations completed successfully."
echo "Results are stored in: $(pwd)/$RESULTS_DIR/"
echo "=============================================================="
