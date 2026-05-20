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
package org.kie.benchmark.cep.wikimedia;

import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Research-grade characterization collector for the Wikimedia CEP benchmark.
 * Aligned exactly with the Binance CharacterizationCollector output.
 */
public class WikimediaCharacterizationCollector {

    private static final String DRL_PATH  = "rules/wikimedia_content_moderation_join_heavy.drl";
    private static final String DATA_FILE = "src/main/resources/data/data/split_400k.jsonl";

    // ── A) Agenda listener ────────────────────────────────────────────────────
    static class AgendaMetrics implements AgendaEventListener {
        final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();
        int currentConflictSet = 0; long conflictSetSamples = 0, conflictSetSum = 0; int peakConflictSet = 0;
        final Set<Long> selectiveEventTs = new HashSet<>(); long currentEventTs = -1;

        @Override public void matchCreated(MatchCreatedEvent e) {
            currentConflictSet++;
            if (currentConflictSet > peakConflictSet) peakConflictSet = currentConflictSet;
            if (currentEventTs >= 0) selectiveEventTs.add(currentEventTs);
        }
        @Override public void matchCancelled(MatchCancelledEvent e) { currentConflictSet = Math.max(0, currentConflictSet-1); }
        @Override public void beforeMatchFired(BeforeMatchFiredEvent e) { conflictSetSamples++; conflictSetSum += currentConflictSet; }
        @Override public void afterMatchFired(AfterMatchFiredEvent e) {
            currentConflictSet = Math.max(0, currentConflictSet-1);
            fireCounts.computeIfAbsent(e.getMatch().getRule().getName(), k -> new AtomicInteger()).incrementAndGet();
        }
        @Override public void agendaGroupPopped(AgendaGroupPoppedEvent e) {}
        @Override public void agendaGroupPushed(AgendaGroupPushedEvent e) {}
        @Override public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {}
        @Override public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {}
        @Override public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {}
        @Override public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {}
        double avgConflictSet() { return conflictSetSamples > 0 ? (double) conflictSetSum / conflictSetSamples : 0; }
    }

    // ── B) WM listener ────────────────────────────────────────────────────────
    static class WMMetrics implements RuleRuntimeEventListener {
        final AtomicLong insertions = new AtomicLong(), retractions = new AtomicLong(), updates = new AtomicLong();
        long peakFactCount = 0; long factCountSum = 0; long factCountSamples = 0;
        private KieSession session;
        WMMetrics(KieSession session) { this.session = session; }
        @Override public void objectInserted(ObjectInsertedEvent e)  { insertions.incrementAndGet(); sampleWM(); }
        @Override public void objectDeleted(ObjectDeletedEvent e)    { retractions.incrementAndGet(); sampleWM(); }
        @Override public void objectUpdated(ObjectUpdatedEvent e)    { updates.incrementAndGet(); sampleWM(); }
        void sampleWM() {
            long fc = session.getFactCount();
            if (fc > peakFactCount) peakFactCount = fc;
            factCountSum += fc;
            factCountSamples++;
        }
        double avgFactCount() { return factCountSamples > 0 ? (double) factCountSum / factCountSamples : 0; }
        long totalWMChanges() { return insertions.get() + retractions.get() + updates.get(); }
    }

    // ── Static DRL analysis ────────────────────────────────────────────────
    static class RuleMeta {
        String name;
        Set<String> inputs = new LinkedHashSet<>();
        Set<String> outputs = new LinkedHashSet<>();
    }

    static class DrlStaticAnalysis {
        int totalRules = 0, temporalRules = 0, joinRules = 0, chainingRules = 0;
        int totalConditions = 0, maxConditions = 0, minConditions = 999;
        int temporalWindowCount = 0;
        final Set<String> eventTypes = new LinkedHashSet<>();
        final List<RuleMeta> metas = new ArrayList<>();
        final Map<String, Long> condHistogram = new LinkedHashMap<>();

