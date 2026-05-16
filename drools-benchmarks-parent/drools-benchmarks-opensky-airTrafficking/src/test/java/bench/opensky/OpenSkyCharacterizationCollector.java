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

import bench.opensky.analysis.*;
import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.KieSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Research-grade characterization collector for the OpenSky AirTraffic CEP benchmark.
 *
 * Aligned with Binance/Wikimedia characterization structure:
 *   A  Static rule-base properties
 *   B  Structural properties (rule coverage, condition distribution)
 *   C  Runtime metrics (firings, WM, conflict set, selectivity, IAT)
 *   D  Data / domain properties (event count, timestamp span, throughput)
 *   E  Category activations per event (by rule group R0xx)
 *
 * Run via:
 *   mvn exec:java -Dexec.mainClass=bench.opensky.OpenSkyCharacterizationCollector
 *                 -Dexec.classpathScope=test --no-transfer-progress
 */
public class OpenSkyCharacterizationCollector {

    private static final String DRL_PATH  = "airTraffick_rules.drl";
    private static final String DATA_FILE = "data/data/split_400k.jsonl";

    // ── Agenda listener ────────────────────────────────────────────────────
    static class AgendaMetrics implements AgendaEventListener {
        final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();
        int cs = 0, peakCS = 0;
        long samples = 0, csSum = 0;
        final Set<Long> selectiveTs = new HashSet<>();
        long currentTs = -1;

        @Override public void matchCreated(MatchCreatedEvent e) {
            cs++; if (cs > peakCS) peakCS = cs;
            if (currentTs >= 0) selectiveTs.add(currentTs);
        }
        @Override public void matchCancelled(MatchCancelledEvent e) { cs = Math.max(0, cs - 1); }
        @Override public void beforeMatchFired(BeforeMatchFiredEvent e) { samples++; csSum += cs; }
        @Override public void afterMatchFired(AfterMatchFiredEvent e) {
            cs = Math.max(0, cs - 1);
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
        final AtomicLong insertions = new AtomicLong(), retractions = new AtomicLong(), updates = new AtomicLong();
        long peakFacts = 0, currentFacts = 0;
        @Override public void objectInserted(ObjectInsertedEvent e)  { currentFacts++; if (currentFacts > peakFacts) peakFacts = currentFacts; insertions.incrementAndGet(); }
        @Override public void objectDeleted(ObjectDeletedEvent e)    { currentFacts = Math.max(0, currentFacts - 1); retractions.incrementAndGet(); }
        @Override public void objectUpdated(ObjectUpdatedEvent e)    { updates.incrementAndGet(); }
        long totalChanges() { return insertions.get() + retractions.get() + updates.get(); }
    }

    // ── Static DRL analysis ────────────────────────────────────────────────
    static class DrlStaticAnalysis {
        int totalRules = 0, temporalRules = 0, joinRules = 0, chainingRules = 0;
        int totalConds = 0, maxConds = 0, windowCount = 0;
        long cond1 = 0, cond2 = 0, cond3 = 0, cond4plus = 0;
        final Set<String> lhsTypes = new LinkedHashSet<>();

        static DrlStaticAnalysis analyse(String drl) {
            DrlStaticAnalysis a = new DrlStaticAnalysis();
            String[] parts = drl.split("(?m)^rule\\s+");
            for (int i = 1; i < parts.length; i++) {
                String rule = parts[i]; a.totalRules++;
                String when = extractWhen(rule), then = extractThen(rule);
                int conds = countPatterns(when);
                a.totalConds += conds; if (conds > a.maxConds) a.maxConds = conds;
                if (conds == 1) a.cond1++;
                else if (conds == 2) a.cond2++;
                else if (conds == 3) a.cond3++;
                else if (conds >= 4) a.cond4plus++;
                Matcher tempMatcher = Pattern.compile("\\b(before|after|over window|within|meets|metby|overlaps|overlappedby|during|includes|starts|startedby|finishes|finishedby|coincides)\\b").matcher(when);
                if (tempMatcher.find() || when.contains("@Expires")) a.temporalRules++;
                a.windowCount += countOccurrences(when, "over window");
                if (conds >= 2) a.joinRules++;
                if (then.contains("insert(") || then.contains("insertLogical(")) a.chainingRules++;
                collectTypes(when, a.lhsTypes);
            }
            return a;
        }

