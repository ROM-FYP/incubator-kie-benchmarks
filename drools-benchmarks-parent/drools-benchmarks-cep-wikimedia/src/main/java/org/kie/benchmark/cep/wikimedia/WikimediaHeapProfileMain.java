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
package org.kie.benchmark.cep.wikimedia;

import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.kie.benchmark.cep.wikimedia.runner.WikimediaBaselineBenchmark;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Standalone heap / GC profiler for the Wikimedia CEP benchmark.
 *
 * <p>Runs N trials of a full dataset replay and reports:
 * <ul>
 *   <li>JVM baseline heap (before session creation)</li>
 *   <li>Peak heap (after all rules fired, before dispose)</li>
 *   <li>Post-dispose heap (after GC settle)</li>
 *   <li>Delta MB = peak − post-dispose (working memory footprint)</li>
 * </ul>
 *
 * <p>Supports modes: {@code baseline}, {@code PARALLEL_EVALUATION}, {@code FULLY_PARALLEL}.
 * Symmetric with {@code BinanceHeapProfileMain} and {@code OpenSkyHeapProfileMain}.
 *
 * <p>Usage:
 * <pre>
 *   mvn exec:java -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.WikimediaHeapProfileMain \
 *       -Dexec.args="--dataset src/main/resources/data/data/split_400k.jsonl \
 *                    --mode baseline --trials 3 \
 *                    --output output/heap_wikimedia_baseline.json"
 * </pre>
 */
public class WikimediaHeapProfileMain {

    private static final String  DRL_PATH    = "rules/wikimedia_content_moderation_join_heavy.drl";
    private static final int     GC_SETTLE_MS = 500;
    private static final MemoryMXBean MX     = ManagementFactory.getMemoryMXBean();

