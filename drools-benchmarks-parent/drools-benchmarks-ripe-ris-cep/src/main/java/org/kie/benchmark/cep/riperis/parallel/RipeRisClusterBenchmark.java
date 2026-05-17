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
package org.kie.benchmark.cep.riperis.parallel;

import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark;
import org.kie.benchmark.cep.riperis.util.CepSessionFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark harness that runs both the single-session baseline and the
 * parallel execution, comparing throughput and rule firing counts.
 */
public class RipeRisClusterBenchmark {

    private static final String DRL_PATH = "rules/ripe_rfc4271_benchmark_79_rules.drl";

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: RipeRisClusterBenchmark <dataFile> [maxEvents]");
                System.exit(1);
            }
            String dataFile = args[0];
            int maxEvents = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  Ripe RIS CEP — Cluster Parallel Benchmark  ║");
            System.out.println("╚══════════════════════════════════════════════╝\n");

            // 1. Load events
            System.out.println("[Benchmark] Loading events from: " + dataFile);
            List<RisMessage> events = RipeRisBaselineBenchmark.loadEvents(dataFile, maxEvents);
            System.out.printf("[Benchmark] Loaded %,d events%n%n", events.size());

            // 2. Read DRL content for cluster splitting
            String drlContent;
            try (InputStream is = RipeRisClusterBenchmark.class
                    .getClassLoader().getResourceAsStream(DRL_PATH)) {
                if (is == null)
                    throw new RuntimeException("Cannot find " + DRL_PATH);
                drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // ══════════════════════════════════════════════
            // BASELINE
            // ══════════════════════════════════════════════
            System.out.println("── Single-Session Baseline ──────────────────");
            CepSessionFactory factory = new CepSessionFactory(DRL_PATH);
            KieSession baselineSession = factory.createSession(true);

            long baselineStart = System.currentTimeMillis();
            int baselineFired = 0;
            SessionPseudoClock baseClock = baselineSession.getSessionClock();

            for (int i = 0; i < events.size(); i++) {
                RisMessage event = events.get(i);
                long eventTime = (long) (event.getTimestamp() * 1000);
                long currentTime = baseClock.getCurrentTime();
                if (eventTime > currentTime) {
                    baseClock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
                }
                baselineSession.insert(event);
                baselineFired += baselineSession.fireAllRules();
            }
            long baselineDuration = System.currentTimeMillis() - baselineStart;
            baselineSession.dispose();

            System.out.printf("Rules fired:  %,d%n", baselineFired);
            System.out.printf("Duration:     %,d ms%n", baselineDuration);
            System.out.printf("Throughput:   %.2f events/sec%n",
                    events.size() * 1000.0 / baselineDuration);

            // ══════════════════════════════════════════════
            // CLUSTER PARALLEL
            // ══════════════════════════════════════════════
            System.out.println("\n── Cluster-Parallel Execution ─────────");

            RipeRisClusterOrchestrator orchestrator = new RipeRisClusterOrchestrator(drlContent);

            long clusterStart = System.currentTimeMillis();
            int clusterFired = orchestrator.replayEvents(events);
            long clusterDuration = System.currentTimeMillis() - clusterStart;

            Map<Integer, Integer> perSessionFired = orchestrator.getPerSessionFired();
            Map<Integer, Integer> perSessionEvents = orchestrator.getPerSessionEventsReceived();

            orchestrator.dispose();

            System.out.printf("Rules fired:  %,d%n", clusterFired);
            System.out.printf("Duration:     %,d ms%n", clusterDuration);
            System.out.printf("Throughput:   %.2f events/sec%n",
                    events.size() * 1000.0 / clusterDuration);
            System.out.printf("Pool size:    %d%n", RipeRisClusterDrlGenerator.getClusterCount());

            // ══════════════════════════════════════════════
            // COMPARISON
            // ══════════════════════════════════════════════
            System.out.println("\n── Comparison ──────────────────────────────");
            System.out.printf("%-25s %15s %15s%n", "", "Single", "Cluster");
            System.out.printf("%-25s %,15d %,15d%n", "Rules fired",
                    baselineFired, clusterFired);
            System.out.printf("%-25s %,12d ms %,12d ms%n", "Duration",
                    baselineDuration, clusterDuration);
            System.out.printf("%-25s %15.2f %15.2f%n", "Events/sec",
                    events.size() * 1000.0 / baselineDuration,
                    events.size() * 1000.0 / clusterDuration);

            double speedup = (double) baselineDuration / clusterDuration;
            System.out.printf("%-25s %15s %14.2fx%n", "Speedup", "1.00x", speedup);

            // ══════════════════════════════════════════════
            // PER-SESSION BREAKDOWN
            // ══════════════════════════════════════════════
            String[] names = RipeRisClusterDrlGenerator.getClusterNames();
            System.out.println("\n── Per-Session Breakdown ───────────────────");
            for (Map.Entry<Integer, Integer> entry : perSessionFired.entrySet()) {
                int cid = entry.getKey();
                int fired = entry.getValue();
                int eventsRecv = perSessionEvents.getOrDefault(cid, 0);
                System.out.printf("  %-30s Events: %,8d  Fired: %,8d%n",
                        names[cid], eventsRecv, fired);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
