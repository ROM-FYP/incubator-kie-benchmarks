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
 * Measures throughput and latency for 70-rule taxonomy with single-symbol data.
 * 
 * Benchmark Configuration:
 * - Rules: 70 (taxonomy.drl)
 * - Dataset: run_20260216_0632_10sym (67K events, 5 minutes)
 * - Mode: Event-time replay with SessionPseudoClock
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = { "-Xms4g", "-Xmx4g" })
public class BinanceRiskControlBenchmark {

    @Param({ "BTCUSDT" })
    private String symbol;

    private BinanceRulesProvider rulesProvider;
    private BinanceEventProvider eventProvider;
    private List<MarketEvent> events;
    private KieSession kieSession;
    private EventReplayController replayController;

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

        System.out.println("=== Benchmark Setup ===");
        System.out.println("Symbol: " + symbol);
        System.out.println("Total events: " + events.size());
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
    }

    /**
     * Benchmark method: Replay events and measure throughput.
     * Returns events/sec processed.
     */
    @Benchmark
    public int benchmarkEventReplay() {
        int rulesFired = replayController.replayEvents(events);
        return rulesFired;
    }

    /**
     * Teardown: Dispose session after each invocation.
     */
    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        if (kieSession != null) {
            kieSession.dispose();
        }
    }

    /**
     * Teardown: Cleanup after all iterations.
     */
    @TearDown(Level.Trial)
    public void teardownTrial() {
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