        static String extractWhen(String r) { int w=r.indexOf("when"),t=r.indexOf("then"); return (w<0||t<=w)?""  :r.substring(w+4,t); }
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

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  OpenSky AirTraffic CEP — Characterization Collector ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ── Static DRL analysis ───────────────────────────────────────────
        String drlContent;
        try (InputStream is = OpenSkyCharacterizationCollector.class.getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) throw new FileNotFoundException("DRL not found on classpath: " + DRL_PATH);
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        DrlStaticAnalysis sta = DrlStaticAnalysis.analyse(drlContent);
        
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> ruleMetas = parser.parse(drlContent);
        ForwardChainFinder fcf = new ForwardChainFinder(ruleMetas);
        ForwardChainFinder.ForwardChainResult fcResult = fcf.findForwardChain("OpenSkyStateVector");
        int maxChainDepth = fcResult.getMaxDepth();

        // Alpha-sharing ratio proxy: (total input occurrences - distinct types) / total input occurrences
        long totalInputOccurrences = sta.totalConds;
        long uniqueInputTypes      = sta.lhsTypes.size();
        double alphaSharingRatio   = totalInputOccurrences > 0
                ? 1.0 - ((double) uniqueInputTypes / totalInputOccurrences) : 0;

        // Temporal pattern counts from DRL text
        long windowTimeCount = countRegex(drlContent, "window:time");
        long windowLenCount  = countRegex(drlContent, "window:length");
        long afterCount      = countRegex(drlContent, "\\bafter\\b");
        long beforeCount     = countRegex(drlContent, "\\bbefore\\b");
        long notCount        = countRegex(drlContent, "\\bnot\\b");
        long accumCount      = countRegex(drlContent, "\\baccumulate\\b");
        long evalCount       = countRegex(drlContent, "\\beval\\b");

        // ── [A] Static Rule-Base Properties ──────────────────────────────
        System.out.println("── [A] Static Rule-Base Properties ─────────────────────");
        System.out.printf("  A1  Total rules (R):              %d%n", sta.totalRules);
        System.out.printf("  A2  Avg conditions/rule:          %.2f  (max=%d)%n",
                sta.totalRules > 0 ? (double) sta.totalConds / sta.totalRules : 0, sta.maxConds);
        System.out.printf("  A2  Condition distribution:       {1 cond=%d, 2 cond=%d, 3 cond=%d, 4+ cond=%d}%n",
                sta.cond1, sta.cond2, sta.cond3, sta.cond4plus);
        System.out.printf("  A3  Temporal rules:               %d%n", sta.temporalRules);
        System.out.printf("  A4  Join-heavy rules (≥2 conds):  %d%n", sta.joinRules);
        System.out.printf("  A4  Chaining rules (insert RHS):  %d%n", sta.chainingRules);
        System.out.printf("  A5  Alpha sharing ratio (proxy):  %.3f%n", alphaSharingRatio);
        System.out.printf("  A6  Distinct input fact types:    %d  → %s%n", sta.lhsTypes.size(), sta.lhsTypes);
        System.out.printf("  A7  Temporal windows (over):      %d%n", sta.windowCount);
        System.out.printf("  A7  window:time / window:length:  %d / %d%n", windowTimeCount, windowLenCount);
        System.out.printf("  A8  'after' / 'before' patterns:  %d / %d%n", afterCount, beforeCount);
        System.out.printf("  A8  negation 'not' patterns:      %d%n", notCount);
        System.out.printf("  A8  'accumulate' patterns:        %d%n", accumCount);
        System.out.printf("  A8  'eval' temporal patterns:     %d%n", evalCount);
        System.out.println();

        // ── Load events ───────────────────────────────────────────────────
        System.out.printf("── [D] Loading dataset: %s%n", dataFile);
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat(dataFile);
        System.out.printf("  Loaded %,d state-vector events%n%n", events.size());

