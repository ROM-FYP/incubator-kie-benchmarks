package bench.opensky.benchmark;

import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Standalone peak-heap memory profiling tool for OpenSky architectures.
 *
 * <p><strong>Purpose:</strong> Measures the peak live-heap footprint of each
 * CEP architecture when processing a full dataset. This is NOT a JMH benchmark
 * — it is a controlled standalone measurement program designed for research-grade
 * memory analysis.
 *
 * <p><strong>Measurement protocol (per trial):</strong>
 * <ol>
 *   <li>Force 2× GC + settle → read {@code jvm_baseline_mb} (JVM + loaded data, no session)</li>
 *   <li>Create engine / session(s) for this architecture</li>
 *   <li>Replay ALL events through the engine</li>
 *   <li>Force 2× GC + settle → read {@code peak_heap_mb} (session still alive, WM at max)</li>
 *   <li>Dispose session(s)</li>
 *   <li>Force 2× GC + settle → read {@code post_dispose_mb} (session gone, only JVM + data)</li>
 *   <li>{@code delta_mb = peak_heap_mb − post_dispose_mb} ← architectural WM cost</li>
 * </ol>
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * java -Xms4g -Xmx4g -cp benchmarks.jar bench.opensky.benchmark.HeapProfileMain \
 *   --dataset data/data/split_400k.jsonl \
 *   --mode    baseline \
 *   --trials  5 \
 *   --output  /path/to/heap_baseline.json
 * </pre>
 *
 * <p>For built-in parallel modes, pass
 * {@code -Djava.util.concurrent.ForkJoinPool.common.parallelism=N} as a JVM arg;
 * the tool reads this property automatically and records it in the output JSON.
 */
public class HeapProfileMain {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Two GC cycles with this settle gap ensures G1GC major collection completes. */
    private static final long GC_SETTLE_MS = 250;

