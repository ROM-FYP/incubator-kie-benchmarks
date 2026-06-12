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

import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.parallel.RipeRisClusterDrlGenerator;
import org.kie.benchmark.cep.riperis.parallel.RipeRisClusterOrchestrator;
import org.kie.benchmark.cep.riperis.util.EnvConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Runner that executes only the 3-cluster parallel execution.
 *
 * <p>Architecture:
 * <ul>
 *   <li>P1 (Ingestion/Validation) — ALL events, 18 rules</li>
 *   <li>P2 (Announcement Pipeline) — announcement events, 53 rules</li>
 *   <li>P3 (Withdrawal Pipeline) — withdrawal events, 11 rules</li>
 * </ul>
 */
public class RipeRisClusterRunner {

    private static final String DRL_PATH = "rules/ripe_rfc4271_benchmark_79_rules.drl";

    public static void main(String[] args) throws Exception {
        String dataFile;
        long maxEvents = Long.MAX_VALUE;

        if (args.length > 0) {
            dataFile = EnvConfig.get(args[0]);
            if (dataFile == null) {
                dataFile = args[0];
            }
            if (args.length > 1) {
                maxEvents = Long.parseLong(args[1]);
            }
        } else {
            dataFile = EnvConfig.get("RIPERIS_DEFAULT_DATA_FILE");
        }

        // 1. Load events
        System.out.println("[Cluster] Loading events from: " + dataFile);
        List<RisMessage> events = RipeRisBaselineBenchmark.loadEvents(dataFile, maxEvents);
        System.out.printf("[Cluster] Loaded %,d events%n", events.size());

        // 2. Read DRL content for cluster splitting
        String drlContent;
        try (InputStream is = RipeRisClusterRunner.class.getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) {
                throw new RuntimeException("Cannot find " + DRL_PATH);
            }
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 3. Initialize Orchestrator and Run Parallel Benchmark
        System.out.println("[Cluster] Initializing Orchestrator...");
        RipeRisClusterOrchestrator orchestrator = new RipeRisClusterOrchestrator(drlContent);

        System.out.printf("[Cluster] Ingesting %,d events...%n", events.size());
        long t0 = System.currentTimeMillis();
        int clusterFired = orchestrator.replayEvents(events);
        long elapsed = System.currentTimeMillis() - t0;

        Map<Integer, Integer> perSessionFired = orchestrator.getPerSessionFired();
        Map<Integer, Integer> perSessionEvents = orchestrator.getPerSessionEventsReceived();

        orchestrator.dispose();

        // 4. Report
        System.out.println("========================================");
        System.out.println("RIPE RIS CLUSTER BENCHMARK RESULTS");
        System.out.println("========================================");
        System.out.printf("Events Ingested : %,d%n", events.size());
        System.out.printf("Rules Fired     : %,d%n", clusterFired);
        System.out.printf("Duration        : %,d ms (%.2f s)%n", elapsed, elapsed / 1000.0);
        System.out.printf("Throughput      : %,.0f events/sec%n", events.size() / (elapsed / 1000.0));
        System.out.printf("Clusters        : %d%n", RipeRisClusterDrlGenerator.getClusterCount());
        System.out.println("========================================");

        // 5. Per-session breakdown
        String[] names = RipeRisClusterDrlGenerator.getClusterNames();
        System.out.println("\n── Per-Session Breakdown ───────────────────");
        for (Map.Entry<Integer, Integer> entry : perSessionFired.entrySet()) {
            int cid = entry.getKey();
            int fired = entry.getValue();
            int eventsRecv = perSessionEvents.getOrDefault(cid, 0);
            System.out.printf("  %-30s Events: %,8d  Fired: %,8d%n",
                    names[cid], eventsRecv, fired);
        }
    }
}