        static DrlStaticAnalysis analyse(String drl) {
            DrlStaticAnalysis a = new DrlStaticAnalysis();
            a.condHistogram.put("1 cond", 0L);
            a.condHistogram.put("2 cond", 0L);
            a.condHistogram.put("3 cond", 0L);
            a.condHistogram.put("4+ cond", 0L);

            String[] ruleSections = drl.split("(?m)^rule\\s+");
            for (int i = 1; i < ruleSections.length; i++) {
                String rule = ruleSections[i];
                a.totalRules++;
                RuleMeta meta = new RuleMeta();
                // extract name
                Matcher nm = Pattern.compile("\"([^\"]+)\"").matcher(rule);
                if (nm.find()) meta.name = nm.group(1);

                String whenPart = extractWhen(rule);
                String thenPart = extractThen(rule);
                int conds = countPatterns(whenPart);
                a.totalConditions += conds;
                if (conds > a.maxConditions) a.maxConditions = conds;
                if (conds < a.minConditions) a.minConditions = conds;
                
                if (conds == 1) a.condHistogram.put("1 cond", a.condHistogram.get("1 cond") + 1);
                else if (conds == 2) a.condHistogram.put("2 cond", a.condHistogram.get("2 cond") + 1);
                else if (conds == 3) a.condHistogram.put("3 cond", a.condHistogram.get("3 cond") + 1);
                else if (conds >= 4) a.condHistogram.put("4+ cond", a.condHistogram.get("4+ cond") + 1);

                if (whenPart.contains("over window") || whenPart.contains("@Expires") ||
                    whenPart.matches("(?s).*\\bwithin\\b.*") || whenPart.contains("SessionPseudoClock")) {
                    a.temporalRules++;
                }
                if (whenPart.contains("over window")) {
                    a.temporalWindowCount += countMatches(whenPart, "over window");
                }
                if (conds >= 2) a.joinRules++;
                if (thenPart.contains("insert(")) a.chainingRules++;

                collectTypes(whenPart, meta.inputs);
                a.eventTypes.addAll(meta.inputs);
                collectTypesThen(thenPart, meta.outputs);
                a.metas.add(meta);
            }
            if (a.minConditions == 999) a.minConditions = 0;
            return a;
        }

        private static String extractWhen(String ruleBody) {
            int w = ruleBody.indexOf("when"); int t = ruleBody.indexOf("then");
            if (w < 0 || t < 0 || t <= w) return "";
            return ruleBody.substring(w + 4, t);
        }
        private static String extractThen(String ruleBody) {
            int t = ruleBody.indexOf("then"); int e = ruleBody.indexOf("\nend");
            if (t < 0) return "";
            return e > t ? ruleBody.substring(t + 4, e) : ruleBody.substring(t + 4);
        }
        private static int countPatterns(String when) {
            // Count top-level LHS patterns. Must handle:
            //   $var : Type(...)       — bound pattern
            //   not Type(...)          — negation
            //   Type(...)              — unbound pattern
            //   $var : Number(...) from accumulate(...)  — accumulate result
            int c = 0;
            for (String line : when.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                // Strip optional binding prefix: $var :
                String rest = trimmed.replaceFirst("^\\$\\w+\\s*:\\s*", "");
                // Strip optional 'not' prefix
                rest = rest.replaceFirst("^not\\s+", "");
                // Now check if line starts with a CapitalizedType(
                if (rest.matches("^[A-Z][A-Za-z]+\\s*\\(.*")) {
                    c++;
                }
            }
            return c;
        }
        private static void collectTypes(String src, Set<String> out) {
            Matcher m = Pattern.compile("\\b([A-Z][A-Za-z]+)\\s*\\(").matcher(src);
            while (m.find()) out.add(m.group(1));
        }
        private static void collectTypesThen(String src, Set<String> out) {
            Matcher m = Pattern.compile("insert\\(\\s*new\\s+([A-Z][A-Za-z]+)\\s*\\(").matcher(src);
            while (m.find()) out.add(m.group(1));
        }
        private static int countMatches(String s, String sub) {
            int c = 0, idx = 0;
            while ((idx = s.indexOf(sub, idx)) >= 0) { c++; idx += sub.length(); }
            return c;
        }
    }

    // ── Main ───────────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        String dataFile = args.length > 0 ? args[0] : DATA_FILE;
        int maxEvents   = args.length > 1 ? Integer.parseInt(args[1]) : Integer.MAX_VALUE;

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Wikimedia CEP Benchmark — Characterization Collector║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ── Load DRL for static analysis ──────────────────────────────────
        String drlContent;
        try (InputStream is = WikimediaCharacterizationCollector.class.getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) throw new FileNotFoundException("DRL not found: " + DRL_PATH);
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        
        System.out.println("── [A] Static Rule-Base Properties ─────────────────────");
        DrlStaticAnalysis static_ = DrlStaticAnalysis.analyse(drlContent);

