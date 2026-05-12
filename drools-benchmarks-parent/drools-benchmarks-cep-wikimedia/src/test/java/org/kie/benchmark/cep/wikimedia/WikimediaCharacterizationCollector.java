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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * Research-grade characterization collector for the Wikimedia CEP benchmark.
 *
 * Computes benchmark characterization dimensions:
 *   A  Static rule-base properties (rule count, patterns, temporal, join heavy, chaining)
 *   B  Structural properties (rule depth, avg conditions, temporal window density)
 *   C  Dynamic / runtime properties (rule coverage, firings per event, conflict set, WM size)
 *   D  Data / domain properties (event count, event types, timestamp span)
 *
 * Run via:
 *   mvn exec:java -Dexec.mainClass=org.kie.benchmark.cep.wikimedia.WikimediaCharacterizationCollector
 *                 -Dexec.classpathScope=test --no-transfer-progress
 *   (or just run main() from your IDE with the dataset path as arg)
 */
public class WikimediaCharacterizationCollector {

    private static final String DRL_PATH  = "rules/wikimedia_content_moderation_join_heavy.drl";
    private static final String DATA_FILE = "src/main/resources/data/data/split_400k.jsonl";

    // ── Agenda listener ────────────────────────────────────────────────────
    static class AgendaMetrics implements AgendaEventListener {
        final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();
        int currentConflictSet = 0; long samples = 0, csSum = 0; int peakCS = 0;
        final Set<Long> selectiveTs = new HashSet<>(); long currentTs = -1;

        @Override public void matchCreated(MatchCreatedEvent e) {
            currentConflictSet++;
            if (currentConflictSet > peakCS) peakCS = currentConflictSet;
            if (currentTs >= 0) selectiveTs.add(currentTs);
        }
        @Override public void matchCancelled(MatchCancelledEvent e) { currentConflictSet = Math.max(0, currentConflictSet-1); }
        @Override public void beforeMatchFired(BeforeMatchFiredEvent e) { samples++; csSum += currentConflictSet; }
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
        double avgCS() { return samples > 0 ? (double) csSum / samples : 0; }
    }

    // ── WM listener ────────────────────────────────────────────────────────
    static class WMMetrics implements RuleRuntimeEventListener {
        final AtomicLong insertions = new AtomicLong(), retractions = new AtomicLong(), updates = new AtomicLong();
        long peakFacts = 0; long currentFacts = 0;
        @Override public void objectInserted(ObjectInsertedEvent e)  { currentFacts++; if (currentFacts > peakFacts) peakFacts = currentFacts; insertions.incrementAndGet(); }
        @Override public void objectDeleted(ObjectDeletedEvent e)    { currentFacts = Math.max(0, currentFacts-1); retractions.incrementAndGet(); }
        @Override public void objectUpdated(ObjectUpdatedEvent e)    { updates.incrementAndGet(); }
    }

    // ── Static DRL analysis ────────────────────────────────────────────────
    static class DrlStaticAnalysis {
        int totalRules = 0, temporalRules = 0, joinRules = 0, chainingRules = 0;
        int totalConditions = 0, maxConditions = 0;
        int temporalWindowCount = 0;
        final Set<String> eventTypes = new LinkedHashSet<>();
        final Set<String> consequenceTypes = new LinkedHashSet<>();

