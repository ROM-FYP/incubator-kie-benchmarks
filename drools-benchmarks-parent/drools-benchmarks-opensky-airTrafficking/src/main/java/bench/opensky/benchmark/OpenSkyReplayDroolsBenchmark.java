package bench.opensky.benchmark;

import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import bench.opensky.router.Router;
import bench.opensky.router.SessionManager;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH CEP-stream benchmark for the OpenSky air-traffic Drools rules.
 *
 * <p>Pattern follows {@code AbstractCEPBenchmark} from the drools-benchmarks module:
 * a <strong>fresh KieSession</strong> is created for every JMH measurement iteration,
 * and the {@code @Benchmark} method processes a fixed-size sliding window of events
 * (default: {@value #WINDOW_SIZE}) per operation. The pseudo-clock is advanced per
 * event so that {@code @Expires} annotations correctly expire stale derived facts.</p>
 *
 * <p>Throughput is reported in windows/second; average time is reported in ms/window.</p>
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 10, time = 5)
@Measurement(iterations = 5, time = 10)
public class OpenSkyReplayDroolsBenchmark {

    /** Number of events ingested per benchmark operation (one sliding window). */
    private static final int WINDOW_SIZE = 200;

    @Param({"data/opensky_flat_20260217_160412.jsonl"})
    private String dataset;

    @Param({"false"})
    private boolean profilingEnabled;

    @Param({"false"})
    private boolean causalTracingEnabled;

    @Param({"baseline", "routed", "parallel_broadcast", "parallel_alpha_routed", "cluster_two_session"})
    private String mode;

    @Param({"src/main/resources/tree_rules.txt"})
    private String treeRulesFile;

    @Param({"src/main/resources/rule_graph_full.ftree"})
    private String ftreeFile;

    // ---- loaded once at trial setup ----
    private List<OpenSkyStateVector> events;

    // ---- cluster_two_session: sessions live for the ENTIRE trial (not per iteration) ----
    private SessionManager trialSessionManager;
    private OpenSkyReplayEngine sharedEngine; // clock-less passthrough engine for two-session mode

    // ---- fresh per iteration (like AbstractCEPBenchmark) ----
    private OpenSkyReplayEngine engine;
    private int eventIndex;

    // ---- routed mode components ----
    private Router router;
    private bench.opensky.router.AlphaRouter alphaRouter;
    private SessionManager sessionManager;

    // -------------------------------------------------------------------------
    // JMH lifecycle
    // -------------------------------------------------------------------------

    /** Load event list once for the entire trial. Also initialises two-session cluster if needed. */
    @Setup(Level.Trial)
    public void loadData() throws IOException {
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        events = loader.loadFlat(dataset);
        System.out.println("[Setup] Loaded " + events.size() + " events");

        if ("cluster_two_session".equals(mode)) {
            System.setProperty("opensky.cluster.two.session", "true");
            trialSessionManager = new SessionManager();
            trialSessionManager.init("airTraffick_rules.drl", ftreeFile);
            System.clearProperty("opensky.cluster.two.session");
            sharedEngine = new OpenSkyReplayEngine(); // used only as a call target (no KieSession)
            System.out.println("[Setup] Two-session cluster ready: " + trialSessionManager.getActiveClusterIds());
        }
    }

    @org.openjdk.jmh.annotations.TearDown(Level.Trial)
    public void tearDownTrial() {
        if (trialSessionManager != null) {
            trialSessionManager.disposeAll();
            trialSessionManager = null;
        }
    }

    /**
     * Create a fresh KieSession for each measurement iteration.
     * This matches the pattern used by the reference CEP benchmarks and prevents
     * unbounded accumulation of derived facts (PairRiskState, etc.) across iterations.
     */
    @Setup(Level.Iteration)
    public void setupIteration() throws IOException {
        engine = new OpenSkyReplayEngine();

        if ("routed".equals(mode)) {
            // Routed mode: initialize Router + SessionManager
            router = new Router(treeRulesFile);
            sessionManager = new SessionManager();
            sessionManager.init("airTraffick_rules.drl", ftreeFile);
            // No baseline engine.init() needed in routed mode
        } else if ("parallel_broadcast".equals(mode)) {
            // Parallel broadcast mode (89/13 empirical check)
            System.setProperty("opensky.empirical.override", "true");
            sessionManager = new SessionManager();
            sessionManager.init("airTraffick_rules.drl", ftreeFile);
            System.clearProperty("opensky.empirical.override");
        } else if ("parallel_alpha_routed".equals(mode)) {
            // Parallel alpha mode (same 89/13 tree but with java router)
            System.setProperty("opensky.empirical.override", "true");
            alphaRouter = new bench.opensky.router.AlphaRouter();
            sessionManager = new SessionManager();
            sessionManager.init("airTraffick_rules.drl", ftreeFile);
            System.clearProperty("opensky.empirical.override");
        } else if ("cluster_two_session".equals(mode)) {
            // Sessions are long-lived (trial-level); nothing to do per iteration
        } else {
            // Baseline mode: single session
            engine.init();
            if (profilingEnabled) {
                engine.enableProfiling();
            }
            if (causalTracingEnabled) {
                engine.enableCausalTracing("causal_trace.jsonl");
            }
        }

        eventIndex = 0;
    }

    @TearDown(Level.Iteration)
    public void tearDownIteration() {
        if ("routed".equals(mode) || "parallel_broadcast".equals(mode) || "parallel_alpha_routed".equals(mode)) {
            if (sessionManager != null) {
                sessionManager.disposeAll();
                sessionManager = null;
            }
            router = null;
            alphaRouter = null;
        } else if ("cluster_two_session".equals(mode)) {
            // sessions are trial-scoped; do NOT dispose here
        } else {
            if (profilingEnabled && engine != null && engine.getProfilingLogger() != null) {
                engine.getProfilingLogger().printReport();
            }
            if (engine != null) engine.dispose();
        }
    }

    /**
     * Ingest one window of {@value #WINDOW_SIZE} events.
     * Wraps back to the start of the event list (without engine reset) when exhausted,
     * so the benchmark can run for the full measurement duration.
     *
     * @return total rule activations fired across the window
     */
    @Benchmark
    public int ingestWindow() {
        int total = 0;
        for (int i = 0; i < WINDOW_SIZE; i++) {
            if (eventIndex >= events.size()) {
                eventIndex = 0;  // wrap — session stays alive, clock keeps advancing
            }

            OpenSkyStateVector sv = events.get(eventIndex++);

            if ("routed".equals(mode)) {
                total += engine.ingestEventRouted(sv, router, sessionManager);
            } else if ("parallel_broadcast".equals(mode)) {
                total += engine.ingestEventBroadcastParallel(sv, sessionManager);
            } else if ("parallel_alpha_routed".equals(mode)) {
                total += engine.ingestEventAlphaRoutedParallel(sv, sessionManager, alphaRouter);
            } else if ("cluster_two_session".equals(mode)) {
                total += sharedEngine.ingestEventTwoSessionParallel(sv, trialSessionManager);
            } else {
                total += engine.ingestEvent(sv);
            }
        }
        return total;
    }

    // -------------------------------------------------------------------------
    // Main — run from uber-jar
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(OpenSkyReplayDroolsBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(10)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}
