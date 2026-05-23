/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.benchmark.binance;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standalone heap / GC profiler for the Binance CEP benchmark.
 * Symmetric with WikimediaHeapProfileMain and OpenSkyHeapProfileMain.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass=org.kie.benchmark.binance.BinanceHeapProfileMain
 *       -Dexec.args="--mode baseline --trials 3 --output output/heap_binance_baseline.json"
 */
public class BinanceHeapProfileMain {

    private static final int          GC_SETTLE_MS = 500;
    private static final MemoryMXBean MX           = ManagementFactory.getMemoryMXBean();

    public static void main(String[] args) throws Exception {
        String mode = "baseline"; int trials = 3; String outputFile = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mode":   mode       = args[++i]; break;
                case "--trials": trials     = Integer.parseInt(args[++i]); break;
                case "--output": outputFile = args[++i]; break;
            }
        }
        if (outputFile == null) {
            System.err.println("Usage: BinanceHeapProfileMain --mode <baseline|PARALLEL_EVALUATION|FULLY_PARALLEL> [--trials <n>] --output <file>");
            System.exit(1);
        }

        int    threads = Integer.getInteger("java.util.concurrent.ForkJoinPool.common.parallelism", 1);
        long   xmxMb   = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String jvm     = System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + ")";

        BinanceEventProvider ep = new BinanceEventProvider();
        List<MarketEvent> events = ep.getEvents();
        Set<String> symbols = events.stream().map(MarketEvent::getSymbol).collect(Collectors.toSet());
        System.out.printf("[BinanceHeapProfile] %,d events | mode=%s%n", events.size(), mode);

        List<Map<String, Object>> results = new ArrayList<>();
        for (int t = 1; t <= trials; t++) {
            Map<String, Object> r = runTrial(t, mode, events, symbols);
            results.add(r);
            System.out.printf("[Trial %d] peak=%.1f MB | post=%.1f MB | delta=%.1f MB | fired=%,d%n",
                    t, (double)r.get("peak_heap_mb"), (double)r.get("post_dispose_mb"),
                    (double)r.get("delta_mb"), (int)r.get("rules_fired"));
        }

        double medPeak = median(results, "peak_heap_mb"), medPost = median(results, "post_dispose_mb"),
               medDelta = median(results, "delta_mb");
        System.out.printf("[BinanceHeapProfile] median_peak=%.2f | median_delta=%.2f MB%n", medPeak, medDelta);
        writeJson(outputFile, events.size(), mode, threads, trials, jvm, xmxMb, results, medPeak, medPost, medDelta);
        System.out.printf("[BinanceHeapProfile] -> %s%n", outputFile);
    }

    private static Map<String, Object> runTrial(int t, String mode,
            List<MarketEvent> events, Set<String> symbols) throws Exception {
        forceGc(); double baseline = heapUsedMb();
        BinanceRulesProvider rp = new BinanceRulesProvider(mode);
        KieSession s = rp.createSession();
        s.registerChannel("alerts", o -> {});
        for (String sym : symbols) {
            s.insert(new RiskConfig(sym));
            s.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
            s.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
        }
        s.fireAllRules();
        int fired = new EventReplayController(s).replayEvents(events);
        forceGc(); double peak = heapUsedMb();
        s.dispose(); rp.dispose(); forceGc(); double post = heapUsedMb();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("trial", t); r.put("jvm_baseline_mb", baseline);
        r.put("peak_heap_mb", peak); r.put("post_dispose_mb", post);
        r.put("delta_mb", peak - post); r.put("rules_fired", fired);
        return r;
    }

    private static void forceGc() throws InterruptedException {
        System.gc(); Thread.sleep(GC_SETTLE_MS); System.gc(); Thread.sleep(GC_SETTLE_MS);
    }
    private static double heapUsedMb() {
        return MX.getHeapMemoryUsage().getUsed() / (1024.0 * 1024.0);
    }
    private static double median(List<Map<String, Object>> list, String key) {
        List<Double> v = new ArrayList<>();
        for (Map<String, Object> m : list) { Object x = m.get(key); v.add(x instanceof Double ? (Double)x : ((Number)x).doubleValue()); }
        Collections.sort(v); int n = v.size();
        return n % 2 == 0 ? (v.get(n/2-1)+v.get(n/2))/2.0 : v.get(n/2);
    }
    private static void writeJson(String out, int events, String mode, int threads, int trials,
            String jvm, long xmxMb, List<Map<String, Object>> trs,
            double medPeak, double medPost, double medDelta) throws Exception {
        StringBuilder sb = new StringBuilder("{\n");
        sb.append("  \"project\": \"binance\",\n");
        sb.append(String.format("  \"events\": %d,\n  \"mode\": \"%s\",\n  \"threads\": %d,\n  \"trials\": %d,\n", events, mode, threads, trials));
        sb.append(String.format("  \"jvm\": \"%s\",\n  \"xmx_mb\": %d,\n", jvm.replace("\"","'"), xmxMb));
        sb.append("  \"trial_results\": [\n");
        for (int i = 0; i < trs.size(); i++) {
            Map<String, Object> r = trs.get(i);
            sb.append(String.format("    {\"trial\":%d,\"jvm_baseline_mb\":%.2f,\"peak_heap_mb\":%.2f,\"post_dispose_mb\":%.2f,\"delta_mb\":%.2f,\"rules_fired\":%d}%s\n",
                    ((Number)r.get("trial")).intValue(), (double)r.get("jvm_baseline_mb"),
                    (double)r.get("peak_heap_mb"), (double)r.get("post_dispose_mb"),
                    (double)r.get("delta_mb"), ((Number)r.get("rules_fired")).intValue(),
                    i < trs.size()-1 ? "," : ""));
        }
        sb.append(String.format("  ],\n  \"median_peak_heap_mb\": %.2f,\n  \"median_post_dispose_mb\": %.2f,\n  \"median_delta_mb\": %.2f\n}\n", medPeak, medPost, medDelta));
        Files.createDirectories(Paths.get(out).getParent());
        Files.write(Paths.get(out), sb.toString().getBytes());
    }
}
