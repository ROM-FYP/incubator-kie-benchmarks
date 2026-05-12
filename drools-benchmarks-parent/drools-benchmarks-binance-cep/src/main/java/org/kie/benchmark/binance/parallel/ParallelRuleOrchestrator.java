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
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Symbol-level parallel rule execution engine.
 *
 * <p>Creates one full KieSession per symbol, each containing the complete
 * ruleset (identical to the single-session baseline). Events are partitioned
 * by symbol and each symbol's event stream is processed in parallel.</p>
 *
 * <p><b>Why this works:</b> All 110 rules in taxonomy.drl are either
 * symbol-bound (join on {@code symbol == $sym}), no-op stubs ({@code eval(false)}),
 * or symbol-agnostic guards (CLEANUP, ingestion validation). Zero cross-symbol
 * state interactions exist, so each symbol can run in complete isolation.</p>
 *
 * <p><b>Expected speedup:</b> Near-linear with min(poolSize, numSymbols).
 * With 10 symbols and 4 threads → ~4x theoretical speedup.</p>
 */
public class ParallelRuleOrchestrator {

    private final int poolSize;
    private final Set<String> symbols;

    /**
     * Creates the parallel orchestrator.
     *
     * @param plan      the partition plan (used for documentation; symbol parallelism
     *                  doesn't need phase/cluster info, but we keep the API consistent)
     * @param poolSize  number of threads for parallel symbol processing
     * @param symbols   set of symbols to create sessions for
     */
    public ParallelRuleOrchestrator(PartitionPlan plan, int poolSize, Set<String> symbols) {
        this.poolSize = poolSize;
        this.symbols = symbols;

        System.out.println("[Orchestrator] Symbol-level parallelism mode");
        System.out.println("[Orchestrator] Symbols: " + symbols.size()
                + " | Pool size: " + poolSize);
        if (plan != null) {
            System.out.println("[Orchestrator] Partition plan: "
                    + plan.getPhases().size() + " phases, "
                    + plan.totalRules() + " rules (for reference only)");
        }
    }

    /**
     * Replays all events through parallel per-symbol sessions.
     *
     * <p>Flow:
     * <ol>
     *   <li>Partition events by symbol</li>
     *   <li>For each symbol, create a full KieSession + bootstrap + replay</li>
     *   <li>Run all symbols in parallel using a fixed thread pool</li>
     *   <li>Sum rule-fire counts across all symbols</li>
     * </ol>
     *
     * @param events the list of MarketEvents in time order
     * @return total number of rules fired across all symbol sessions
     */
    public int replayEvents(List<MarketEvent> events) {
        // Step 1: Partition events by symbol, preserving time order within each
        Map<String, List<MarketEvent>> eventsBySymbol = new LinkedHashMap<>();
        for (String sym : symbols) {
            eventsBySymbol.put(sym, new ArrayList<>());
        }
        for (MarketEvent event : events) {
            List<MarketEvent> list = eventsBySymbol.get(event.getSymbol());
            if (list != null) {
                list.add(event);
            }
            // Events for unknown symbols are silently dropped
            // (A04_SymbolAllowlist would retract them anyway)
        }

        System.out.println("[Orchestrator] Event distribution by symbol:");
        for (Map.Entry<String, List<MarketEvent>> e : eventsBySymbol.entrySet()) {
            System.out.println("  " + e.getKey() + ": " + e.getValue().size() + " events");
        }

        // Step 2: Submit each symbol's replay to thread pool
        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        List<Future<Integer>> futures = new ArrayList<>();

        for (Map.Entry<String, List<MarketEvent>> entry : eventsBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<MarketEvent> symbolEvents = entry.getValue();

            futures.add(executor.submit(() -> replaySymbol(symbol, symbolEvents)));
        }

        // Step 3: Collect results
        int totalFired = 0;
        for (Future<Integer> future : futures) {
            try {
                totalFired += future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Symbol replay failed", e);
            }
        }

        executor.shutdown();
        return totalFired;
    }

    /**
     * Replay all events for a single symbol in its own dedicated KieSession.
     * This is identical to the single-session baseline, just scoped to one symbol.
     */
    private int replaySymbol(String symbol, List<MarketEvent> events) {
        if (events.isEmpty()) return 0;

        // Create a fresh session with all rules (same as baseline)
        BinanceRulesProvider rulesProvider = new BinanceRulesProvider();
        KieSession session = rulesProvider.createSession();
        EventReplayController controller = new EventReplayController(session);

        try {
            // Bootstrap facts for this symbol only
            session.insert(new RiskConfig(symbol));
            session.insert(new ModeState(symbol, "NORMAL", false, 0L, ""));
            session.insert(new FeedHealth(symbol, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
            session.fireAllRules();

            // Replay all events for this symbol
            int fired = controller.replayEvents(events);

            System.out.println("[" + symbol + "] " + events.size()
                    + " events → " + fired + " rules fired");

            return fired;
        } finally {
            session.dispose();
            rulesProvider.dispose();
        }
    }

    /**
     * Dispose resources (no-op for symbol-level parallelism since sessions
     * are created and disposed within replayEvents).
     */
    public void dispose() {
        // Sessions are already disposed in replaySymbol()
    }
}
