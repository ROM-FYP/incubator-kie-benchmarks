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
import java.util.concurrent.TimeUnit;

/**
 * Baseline CEP benchmark for Binance market risk control system.
 * Measures throughput and latency for 108-rule taxonomy with single-symbol data.
 *
 * Benchmark Configuration:
 * - Rules: 108 (taxonomy.drl)
 * - Dataset: run_20260311_1340_10sym (1.6M events, 10 symbols, 30 minutes)
 * - Mode: Event-time replay with SessionPseudoClock
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "-Xms4g", "-Xmx4g" })
public class BinanceRiskControlBenchmark {

    @Param({ "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
            "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "LINKUSDT", "ARBUSDT" })
    private String symbol;


    private BinanceRulesProvider rulesProvider;
    private BinanceEventProvider eventProvider;
    private List<MarketEvent> events;
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
     * Setup: Load rules and events once per benchmark iteration.
     */
    @Setup(Level.Trial)
    public void setupTrial() {
        // Load rules (compile DRL)
        rulesProvider = new BinanceRulesProvider();

        // Load events from dataset
        eventProvider = new BinanceEventProvider();
        events = eventProvider.getEventsForSymbol(symbol);

        // Reset cumulative counters
        totalEventsProcessed = 0;
        totalRulesFired = 0;
        totalTimeElapsed = 0;
        invocationCount = 0;

        System.out.println("=== Benchmark Setup ===");
        System.out.println("Symbol: " + symbol);
        System.out.println("Events per invocation: " + events.size());
        System.out.println("Dataset: " + eventProvider.getDatasetId());
    }

    /**
     * Setup: Create fresh KieSession for each benchmark invocation.
     */
    @Setup(Level.Invocation)
    public void setupInvocation() {
        // Create new session with SessionPseudoClock
        kieSession = rulesProvider.createSession();
        replayController = new EventReplayController(kieSession);

        // Insert initial facts (RiskConfig, ModeState, FeedHealth)
        insertBootstrapFacts();

        // Start timing for per-invocation metrics
        invocationStartTime = System.currentTimeMillis();
    }

    /**
     * Benchmark method: Replay events and measure throughput.
     * Returns events/sec processed.
     */
    @Benchmark
    public int benchmarkEventReplay() {
        lastRulesFired = replayController.replayEvents(events);
        return lastRulesFired;
    }

    /**
     * Teardown: Dispose session after each invocation.
     */
    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        // Calculate per-invocation metrics
        long duration = System.currentTimeMillis() - invocationStartTime;
        double throughput = (duration > 0) ? (events.size() * 1000.0) / duration : 0;

        // Accumulate trial-level totals
        invocationCount++;
        totalEventsProcessed += events.size();
        totalRulesFired += lastRulesFired;
        totalTimeElapsed += duration;

        System.out.println("[Invocation " + invocationCount + "] "
                + "Events: " + events.size()
                + " | Rules fired: " + lastRulesFired
                + " | Duration: " + duration + " ms"
                + " | Throughput: " + String.format("%.2f", throughput) + " events/sec");

        if (kieSession != null) {
            kieSession.dispose();
        }
    }

    /**
     * Teardown: Cleanup after all iterations.
     */
    @TearDown(Level.Trial)
    public void teardownTrial() {
        // Print cumulative trial summary
        double avgThroughput = (totalTimeElapsed > 0)
                ? (totalEventsProcessed * 1000.0) / totalTimeElapsed
                : 0;
        System.out.println("\n=== Trial Summary [" + symbol + "] ===");
        System.out.println("Total invocations:      " + invocationCount);
        System.out.println("Total events processed: " + totalEventsProcessed);
        System.out.println("Total rules fired:      " + totalRulesFired);
        System.out.println("Total time elapsed:     " + totalTimeElapsed + " ms"
                + " (" + String.format("%.2f", totalTimeElapsed / 1000.0) + " s)");
        System.out.println("Avg throughput:         " + String.format("%.2f", avgThroughput) + " events/sec");
        System.out.println("==============================\n");

        if (rulesProvider != null) {
            rulesProvider.dispose();
        }
    }

    /**
     * Insert bootstrap facts required by taxonomy.drl.
     * These are stateful facts that rules expect to exist.
     */
    private void insertBootstrapFacts() {
        // Insert RiskConfig with default thresholds
        RiskConfig config = new RiskConfig(symbol);
        kieSession.insert(config);

        // Insert initial ModeState (NORMAL)
        ModeState modeState = new ModeState(symbol, "NORMAL", false, 0L, "");
        kieSession.insert(modeState);

        // Insert initial FeedHealth (OK)
        FeedHealth feedHealth = new FeedHealth(symbol, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0);
        kieSession.insert(feedHealth);

        // Fire bootstrap rules
        kieSession.fireAllRules();
    }

    /**
     * Main method for quick testing (not JMH).
     */
    public static void main(String[] args) {
        BinanceRiskControlBenchmark benchmark = new BinanceRiskControlBenchmark();
        benchmark.symbol = "BTCUSDT";

        try {
            benchmark.setupTrial();
            benchmark.setupInvocation();

            long startTime = System.currentTimeMillis();
            int rulesFired = benchmark.benchmarkEventReplay();
            long endTime = System.currentTimeMillis();

            long duration = endTime - startTime;
            double eventsPerSec = (benchmark.events.size() * 1000.0) / duration;

            System.out.println("\n=== Quick Test Results ===");
            System.out.println("Events processed: " + benchmark.events.size());
            System.out.println("Rules fired: " + rulesFired);
            System.out.println("Duration: " + duration + " ms");
            System.out.println("Throughput: " + String.format("%.2f", eventsPerSec) + " events/sec");

            benchmark.teardownInvocation();
            benchmark.teardownTrial();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
