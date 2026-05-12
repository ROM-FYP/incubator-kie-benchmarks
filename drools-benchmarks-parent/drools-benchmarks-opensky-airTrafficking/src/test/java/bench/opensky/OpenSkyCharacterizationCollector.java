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
package bench.opensky;

import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.KieSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;

/**
 * Research-grade characterization collector for the OpenSky AirTraffic CEP benchmark.
 *
 * Computes benchmark characterization dimensions:
 *   A  Static rule-base properties (rule count, patterns, temporal, join heavy, chaining)
 *   B  Structural properties (conditions per rule, temporal window density)
 *   C  Dynamic / runtime properties (rule coverage, firings per event, conflict set, WM size)
 *   D  Data / domain properties (event count, timestamp span, replay throughput)
 *
 * Run via:
 *   mvn exec:java -Dexec.mainClass=bench.opensky.OpenSkyCharacterizationCollector
 *                 -Dexec.classpathScope=test --no-transfer-progress
 *   (or run main() from IDE with optional dataset path arg)
 */
public class OpenSkyCharacterizationCollector {

    private static final String DRL_PATH  = "airTraffick_rules.drl";
    private static final String DATA_FILE = "data/data/split_400k.jsonl";

    // ── Agenda listener ────────────────────────────────────────────────────
    static class AgendaMetrics implements AgendaEventListener {
        final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();
        int cs = 0, peakCS = 0; long samples = 0, csSum = 0;
        final Set<Long> selectiveTs = new HashSet<>(); long currentTs = -1;

        @Override public void matchCreated(MatchCreatedEvent e) {
            cs++; if (cs > peakCS) peakCS = cs;
            if (currentTs >= 0) selectiveTs.add(currentTs);
        }
        @Override public void matchCancelled(MatchCancelledEvent e) { cs = Math.max(0, cs-1); }
        @Override public void beforeMatchFired(BeforeMatchFiredEvent e) { samples++; csSum += cs; }
        @Override public void afterMatchFired(AfterMatchFiredEvent e) {
            cs = Math.max(0, cs-1);
            fireCounts.computeIfAbsent(e.getMatch().getRule().getName(), k -> new AtomicInteger()).incrementAndGet();
        }
        @Override public void agendaGroupPopped(AgendaGroupPoppedEvent e) {}
        @Override public void agendaGroupPushed(AgendaGroupPushedEvent e) {}
        @Override public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {}
        @Override public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {}
        @Override public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {}
        @Override public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {}
        double avgCS() { return samples > 0 ? (double) csSum / samples : 0; }
    }

    // ── WM listener ────────────────────────────────────────────────────────
    static class WMMetrics implements RuleRuntimeEventListener {
        AtomicLong ins = new AtomicLong(), del = new AtomicLong(), upd = new AtomicLong();
        long peak = 0, cur = 0;
        @Override public void objectInserted(ObjectInsertedEvent e)  { cur++; if (cur > peak) peak = cur; ins.incrementAndGet(); }
        @Override public void objectDeleted(ObjectDeletedEvent e)    { cur = Math.max(0, cur-1); del.incrementAndGet(); }
        @Override public void objectUpdated(ObjectUpdatedEvent e)    { upd.incrementAndGet(); }
    }

    // ── Static DRL analysis ────────────────────────────────────────────────
    static class DrlStaticAnalysis {
        int totalRules = 0, temporalRules = 0, joinRules = 0, chainingRules = 0;
        int totalConds = 0, maxConds = 0, windowCount = 0;
        final Set<String> lhsTypes = new LinkedHashSet<>();

        static DrlStaticAnalysis analyse(String drl) {
            DrlStaticAnalysis a = new DrlStaticAnalysis();
            String[] parts = drl.split("(?m)^rule\\s+");
            for (int i = 1; i < parts.length; i++) {
                String rule = parts[i]; a.totalRules++;
                String when = extractWhen(rule), then = extractThen(rule);
                int conds = countPatterns(when);
                a.totalConds += conds; if (conds > a.maxConds) a.maxConds = conds;
                if (when.contains("over window") || when.contains("@Expires") || when.contains("within")) a.temporalRules++;
                a.windowCount += countOccurrences(when, "over window");
                if (conds >= 2) a.joinRules++;
                if (then.contains("insert(") || then.contains("insertLogical(")) a.chainingRules++;
                collectTypes(when, a.lhsTypes);
            }
            return a;
        }

        static String extractWhen(String r) { int w=r.indexOf("when"),t=r.indexOf("then"); return (w<0||t<=w)?"":r.substring(w+4,t); }
        static String extractThen(String r) { int t=r.indexOf("then"),e=r.indexOf("\nend"); return t<0?"":e>t?r.substring(t+4,e):r.substring(t+4); }
        static int countPatterns(String s) {
            Matcher m = Pattern.compile("^\\s+[A-Z][A-Za-z]+\\s*\\(", Pattern.MULTILINE).matcher(s);
            int c=0; while(m.find()) c++; return c;
        }
        static void collectTypes(String s, Set<String> out) {
            Matcher m = Pattern.compile("\\b([A-Z][A-Za-z]+)\\s*\\(").matcher(s); while(m.find()) out.add(m.group(1));
        }
        static int countOccurrences(String s, String sub) { int c=0,i=0; while((i=s.indexOf(sub,i))>=0){c++;i+=sub.length();} return c; }
    }

