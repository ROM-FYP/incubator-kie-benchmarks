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

package org.kie.benchmark.binance;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Full-dataset CEP benchmark for Binance market risk control system.
 * Replays ALL events (all symbols, all stream types) in a single invocation.
 *
 * Benchmark Configuration:
 * - Rules: 108 (taxonomy.drl)
 * - Dataset: run_20260311_1340_10sym (1.6M events, 10 symbols, 30 minutes)
 * - Mode: Event-time replay with SessionPseudoClock
 *
 * Unlike BinanceRiskControlBenchmark (which filters per-symbol),
 * this benchmark replays the entire dataset per invocation.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 30, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 60, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "-Xms4g", "-Xmx4g" })
public class BinanceFullDatasetBenchmark {


    private BinanceRulesProvider rulesProvider;
    private BinanceEventProvider eventProvider;
    private List<MarketEvent> allEvents;
    private Set<String> symbols;
    private KieSession kieSession;
    private EventReplayController replayController;

    // Per-invocation metrics
    private long invocationStartTime;
    private int lastRulesFired;

    // Cumulative trial-level metrics
    private long totalEventsProcessed;
    private long totalRulesFired;
    private long totalTimeElapsed;
    private int invocationCount;

    /**
     * Setup: Load rules and ALL events once per trial.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        // Load rules (compile DRL)
        rulesProvider = new BinanceRulesProvider();

        // Load ALL events from dataset (no symbol filtering)
        eventProvider = new BinanceEventProvider();
        allEvents = eventProvider.getEvents();

        // Extract unique symbols for bootstrap facts
        symbols = allEvents.stream()
                .map(MarketEvent::getSymbol)
                .collect(Collectors.toSet());

        // Reset cumulative counters
        totalEventsProcessed = 0;
        totalRulesFired = 0;
        totalTimeElapsed = 0;
        invocationCount = 0;

        System.out.println("=== Full Dataset Benchmark Setup ===");
        System.out.println("Total events per invocation: " + allEvents.size());
        System.out.println("Symbols: " + symbols);
        System.out.println("Dataset: " + eventProvider.getDatasetId());
    }

    /**
     * Setup: Create fresh KieSession for each benchmark invocation.
     */
    @Setup(Level.Invocation)
    public void setupInvocation() {
        kieSession = rulesProvider.createSession();
        replayController = new EventReplayController(kieSession);

        // Register alerts channel (rules emit RiskSignals via channels["alerts"])
        kieSession.registerChannel("alerts", obj -> { /* no-op sink */ });

        // Insert bootstrap facts for ALL symbols
        insertBootstrapFacts();

        invocationStartTime = System.currentTimeMillis();
    }

    /**
     * Benchmark method: Replay ALL events and measure throughput.
     */
    @Benchmark
    public int benchmarkFullReplay() {
        lastRulesFired = replayController.replayEvents(allEvents);
        return lastRulesFired;
    }

    /**
     * Teardown: Dispose session after each invocation.
     */
    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        long duration = System.currentTimeMillis() - invocationStartTime;
        double throughput = (duration > 0) ? (allEvents.size() * 1000.0) / duration : 0;

        // Accumulate trial-level totals
        invocationCount++;
        totalEventsProcessed += allEvents.size();
        totalRulesFired += lastRulesFired;
        totalTimeElapsed += duration;

        System.out.println("[Invocation " + invocationCount + "] "
                + "Events: " + allEvents.size()
                + " | Rules fired: " + lastRulesFired
                + " | Duration: " + duration + " ms"
                + " | Throughput: " + String.format("%.2f", throughput) + " events/sec");

        if (kieSession != null) {
            kieSession.dispose();
        }
    }

    /**
     * Teardown: Cleanup after all iterations and print summary.
     */
    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = (totalTimeElapsed > 0)
                ? (totalEventsProcessed * 1000.0) / totalTimeElapsed
                : 0;
        System.out.println("\n=== Full Dataset Trial Summary ===");
        System.out.println("Total invocations:      " + invocationCount);
        System.out.println("Total events processed: " + totalEventsProcessed);
        System.out.println("Total rules fired:      " + totalRulesFired);
        System.out.println("Total time elapsed:     " + totalTimeElapsed + " ms"
                + " (" + String.format("%.2f", totalTimeElapsed / 1000.0) + " s)");
        System.out.println("Avg throughput:         " + String.format("%.2f", avgThroughput) + " events/sec");
        System.out.println("==================================\n");

        if (rulesProvider != null) {
            rulesProvider.dispose();
        }
    }

    /**
     * Insert bootstrap facts for ALL symbols in the dataset.
     */
    private void insertBootstrapFacts() {
        for (String sym : symbols) {
            kieSession.insert(new RiskConfig(sym));
            kieSession.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
            kieSession.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
        }
        kieSession.fireAllRules();
    }

    /**
     * Main method for quick testing (not JMH).
     */
    public static void main(String[] args) {
        BinanceFullDatasetBenchmark benchmark = new BinanceFullDatasetBenchmark();

        try {
            benchmark.setupTrial();
            benchmark.setupInvocation();

            long startTime = System.currentTimeMillis();
            int rulesFired = benchmark.benchmarkFullReplay();
            long endTime = System.currentTimeMillis();

            long duration = endTime - startTime;
            double eventsPerSec = (benchmark.allEvents.size() * 1000.0) / duration;

            System.out.println("\n=== Quick Test Results (Full Dataset) ===");
            System.out.println("Events processed: " + benchmark.allEvents.size());
            System.out.println("Symbols:          " + benchmark.symbols);
            System.out.println("Rules fired:      " + rulesFired);
            System.out.println("Duration:         " + duration + " ms");
            System.out.println("Throughput:       " + String.format("%.2f", eventsPerSec) + " events/sec");

            benchmark.teardownInvocation();
            benchmark.teardownTrial();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
