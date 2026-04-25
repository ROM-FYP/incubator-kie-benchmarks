package bench.opensky.runner;

import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;

import java.util.List;

/**
 * Standalone runner that feeds the ENTIRE dataset through the engine exactly once
 * with causal trace logging enabled.
 *
 * Usage (from the benchmarks uber-jar):
 *   java -cp target/benchmarks.jar bench.opensky.runner.CausalTraceRunner \
 *        [dataset-resource] [output-file]
 *
 * Defaults:
 *   dataset  → data/opensky_flat_20260217_160412.jsonl  (classpath resource)
 *   output   → causal_trace_full.jsonl
 */
public class CausalTraceRunner {

    public static void main(String[] args) throws Exception {
        String dataset    = args.length > 0 ? args[0] : "data/opensky_flat_20260217_160412.jsonl";
        String outputFile = args.length > 1 ? args[1] : "causal_trace_full.jsonl";

        System.out.printf("[CausalTraceRunner] Loading dataset: %s%n", dataset);
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat(dataset);
        System.out.printf("[CausalTraceRunner] Loaded %,d events%n", events.size());

        OpenSkyReplayEngine engine = new OpenSkyReplayEngine();
        engine.init();
        engine.enableCausalTracing(outputFile);

        System.out.printf("[CausalTraceRunner] Ingesting all %,d events → %s%n", events.size(), outputFile);
        long t0 = System.currentTimeMillis();

        int totalFirings = 0;
        for (int i = 0; i < events.size(); i++) {
            totalFirings += engine.ingestEvent(events.get(i));
            if ((i + 1) % 5000 == 0) {
                System.out.printf("[CausalTraceRunner]   %,d / %,d events ingested...%n", i + 1, events.size());
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        System.out.printf("[CausalTraceRunner] Done.  Total rule firings: %,d  |  Time: %.1f s%n",
                totalFirings, elapsed / 1000.0);

        engine.dispose();
        System.out.printf("[CausalTraceRunner] Trace written to: %s%n", outputFile);
    }
}
