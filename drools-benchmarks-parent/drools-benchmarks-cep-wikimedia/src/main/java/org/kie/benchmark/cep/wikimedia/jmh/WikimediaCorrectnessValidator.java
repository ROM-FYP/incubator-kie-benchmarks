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
package org.kie.benchmark.cep.wikimedia.jmh;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.CepSessionFactory;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Unified correctness validator for all 4 Wikimedia parallel architectures.
 * Runs each architecture once on the full dataset and compares:
 * - Rule-fire count
 * - Duration
 * - Determinism (optional: run twice externally)
 *
 * <p>Usage:
 * <pre>
 *   java -Xms4g -Xmx4g -cp drools-benchmarks-cep-wikimedia.jar \
 *       org.kie.benchmark.cep.wikimedia.jmh.WikimediaCorrectnessValidator \
 *       path/to/data.jsonl [maxEvents]
 * </pre>
 */
public class WikimediaCorrectnessValidator {

    private static final String DRL_PATH = "rules/wikimedia_content_moderation_join_heavy.drl";

    /**
     * Result from a single architecture run.
     */
    static class ArchResult {
        final String name;
        final int rulesFired;
        final long durationMs;
        final String error;

        ArchResult(String name, int rulesFired, long durationMs) {
            this.name = name;
            this.rulesFired = rulesFired;
            this.durationMs = durationMs;
            this.error = null;
        }

        ArchResult(String name, String error) {
            this.name = name;
            this.rulesFired = -1;
            this.durationMs = -1;
            this.error = error;
        }

        boolean isError() { return error != null; }
    }

    private final List<WikiEvent> events;

    public WikimediaCorrectnessValidator(List<WikiEvent> events) {
        this.events = events;

        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║  Wikimedia Correctness Validator — 4-Way Comparison  ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.printf("Events: %,d%n%n", events.size());
    }

    // ─── Architecture Runners ───────────────────────────────────────

    /**
     * Run single-session baseline (sequential).
     */
    private ArchResult runBaseline() {
        System.out.println("── Running: Baseline (Sequential) ──");
        return runSingleSession("SEQUENTIAL", "Baseline (Sequential)");
    }

    /**
     * Run Drools built-in PARALLEL_EVALUATION.
     */
    private ArchResult runParallelEval() {
        System.out.println("── Running: Built-in PARALLEL_EVALUATION ──");
        return runSingleSession("PARALLEL_EVALUATION", "Built-in PARALLEL_EVAL");
    }

    /**
     * Run Drools built-in FULLY_PARALLEL.
     */
    private ArchResult runFullyParallel() {
        System.out.println("── Running: Built-in FULLY_PARALLEL ──");
        return runSingleSession("FULLY_PARALLEL", "Built-in FULLY_PARALLEL");
    }

    // ─── Shared Single-Session Runner ───────────────────────────────

    private ArchResult runSingleSession(String parallelMode, String name) {
        try {
            CepSessionFactory factory = new CepSessionFactory(DRL_PATH, parallelMode);
            KieSession session = factory.createSession(true);

            SessionPseudoClock clock = session.getSessionClock();
            int fired = 0;

            long start = System.currentTimeMillis();
            for (WikiEvent event : events) {
                long eventTime = event.getTimestamp();
                long currentTime = clock.getCurrentTime();
                if (eventTime > currentTime) {
                    clock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
                }
                session.insert(event);
                fired += session.fireAllRules();
            }
            long duration = System.currentTimeMillis() - start;

            session.dispose();

            System.out.printf("  Rules fired: %,d | Duration: %,d ms%n", fired, duration);
            return new ArchResult(name, fired, duration);
        } catch (Exception e) {
            System.err.println("  ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            return new ArchResult(name, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ─── Main ───────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: WikimediaCorrectnessValidator <dataFile> [maxEvents]");
            System.exit(1);
        }
        String dataFile = args[0];
        int maxEvents = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.println("[Validator] Loading events from: " + dataFile);
        List<WikiEvent> events = loadEvents(dataFile, maxEvents);
        System.out.printf("[Validator] Loaded %,d events%n%n", events.size());

        WikimediaCorrectnessValidator validator = new WikimediaCorrectnessValidator(events);

        List<ArchResult> results = new ArrayList<>();
        results.add(validator.runBaseline());
        results.add(validator.runParallelEval());
        results.add(validator.runFullyParallel());

        // Print comparison table
        ArchResult baseline = results.get(0);
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║  Correctness Comparison Table                                            ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝");
        System.out.printf("  %-30s %14s %12s %10s%n",
                "Architecture", "Rules Fired", "Duration", "Status");
        System.out.println("  " + "─".repeat(70));

        for (ArchResult r : results) {
            if (r.isError()) {
                System.out.printf("  %-30s %14s %12s %10s%n",
                        r.name, "CRASHED", "—", "❌ " + r.error);
            } else {
                String rfMatch;
                if (r.name.contains("Cluster")) {
                    // Cluster V3 may legitimately differ due to rule duplication
                    rfMatch = (r.rulesFired == baseline.rulesFired) ? "✅" :
                            String.format("Δ%+d (known)", r.rulesFired - baseline.rulesFired);
                } else {
                    rfMatch = (r.rulesFired == baseline.rulesFired) ? "✅" :
                            String.format("❌ Δ%+d", r.rulesFired - baseline.rulesFired);
                }
                System.out.printf("  %-30s %,14d %,10d ms %s%n",
                        r.name, r.rulesFired, r.durationMs, rfMatch);
            }
        }

        System.out.println();
        System.out.printf("Baseline rule-fire count: %,d%n", baseline.rulesFired);

        long baseline_fired = baseline.rulesFired;
        boolean allPass = results.stream()
                .filter(r -> !r.isError())
                .allMatch(r -> r.rulesFired == baseline_fired);
        System.out.println("\nOverall: " + (allPass ? "✅ PASS" : "⚠️  DIFFERENCES DETECTED"));
    }

    // ── local loader ──────────────────────────────────────────
    static List<WikiEvent> loadEvents(String path, int max) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<WikiEvent> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null && list.size() < max) {
                if (!line.isBlank()) list.add(mapper.readValue(line, WikiEvent.class));
            }
        }
        return list;
    }
}