    public static void main(String[] args) throws Exception {
        // ── Parse args ──────────────────────────────────────────────────────
        String dataset    = null;
        String mode       = "baseline";
        int    trials     = 3;
        int    maxEvents  = Integer.MAX_VALUE;
        String outputFile = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dataset":   dataset    = args[++i]; break;
                case "--mode":      mode       = args[++i]; break;
                case "--trials":    trials     = Integer.parseInt(args[++i]); break;
                case "--max-events":maxEvents  = Integer.parseInt(args[++i]); break;
                case "--output":    outputFile = args[++i]; break;
                default: break;
            }
        }

        if (dataset == null || outputFile == null) {
            System.err.println("Usage: WikimediaHeapProfileMain --dataset <path> " +
                               "--mode <baseline|PARALLEL_EVALUATION|FULLY_PARALLEL> " +
                               "[--trials <n>] [--max-events <n>] --output <file>");
            System.exit(1);
        }

        int    threads = Integer.getInteger("java.util.concurrent.ForkJoinPool.common.parallelism", 1);
        long   xmxMb   = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String jvm     = System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")";

        System.out.printf("[WikimediaHeapProfile] Loading dataset: %s%n", dataset);
        List<WikiEvent> events = WikimediaBaselineBenchmark.loadEvents(dataset, maxEvents);
        System.out.printf("[WikimediaHeapProfile] Loaded %,d events | mode=%s | threads=%d | trials=%d%n",
                events.size(), mode, threads, trials);

        List<Map<String, Object>> trialResults = new ArrayList<>();

        for (int t = 1; t <= trials; t++) {
            System.out.printf("%n[Trial %d/%d] ------------------------------------------%n", t, trials);
            Map<String, Object> r = runTrial(t, mode, events);
            trialResults.add(r);
            System.out.printf("[Trial %d/%d] jvm_baseline=%.1f MB | peak=%.1f MB | " +
                              "post_dispose=%.1f MB | delta=%.1f MB | rules_fired=%,d%n",
                    t, trials,
                    (double) r.get("jvm_baseline_mb"), (double) r.get("peak_heap_mb"),
                    (double) r.get("post_dispose_mb"),  (double) r.get("delta_mb"),
                    (int) r.get("rules_fired"));
        }

        double medPeak  = median(trialResults, "peak_heap_mb");
        double medPost  = median(trialResults, "post_dispose_mb");
        double medDelta = median(trialResults, "delta_mb");

        System.out.printf("%n[WikimediaHeapProfile] SUMMARY:%n");
        System.out.printf("  median_peak_heap_mb    = %.2f MB%n", medPeak);
        System.out.printf("  median_post_dispose_mb = %.2f MB%n", medPost);
        System.out.printf("  median_delta_mb        = %.2f MB  <- architectural WM footprint%n", medDelta);

        writeJson(outputFile, dataset, events.size(), mode, threads, trials, jvm, xmxMb,
                trialResults, medPeak, medPost, medDelta);
        System.out.printf("%n[WikimediaHeapProfile] Results written to: %s%n", outputFile);
    }

    private static Map<String, Object> runTrial(int t, String mode, List<WikiEvent> events)
            throws Exception {
        forceGc();
        double jvmBaseline = heapUsedMb();

        CepSessionFactory factory = "baseline".equals(mode)
                ? new CepSessionFactory(DRL_PATH)
                : new CepSessionFactory(DRL_PATH, mode);

        KieSession session = factory.createSession(true);
        SessionPseudoClock clock = session.getSessionClock();

        int fired = 0;
        for (WikiEvent ev : events) {
            long ts = ev.getTimestamp(), now = clock.getCurrentTime();
            if (ts > now) clock.advanceTime(ts - now, TimeUnit.MILLISECONDS);
            session.insert(ev);
            fired += session.fireAllRules();
        }

        forceGc();
        double peak = heapUsedMb();

        session.dispose();
        forceGc();
        double postDispose = heapUsedMb();

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("trial",           t);
        r.put("jvm_baseline_mb", jvmBaseline);
        r.put("peak_heap_mb",    peak);
        r.put("post_dispose_mb", postDispose);
        r.put("delta_mb",        peak - postDispose);
        r.put("rules_fired",     fired);
        return r;
    }

    private static void forceGc() throws InterruptedException {
        System.gc(); Thread.sleep(GC_SETTLE_MS);
        System.gc(); Thread.sleep(GC_SETTLE_MS);
    }

    private static double heapUsedMb() {
        return MX.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0);
    }

    private static double median(List<Map<String, Object>> list, String key) {
        List<Double> vals = new ArrayList<>();
        for (Map<String, Object> m : list) {
            Object v = m.get(key);
            vals.add(v instanceof Double ? (Double) v : ((Number) v).doubleValue());
        }
        Collections.sort(vals);
        int n = vals.size();
        return n % 2 == 0 ? (vals.get(n / 2 - 1) + vals.get(n / 2)) / 2.0 : vals.get(n / 2);
    }

    private static void writeJson(String out, String dataset, int events, String mode,
            int threads, int trials, String jvm, long xmxMb,
            List<Map<String, Object>> trs,
            double medPeak, double medPost, double medDelta) throws Exception {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append(String.format("  \"project\": \"wikimedia\",\n"));
        sb.append(String.format("  \"dataset\": \"%s\",\n", dataset));
        sb.append(String.format("  \"events\": %d,\n", events));
        sb.append(String.format("  \"mode\": \"%s\",\n", mode));
        sb.append(String.format("  \"threads\": %d,\n", threads));
        sb.append(String.format("  \"trials_requested\": %d,\n", trials));
        sb.append(String.format("  \"jvm\": \"%s\",\n", jvm.replace("\"", "'")));
        sb.append(String.format("  \"xmx_mb\": %d,\n", xmxMb));
        sb.append("  \"trials\": [\n");
        for (int i = 0; i < trs.size(); i++) {
            Map<String, Object> r = trs.get(i);
            sb.append("    {\n");
            sb.append(String.format("      \"trial\": %d,\n",            ((Number) r.get("trial")).intValue()));
            sb.append(String.format("      \"jvm_baseline_mb\": %.2f,\n",(double) r.get("jvm_baseline_mb")));
            sb.append(String.format("      \"peak_heap_mb\": %.2f,\n",   (double) r.get("peak_heap_mb")));
            sb.append(String.format("      \"post_dispose_mb\": %.2f,\n",(double) r.get("post_dispose_mb")));
            sb.append(String.format("      \"delta_mb\": %.2f,\n",       (double) r.get("delta_mb")));
            sb.append(String.format("      \"rules_fired\": %d\n",       ((Number) r.get("rules_fired")).intValue()));
            sb.append(i < trs.size() - 1 ? "    },\n" : "    }\n");
        }
        sb.append("  ],\n");
        sb.append(String.format("  \"median_peak_heap_mb\": %.2f,\n", medPeak));
        sb.append(String.format("  \"median_post_dispose_mb\": %.2f,\n", medPost));
        sb.append(String.format("  \"median_delta_mb\": %.2f\n", medDelta));
        sb.append("}\n");
        Files.createDirectories(Paths.get(out).getParent());
        Files.write(Paths.get(out), sb.toString().getBytes());
    }
}
