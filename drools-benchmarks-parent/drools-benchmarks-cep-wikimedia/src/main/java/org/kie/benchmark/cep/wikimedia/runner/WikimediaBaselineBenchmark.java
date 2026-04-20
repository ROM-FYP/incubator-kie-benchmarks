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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.CepSessionFactory;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Clean baseline benchmark runner for the Wikimedia CEP system.
 * Replays a JSONL data dump through a single KieSession with pseudo-clock,
 * measuring throughput and rule firing count.
 *
 * <h3>Usage</h3>
 * <pre>
 *   mvn exec:java -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.runner.WikimediaBaselineBenchmark \
 *       -Dexec.args="path/to/data.jsonl [maxEvents]"
 * </pre>
 */
public class WikimediaBaselineBenchmark {

    private static final String DRL_PATH = "rules/wikimedia_content_moderation_join_heavy.drl";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: WikimediaBaselineBenchmark <dataFile> [maxEvents]");
            System.exit(1);
        }
        String dataFile = args[0];
        int maxEvents = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        // 1. Load events
        System.out.println("[Baseline] Loading events from: " + dataFile);
        List<WikiEvent> events = loadEvents(dataFile, maxEvents);
        System.out.printf("[Baseline] Loaded %,d events%n", events.size());

        // 2. Build session
        System.out.println("[Baseline] Initializing Drools session (pseudo-clock, STREAM mode)...");
        CepSessionFactory factory = new CepSessionFactory(DRL_PATH);
        KieSession session = factory.createSession(true);

        // 3. Replay
        System.out.printf("[Baseline] Ingesting %,d events...%n", events.size());
        long t0 = System.currentTimeMillis();
        int totalFirings = 0;

        SessionPseudoClock clock = session.getSessionClock();

        for (int i = 0; i < events.size(); i++) {
            WikiEvent event = events.get(i);

            // Advance clock to event time
            long eventTime = event.getTimestamp();
            long currentTime = clock.getCurrentTime();
            if (eventTime > currentTime) {
                clock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
            }

            session.insert(event);
            totalFirings += session.fireAllRules();

            if ((i + 1) % 5000 == 0) {
                System.out.printf("[Baseline]   %,d / %,d events ingested...%n", i + 1, events.size());
            }
        }

        long elapsed = System.currentTimeMillis() - t0;

        // 4. Report
        System.out.println("========================================");
        System.out.println("WIKIMEDIA BASELINE BENCHMARK RESULTS");
        System.out.println("========================================");
        System.out.printf("Events Ingested : %,d%n", events.size());
        System.out.printf("Rules Fired     : %,d%n", totalFirings);
        System.out.printf("Duration        : %,d ms (%.2f s)%n", elapsed, elapsed / 1000.0);
        System.out.printf("Throughput      : %,.0f events/sec%n", events.size() / (elapsed / 1000.0));
        System.out.printf("Cost/Rule       : %.2f µs/rule%n",
                (elapsed * 1000.0) / Math.max(1, totalFirings));
        System.out.println("========================================");

        session.dispose();
    }

    /**
     * Load WikiEvents from a JSONL file (Wikimedia SSE recent-change format).
     * Extracts: title, user, comment, bot, timestamp (Unix epoch seconds → ms), sizeDelta.
     */
    public static List<WikiEvent> loadEvents(String filePath, int maxEvents) throws Exception {
        List<WikiEvent> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
            String line;
            while ((line = reader.readLine()) != null && events.size() < maxEvents) {
                if (line.trim().isEmpty()) continue;
                try {
                    JsonNode node = MAPPER.readTree(line);

                    String title = node.has("title") ? node.get("title").asText("") : "";
                    String user = node.has("user") ? node.get("user").asText("") : "";
                    String comment = node.has("comment") ? node.get("comment").asText("") : "";
                    boolean bot = node.has("bot") && node.get("bot").asBoolean(false);

                    // Timestamp: Wikimedia uses Unix epoch seconds
                    long timestamp = 0;
                    if (node.has("timestamp")) {
                        timestamp = node.get("timestamp").asLong(0) * 1000; // → ms
                    }

                    // Size delta from length.new - length.old
                    int sizeDelta = 0;
                    if (node.has("length")) {
                        JsonNode length = node.get("length");
                        int newLen = length.has("new") ? length.get("new").asInt(0) : 0;
                        int oldLen = length.has("old") ? length.get("old").asInt(0) : 0;
                        sizeDelta = newLen - oldLen;
                    }

                    WikiEvent event = new WikiEvent(title, user, comment, bot, timestamp, sizeDelta);
                    events.add(event);
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }
        return events;
    }
}