        // Strip single-line comments before running pattern counts so that
        // comment text (e.g. "// accumulate over window:time") doesn't inflate C8/C9 metrics.
        String drlNoComments = drlContent.replaceAll("//[^\\n]*", "");

        long windowTimeCount  = countRegex(drlNoComments, "window:time");
        long windowLenCount   = countRegex(drlNoComments, "window:length");
        long afterCount       = countRegex(drlNoComments, "\\bafter\\b");
        // Match only LHS negation patterns: 'not TypeName(' — excludes field-level 'not contains'
        long notCount         = countRegex(drlNoComments, "\\bnot\\s+[A-Z][A-Za-z]+\\s*\\(");
        long accumCount       = countRegex(drlNoComments, "\\baccumulate\\b");
        long evalCount        = countRegex(drlNoComments, "\\beval\\b");

        long totalInputOccurrences = static_.metas.stream().mapToLong(rm -> rm.inputs.size()).sum();
        long uniqueInputTypes      = static_.eventTypes.size();
        double alphaSharingRatio   = totalInputOccurrences > 0 ? 1.0 - ((double) uniqueInputTypes / totalInputOccurrences) : 0;

        double avgConds = static_.totalRules > 0 ? (double)static_.totalConditions / static_.totalRules : 0;
        System.out.printf("  A1  Total rules (R):              %d%n", static_.totalRules);
        System.out.printf("  A2  Avg conditions/rule:          %.2f  (max=%d, min=%d)%n", avgConds, static_.maxConditions, static_.minConditions);
        System.out.printf("  A2  Condition distribution:       %s%n", static_.condHistogram);
        System.out.printf("  A5  Alpha sharing ratio (proxy):  %.3f%n", alphaSharingRatio);
        System.out.printf("  A6  Distinct input fact types:    %d  → %s%n", static_.eventTypes.size(), static_.eventTypes);
        System.out.printf("  C8  window:time rules:            %d%n", windowTimeCount);
        System.out.printf("  C8  window:length rules:          %d%n", windowLenCount);
        System.out.printf("  C9  'after' patterns:             %d%n", afterCount);
        System.out.printf("  C9  negation 'not' patterns:      %d%n", notCount);
        System.out.printf("  C9  'accumulate' patterns:        %d%n", accumCount);
        System.out.printf("  C9  'eval' temporal patterns:     %d%n", evalCount);
        System.out.println();

        System.out.println("── [B] Dependency / Structural Properties ───────────────");

        // Build a simple dependency graph: rule A → rule B if A produces a fact type that B consumes.
        // Then compute B1 (density), B2 (largest connected component), B3 (# components).
        Map<String, Set<String>> ruleProducesMap = new HashMap<>();
        Map<String, Set<String>> ruleConsumesMap = new HashMap<>();
        for (RuleMeta rm : static_.metas) {
            ruleProducesMap.put(rm.name, rm.outputs);
            ruleConsumesMap.put(rm.name, rm.inputs);
        }

        // Build adjacency: edge from ruleA → ruleB if ruleA.outputs ∩ ruleB.inputs ≠ ∅
        int V = static_.metas.size();
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (RuleMeta rm : static_.metas) adj.put(rm.name, new LinkedHashSet<>());
        int edgeCount = 0;
        for (RuleMeta a : static_.metas) {
            for (RuleMeta b : static_.metas) {
                if (a.name.equals(b.name)) continue;
                boolean connected = false;
                for (String out : a.outputs) {
                    if (b.inputs.contains(out)) { connected = true; break; }
                }
                if (connected) {
                    adj.get(a.name).add(b.name);
                    edgeCount++;
                }
            }
        }
        double maxEdges = (double) V * (V - 1);
        double graphDensity = maxEdges > 0 ? edgeCount / maxEdges : 0;