        // ── [D] Data / Domain Properties (IAT analysis) ───────────────────
        System.out.println("── [D] Data / Domain Properties ─────────────────────────");

        // Per-aircraft IAT (same as per-symbol IAT in Binance)
        Map<String, List<Long>> perAircraftTs = new LinkedHashMap<>();
        long firstTs = -1, lastTs = -1;
        Set<String> distinctIcao24 = new LinkedHashSet<>();
        for (OpenSkyStateVector sv : events) {
            long tsMs = sv.getSnapshotTime() * 1000L;
            if (firstTs < 0) firstTs = tsMs;
            lastTs = tsMs;
            String icao = sv.getIcao24();
            if (icao != null) {
                distinctIcao24.add(icao);
                perAircraftTs.computeIfAbsent(icao, k -> new ArrayList<>()).add(tsMs);
            }
        }
        long spanMs = (firstTs >= 0 && lastTs >= firstTs) ? (lastTs - firstTs) : 0;
        double arrivalRatePerSec = spanMs > 0 ? events.size() * 1000.0 / spanMs : 0;

        List<Double> allIATs = new ArrayList<>();
        for (List<Long> tsList : perAircraftTs.values()) {
            Collections.sort(tsList);
            for (int i = 1; i < tsList.size(); i++) {
                allIATs.add((double)(tsList.get(i) - tsList.get(i - 1)));
            }
        }
        Collections.sort(allIATs);
        double medianIAT = allIATs.isEmpty() ? 0 : allIATs.get(allIATs.size() / 2);
        double sumIAT = 0, sumIAT2 = 0;
        for (double iat : allIATs) { sumIAT += iat; sumIAT2 += iat * iat; }
        double meanIAT = allIATs.isEmpty() ? 0 : sumIAT / allIATs.size();
        double varIAT  = allIATs.isEmpty() ? 0 : (sumIAT2 / allIATs.size()) - (meanIAT * meanIAT);
        double stdIAT  = Math.sqrt(Math.max(0, varIAT));
        double cvIAT   = meanIAT > 0 ? stdIAT / meanIAT : 0;
        String velocityClass = cvIAT > 1.0 ? "BURSTY" : (cvIAT > 0.5 ? "MODERATE" : "STEADY/PERIODIC");

        System.out.printf("  D1  Total events in dataset:      %,d%n", events.size());
        System.out.printf("  D1  Time span:                    %,d ms (%.1f min)%n", spanMs, spanMs / 60000.0);
        System.out.printf("  D2  Distinct aircraft (ICAO24):   %,d%n", distinctIcao24.size());
        System.out.printf("  C1  Event arrival rate (dataset): %.0f events/sec%n", arrivalRatePerSec);
        System.out.printf("  C2  Mean per-aircraft IAT:        %.2f ms%n", meanIAT);
        System.out.printf("  C2  Median per-aircraft IAT:      %.2f ms%n", medianIAT);
        System.out.printf("  C2  IAT std dev (per-aircraft):   %.2f ms%n", stdIAT);
        System.out.printf("  C2  Coeff of variation (CV):      %.3f → %s%n", cvIAT, velocityClass);
        System.out.println();

        // ── [C] Runtime Metrics — full replay ────────────────────────────
        System.out.println("── [C] Runtime Metrics (full replay) ────────────────────");
        System.out.printf("  Replaying %,d events...%n", events.size());

        OpenSkyReplayEngine engine = new OpenSkyReplayEngine();
        engine.init();
        KieSession session = engine.getSession();

        AgendaMetrics agenda = new AgendaMetrics();
        WMMetrics wm         = new WMMetrics();
        session.addEventListener(agenda);
        session.addEventListener(wm);