    private static final MemoryMXBean MX = ManagementFactory.getMemoryMXBean();

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws Exception {

        // ── Parse CLI arguments ──────────────────────────────────────────
        String dataset  = null;
        String mode     = "baseline";
        int    trials   = 5;
        String ftreeFile = "src/main/resources/rule_graph_full.ftree";
        String outputFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dataset": dataset   = args[++i]; break;
                case "--mode":    mode      = args[++i]; break;
                case "--trials":  trials    = Integer.parseInt(args[++i]); break;
                case "--ftree":   ftreeFile = args[++i]; break;
                case "--output":  outputFile = args[++i]; break;
                default: /* ignore unknown flags */ break;
            }
        }

        if (dataset == null || outputFile == null) {
            System.err.println("Usage: HeapProfileMain --dataset <path> --mode <mode>" +
                    " [--trials <n>] [--ftree <path>] --output <file>");
            System.exit(1);
        }

        // Read thread count from ForkJoinPool system property (set by caller via -D flag)
        int threads = Integer.getInteger(
                "java.util.concurrent.ForkJoinPool.common.parallelism", 1);

        // JVM metadata
        long xmxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String jvm = System.getProperty("java.version")
                + " (" + System.getProperty("java.vm.name") + ")";

        // ── Load events once (not counted in heap measurement) ──────────
        System.out.printf("[HeapProfileMain] Loading dataset: %s%n", dataset);
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat(dataset);
        System.out.printf("[HeapProfileMain] Loaded %,d events | mode=%s | threads=%d | trials=%d%n",
                events.size(), mode, threads, trials);
        System.out.printf("[HeapProfileMain] JVM: %s | Xmx=%d MB%n", jvm, xmxMb);

        // ── Run measurement trials ────────────────────────────────────────
        List<Map<String, Object>> trialResults = new ArrayList<>();

        for (int t = 1; t <= trials; t++) {
            System.out.printf("%n[Trial %d/%d] ------------------------------------------%n", t, trials);
            Map<String, Object> result = runTrial(t, mode, ftreeFile, events);
            trialResults.add(result);
            System.out.printf("[Trial %d/%d] jvm_baseline=%.1f MB | peak=%.1f MB | " +
                            "post_dispose=%.1f MB | delta=%.1f MB | rules_fired=%,d%n",
                    t, trials,
                    (double) result.get("jvm_baseline_mb"),
                    (double) result.get("peak_heap_mb"),
                    (double) result.get("post_dispose_mb"),
                    (double) result.get("delta_mb"),
                    (int) result.get("rules_fired"));
        }

        // ── Compute medians ──────────────────────────────────────────────
        double medianPeak       = median(trialResults, "peak_heap_mb");
        double medianPostDispose = median(trialResults, "post_dispose_mb");
        double medianDelta      = median(trialResults, "delta_mb");

        System.out.printf("%n[HeapProfileMain] SUMMARY:%n");
        System.out.printf("  median_peak_heap_mb   = %.2f MB%n", medianPeak);
        System.out.printf("  median_post_dispose_mb = %.2f MB%n", medianPostDispose);
        System.out.printf("  median_delta_mb        = %.2f MB  ← architectural WM footprint%n", medianDelta);

        // ── Write JSON output ─────────────────────────────────────────────
        writeJson(outputFile, dataset, events.size(), mode, threads,
                trials, jvm, xmxMb, trialResults, medianPeak, medianPostDispose, medianDelta);
        System.out.printf("%n[HeapProfileMain] Results written to: %s%n", outputFile);
    }

    // -----------------------------------------------------------------------
    // Trial execution
    // -----------------------------------------------------------------------

    private static Map<String, Object> runTrial(int trialNum, String mode, String ftreeFile,
            List<OpenSkyStateVector> events) throws Exception {

        forceGc();
        double jvmBaselineMb = heapUsedMb();

        OpenSkyReplayEngine engine;
        switch (mode) {
            case "PARALLEL_EVALUATION":
            case "FULLY_PARALLEL":
                engine = new OpenSkyReplayEngine();
                engine.init(mode);
                break;
            default: // baseline
                engine = new OpenSkyReplayEngine();
                engine.init();
                break;
        }

        int totalFired = 0;
        for (OpenSkyStateVector sv : events) {
            totalFired += engine.ingestEvent(sv);
        }

        forceGc();
        double peakHeapMb = heapUsedMb();

        engine.dispose();
        engine = null;

        forceGc();
        double postDisposeMb = heapUsedMb();

        double deltaMb = peakHeapMb - postDisposeMb;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("trial",           trialNum);
        r.put("jvm_baseline_mb", jvmBaselineMb);
        r.put("peak_heap_mb",    peakHeapMb);
        r.put("post_dispose_mb", postDisposeMb);
        r.put("delta_mb",        deltaMb);
        r.put("rules_fired",     totalFired);
        return r;
    }

    // -----------------------------------------------------------------------
    // GC helpers
    // -----------------------------------------------------------------------

    /**
     * Forces two GC cycles with a {@value GC_SETTLE_MS} ms settle between them.
     * Two cycles are needed because G1GC may only perform minor collections on
     * the first call, promoting tenured objects that the second call then evicts.
     */
    private static void forceGc() throws InterruptedException {
        System.gc();
        Thread.sleep(GC_SETTLE_MS);
        System.gc();
        Thread.sleep(GC_SETTLE_MS);
    }

    private static double heapUsedMb() {
        return MX.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0);
    }

    // -----------------------------------------------------------------------
    // Statistics
    // -----------------------------------------------------------------------

    private static double median(List<Map<String, Object>> results, String key) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> r : results) {
            Object v = r.get(key);
            values.add(v instanceof Double ? (Double) v : ((Number) v).doubleValue());
        }
        Collections.sort(values);
        int n = values.size();
        return (n % 2 == 0)
                ? (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0
                : values.get(n / 2);
    }

    // -----------------------------------------------------------------------
    // JSON serialisation (no external library dependency)
    // -----------------------------------------------------------------------

    private static void writeJson(
            String outputFile, String dataset, int eventCount, String mode, int threads,
            int trialsRequested, String jvm, long xmxMb,
            List<Map<String, Object>> trials,
            double medianPeak, double medianPostDispose, double medianDelta) throws IOException {

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"project\": \"opensky\",\n"));
        sb.append(String.format("  \"dataset\": \"%s\",\n", dataset));
        sb.append(String.format("  \"events\": %d,\n", eventCount));
        sb.append(String.format("  \"mode\": \"%s\",\n", mode));
        sb.append(String.format("  \"threads\": %d,\n", threads));
        sb.append(String.format("  \"trials_requested\": %d,\n", trialsRequested));
        sb.append(String.format("  \"jvm\": \"%s\",\n", jvm.replace("\"", "'")));
        sb.append(String.format("  \"xmx_mb\": %d,\n", xmxMb));
        sb.append("  \"trials\": [\n");

        for (int i = 0; i < trials.size(); i++) {
            Map<String, Object> r = trials.get(i);
            sb.append("    {\n");
            sb.append(String.format("      \"trial\": %d,\n",           ((Number) r.get("trial")).intValue()));
            sb.append(String.format("      \"jvm_baseline_mb\": %.2f,\n", (double) r.get("jvm_baseline_mb")));
            sb.append(String.format("      \"peak_heap_mb\": %.2f,\n",    (double) r.get("peak_heap_mb")));
            sb.append(String.format("      \"post_dispose_mb\": %.2f,\n", (double) r.get("post_dispose_mb")));
            sb.append(String.format("      \"delta_mb\": %.2f,\n",        (double) r.get("delta_mb")));
            sb.append(String.format("      \"rules_fired\": %d\n",        ((Number) r.get("rules_fired")).intValue()));
            sb.append(i < trials.size() - 1 ? "    },\n" : "    }\n");
        }

        sb.append("  ],\n");
        sb.append(String.format("  \"median_peak_heap_mb\": %.2f,\n",    medianPeak));
        sb.append(String.format("  \"median_post_dispose_mb\": %.2f,\n", medianPostDispose));
        sb.append(String.format("  \"median_delta_mb\": %.2f\n",         medianDelta));
        sb.append("}\n");

        Files.createDirectories(Paths.get(outputFile).getParent());
        Files.write(Paths.get(outputFile), sb.toString().getBytes());
    }
}
