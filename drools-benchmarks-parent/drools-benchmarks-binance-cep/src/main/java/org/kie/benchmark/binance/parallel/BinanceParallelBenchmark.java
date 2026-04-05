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
package org.kie.benchmark.binance.parallel;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JMH benchmark comparing single-session vs parallel multi-session rule execution.
 *
 * <p>Splits the 110-rule taxonomy.drl into structurally-safe parallel clusters
 * based on dependency-graph phases and Infomap community-detection, then runs
 * each cluster in its own KieSession with phase-ordered synchronization.</p>
 *
 * <p>Also includes a {@link #main(String[])} method for quick non-JMH correctness testing.</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 30, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 60, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "-Xms4g", "-Xmx4g" })
public class BinanceParallelBenchmark {

    @Param({ "2" })
    private int poolSize;

    private BinanceEventProvider eventProvider;
    private List<MarketEvent> allEvents;
    private Set<String> symbols;
    private ParallelRuleOrchestrator orchestrator;

    // Per-invocation metrics
    private long invocationStartTime;
    private int lastRulesFired;

    // Cumulative trial-level metrics
    private long totalEventsProcessed;
    private long totalRulesFired;
    private long totalTimeElapsed;
    private int invocationCount;

    /**
     * Setup: Parse DRL + .ftree → build PartitionPlan → create orchestrator.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        try {
            // Load events
            eventProvider = new BinanceEventProvider();
            allEvents = eventProvider.getEvents();
            symbols = allEvents.stream()
                    .map(MarketEvent::getSymbol)
                    .collect(Collectors.toSet());

            // Load DRL content
            String drlContent;
            try (InputStream is = getClass().getResourceAsStream("/rules/taxonomy.drl")) {
                if (is == null) throw new RuntimeException("taxonomy.drl not found");
                drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Build partition plan
            try (InputStream ftreeIs = getClass().getResourceAsStream(
                    "/clusters/binance_rule_graph.ftree")) {
                if (ftreeIs == null) throw new RuntimeException("ftree file not found");
                PartitionPlan plan = ClusterPartitioner.buildFromResources(drlContent, ftreeIs);
                System.out.println(plan);

                // Create orchestrator
                orchestrator = new ParallelRuleOrchestrator(plan, poolSize, symbols);
            }

            // Reset cumulative counters
            totalEventsProcessed = 0;
            totalRulesFired = 0;
            totalTimeElapsed = 0;
            invocationCount = 0;

            System.out.println("=== Parallel Benchmark Setup ===");
            System.out.println("Total events per invocation: " + allEvents.size());
            System.out.println("Symbols: " + symbols);
            System.out.println("Pool size: " + poolSize);

        } catch (Exception e) {
            throw new RuntimeException("Parallel benchmark setup failed", e);
        }
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        invocationStartTime = System.currentTimeMillis();
    }

    /**
     * Benchmark method: Replay ALL events through parallel orchestrator.
     */
    @Benchmark
    public int benchmarkParallelReplay() {
        lastRulesFired = orchestrator.replayEvents(allEvents);
        return lastRulesFired;
    }

    /**
     * Teardown: Record metrics after each invocation.
     */
    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        long duration = System.currentTimeMillis() - invocationStartTime;
        double throughput = (duration > 0) ? (allEvents.size() * 1000.0) / duration : 0;

        invocationCount++;
        totalEventsProcessed += allEvents.size();
        totalRulesFired += lastRulesFired;
        totalTimeElapsed += duration;

        System.out.println("[Parallel Invocation " + invocationCount + "] "
                + "Events: " + allEvents.size()
                + " | Rules fired: " + lastRulesFired
                + " | Duration: " + duration + " ms"
                + " | Throughput: " + String.format("%.2f", throughput) + " events/sec");
    }

    /**
     * Teardown: Print summary and dispose resources.
     */
    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = (totalTimeElapsed > 0)
                ? (totalEventsProcessed * 1000.0) / totalTimeElapsed : 0;

        System.out.println("\n=== Parallel Benchmark Trial Summary ===");
        System.out.println("Pool size:              " + poolSize);
        System.out.println("Total invocations:      " + invocationCount);
        System.out.println("Total events processed: " + totalEventsProcessed);
        System.out.println("Total rules fired:      " + totalRulesFired);
        System.out.println("Total time elapsed:     " + totalTimeElapsed + " ms"
                + " (" + String.format("%.2f", totalTimeElapsed / 1000.0) + " s)");
        System.out.println("Avg throughput:         " + String.format("%.2f", avgThroughput) + " events/sec");
        System.out.println("=========================================\n");

        if (orchestrator != null) {
            orchestrator.dispose();
        }
    }

    // =========================================================================
    // Quick test main() method (non-JMH)
    // =========================================================================

    /**
     * Quick non-JMH test that runs both single-session and parallel benchmarks
     * and compares rule-fire counts for correctness validation.
     */
    public static void main(String[] args) {
        try {
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  Parallel KieSession Benchmark - Quick Test  ║");
            System.out.println("╚══════════════════════════════════════════════╝\n");

            // ------------------------------------------------------------------
            // Step 1: Load shared resources
            // ------------------------------------------------------------------
            BinanceEventProvider eventProvider = new BinanceEventProvider();
            List<MarketEvent> events = eventProvider.getEvents();
            Set<String> symbols = events.stream()
                    .map(MarketEvent::getSymbol)
                    .collect(Collectors.toSet());

            System.out.println("Events: " + events.size() + " | Symbols: " + symbols.size());

            // ------------------------------------------------------------------
            // Step 2: Single-session baseline
            // ------------------------------------------------------------------
            System.out.println("\n── Single-Session Baseline ──────────────────");
            BinanceRulesProvider rulesProvider = new BinanceRulesProvider();
            KieSession baselineSession = rulesProvider.createSession();
            EventReplayController controller = new EventReplayController(baselineSession);

            // Insert bootstrap facts
            for (String sym : symbols) {
                baselineSession.insert(new RiskConfig(sym));
                baselineSession.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
                baselineSession.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
            }
            baselineSession.fireAllRules();

            long baselineStart = System.currentTimeMillis();
            int baselineFired = controller.replayEvents(events);
            long baselineDuration = System.currentTimeMillis() - baselineStart;

            baselineSession.dispose();
            rulesProvider.dispose();

            System.out.println("Rules fired:  " + baselineFired);
            System.out.println("Duration:     " + baselineDuration + " ms");
            System.out.println("Throughput:   " + String.format("%.2f",
                    events.size() * 1000.0 / baselineDuration) + " events/sec");

            // ------------------------------------------------------------------
            // Step 3: Parallel execution
            // ------------------------------------------------------------------
            System.out.println("\n── Parallel Execution ──────────────────────");

            String drlContent;
            try (InputStream is = BinanceParallelBenchmark.class
                    .getResourceAsStream("/rules/taxonomy.drl")) {
                drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            PartitionPlan plan;
            try (InputStream ftreeIs = BinanceParallelBenchmark.class
                    .getResourceAsStream("/clusters/binance_rule_graph.ftree")) {
                plan = ClusterPartitioner.buildFromResources(drlContent, ftreeIs);
            }

            System.out.println(plan);

            int poolSize = 2;
            ParallelRuleOrchestrator orchestrator =
                    new ParallelRuleOrchestrator(plan, poolSize, symbols);

            long parallelStart = System.currentTimeMillis();
            int parallelFired = orchestrator.replayEvents(events);
            long parallelDuration = System.currentTimeMillis() - parallelStart;

            orchestrator.dispose();

            System.out.println("Rules fired:  " + parallelFired);
            System.out.println("Duration:     " + parallelDuration + " ms");
            System.out.println("Throughput:   " + String.format("%.2f",
                    events.size() * 1000.0 / parallelDuration) + " events/sec");
            System.out.println("Pool size:    " + poolSize);

            // ------------------------------------------------------------------
            // Step 4: Comparison
            // ------------------------------------------------------------------
            System.out.println("\n── Comparison ──────────────────────────────");
            System.out.println(String.format("%-25s %15s %15s", "", "Single", "Parallel"));
            System.out.println(String.format("%-25s %,15d %,15d", "Rules fired", baselineFired, parallelFired));
            System.out.println(String.format("%-25s %,15d ms %,12d ms", "Duration", baselineDuration, parallelDuration));
            System.out.println(String.format("%-25s %15.2f %15.2f", "Events/sec",
                    events.size() * 1000.0 / baselineDuration,
                    events.size() * 1000.0 / parallelDuration));

            double speedup = (double) baselineDuration / parallelDuration;
            System.out.println(String.format("%-25s %15s %14.2fx", "Speedup", "1.00x", speedup));

            boolean match = baselineFired == parallelFired;
            System.out.println("\nCorrectness: " + (match ? "✅ PASS" : "❌ FAIL")
                    + " (rules fired " + (match ? "match" : "MISMATCH") + ")");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