        // Collect all rule names from KieBase
        Set<String> allRuleNames = session.getKieBase().getKiePackages().stream()
                .flatMap(pkg -> pkg.getRules().stream())
                .map(Rule::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        long t0 = System.currentTimeMillis();
        long totalFired = 0;
        for (OpenSkyStateVector sv : events) {
            long tsMs = sv.getSnapshotTime() * 1000L;
            agenda.currentTs = tsMs;
            totalFired += engine.ingestEvent(sv);
        }
        long replayMs = System.currentTimeMillis() - t0;
        engine.dispose();

        // Coverage
        Set<String> firedRuleNames = agenda.fireCounts.keySet();
        long coveredCount = allRuleNames.stream().filter(firedRuleNames::contains).count();
        double coveragePct = allRuleNames.isEmpty() ? 0 : 100.0 * coveredCount / allRuleNames.size();
        long selectiveEvents = agenda.selectiveTs.size();
        double selectivity = 100.0 * selectiveEvents / events.size();

        System.out.printf("  C1  Replay throughput:            %.0f events/sec%n",
                replayMs > 0 ? events.size() * 1000.0 / replayMs : 0);
        System.out.printf("  C1  Wall-clock replay time:       %,d ms%n", replayMs);
        System.out.printf("  C3  Selectivity:                  %.2f%% of events trigger ≥1 rule (%,d/%,d)%n",
                selectivity, selectiveEvents, events.size());
        System.out.printf("  C4  Peak WM size:                 %,d facts%n", wm.peakFacts);
        System.out.printf("  C5  Total WM changes:             %,d (ins=%,d  ret=%,d  upd=%,d)%n",
                wm.totalChanges(), wm.insertions.get(), wm.retractions.get(), wm.updates.get());
        System.out.printf("  C5  Avg WM changes/event:         %.2f%n",
                events.isEmpty() ? 0 : (double) wm.totalChanges() / events.size());
        System.out.printf("  C6  Avg conflict-set size:        %.4f activations%n", agenda.avgCS());
        System.out.printf("  C6  Peak conflict-set size:       %,d activations%n", agenda.peakCS);
        System.out.printf("  C7  Total rule activations:       %,d%n", totalFired);
        System.out.printf("  C7  Rules fired per event:        %.4f%n",
                events.isEmpty() ? 0 : (double) totalFired / events.size());
        System.out.printf("  C3  Rule coverage:                %.1f%% (%d/%d rules fired ≥1x)%n",
                coveragePct, coveredCount, allRuleNames.size());
        System.out.println();

        // ── [B] Structural Properties ─────────────────────────────────────
        System.out.println("── [B] Structural Properties ────────────────────────────");
        System.out.printf("  B1  Rules covered at runtime:     %d / %d (%.1f%%)%n",
                coveredCount, allRuleNames.size(), coveragePct);
        System.out.printf("  B2  Zero-coverage rules:          %d%n",
                allRuleNames.size() - coveredCount);
        System.out.printf("  B4  Max chaining depth:           %d%n", maxChainDepth);
        System.out.println();

        // ── Paper-ready characterization table ────────────────────────────
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         CHARACTERIZATION TABLE (Paper-ready)         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("│ %-36s │ %-15s │%n", "Property", "OpenSky CEP");
        System.out.println("├──────────────────────────────────────┼─────────────────┤");
        System.out.printf("│ %-36s │ %-15d │%n", "A1  Rules (R)", sta.totalRules);
        System.out.printf("│ %-36s │ %-15.2f │%n", "A2  Avg conditions/rule",
                sta.totalRules > 0 ? (double) sta.totalConds / sta.totalRules : 0);
        System.out.printf("│ %-36s │ %-15d │%n", "A2  Max conditions/rule", sta.maxConds);
        System.out.printf("│ %-36s │ %-15d │%n", "A4  Chaining rules (insert RHS)", sta.chainingRules);
        System.out.printf("│ %-36s │ %-15d │%n", "A6  Distinct input fact types", sta.lhsTypes.size());
        System.out.printf("│ %-36s │ %-15.3f │%n", "A5  Alpha sharing ratio (proxy)", alphaSharingRatio);
        System.out.printf("│ %-36s │ %-15d │%n", "A7  Temporal windows (over)", sta.windowCount);
        System.out.printf("│ %-36s │ %-15s │%n", "A8  'after'/'before' patterns", afterCount + "/" + beforeCount);
        System.out.printf("│ %-36s │ %-15d │%n", "A8  eval temporal patterns", (int) evalCount);
        System.out.printf("│ %-36s │ %-15d │%n", "B4  Max chaining depth", maxChainDepth);
        System.out.printf("│ %-36s │ %-15.0f │%n", "C1  Event arrival rate (ev/s)", arrivalRatePerSec);
        System.out.printf("│ %-36s │ %-15.3f │%n", "C2  IAT coeff of variation (CV)", cvIAT);
        System.out.printf("│ %-36s │ %-15s │%n", "C2  Velocity class", velocityClass);
        System.out.printf("│ %-36s │ %-14.2f%% │%n", "C3  Selectivity", selectivity);
        System.out.printf("│ %-36s │ %-15d │%n", "C4  Peak WM size (facts)", wm.peakFacts);
        System.out.printf("│ %-36s │ %-15.2f │%n", "C5  Avg WM changes/event",
                events.isEmpty() ? 0 : (double) wm.totalChanges() / events.size());
        System.out.printf("│ %-36s │ %-15.4f │%n", "C6  Avg conflict set size", agenda.avgCS());
        System.out.printf("│ %-36s │ %-15d │%n", "C6  Peak conflict set size", agenda.peakCS);
        System.out.printf("│ %-36s │ %-15.4f │%n", "C7  Rules fired per event",
                events.isEmpty() ? 0 : (double) totalFired / events.size());
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "C3  Rule coverage on dataset", coveragePct);
        System.out.printf("│ %-36s │ %-15s │%n", "D1  Dataset size (events)", String.format("%,d", events.size()));
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct aircraft (ICAO24)", distinctIcao24.size());
        System.out.printf("│ %-36s │ %-15s │%n", "D4  Data provenance", "OpenSky Network");
        System.out.println("└──────────────────────────────────────┴─────────────────┘");
        System.out.println();

