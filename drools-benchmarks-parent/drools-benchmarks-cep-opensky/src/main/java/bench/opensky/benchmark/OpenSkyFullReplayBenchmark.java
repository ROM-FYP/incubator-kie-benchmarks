/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package bench.opensky.benchmark;

import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Full-dataset SingleShotTime benchmark for the OpenSky AirTraffic Drools rules.
 *
 * <p><strong>Purpose — scalability correctness:</strong>
 * Replays the entire loaded dataset in a single fresh KieSession per JMH invocation.
 * Directly comparable with Wikimedia and Binance counterparts:
 * <ul>
 *   <li>{@code WikimediaJmhBenchmark}        — same SingleShotTime / full-replay pattern</li>
 *   <li>{@code BinanceFullDatasetBenchmark}  — same full-replay pattern (Throughput mode)</li>
 * </ul>
 *
 * <p><strong>Scalability signal:</strong> Processing N events takes ~N × per-event time,
 * so doubling the dataset doubles the reported time. This gives a true scalability
 * curve for crossover analysis (400k → 800k → 1200k → 1600k).
 *
 * <p><strong>Session lifecycle:</strong> {@code Level.Invocation} — fresh KieSession
 * for every measured invocation. No state accumulates between measurements.
 *
 * <p><strong>JMH mode:</strong> {@code SingleShotTime} — reports ms/op.
 *
 * <p><strong>Architectures (mode param):</strong>
 * <ul>
 *   <li>{@code baseline}            — single-session sequential evaluation</li>
 *   <li>{@code PARALLEL_EVALUATION} — Drools built-in parallel LHS evaluation</li>
 *   <li>{@code FULLY_PARALLEL}      — Drools built-in fully parallel (LHS + RHS)</li>
 * </ul>
 *
 * <p>Symmetric with {@code WikimediaJmhBenchmark} and {@code BinanceFullDatasetBenchmark}
 * across all three CEP benchmark domains.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class OpenSkyFullReplayBenchmark {

    // -------------------------------------------------------------------------
    // Benchmark parameters
    // -------------------------------------------------------------------------

    /** Classpath-relative path to the JSONL dataset file. */
    @Param({"src/main/resources/data/data/split_1600k.jsonl"})
    private String dataFile;

    /**
     * Execution mode.
     * <ul>
     *   <li>{@code baseline}            — sequential single-session</li>
     *   <li>{@code PARALLEL_EVALUATION} — parallel LHS evaluation only</li>
     *   <li>{@code FULLY_PARALLEL}      — parallel LHS + RHS</li>
     * </ul>
     */
    @Param({"baseline", "PARALLEL_EVALUATION", "FULLY_PARALLEL"})
    private String mode;

    // -------------------------------------------------------------------------
    // State: loaded once at trial setup, never mutated
    // -------------------------------------------------------------------------

    private List<OpenSkyStateVector> events;

    // -------------------------------------------------------------------------
    // State: created fresh per invocation
    // -------------------------------------------------------------------------

    private OpenSkyReplayEngine engine;

    // Per-invocation state
    private int lastRulesFired;
    private long invocationStartTime;

    // Cumulative trial-level metrics
    private long totalRulesFired;
    private long totalTimeElapsed;
    private int invocationCount;

    // -------------------------------------------------------------------------
    // JMH lifecycle
    // -------------------------------------------------------------------------

    /**
     * Load all events once per trial — NOT counted in benchmark time.
     */
    @Setup(Level.Trial)
    public void loadData() throws IOException {
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        events = loader.loadFlat(dataFile);
        totalRulesFired  = 0;
        totalTimeElapsed = 0;
        invocationCount  = 0;

        System.out.printf("%n=== OpenSky JMH Setup [mode=%s] ===%n", mode);
        System.out.printf("Loaded %,d events | dataset=%s%n", events.size(), dataFile);
        System.out.println("=============================================\n");
    }

    /**
     * Create a fresh engine / session for each measured invocation.
     * Setup time is NOT counted in the SingleShotTime measurement.
     */
    @Setup(Level.Invocation)
    public void setupInvocation() throws IOException {
        engine = new OpenSkyReplayEngine();
        switch (mode) {
            case "PARALLEL_EVALUATION":
            case "FULLY_PARALLEL":
                engine.init(mode);
                break;
            case "baseline":
            default:
                engine.init();
                break;
        }
        invocationStartTime = System.currentTimeMillis();
    }

    /**
     * Dispose the session after each measured invocation.
     * Teardown time is NOT counted in the SingleShotTime measurement.
     */
    @TearDown(Level.Invocation)
    public void tearDownInvocation() {
        long   duration   = System.currentTimeMillis() - invocationStartTime;
        double throughput = (duration > 0) ? (events.size() * 1000.0) / duration : 0;

        invocationCount++;
        totalRulesFired  += lastRulesFired;
        totalTimeElapsed += duration;

        System.out.printf("[%s Invocation %d] Events: %d | Rules fired: %,d | Duration: %d ms | Throughput: %.2f ev/s%n",
                mode, invocationCount, events.size(), lastRulesFired, duration, throughput);

        if (engine != null) {
            engine.dispose();
            engine = null;
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = (totalTimeElapsed > 0)
                ? (invocationCount * events.size() * 1000.0) / totalTimeElapsed : 0;

        System.out.printf("%n=== OpenSky Trial Summary [%s] ===%n", mode);
        System.out.println("Total invocations:     " + invocationCount);
        System.out.println("Events per invocation: " + events.size());
        System.out.printf("Total rules fired:     %,d%n", totalRulesFired);
        System.out.println("Total time elapsed:    " + totalTimeElapsed + " ms");
        System.out.printf("Avg throughput:        %.2f events/sec%n", avgThroughput);
        System.out.println("==========================================\n");
    }

    // -------------------------------------------------------------------------
    // Benchmark method — this is the measured region
    // -------------------------------------------------------------------------

    /**
     * Replay <strong>all</strong> loaded events through the CEP engine in a single pass.
     *
     * @return total rule activations fired (consumed by JMH blackhole to prevent DCE)
     */
    @Benchmark
    public int fullReplay() {
        int total = 0;
        for (OpenSkyStateVector sv : events) {
            total += engine.ingestEvent(sv);
        }
        lastRulesFired = total;
        return total;
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(OpenSkyFullReplayBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
