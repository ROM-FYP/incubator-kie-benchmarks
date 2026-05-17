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

package org.kie.benchmark.cep.riperis.runner;

import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.riperis.util.CepSessionFactory;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.util.CausalTraceListener;
import org.kie.benchmark.cep.riperis.util.EnvConfig;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Standalone runner that feeds a RIPE RIS dataset through the Drools engine
 * with full causal trace logging enabled.
 */
public class RipeRisCausalTraceRunner {

    private static final String DRL_PATH = EnvConfig.get("RIPERIS_RULES_FILE", "rules/ripe_rfc4271_benchmark_79_rules.drl");

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: RipeRisCausalTraceRunner <dataFile> [outputFile] [maxEvents]");
            System.exit(1);
        }
        String dataFile = args[0];
        String traceDir = EnvConfig.get("RIPERIS_RECORDED_TRACE_DIR", ".");
        String defaultOutput = traceDir + "/riperis_causal_trace.jsonl";
        String outputFile = args.length > 1 ? args[1] : defaultOutput;
        int maxEvents = args.length > 2 ? Integer.parseInt(args[2]) : Integer.MAX_VALUE;

        // 1. Load events
        System.out.println("[CausalTraceRunner] Loading dataset...");
        List<RisMessage> events = RipeRisBaselineBenchmark.loadEvents(dataFile, maxEvents);
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
            RisMessage event = events.get(i);

            // Advance clock to event time (convert seconds to ms)
            long eventTimeMs = (long) (event.getTimestamp() * 1000);
            long currentTime = clock.getCurrentTime();
            if (eventTimeMs > currentTime) {
                clock.advanceTime(eventTimeMs - currentTime, TimeUnit.MILLISECONDS);
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