        // ── Per-rule fire counts (top 20) ─────────────────────────────────
        System.out.println("── Per-Rule Activation Counts (top 20) ─────────────────");
        agenda.fireCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(20)
                .forEach(e -> System.out.printf("  %-55s %,10d%n", e.getKey(), e.getValue().get()));

        System.out.println();
        System.out.println("── Rules That NEVER Fired ───────────────────────────────");
        allRuleNames.stream()
                .filter(r -> !firedRuleNames.contains(r))
                .sorted()
                .forEach(r -> System.out.println("  ❌ " + r));

        // ── [E] Category Activations per Event ───────────────────────────
        System.out.println();
        System.out.println("── [E] Category Activations per Event ───────────────────");
        System.out.println("  Acts/Evt = category_activations / total_events");
        System.out.println();

        // Category by rule number prefix
        java.util.function.Function<String, String> categorize = name -> {
            Matcher m2 = Pattern.compile("^R(\\d+)").matcher(name);
            if (m2.find()) {
                int n = Integer.parseInt(m2.group(1));
                if (n <= 20)  return "R001-R020  Track & Data Quality";
                if (n <= 40)  return "R021-R040  Kinematics & Anomaly";
                if (n <= 55)  return "R041-R055  Grid & Pair Candidates";
                if (n <= 80)  return "R056-R080  Conflict Detection (CPA)";
                if (n <= 90)  return "R081-R090  Alert & Advisory Mgmt";
                              return "R091-R100  Audit & Compound Rules";
            }
            return "OTHER / LIFECYCLE";
        };

        Map<String, Long> catActivations = new TreeMap<>();
        Map<String, Long> catRuleCount   = new TreeMap<>();
        Map<String, Long> catFiredRules  = new TreeMap<>();
        for (String ruleName : allRuleNames) {
            String cat = categorize.apply(ruleName);
            catRuleCount.merge(cat, 1L, Long::sum);
            long fires = agenda.fireCounts.containsKey(ruleName)
                    ? agenda.fireCounts.get(ruleName).get() : 0L;
            catActivations.merge(cat, fires, Long::sum);
            if (fires > 0) catFiredRules.merge(cat, 1L, Long::sum);
        }