        static DrlStaticAnalysis analyse(String drl) {
            DrlStaticAnalysis a = new DrlStaticAnalysis();
            // Split into individual rules
            String[] ruleSections = drl.split("(?m)^rule\\s+");
            for (int i = 1; i < ruleSections.length; i++) {
                String rule = ruleSections[i];
                a.totalRules++;
                // Count conditions (pattern lines beginning with a type name or "not"/"exists"/"accumulate")
                String whenPart = extractWhen(rule);
                int conds = countPatterns(whenPart);
                a.totalConditions += conds;
                if (conds > a.maxConditions) a.maxConditions = conds;
                // Temporal
                if (whenPart.contains("over window") || whenPart.contains("@Expires") ||
                    whenPart.matches("(?s).*\\bwithin\\b.*") || whenPart.contains("SessionPseudoClock")) {
                    a.temporalRules++;
                }
                if (whenPart.contains("over window")) {
                    a.temporalWindowCount += countMatches(whenPart, "over window");
                }
                // Join (multiple fact patterns in LHS)
                if (conds >= 2) a.joinRules++;
                // Chaining (consequence inserts a type that LHS of other rules matches)
                if (extractThen(rule).contains("insert(")) a.chainingRules++;
                // Collect event types from LHS patterns like "WikiEvent(" or model class names
                collectTypes(whenPart, a.eventTypes);
                collectTypes(extractThen(rule), a.consequenceTypes);
            }
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
            // count lines that look like fact patterns (capitalised word followed by '(')
            Pattern p = Pattern.compile("^\\s+[A-Z][A-Za-z]+\\s*\\(", Pattern.MULTILINE);
            Matcher m = p.matcher(when); int c = 0; while (m.find()) c++;
            return c;
        }
        private static void collectTypes(String src, Set<String> out) {
            Matcher m = Pattern.compile("\\b([A-Z][A-Za-z]+)\\s*\\(").matcher(src);
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

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║   Wikimedia CEP Benchmark — Characterization Collector       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // ── Load DRL for static analysis ──────────────────────────────────
        String drlContent;
        try (InputStream is = WikimediaCharacterizationCollector.class.getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) throw new FileNotFoundException("DRL not found: " + DRL_PATH);
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        DrlStaticAnalysis static_ = DrlStaticAnalysis.analyse(drlContent);

        // ── Load events ───────────────────────────────────────────────────
        System.out.printf("[A] Loading events from: %s%n", dataFile);
        List<WikiEvent> events = loadEvents(dataFile, maxEvents);
        System.out.printf("[A] Loaded %,d events%n", events.size());

        // ── Dynamic run ───────────────────────────────────────────────────
        CepSessionFactory factory = new CepSessionFactory(DRL_PATH);
        KieSession session = factory.createSession(true);

        AgendaMetrics agenda = new AgendaMetrics();
        WMMetrics wm         = new WMMetrics();
        session.addEventListener(agenda);
        session.addEventListener(wm);

        long t0 = System.currentTimeMillis();
        SessionPseudoClock clock = session.getSessionClock();
        long totalFired = 0;
        long firstTs = -1, lastTs = -1;

        for (WikiEvent ev : events) {
            long ts = ev.getTimestamp();
            if (firstTs < 0) firstTs = ts;
            lastTs = ts;
            agenda.currentTs = ts;
            long now = clock.getCurrentTime();
            if (ts > now) clock.advanceTime(ts - now, TimeUnit.MILLISECONDS);
            session.insert(ev);
            totalFired += session.fireAllRules();
        }
        long elapsed = System.currentTimeMillis() - t0;
        session.dispose();

        // ── Collect registered rules from KieBase ─────────────────────────
        int registeredRuleCount = static_.totalRules; // from DRL parse

        // ── Print characterization table ──────────────────────────────────
        double spanSec = (firstTs >= 0 && lastTs >= firstTs) ? (lastTs - firstTs) / 1000.0 : 0;
        double eventsPerSec = elapsed > 0 ? events.size() * 1000.0 / elapsed : 0;
        double firingRate = events.size() > 0 ? (double) totalFired / events.size() : 0;
        int coveredRules  = agenda.fireCounts.size();
        double coverage   = registeredRuleCount > 0 ? 100.0 * coveredRules / registeredRuleCount : 0;
        double selectivity = events.size() > 0 ? 100.0 * agenda.selectiveTs.size() / events.size() : 0;

        System.out.println("\n══════════════════════ CHARACTERIZATION RESULTS ══════════════════════");
        System.out.printf("%-38s %s%n", "Dimension", "Value");
        System.out.println("──────────────────────────────────────────────────────────────────────");
        // A — Static
        System.out.printf("A1  Total rules (DRL parse)          %8d%n", static_.totalRules);
        System.out.printf("A2  Temporal rules                   %8d%n", static_.temporalRules);
        System.out.printf("A3  Join-heavy rules (≥2 patterns)   %8d%n", static_.joinRules);
        System.out.printf("A4  Chaining rules (insert in then)  %8d%n", static_.chainingRules);
        System.out.printf("A5  Avg conditions per rule          %8.2f%n", static_.totalRules > 0 ? (double)static_.totalConditions/static_.totalRules : 0);
        System.out.printf("A6  Max conditions in a single rule  %8d%n", static_.maxConditions);
        System.out.printf("A7  Temporal windows (over window)   %8d%n", static_.temporalWindowCount);
        System.out.printf("A8  Distinct input types (LHS)       %8d  %s%n", static_.eventTypes.size(), static_.eventTypes);
        // B — Structure
        System.out.printf("B1  Rules covered at runtime         %8d / %d (%.1f%%)%n", coveredRules, registeredRuleCount, coverage);
        // C — Dynamic
        System.out.printf("C1  Total rules fired                %8,d%n", totalFired);
        System.out.printf("C2  Firings per event                %8.4f%n", firingRate);
        System.out.printf("C3  Avg conflict-set size            %8.4f%n", agenda.avgCS());
        System.out.printf("C4  Peak conflict-set size           %8d%n", agenda.peakCS);
        System.out.printf("C5  Event selectivity                %8.2f%%%n", selectivity);
        System.out.printf("C6  Total WM insertions              %8,d%n", wm.insertions.get());
        System.out.printf("C7  Total WM retractions             %8,d%n", wm.retractions.get());
        System.out.printf("C8  Total WM updates                 %8,d%n", wm.updates.get());
        System.out.printf("C9  Peak WM fact count               %8,d%n", wm.peakFacts);
        // D — Data
        System.out.printf("D1  Total events replayed            %8,d%n", events.size());
        System.out.printf("D2  Event timestamp span             %8.1f s%n", spanSec);
        System.out.printf("D3  Replay throughput                %8.0f ev/s%n", eventsPerSec);
        System.out.printf("D4  Wall-clock replay time           %8d ms%n", elapsed);
        System.out.println("══════════════════════════════════════════════════════════════════════");

        System.out.println("\n── Top-10 rules by fire count ──────────────────────────────────────");
        agenda.fireCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(10)
                .forEach(e -> System.out.printf("  %-50s %,8d%n", e.getKey(), e.getValue().get()));

        System.out.println("\n── Zero-coverage rules ─────────────────────────────────────────────");
        // Get all rule names from DRL
        Pattern ruleNamePat = Pattern.compile("(?m)^rule\\s+\"([^\"]+)\"");
        Matcher m = ruleNamePat.matcher(drlContent);
        List<String> allRuleNames = new ArrayList<>();
        while (m.find()) allRuleNames.add(m.group(1));
        allRuleNames.stream()
                .filter(n -> !agenda.fireCounts.containsKey(n))
                .forEach(n -> System.out.printf("  ! %s%n", n));
    }

    public static List<WikiEvent> loadEvents(String path, int maxEvents) throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<WikiEvent> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null && list.size() < maxEvents) {
                if (!line.isBlank()) list.add(mapper.readValue(line, WikiEvent.class));
            }
        }
        return list;
    }
}
