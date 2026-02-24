package bench.opensky.benchmark;

import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for replaying OpenSky state vectors through Drools rules.
 *
 * <p>Each benchmark operation replays one snapshot (one 5-second cadence)
 * through the rule engine and measures throughput/latency.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
public class OpenSkyReplayDroolsBenchmark {

    // ---- @Param fields ----

    @Param({"data/opensky_flat_20260217_160412.jsonl"})
    private String dataset;

    @Param({"GROUP_BY_SNAPSHOT"})
    private String mode;

    @Param({"PSEUDO"})
    private String clock;

    @Param({"UPSERT_BY_ICAO24"})
    private String updateStrategy;

    @Param({"RETRACT_PREVIOUS_SNAPSHOT"})
    private String cleanupStrategy;

    @Param({"FIRE_ALL_PER_SNAPSHOT"})
    private String fireStrategy;

    @Param({"0"})
    private int batchSize;

    // ---- runtime state ----

    private List<List<OpenSkyStateVector>> snapshots;
    private OpenSkyReplayEngine engine;
    private int snapshotIndex;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        // Load data
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        OpenSkyJsonlLoader.Mode loaderMode = OpenSkyJsonlLoader.Mode.valueOf(mode);
        snapshots = loader.load(dataset, loaderMode);
        System.out.println("[Setup] Loaded " + snapshots.size() + " snapshots");

        // Create engine
        engine = new OpenSkyReplayEngine();
        engine.setUpdateStrategy(OpenSkyReplayEngine.UpdateStrategy.valueOf(updateStrategy));
        engine.setCleanupStrategy(OpenSkyReplayEngine.CleanupStrategy.valueOf(cleanupStrategy));
        engine.setFireStrategy(OpenSkyReplayEngine.FireStrategy.valueOf(fireStrategy));
        engine.init();
        System.out.println("[Setup] Engine initialized");

        snapshotIndex = 0;
    }

    @Benchmark
    public int replayOneSnapshot() {
        if (snapshotIndex >= snapshots.size()) {
            // Wrap around for continuous measurement
            snapshotIndex = 0;
            // Re-initialize engine for clean state
            engine.dispose();
            engine = new OpenSkyReplayEngine();
            engine.setUpdateStrategy(OpenSkyReplayEngine.UpdateStrategy.valueOf(updateStrategy));
            engine.setCleanupStrategy(OpenSkyReplayEngine.CleanupStrategy.valueOf(cleanupStrategy));
            engine.setFireStrategy(OpenSkyReplayEngine.FireStrategy.valueOf(fireStrategy));
            engine.init();
        }

        List<OpenSkyStateVector> snapshot = snapshots.get(snapshotIndex++);
        return engine.replaySnapshot(snapshot);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (engine != null) {
            engine.dispose();
        }
    }

    // ---- Main: run from uber-jar ----

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OpenSkyReplayDroolsBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}