    // ── Main ───────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        String dataFile = args.length > 0 ? args[0] : DATA_FILE;

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   OpenSky AirTraffic CEP — Characterization Collector        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // ── Static DRL analysis ───────────────────────────────────────────
        String drlContent;
        try (InputStream is = OpenSkyCharacterizationCollector.class.getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) throw new FileNotFoundException("DRL not found on classpath: " + DRL_PATH);
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        DrlStaticAnalysis sta = DrlStaticAnalysis.analyse(drlContent);

        // ── Load events ───────────────────────────────────────────────────
        System.out.printf("[D] Loading events from: %s%n", dataFile);
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat(dataFile);
        System.out.printf("[D] Loaded %,d state-vector events%n", events.size());

        // ── Dynamic run ───────────────────────────────────────────────────
        OpenSkyReplayEngine engine = new OpenSkyReplayEngine();
        engine.init();
        KieSession session = engine.getSession();

        AgendaMetrics agenda = new AgendaMetrics();
        WMMetrics wm         = new WMMetrics();
        session.addEventListener(agenda);
        session.addEventListener(wm);

        long t0 = System.currentTimeMillis();
        long firstTs = -1, lastTs = -1;
        int totalFired = 0;

        for (OpenSkyStateVector sv : events) {
            long tsMs = sv.getSnapshotTime() * 1000L;
            if (firstTs < 0) firstTs = tsMs; lastTs = tsMs;
            agenda.currentTs = tsMs;
            totalFired += engine.ingestEvent(sv);
        }
        long elapsed = System.currentTimeMillis() - t0;
        engine.dispose();

        // ── Print results ─────────────────────────────────────────────────
        double spanSec   = firstTs >= 0 ? (lastTs - firstTs) / 1000.0 : 0;
        double evPerSec  = elapsed > 0 ? events.size() * 1000.0 / elapsed : 0;
        double fireRate  = events.size() > 0 ? (double) totalFired / events.size() : 0;
        int covered      = agenda.fireCounts.size();
        double coverage  = sta.totalRules > 0 ? 100.0 * covered / sta.totalRules : 0;
        double selectivity = events.size() > 0 ? 100.0 * agenda.selectiveTs.size() / events.size() : 0;

        System.out.println("\n══════════════════════ CHARACTERIZATION RESULTS ══════════════════════");
        System.out.printf("A1  Total rules (DRL parse)          %8d%n", sta.totalRules);
        System.out.printf("A2  Temporal rules                   %8d%n", sta.temporalRules);
        System.out.printf("A3  Join-heavy rules (≥2 patterns)   %8d%n", sta.joinRules);
        System.out.printf("A4  Chaining rules (insert in RHS)   %8d%n", sta.chainingRules);
        System.out.printf("A5  Avg conditions per rule          %8.2f%n", sta.totalRules>0?(double)sta.totalConds/sta.totalRules:0);
        System.out.printf("A6  Max conditions in a single rule  %8d%n", sta.maxConds);
        System.out.printf("A7  Temporal windows (over window)   %8d%n", sta.windowCount);
        System.out.printf("A8  Distinct LHS pattern types       %8d  %s%n", sta.lhsTypes.size(), sta.lhsTypes);
        System.out.printf("B1  Rules covered at runtime         %8d / %d (%.1f%%)%n", covered, sta.totalRules, coverage);
        System.out.printf("C1  Total rules fired                %8,d%n", totalFired);
        System.out.printf("C2  Firings per event                %8.4f%n", fireRate);
        System.out.printf("C3  Avg conflict-set size            %8.4f%n", agenda.avgCS());
        System.out.printf("C4  Peak conflict-set size           %8d%n", agenda.peakCS);
        System.out.printf("C5  Event selectivity                %8.2f%%%n", selectivity);
        System.out.printf("C6  Total WM insertions              %8,d%n", wm.ins.get());
        System.out.printf("C7  Total WM retractions             %8,d%n", wm.del.get());
        System.out.printf("C8  Total WM updates                 %8,d%n", wm.upd.get());
        System.out.printf("C9  Peak WM fact count               %8,d%n", wm.peak);
        System.out.printf("D1  Total events replayed            %8,d%n", events.size());
        System.out.printf("D2  Event timestamp span             %8.1f s%n", spanSec);
        System.out.printf("D3  Replay throughput                %8.0f ev/s%n", evPerSec);
        System.out.printf("D4  Wall-clock replay time           %8d ms%n", elapsed);
        System.out.println("══════════════════════════════════════════════════════════════════════");

        System.out.println("\n── Top-10 rules by fire count ──────────────────────────────────────");
        agenda.fireCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(10)
                .forEach(e -> System.out.printf("  %-50s %,8d%n", e.getKey(), e.getValue().get()));

        System.out.println("\n── Zero-coverage rules ─────────────────────────────────────────────");
        Matcher m = Pattern.compile("(?m)^rule\\s+\"([^\"]+)\"").matcher(drlContent);
        while (m.find()) { String n = m.group(1); if (!agenda.fireCounts.containsKey(n)) System.out.printf("  ! %s%n", n); }
    }
}
