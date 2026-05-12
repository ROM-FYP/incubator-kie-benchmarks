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

package org.kie.benchmark.binance.runner;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.BinanceCausalTraceListener;
import org.kie.benchmark.binance.util.EventReplayController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone runner that feeds the ENTIRE Binance dataset through the Drools
 * engine
 * exactly once with full causal trace logging enabled.
 *
 * <p>
 * This is <strong>not</strong> a JMH benchmark — it is a data-collection tool.
 * Its sole purpose is to generate a {@code .jsonl} causal trace file capturing
 * every
 * fact lifecycle event and rule activation, for downstream rule-interaction
 * graph
 * construction and decision-tree dataset building.
 *
 * <h3>Usage (from the benchmarks uber-jar)</h3>
 * 
 * <pre>
 *   java -cp target/benchmarks.jar org.kie.benchmark.binance.runner.BinanceCausalTraceRunner \
 *        [datasetId] [outputFile]
 *
 *   Defaults:
 *     datasetId  → (BinanceEventProvider default: run_20260311_1340_10sym)
 *     outputFile → binance_causal_trace.jsonl
 * </pre>
 *
 * <h3>Output</h3>
 * <p>
 * Each line of the output file is a JSON object with one of the following
 * {@code type} values:
 * <ul>
 * <li>{@code FACT_INSERT} — a fact was inserted into working memory
 * <li>{@code FACT_UPDATE} — a fact was updated in working memory
 * <li>{@code FACT_DELETE} — a fact was retracted from working memory
 * <li>{@code ACTIVATION_CREATED} — a rule match was created (with supporting
 * fact IDs)
 * <li>{@code ACTIVATION_FIRED} — a rule match was executed
 * </ul>
 */
public class BinanceCausalTraceRunner {

        public static void main(String[] args) throws Exception {
                String datasetId = args.length > 0 ? args[0] : null; // null → provider default
                String outputFile = args.length > 1 ? args[1] : "binance_causal_trace.jsonl";

                // ----------------------------------------------------------------
                // 1. Load all events
                // ----------------------------------------------------------------
                System.out.println("[BinanceCausalTraceRunner] Loading dataset...");
                BinanceEventProvider eventProvider = (datasetId != null)
                                ? new BinanceEventProvider(datasetId)
                                : new BinanceEventProvider();

                List<MarketEvent> events = eventProvider.getEvents();
                System.out.printf("[BinanceCausalTraceRunner] Loaded %,d events from dataset: %s%n",
                                events.size(), eventProvider.getDatasetId());

                // ----------------------------------------------------------------
                // 2. Build KieSession (pseudo-clock, same as benchmarks)
                // ----------------------------------------------------------------
                System.out.println("[BinanceCausalTraceRunner] Initializing Drools engine...");
                BinanceRulesProvider rulesProvider = new BinanceRulesProvider();
                KieSession session = rulesProvider.createSession();

                // ----------------------------------------------------------------
                // 3. Attach causal trace listener (BEFORE any facts are inserted)
                // ----------------------------------------------------------------
                BinanceCausalTraceListener listener = new BinanceCausalTraceListener(outputFile);
                session.addEventListener(listener.agendaListener());
                session.addEventListener(listener.runtimeListener());
                System.out.printf("[BinanceCausalTraceRunner] Causal trace listener attached → %s%n", outputFile);

                // ----------------------------------------------------------------
                // 4. Insert bootstrap singleton facts (same as benchmarks)
                // ----------------------------------------------------------------
                Set<String> symbols = events.stream()
                                .map(MarketEvent::getSymbol)
                                .collect(Collectors.toSet());

                for (String symbol : symbols) {
                        session.insert(new RiskConfig(symbol));
                        session.insert(new ModeState(symbol, "NORMAL", false, 0L, ""));
                        session.insert(new FeedHealth(symbol, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
                }
                session.fireAllRules();
                System.out.printf("[BinanceCausalTraceRunner] Bootstrap facts inserted for %d symbols.%n",
                                symbols.size());

                // ----------------------------------------------------------------
                // 5. Replay all events with causal tracing
                // ----------------------------------------------------------------
                System.out.printf("[BinanceCausalTraceRunner] Ingesting %,d events → %s%n",
                                events.size(), outputFile);

                EventReplayController replayController = new EventReplayController(session);
                long t0 = System.currentTimeMillis();
                int totalFirings = 0;

                for (int i = 0; i < events.size(); i++) {
                        // Insert one event and fire rules (matches EventReplayController behaviour)
                        MarketEvent event = events.get(i);
                        long eventTime = event.getTsMs();
                        long currentTime = session.<org.drools.core.time.SessionPseudoClock>getSessionClock()
                                        .getCurrentTime();
                        if (eventTime > currentTime) {
                                session.<org.drools.core.time.SessionPseudoClock>getSessionClock()
                                                .advanceTime(eventTime - currentTime,
                                                                java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                        session.insert(event);
                        totalFirings += session.fireAllRules();

                        if ((i + 1) % 5000 == 0) {
                                System.out.printf("[BinanceCausalTraceRunner]   %,d / %,d events ingested...%n",
                                                i + 1, events.size());
                        }
                }

                long elapsed = System.currentTimeMillis() - t0;
                System.out.printf("[BinanceCausalTraceRunner] Done.  Total rule firings: %,d  |  Time: %.1f s%n",
                                totalFirings, elapsed / 1000.0);

                // ----------------------------------------------------------------
                // 6. Cleanup
                // ----------------------------------------------------------------
                listener.close();
                session.dispose();
                rulesProvider.dispose();

                System.out.printf("[BinanceCausalTraceRunner] Trace written to: %s%n", outputFile);
        }
}
