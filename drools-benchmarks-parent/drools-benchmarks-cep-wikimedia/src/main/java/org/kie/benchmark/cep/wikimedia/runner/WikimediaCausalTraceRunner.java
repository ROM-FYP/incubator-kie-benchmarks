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

package org.kie.benchmark.cep.wikimedia.runner;

import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.CepSessionFactory;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.kie.benchmark.cep.wikimedia.util.CausalTraceListener;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Standalone runner that feeds a Wikimedia dataset through the Drools engine
 * with full causal trace logging enabled.
 *
 * <p>This is a data-collection tool (not a JMH benchmark). Its sole purpose is
 * to generate a {@code .jsonl} causal trace file capturing every fact lifecycle
 * event and rule activation, for downstream rule-interaction graph construction.
 *
 * <h3>Usage</h3>
 * <pre>
 *   mvn exec:java -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.runner.WikimediaCausalTraceRunner \
 *       -Dexec.args="path/to/data.jsonl [outputFile] [maxEvents]"
 *
 *   Defaults:
 *     outputFile → wikimedia_causal_trace.jsonl
 *     maxEvents  → all events in the file
 * </pre>
 *
 * <h3>Output</h3>
 * Each line of the output file is a JSON object with one of: FACT_INSERT,
 * FACT_UPDATE, FACT_DELETE, ACTIVATION_CREATED, ACTIVATION_FIRED.
 */
public class WikimediaCausalTraceRunner {

    private static final String DRL_PATH = "rules/wikimedia_content_moderation_join_heavy.drl";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: WikimediaCausalTraceRunner <dataFile> [outputFile] [maxEvents]");
            System.exit(1);
        }
        String dataFile = args[0];
        String outputFile = args.length > 1 ? args[1] : "wikimedia_causal_trace.jsonl";
        int maxEvents = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;

        // 1. Load events (reuse baseline loader)
        System.out.println("[CausalTraceRunner] Loading dataset...");
        List<WikiEvent> events = WikimediaBaselineBenchmark.loadEvents(dataFile, maxEvents);
        System.out.printf("[CausalTraceRunner] Loaded %,d events%n", events.size());

        // 2. Build KieSession (pseudo-clock, STREAM mode)
        System.out.println("[CausalTraceRunner] Initializing Drools engine...");
        CepSessionFactory factory = new CepSessionFactory(DRL_PATH);
        KieSession session = factory.createSession(true);

        // 3. Attach causal trace listener
        CausalTraceListener listener = new CausalTraceListener(outputFile);
        session.addEventListener(listener.agendaListener());
        session.addEventListener(listener.runtimeListener());
        System.out.printf("[CausalTraceRunner] Trace listener attached → %s%n", outputFile);

        // 4. Replay all events with tracing
        System.out.printf("[CausalTraceRunner] Ingesting %,d events...%n", events.size());
        SessionPseudoClock clock = session.getSessionClock();
        long t0 = System.currentTimeMillis();
        int totalFirings = 0;

        for (int i = 0; i < events.size(); i++) {
            WikiEvent event = events.get(i);

            // Advance clock
            long eventTime = event.getTimestamp();
            long currentTime = clock.getCurrentTime();
            if (eventTime > currentTime) {
                clock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
            }

            session.insert(event);
            totalFirings += session.fireAllRules();

            if ((i + 1) % 5000 == 0) {
                System.out.printf("[CausalTraceRunner]   %,d / %,d events ingested...%n",
                        i + 1, events.size());
            }
        }

        long elapsed = System.currentTimeMillis() - t0;
        System.out.printf("[CausalTraceRunner] Done. Rules fired: %,d | Time: %.1f s%n",
                totalFirings, elapsed / 1000.0);

        // 5. Cleanup
        listener.close();
        session.dispose();

        System.out.printf("[CausalTraceRunner] Trace written to: %s%n", outputFile);
    }
}