        // Connected components (undirected) via BFS
        Set<String> visited = new HashSet<>();
        List<Integer> componentSizes = new ArrayList<>();
        for (RuleMeta rm : static_.metas) {
            if (visited.contains(rm.name)) continue;
            // BFS undirected
            Deque<String> queue = new ArrayDeque<>();
            queue.add(rm.name); visited.add(rm.name);
            int size = 0;
            while (!queue.isEmpty()) {
                String cur = queue.poll(); size++;
                // Forward edges
                for (String nbr : adj.getOrDefault(cur, Collections.emptySet())) {
                    if (visited.add(nbr)) queue.add(nbr);
                }
                // Reverse edges (undirected)
                for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
                    if (entry.getValue().contains(cur) && visited.add(entry.getKey())) {
                        queue.add(entry.getKey());
                    }
                }
            }
            componentSizes.add(size);
        }
        int numComponents = componentSizes.size();
        int lccSize = componentSizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        double lccPct = V > 0 ? 100.0 * lccSize / V : 0;

        // compute chaining depth
        int maxChainDepth = computeMaxChainDepth(static_.metas);

        System.out.printf("  B1  Dependency graph density:     %.4f%n", graphDensity);
        System.out.printf("  B2  Largest connected component:  %.1f%%%n", lccPct);
        System.out.printf("  B3  Connected components:         %d%n", numComponents);
        System.out.printf("  B4  Max chaining depth:           %d%n", maxChainDepth);
        System.out.println();

        // ── D: Dataset / domain properties ─────────────────────────────────
        System.out.println("── [D] Data / Domain Properties ─────────────────────────");
        List<WikiEvent> allEvents = loadEvents(dataFile, maxEvents);
        Set<String> users = allEvents.stream().map(WikiEvent::getUser).filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));

        long distinctUsers = users.size();
        long distinctEventTypes = 1; // WikiEvent

        // Timestamps are Unix epoch seconds. Bad events (nulls, historical outliers)
        // have been permanently removed from the dataset file.
        long[] timestamps = allEvents.stream()
                .mapToLong(WikiEvent::getTimestamp)
                .filter(t -> t > 0)
                .sorted()
                .toArray();
        long spanSec = timestamps.length > 1 ? timestamps[timestamps.length - 1] - timestamps[0] : 0;
        long spanMs  = spanSec * 1000L;

        // IAT in milliseconds (converting from seconds)
        List<Double> allIATs = new ArrayList<>();
        Map<String, List<Long>> perUserTs = new LinkedHashMap<>();
        for (WikiEvent ev : allEvents) {
            long ts = ev.getTimestamp();
            if (ts == 0) continue;
            String user = ev.getUser() != null ? ev.getUser() : "UNKNOWN";
            perUserTs.computeIfAbsent(user, k -> new ArrayList<>()).add(ts);
        }
        for (List<Long> tsList : perUserTs.values()) {
            Collections.sort(tsList);
            for (int i = 1; i < tsList.size(); i++) {
                allIATs.add((double)(tsList.get(i) - tsList.get(i-1)) * 1000.0);
            }
        }
        Collections.sort(allIATs);
        double medianIAT = allIATs.isEmpty() ? 0 : allIATs.get(allIATs.size() / 2);
        double sumIAT = 0; double sumIAT2 = 0;
        for (double iat : allIATs) { sumIAT += iat; sumIAT2 += iat * iat; }
        double meanIAT = allIATs.isEmpty() ? 0 : sumIAT / allIATs.size();
        double varIAT  = allIATs.isEmpty() ? 0 : (sumIAT2 / allIATs.size()) - (meanIAT * meanIAT);
        double stdIAT  = Math.sqrt(Math.max(0, varIAT));
        double cvIAT   = meanIAT > 0 ? stdIAT / meanIAT : 0;
        String velocityClass = cvIAT > 1.0 ? "BURSTY" : (cvIAT > 0.5 ? "MODERATE" : "STEADY/PERIODIC");

        System.out.printf("  D1  Total events in dataset:      %,d%n", allEvents.size());
        System.out.printf("  D1  Time span:                    %s%n", formatDuration(spanMs));
        System.out.printf("  D2  Distinct users:               %,d%n", distinctUsers);
        System.out.printf("  D2  Distinct event types:         %d%n", distinctEventTypes);
        System.out.printf("  C1  Event arrival rate (dataset): %s%n", formatRate(allEvents.size(), spanMs));
        System.out.printf("  C2  Mean per-user IAT:            %.2f ms%n", meanIAT);
        System.out.printf("  C2  Median per-user IAT:          %.2f ms%n", medianIAT);
        System.out.printf("  C2  IAT std dev (per-user):       %.2f ms%n", stdIAT);
        System.out.printf("  C2  Coeff of variation (CV):      %.3f → %s%n", cvIAT, velocityClass);
        System.out.println();

        // ── C: Runtime Metrics (full replay) ───────────────────────────────
        System.out.println("── [C] Runtime Metrics (full replay) ────────────────────");
        System.out.printf("  Replaying %,d events...%n", allEvents.size());

        CepSessionFactory factory = new CepSessionFactory(DRL_PATH);
        KieSession session = factory.createSession(true);

        AgendaMetrics agendaM = new AgendaMetrics();
        WMMetrics wmM = new WMMetrics(session);
        session.addEventListener(agendaM);
        session.addEventListener(wmM);

        Set<String> allRuleNames = session.getKieBase().getKiePackages().stream()
                .flatMap(pkg -> pkg.getRules().stream())
                .map(Rule::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        long t0 = System.currentTimeMillis();
        long totalFired = 0;
        SessionPseudoClock clock = session.getSessionClock();

        // CRITICAL: initialize the pseudo-clock to the first event's epoch-ms timestamp.
        // Without this, the clock starts at 0 while @Timestamp("timestamp") field values
        // are ~1.7 trillion ms (epoch). Drools computes expiry as (event_timestamp + 90s),
        // which the clock never reaches from 0, so @Expires("90s") never fires and WM
        // grows unboundedly → O(n²) accumulate slowdown.
        long prevTsSec = allEvents.isEmpty() ? 0L : allEvents.get(0).getTimestamp();
        if (!allEvents.isEmpty()) {
            clock.advanceTime(prevTsSec * 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        for (WikiEvent ev : allEvents) {
            long evTsSec = ev.getTimestamp();
            if (evTsSec > prevTsSec) {
                clock.advanceTime((evTsSec - prevTsSec) * 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            prevTsSec = evTsSec;
            agendaM.currentEventTs = evTsSec;
            ev.setTimestamp(evTsSec * 1000L);
            session.insert(ev);
            totalFired += session.fireAllRules();
        }
        long replayMs = System.currentTimeMillis() - t0;
        long peakWM = wmM.peakFactCount;
        long endWM  = session.getFactCount();

        Set<String> firedRuleNames = agendaM.fireCounts.keySet();
        long coveredCount = allRuleNames.stream().filter(firedRuleNames::contains).count();
        double coveragePct = allRuleNames.isEmpty() ? 0 : 100.0 * coveredCount / allRuleNames.size();
        long selectiveEvents = agendaM.selectiveEventTs.size();
        double selectivity = allEvents.isEmpty() ? 0 : 100.0 * selectiveEvents / allEvents.size();

        System.out.printf("  C1  Replay throughput:            %.0f events/sec%n", replayMs > 0 ? allEvents.size() * 1000.0 / replayMs : 0);
        System.out.printf("  C3  Selectivity:                  %.1f%% of events trigger ≥1 rule (%,d/%,d)%n", selectivity, selectiveEvents, allEvents.size());
        System.out.printf("  C4  Peak WM size:                 %,d facts%n", peakWM);
        System.out.printf("  C4  End-of-replay WM size:        %,d facts%n", endWM);
        System.out.printf("  C4  Avg WM size (sampled):        %.1f facts%n", wmM.avgFactCount());
        System.out.printf("  C5  Total WM changes:             %,d (ins=%,d  ret=%,d  upd=%,d)%n", wmM.totalWMChanges(), wmM.insertions.get(), wmM.retractions.get(), wmM.updates.get());
        System.out.printf("  C5  Avg WM changes/event:         %.2f%n", allEvents.isEmpty() ? 0 : (double) wmM.totalWMChanges() / allEvents.size());
        System.out.printf("  C6  Avg conflict set size:        %.2f activations%n", agendaM.avgConflictSet());
        System.out.printf("  C6  Peak conflict set size:       %d activations%n", agendaM.peakConflictSet);
        System.out.printf("  C7  Total rule activations:       %,d%n", totalFired);
        System.out.printf("  C7  Rules fired per event:        %.2f%n", allEvents.isEmpty() ? 0 : (double) totalFired / allEvents.size());
        System.out.printf("  C3  Rule coverage:                %.1f%% (%d/%d rules fired ≥1x)%n", coveragePct, coveredCount, allRuleNames.size());
        System.out.println();

        session.dispose();

        // ── Full characterization table ─────────────────────────────────────
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         CHARACTERIZATION TABLE (Paper-ready)         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("│ %-36s │ %-15s │%n", "Property", "Wikimedia CEP");
        System.out.println("├──────────────────────────────────────┼─────────────────┤");
        System.out.printf("│ %-36s │ %-15d │%n", "A1  Rules (R)", static_.totalRules);
        System.out.printf("│ %-36s │ %-15.2f │%n", "A2  Avg conditions/rule", avgConds);
        System.out.printf("│ %-36s │ %-15d │%n", "A2  Max conditions/rule", static_.maxConditions);
        System.out.printf("│ %-36s │ %-15d │%n", "A6  Distinct input fact types", static_.eventTypes.size());
        System.out.printf("│ %-36s │ %-15.3f │%n", "A5  Alpha sharing ratio (proxy)", alphaSharingRatio);
        System.out.printf("│ %-36s │ %-15.4f │%n", "B1  Dep. graph density", graphDensity);
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "B2  Largest connected component", lccPct);
        System.out.printf("│ %-36s │ %-15d │%n", "B3  Connected components", numComponents);
        System.out.printf("│ %-36s │ %-15d │%n", "B4  Max chaining depth", maxChainDepth);
        System.out.printf("│ %-36s │ %-15s │%n", "C1  Event arrival rate (ev/s)", formatRate(allEvents.size(), spanMs));
        System.out.printf("│ %-36s │ %-15.3f │%n", "C2  IAT coeff of variation (CV)", cvIAT);
        System.out.printf("│ %-36s │ %-15s │%n", "C2  Velocity class", velocityClass);
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "C3  Selectivity", selectivity);
        System.out.printf("│ %-36s │ %-15d │%n", "C4  Peak WM size (facts)", peakWM);
        System.out.printf("│ %-36s │ %-15.1f │%n", "C4  Avg WM size (facts)", wmM.avgFactCount());
        System.out.printf("│ %-36s │ %-15.2f │%n", "C5  Avg WM changes/event", (double) wmM.totalWMChanges() / allEvents.size());
        System.out.printf("│ %-36s │ %-15.2f │%n", "C6  Avg conflict set size", agendaM.avgConflictSet());
        System.out.printf("│ %-36s │ %-15d │%n", "C6  Peak conflict set size", agendaM.peakConflictSet);
        System.out.printf("│ %-36s │ %-15.2f │%n", "C7  Rules fired per event", allEvents.isEmpty() ? 0 : (double) totalFired / allEvents.size());
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "C3  Rule coverage on dataset", coveragePct);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:time rules", windowTimeCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:length rules", windowLenCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  Temporal CEP patterns", afterCount + notCount + accumCount);
        System.out.printf("│ %-36s │ %-15s │%n", "D1  Dataset size (events)", String.format("%,d", allEvents.size()));
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct users", distinctUsers);
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct event types", distinctEventTypes);
        System.out.printf("│ %-36s │ %-15s │%n", "D4  Data provenance", "Wikimedia Streams");
        System.out.println("└──────────────────────────────────────┴─────────────────┘");

        // Per-rule fire counts (top 20)
        System.out.println();
        System.out.println("── Per-Rule Activation Counts (top 20) ─────────────────");
        agendaM.fireCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(20)
                .forEach(e -> System.out.printf("  %-55s %,10d%n", e.getKey(), e.getValue().get()));

        System.out.println();
        System.out.println("── Rules That NEVER Fired ───────────────────────────────");
        allRuleNames.stream().filter(r -> !firedRuleNames.contains(r)).sorted().forEach(r -> System.out.println("  ❌ " + r));

        // ── Section E: Category firing probabilities ────────────────────────
        System.out.println();
        System.out.println("── [E] Category Activations per Event ───────────────────");
        System.out.println("  Acts/Evt = category_activations / total_events");
        System.out.println();

        java.util.function.Function<String, String> categorize = name -> {
            if (name.startsWith("Vandalism_")) return "V  Vandalism Pipeline";
            if (name.startsWith("Bot_"))       return "B  Bot Pipeline";
            if (name.startsWith("Content_"))   return "C  Content Pipeline";
            if (name.startsWith("Minor_"))     return "M  Minor Edits Pipeline";
            if (name.startsWith("Discussion_"))return "D  Discussion Pipeline";
            if (name.startsWith("Temporal_"))  return "T  Temporal CEP Patterns";
            if (name.startsWith("Correlate") || name.startsWith("Boost")) return "X  Correlations & Boosting";
            return "INFRA / LIFECYCLE";
        };

        Map<String, Long> catActivations = new TreeMap<>();
        Map<String, Long> catRuleCount   = new TreeMap<>();
        Map<String, Long> catFiredRules  = new TreeMap<>();
        for (String ruleName : allRuleNames) {
            String cat = categorize.apply(ruleName);
            catRuleCount.merge(cat, 1L, Long::sum);
            long fires = agendaM.fireCounts.containsKey(ruleName) ? agendaM.fireCounts.get(ruleName).get() : 0L;
            catActivations.merge(cat, fires, Long::sum);
            if (fires > 0) catFiredRules.merge(cat, 1L, Long::sum);
        }

        long totalEvts = allEvents.size();
        long totalActs = totalFired;
        System.out.printf("  %-35s  %6s  %8s  %10s  %8s  %8s%n", "Category", "Rules", "Fired", "Activns", "Acts/Evt", "% of total");
        System.out.println("  " + "-".repeat(82));
        catActivations.entrySet().stream()
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                .forEach(e -> {
                    String cat = e.getKey(); long acts = e.getValue();
                    long rules = catRuleCount.getOrDefault(cat, 0L), fired = catFiredRules.getOrDefault(cat, 0L);
                    double prob = totalEvts > 0 ? (double) acts / totalEvts : 0;
                    double pct  = totalActs > 0 ? 100.0 * acts / totalActs : 0;
                    System.out.printf("  %-35s  %6d  %8d  %10s  %8.4f  %7.2f%%%n", cat, rules, fired, String.format("%,d", acts), prob, pct);
                });

        // ── Section F: Chain-depth firing probabilities ─────────────────────
        System.out.println();
        System.out.println("── [F] Forward Chain Depth Activations per Event ────────");
        System.out.println("  Entry point: WikiEvent");
        System.out.println();

        Map<Integer, List<String>> byDepth = computeChainDepths(static_.metas, "WikiEvent");
        Set<String> uncaptured = allRuleNames.stream().filter(r -> byDepth.values().stream().noneMatch(list -> list.contains(r))).collect(Collectors.toSet());

        System.out.printf("  %-8s  %-6s  %-8s  %-12s  %-8s  %-9s%n", "Depth", "Rules", "Fired", "Activations", "Acts/Evt", "Ratio(D0)");
        System.out.println("  " + "-".repeat(60));

        long depth0Acts = 0;
        for (Map.Entry<Integer, List<String>> depthEntry : byDepth.entrySet()) {
            int depth = depthEntry.getKey();
            List<String> rulesAtDepth = depthEntry.getValue();
            long depthActs = rulesAtDepth.stream().mapToLong(r -> agendaM.fireCounts.containsKey(r) ? agendaM.fireCounts.get(r).get() : 0L).sum();
            long firedCount = rulesAtDepth.stream().filter(agendaM.fireCounts::containsKey).count();
            double prob = totalEvts > 0 ? (double) depthActs / totalEvts : 0;
            double condProb = depth == 0 ? 1.0 : (depth0Acts > 0 ? (double) depthActs / depth0Acts : 0);
            if (depth == 0) depth0Acts = depthActs;
            System.out.printf("  Depth %-2d  %6d  %8d  %12s  %8.4f  %9.4f%n", depth, rulesAtDepth.size(), firedCount, String.format("%,d", depthActs), prob, condProb);
        }

        long uncapturedActs = uncaptured.stream().mapToLong(r -> agendaM.fireCounts.containsKey(r) ? agendaM.fireCounts.get(r).get() : 0L).sum();
        System.out.printf("  %-8s  %6d  %8d  %12s  %8.4f  %9s%n", "Other", uncaptured.size(), uncaptured.stream().filter(agendaM.fireCounts::containsKey).count(), String.format("%,d", uncapturedActs), totalEvts > 0 ? (double) uncapturedActs / totalEvts : 0, "N/A");

        System.out.println();
        System.out.println("  Depth-level rule membership:");
        for (Map.Entry<Integer, List<String>> e : byDepth.entrySet()) {
            System.out.printf("  Depth %d: %s%n", e.getKey(), e.getValue().stream().sorted().collect(Collectors.joining(", ")));
        }
    }

    private static long countRegex(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        long c = 0; while (m.find()) c++; return c;
    }

    private static int computeMaxChainDepth(List<RuleMeta> metas) {
        return computeChainDepths(metas, "WikiEvent").keySet().stream().max(Integer::compareTo).orElse(0);
    }

    private static Map<Integer, List<String>> computeChainDepths(List<RuleMeta> metas, String entryFact) {
        // Kahn's topological sort + longest acyclic path on the fact-dependency graph.
        // This is the research-standard method for computing derivation chain depth
        // in production rule systems. Rules involved in cycles never satisfy the
        // "all inputs known" condition in a topological pass and are excluded
        // naturally, giving a clean acyclic longest-path metric for the paper.

        Map<String, Integer> factDepth = new HashMap<>();
        factDepth.put(entryFact, 0);

        Map<String, Integer> ruleDepth = new LinkedHashMap<>();
        // Rules ready to process: all inputs are in factDepth
        Deque<RuleMeta> ready = new ArrayDeque<>();
        Set<String> processed = new HashSet<>();

        // Seed: find rules whose inputs are all immediately known (depth 0 facts)
        for (RuleMeta rm : metas) {
            if (allInputsResolved(rm, factDepth)) {
                ready.add(rm);
            }
        }

        while (!ready.isEmpty()) {
            RuleMeta rm = ready.poll();
            if (processed.contains(rm.name)) continue;
            processed.add(rm.name);

            // Compute this rule's depth from its inputs
            int maxInputDepth = 0;
            for (String in : rm.inputs) {
                if (in.equals("Number") || in.equals("String")) continue;
                maxInputDepth = Math.max(maxInputDepth, factDepth.getOrDefault(in, 0));
            }
            ruleDepth.put(rm.name, maxInputDepth);

            // Propagate outputs as newly known facts at depth+1
            for (String out : rm.outputs) {
                int newFactDepth = maxInputDepth + 1;
                if (!factDepth.containsKey(out) || factDepth.get(out) < newFactDepth) {
                    factDepth.put(out, newFactDepth);
                }
            }

            // Re-check all unprocessed rules — newly resolved facts may unlock them
            for (RuleMeta candidate : metas) {
                if (!processed.contains(candidate.name) && allInputsResolved(candidate, factDepth)) {
                    ready.add(candidate);
                }
            }
        }

        Map<Integer, List<String>> byDepth = new TreeMap<>();
        for (Map.Entry<String, Integer> e : ruleDepth.entrySet()) {
            byDepth.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        return byDepth;
    }

    private static boolean allInputsResolved(RuleMeta rm, Map<String, Integer> factDepth) {
        if (rm.inputs.isEmpty()) return false;
        for (String in : rm.inputs) {
            if (in.equals("Number") || in.equals("String")) continue;
            if (!factDepth.containsKey(in)) return false;
        }
        return true;
    }




    public static List<WikiEvent> loadEvents(String path, int maxEvents) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<WikiEvent> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null && list.size() < maxEvents) {
                if (!line.isBlank()) {
                    WikiEvent ev = mapper.readValue(line, WikiEvent.class);
                    // Keep timestamp in raw seconds — conversion to ms happens
                    // only when advancing the pseudo-clock during replay.
                    list.add(ev);
                }
            }
        }
        return list;
    }

    /** Format a duration in milliseconds into the most human-readable unit. */
    static String formatDuration(long ms) {
        if (ms <= 0)      return "0 ms";
        if (ms < 2_000)   return String.format("%,d ms", ms);
        long secs = ms / 1000;
        if (secs < 120)   return String.format("%d sec (%,d ms)", secs, ms);
        long mins = secs / 60;
        if (mins < 120)   return String.format("%d min %.0f sec (%,d ms)", mins, (secs % 60.0), ms);
        long hours = mins / 60;
        if (hours < 48)   return String.format("%.2f hours (%d min)", hours + (mins % 60) / 60.0, mins);
        double days = ms / 86_400_000.0;
        return String.format("%.2f days (%.1f hours)", days, days * 24);
    }

    /** Format an event arrival rate into the most human-readable unit (never shows "0"). */
    static String formatRate(long events, long spanMs) {
        if (spanMs <= 0) return "N/A";
        double perSec  = events * 1000.0 / spanMs;
        double perMin  = perSec * 60;
        double perHour = perMin * 60;
        double perDay  = perHour * 24;
        if (perSec >= 1.0)   return String.format("%.1f events/sec",  perSec);
        if (perMin >= 1.0)   return String.format("%.1f events/min",  perMin);
        if (perHour >= 1.0)  return String.format("%.1f events/hour", perHour);
        return String.format("%.1f events/day", perDay);
    }
}