        final long totalEvts   = events.size();
        final long totalActsFinal = totalFired;
        System.out.printf("  %-40s  %6s  %8s  %10s  %8s  %8s%n",
                "Category", "Rules", "Fired", "Activns", "Acts/Evt", "% total");
        System.out.println("  " + "-".repeat(86));
        catActivations.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> {
                    String cat  = e.getKey();
                    long acts   = e.getValue();
                    long rules  = catRuleCount.getOrDefault(cat, 0L);
                    long fired  = catFiredRules.getOrDefault(cat, 0L);
                    double prob = totalEvts > 0 ? (double) acts / totalEvts : 0;
                    double pct  = totalActsFinal > 0 ? 100.0 * acts / totalActsFinal : 0;
                    System.out.printf("  %-40s  %6d  %8d  %10s  %8.4f  %7.2f%%%n",
                            cat, rules, fired, String.format("%,d", acts), prob, pct);
                });

        // ── Section F: Chain-depth firing probabilities ─────────────────────
        System.out.println();
        System.out.println("── [F] Forward Chain Depth Activations per Event ────────");
        System.out.println("  Entry point: OpenSkyStateVector (externally inserted by Java replay loop)");
        System.out.println("  Acts/Evt = activations_at_depth_d / total_events");
        System.out.println();

        Map<Integer, java.util.List<String>> byDepth = new java.util.TreeMap<>(fcResult.getChainsByDepth());
        java.util.Set<String> uncaptured = new java.util.LinkedHashSet<>(fcResult.getUncapturedRules());

        System.out.printf("  %-8s  %-6s  %-8s  %-12s  %-8s  %-9s%n",
                "Depth", "Rules", "Fired", "Activations", "Acts/Evt", "Ratio(D0)");
        System.out.println("  " + "-".repeat(60));

        long depth0Acts = 0;
        for (Map.Entry<Integer, java.util.List<String>> depthEntry : byDepth.entrySet()) {
            int depth = depthEntry.getKey();
            java.util.List<String> rulesAtDepth = depthEntry.getValue();
            long depthActs  = rulesAtDepth.stream()
                    .mapToLong(r -> agenda.fireCounts.containsKey(r)
                            ? agenda.fireCounts.get(r).get() : 0L)
                    .sum();
            long firedCount = rulesAtDepth.stream()
                    .filter(agenda.fireCounts::containsKey).count();
            double prob = totalEvts > 0 ? (double) depthActs / totalEvts : 0;
            double condProb = depth == 0 ? 1.0
                    : (depth0Acts > 0 ? (double) depthActs / depth0Acts : 0);
            if (depth == 0) depth0Acts = depthActs;
            System.out.printf("  Depth %-2d  %6d  %8d  %12s  %8.4f  %9.4f%n",
                    depth, rulesAtDepth.size(), firedCount,
                    String.format("%,d", depthActs), prob, condProb);
        }

        long uncapturedActs = uncaptured.stream()
                .mapToLong(r -> agenda.fireCounts.containsKey(r)
                        ? agenda.fireCounts.get(r).get() : 0L)
                .sum();
        System.out.printf("  %-8s  %6d  %8d  %12s  %8.4f  %9s%n",
                "Other", uncaptured.size(),
                uncaptured.stream().filter(agenda.fireCounts::containsKey).count(),
                String.format("%,d", uncapturedActs),
                totalEvts > 0 ? (double) uncapturedActs / totalEvts : 0, "N/A");

        System.out.println();
        System.out.println("  Depth-level rule membership:");
        for (Map.Entry<Integer, java.util.List<String>> e : byDepth.entrySet()) {
            System.out.printf("  Depth %d: %s%n", e.getKey(),
                    e.getValue().stream().sorted().collect(Collectors.joining(", ")));
        }
    }

    /** Count regex pattern occurrences in text */
    private static long countRegex(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        long c = 0; while (m.find()) c++; return c;
    }
}
