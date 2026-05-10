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

import org.drools.drl.parser.DroolsParserException;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.*;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.analysis.DependencyGraphBuilder;
import org.kie.benchmark.binance.analysis.DrlRuleParser;
import org.kie.benchmark.binance.analysis.ForwardChainFinder;
import org.kie.benchmark.binance.analysis.RuleMeta;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One-run research-grade characterization collector.
 *
 * Computes all dimensions from the Benchmark Characterization Guide:
 *   A1-A6  Static rule-base properties
 *   B1-B4  Dependency graph / structural properties
 *   C1-C7  Dynamic / runtime properties
 *   D1-D4  Data / domain properties
 *
 * Run via:
 *   mvn exec:java -Dexec.mainClass=org.kie.benchmark.binance.CharacterizationCollector
 *                 -Dexec.classpathScope=test --no-transfer-progress
 */
public class CharacterizationCollector {

    // -----------------------------------------------------------------------
    // A) Agenda listener — coverage + conflict set + selectivity
    // -----------------------------------------------------------------------
    static class AgendaMetrics implements AgendaEventListener {
        // Per-rule fire counts
        final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();
        // Conflict set tracking (activations currently on the agenda)
        int currentConflictSet = 0;
        long conflictSetSamples = 0;
        long conflictSetSum = 0;
        int peakConflictSet = 0;
        // Selectivity: set of event indices that triggered at least one rule
        final Set<Long> selectiveEventTs = new HashSet<>();
        // Current event timestamp being processed (set by replay loop)
        long currentEventTs = -1;

        @Override
        public void matchCreated(MatchCreatedEvent e) {
            currentConflictSet++;
            if (currentConflictSet > peakConflictSet) peakConflictSet = currentConflictSet;
            if (currentEventTs >= 0) selectiveEventTs.add(currentEventTs);
        }
        @Override
        public void matchCancelled(MatchCancelledEvent e) { currentConflictSet = Math.max(0, currentConflictSet - 1); }
        @Override
        public void beforeMatchFired(BeforeMatchFiredEvent e) {
            conflictSetSamples++;
            conflictSetSum += currentConflictSet;
        }
        @Override
        public void afterMatchFired(AfterMatchFiredEvent e) {
            currentConflictSet = Math.max(0, currentConflictSet - 1);
            fireCounts.computeIfAbsent(e.getMatch().getRule().getName(),
                    k -> new AtomicInteger()).incrementAndGet();
        }
        @Override public void agendaGroupPopped(AgendaGroupPoppedEvent e) {}
        @Override public void agendaGroupPushed(AgendaGroupPushedEvent e) {}
        @Override public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {}
        @Override public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {}
        @Override public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {}
        @Override public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {}

        double avgConflictSet() {
            return conflictSetSamples > 0 ? (double) conflictSetSum / conflictSetSamples : 0;
        }
    }

    // -----------------------------------------------------------------------
    // B) WM listener — insertions, retractions, updates, WM size samples
    // -----------------------------------------------------------------------
    static class WMMetrics implements RuleRuntimeEventListener {
        final AtomicLong insertions = new AtomicLong();
        final AtomicLong retractions = new AtomicLong();
        final AtomicLong updates = new AtomicLong();
        long peakFactCount = 0;
        long factCountSum = 0;
        long factCountSamples = 0;
        private KieSession session;

        WMMetrics(KieSession session) { this.session = session; }

        @Override
        public void objectInserted(ObjectInsertedEvent e) {
            insertions.incrementAndGet();
            sampleWM();
        }
        @Override
        public void objectDeleted(ObjectDeletedEvent e) {
            retractions.incrementAndGet();
            sampleWM();
        }
        @Override
        public void objectUpdated(ObjectUpdatedEvent e) {
            updates.incrementAndGet();
            sampleWM();
        }

        void sampleWM() {
            long fc = session.getFactCount();
            if (fc > peakFactCount) peakFactCount = fc;
            factCountSum += fc;
            factCountSamples++;
        }

        double avgFactCount() {
            return factCountSamples > 0 ? (double) factCountSum / factCountSamples : 0;
        }
        long totalWMChanges() { return insertions.get() + retractions.get() + updates.get(); }
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Binance CEP Benchmark — Characterization Collector  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ── Load DRL text for static analysis ──────────────────────────────
        String drlContent;
        try (InputStream is = CharacterizationCollector.class.getResourceAsStream("/rules/taxonomy.drl")) {
            drlContent = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        }

        // ── A: Static rule-base properties ─────────────────────────────────
        System.out.println("── [A] Static Rule-Base Properties ─────────────────────");
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> ruleMetas;
        try {
            ruleMetas = parser.parse(drlContent);
        } catch (DroolsParserException ex) {
            System.err.println("DRL parse error: " + ex.getMessage());
            ruleMetas = Collections.emptyList();
        }

        int ruleCount = ruleMetas.size();

        // A2: Conditions per rule — count PatternDescr inputs per RuleMeta
        IntSummaryStatistics condStats = ruleMetas.stream()
                .mapToInt(rm -> rm.getInputs().size())
                .summaryStatistics();

        // A6: Distinct input fact types across all rules
        Set<String> allInputTypes = ruleMetas.stream()
                .flatMap(rm -> rm.getInputs().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        // C8/C9: Pattern complexity — regex scan of DRL text
        long windowTimeCount  = count(drlContent, "window:time");
        long windowLenCount   = count(drlContent, "window:length");
        long afterCount       = count(drlContent, "\\bafter\\b");
        long notCount         = count(drlContent, "\\bnot\\b");
        long accumCount       = count(drlContent, "\\baccumulate\\b");
        long evalCount        = count(drlContent, "\\beval\\b");

        // A5: Alpha sharing — count unique alpha patterns (EventType + condition combos)
        // Proxy: distinct (factType, constraint) strings extracted from LHS
        // Approximate as unique input type occurrences vs total
        long totalInputOccurrences = ruleMetas.stream().mapToLong(rm -> rm.getInputs().size()).sum();
        long uniqueInputTypes      = allInputTypes.size();
        double alphaSharingRatio   = totalInputOccurrences > 0
                ? 1.0 - ((double) uniqueInputTypes / totalInputOccurrences) : 0;

        // Condition distribution histogram
        Map<String, Long> condHistogram = new LinkedHashMap<>();
        condHistogram.put("1 cond",  ruleMetas.stream().filter(r -> r.getInputs().size() == 1).count());
        condHistogram.put("2 cond",  ruleMetas.stream().filter(r -> r.getInputs().size() == 2).count());
        condHistogram.put("3 cond",  ruleMetas.stream().filter(r -> r.getInputs().size() == 3).count());
        condHistogram.put("4+ cond", ruleMetas.stream().filter(r -> r.getInputs().size() >= 4).count());

        System.out.printf("  A1  Total rules (R):              %d%n", ruleCount);
        System.out.printf("  A2  Avg conditions/rule:          %.2f  (max=%d, min=%d)%n",
                condStats.getAverage(), condStats.getMax(), condStats.getMin());
        System.out.printf("  A2  Condition distribution:       %s%n", condHistogram);
        System.out.printf("  A5  Alpha sharing ratio (proxy):  %.3f%n", alphaSharingRatio);
        System.out.printf("  A6  Distinct input fact types:    %d  → %s%n", allInputTypes.size(), allInputTypes);
        System.out.printf("  C8  window:time rules:            %d%n", windowTimeCount);
        System.out.printf("  C8  window:length rules:          %d%n", windowLenCount);
        System.out.printf("  C9  'after' patterns:             %d%n", afterCount);
        System.out.printf("  C9  negation 'not' patterns:      %d%n", notCount);
        System.out.printf("  C9  'accumulate' patterns:        %d%n", accumCount);
        System.out.printf("  C9  'eval' temporal patterns:     %d%n", evalCount);
        System.out.println();

        // ── B: Dependency graph metrics ─────────────────────────────────────
        System.out.println("── [B] Dependency / Structural Properties ───────────────");
        double graphDensity = 0; int numComponents = 0; double lccPct = 0; int maxChainDepth = 0;
        if (!ruleMetas.isEmpty()) {
            DependencyGraphBuilder dgb = new DependencyGraphBuilder(ruleMetas);
            Graph<RuleMeta, DefaultEdge> g = dgb.getGraph();
            int V = g.vertexSet().size();
            int E = g.edgeSet().size();
            double maxEdges = (double) V * (V - 1);
            graphDensity = maxEdges > 0 ? E / maxEdges : 0;

            ConnectivityInspector<RuleMeta, DefaultEdge> ci = new ConnectivityInspector<>(g);
            List<Set<RuleMeta>> components = ci.connectedSets();
            numComponents = components.size();
            int lccSize = components.stream().mapToInt(Set::size).max().orElse(0);
            lccPct = V > 0 ? 100.0 * lccSize / V : 0;

            // B4: chaining depth — longest shortest path in the DAG (proxy)
            for (RuleMeta src : g.vertexSet()) {
                for (RuleMeta dst : g.vertexSet()) {
                    if (src == dst) continue;
                    var path = DijkstraShortestPath.findPathBetween(g, src, dst);
                    if (path != null && path.getLength() > maxChainDepth)
                        maxChainDepth = path.getLength();
                }
            }
        }
        System.out.printf("  B1  Dependency graph density:     %.4f%n", graphDensity);
        System.out.printf("  B2  Largest connected component:  %.1f%%%n", lccPct);
        System.out.printf("  B3  Connected components:         %d%n", numComponents);
        System.out.printf("  B4  Max chaining depth:           %d%n", maxChainDepth);
        System.out.println();

        // ── D: Dataset / domain properties ─────────────────────────────────
        System.out.println("── [D] Data / Domain Properties ─────────────────────────");
        BinanceRulesProvider rulesProvider = new BinanceRulesProvider();
        BinanceEventProvider eventProvider = new BinanceEventProvider();
        List<MarketEvent> allEvents = eventProvider.getEvents();
        Set<String> symbols = allEvents.stream().map(MarketEvent::getSymbol).collect(Collectors.toCollection(TreeSet::new));

        // D2: Attribute cardinality
        long distinctSymbols    = symbols.size();
        long distinctEventTypes = allEvents.stream().map(MarketEvent::getEventType).distinct().count();
        long distinctP1         = allEvents.stream().mapToLong(e -> (long)(e.getP1()*100)).distinct().count();

        // D3 / C1 / C2: Temporal distribution and velocity profile.
        // Sort by timestamp to find dataset wall-clock span.
        // Use per-symbol IAT to avoid cross-symbol interleaving gaps skewing CV.
        long[] timestamps = allEvents.stream().mapToLong(MarketEvent::getTsMs).toArray();
        Arrays.sort(timestamps);
        long spanMs = timestamps[timestamps.length - 1] - timestamps[0];
        // C1: arrival rate from dataset time span (how fast events arrive in real-time)
        double arrivalRatePerSec = spanMs > 0 ? allEvents.size() * 1000.0 / spanMs : 0;

        // C2: Compute per-symbol IATs to get meaningful velocity profile.
        // Cross-symbol interleaving creates artificially large IAT gaps.
        List<Double> allIATs = new ArrayList<>();
        Map<String, List<Long>> perSymbolTs = new LinkedHashMap<>();
        for (MarketEvent ev : allEvents) {
            perSymbolTs.computeIfAbsent(ev.getSymbol(), k -> new ArrayList<>()).add(ev.getTsMs());
        }
        for (List<Long> tsList : perSymbolTs.values()) {
            Collections.sort(tsList);
            for (int i = 1; i < tsList.size(); i++) {
                allIATs.add((double)(tsList.get(i) - tsList.get(i-1)));
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

        // Event type distribution
        Map<String, Long> eventTypeDist = allEvents.stream()
                .collect(Collectors.groupingBy(MarketEvent::getEventType, Collectors.counting()));

        System.out.printf("  D1  Total events in dataset:      %,d%n", allEvents.size());
        System.out.printf("  D1  Time span:                    %,d ms (%.1f min)%n", spanMs, spanMs / 60000.0);
        System.out.printf("  D2  Distinct symbols:             %d → %s%n", distinctSymbols, symbols);
        System.out.printf("  D2  Distinct event types:         %d%n", distinctEventTypes);
        System.out.printf("  D2  Distinct p1 values (×100):    %,d (near-continuous)%n", distinctP1);
        System.out.printf("  D3  Event type distribution:      %s%n",
                eventTypeDist.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                        .map(e -> e.getKey() + "=" + String.format("%.1f%%", 100.0*e.getValue()/allEvents.size()))
                        .collect(Collectors.joining(", ")));
        System.out.printf("  C1  Event arrival rate (dataset): %.0f events/sec%n", arrivalRatePerSec);
        System.out.printf("  C2  Dataset wall-clock span:      %,d ms (%.1f min)%n", spanMs, spanMs/60000.0);
        System.out.printf("  C2  Mean per-symbol IAT:          %.2f ms%n", meanIAT);
        System.out.printf("  C2  Median per-symbol IAT:        %.2f ms%n", medianIAT);
        System.out.printf("  C2  IAT std dev (per-symbol):     %.2f ms%n", stdIAT);
        System.out.printf("  C2  Coeff of variation (CV):      %.3f → %s%n", cvIAT, velocityClass);
        System.out.println();

        // ── C: Runtime metrics (full replay) ───────────────────────────────
        System.out.println("── [C] Runtime Metrics (full replay) ────────────────────");
        System.out.printf("  Replaying %,d events...%n", allEvents.size());

        KieSession session = rulesProvider.createSession();
        AgendaMetrics agendaM = new AgendaMetrics();
        WMMetrics wmM = new WMMetrics(session);
        session.addEventListener(agendaM);
        session.addEventListener(wmM);
        session.registerChannel("alerts", obj -> {});

        // Bootstrap
        for (String sym : symbols) {
            session.insert(new RiskConfig(sym));
            session.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
            session.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
        }
        session.fireAllRules();

        // Get all rule names from KieBase
        Set<String> allRuleNames = session.getKieBase().getKiePackages().stream()
                .flatMap(pkg -> pkg.getRules().stream())
                .map(Rule::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        // Replay with per-event ts tracking for selectivity
        EventReplayController controller = new EventReplayController(session);
        long t0 = System.currentTimeMillis();
        // Manual replay to track per-event selectivity
        long totalFired = 0;
        org.drools.core.time.SessionPseudoClock clock = session.getSessionClock();
        long prevTs = allEvents.isEmpty() ? 0L : allEvents.get(0).getTsMs();
        for (MarketEvent ev : allEvents) {
            long evTs = ev.getTsMs();
            if (evTs > prevTs) {
                clock.advanceTime(evTs - prevTs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            prevTs = evTs;
            agendaM.currentEventTs = evTs;
            session.insert(ev);
            totalFired += session.fireAllRules();
        }
        long replayMs = System.currentTimeMillis() - t0;
        long peakWM = wmM.peakFactCount;
        long endWM  = session.getFactCount();

        // Coverage
        Set<String> firedRuleNames = agendaM.fireCounts.keySet();
        long coveredCount = allRuleNames.stream().filter(firedRuleNames::contains).count();
        double coveragePct = allRuleNames.isEmpty() ? 0 : 100.0 * coveredCount / allRuleNames.size();
        long selectiveEvents = agendaM.selectiveEventTs.size();
        double selectivity = 100.0 * selectiveEvents / allEvents.size();

        System.out.printf("  C1  Replay throughput:            %.0f events/sec%n",
                replayMs > 0 ? allEvents.size() * 1000.0 / replayMs : 0);
        System.out.printf("  C3  Selectivity:                  %.1f%% of events trigger ≥1 rule (%,d/%,d)%n",
                selectivity, selectiveEvents, allEvents.size());
        System.out.printf("  C4  Peak WM size:                 %,d facts%n", peakWM);
        System.out.printf("  C4  End-of-replay WM size:        %,d facts%n", endWM);
        System.out.printf("  C4  Avg WM size (sampled):        %.1f facts%n", wmM.avgFactCount());
        System.out.printf("  C5  Total WM changes:             %,d (ins=%,d  ret=%,d  upd=%,d)%n",
                wmM.totalWMChanges(), wmM.insertions.get(), wmM.retractions.get(), wmM.updates.get());
        System.out.printf("  C5  Avg WM changes/event:         %.2f%n",
                allEvents.isEmpty() ? 0 : (double) wmM.totalWMChanges() / allEvents.size());
        System.out.printf("  C6  Avg conflict set size:        %.2f activations%n", agendaM.avgConflictSet());
        System.out.printf("  C6  Peak conflict set size:       %d activations%n", agendaM.peakConflictSet);
        System.out.printf("  C7  Total rule activations:       %,d%n", totalFired);
        System.out.printf("  C7  Rules fired per event:        %.2f%n",
                allEvents.isEmpty() ? 0 : (double) totalFired / allEvents.size());
        System.out.printf("  C3  Rule coverage:                %.1f%% (%d/%d rules fired ≥1x)%n",
                coveragePct, coveredCount, allRuleNames.size());
        System.out.println();

        session.dispose();
        rulesProvider.dispose();

        // ── Full characterization table ─────────────────────────────────────
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         CHARACTERIZATION TABLE (Paper-ready)         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("│ %-36s │ %-15s │%n", "Property", "Binance CEP");
        System.out.println("├──────────────────────────────────────┼─────────────────┤");
        System.out.printf("│ %-36s │ %-15d │%n", "A1  Rules (R)", ruleCount);
        System.out.printf("│ %-36s │ %-15.2f │%n", "A2  Avg conditions/rule", condStats.getAverage());
        System.out.printf("│ %-36s │ %-15d │%n", "A2  Max conditions/rule", condStats.getMax());
        System.out.printf("│ %-36s │ %-15d │%n", "A6  Distinct input fact types", allInputTypes.size());
        System.out.printf("│ %-36s │ %-15.3f │%n", "A5  Alpha sharing ratio (proxy)", alphaSharingRatio);
        System.out.printf("│ %-36s │ %-15.4f │%n", "B1  Dep. graph density", graphDensity);
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "B2  Largest connected component", lccPct);
        System.out.printf("│ %-36s │ %-15d │%n", "B3  Connected components", numComponents);
        System.out.printf("│ %-36s │ %-15d │%n", "B4  Max chaining depth", maxChainDepth);
        System.out.printf("│ %-36s │ %-15.0f │%n", "C1  Event arrival rate (ev/s)", arrivalRatePerSec);
        System.out.printf("│ %-36s │ %-15.3f │%n", "C2  IAT coeff of variation (CV)", cvIAT);
        System.out.printf("│ %-36s │ %-15s │%n", "C2  Velocity class", velocityClass);
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "C3  Selectivity", selectivity);
        System.out.printf("│ %-36s │ %-15d │%n", "C4  Peak WM size (facts)", peakWM);
        System.out.printf("│ %-36s │ %-15.1f │%n", "C4  Avg WM size (facts)", wmM.avgFactCount());
        System.out.printf("│ %-36s │ %-15.2f │%n", "C5  Avg WM changes/event", (double) wmM.totalWMChanges() / allEvents.size());
        System.out.printf("│ %-36s │ %-15.2f │%n", "C6  Avg conflict set size", agendaM.avgConflictSet());
        System.out.printf("│ %-36s │ %-15d │%n", "C6  Peak conflict set size", agendaM.peakConflictSet);
        System.out.printf("│ %-36s │ %-15.2f │%n", "C7  Rules fired per event", (double) totalFired / allEvents.size());
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "C3  Rule coverage on dataset", coveragePct);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:time rules", windowTimeCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:length rules", windowLenCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  Temporal CEP patterns", afterCount + notCount + accumCount);
        System.out.printf("│ %-36s │ %-15s │%n", "D1  Dataset size (events)", String.format("%,d", allEvents.size()));
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct symbols", distinctSymbols);
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct event types", distinctEventTypes);
        System.out.printf("│ %-36s │ %-15s │%n", "D4  Data provenance", "Binance WebSocket");
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
        allRuleNames.stream()
                .filter(r -> !firedRuleNames.contains(r))
                .sorted()
                .forEach(r -> System.out.println("  ❌ " + r));

        // ── Section E: Category firing probabilities ────────────────────────
        // P(category fires | event arrives) = category_activations / total_events
        // Categories derived from rule name prefix (section letter A-N + INFRA).
        System.out.println();
        System.out.println("── [E] Category Firing Probabilities ────────────────────");
        System.out.println("  P(category fires | event) = category_activations / total_events");
        System.out.println();

        // Category classifier based on rule name prefix
        java.util.function.Function<String, String> categorize = name -> {
            if (name.startsWith("BOOTSTRAP_") || name.startsWith("CLEANUP_") ||
                name.startsWith("UPD_")        || name.startsWith("DERIVE_") ||
                name.startsWith("RECOVERY_"))            return "INFRA / LIFECYCLE";
            if (name.startsWith("A"))                    return "A  Ingestion & Schema";
            if (name.startsWith("B"))                    return "B  Feed Health & Connectivity";
            if (name.startsWith("C"))                    return "C  Order Book Validity";
            if (name.startsWith("D"))                    return "D  Trade Tape Sanity";
            if (name.startsWith("E"))                    return "E  Derived Metrics (Spread/Depth)";
            if (name.startsWith("F"))                    return "F  Volatility Regime";
            if (name.startsWith("G"))                    return "G  Mark/Index Dislocation";
            if (name.startsWith("H"))                    return "H  Liquidation Cascade";
            if (name.startsWith("I"))                    return "I  Policy FSM (Mode Control)";
            if (name.startsWith("J"))                    return "J  Forward Chain (Trade Sweep)";
            if (name.startsWith("K"))                    return "K  Forward Chain (Micro-Vol)";
            if (name.startsWith("L"))                    return "L  Forward Chain (Mark Diverge)";
            if (name.startsWith("M"))                    return "M  Temporal CEP Sequences";
            if (name.startsWith("N"))                    return "N  Compound Systemic Risk";
            return "OTHER";
        };

        // Aggregate activations per category
        Map<String, Long> catActivations = new TreeMap<>();
        Map<String, Long> catRuleCount   = new TreeMap<>();
        Map<String, Long> catFiredRules  = new TreeMap<>();
        for (String ruleName : allRuleNames) {
            String cat = categorize.apply(ruleName);
            catRuleCount.merge(cat, 1L, Long::sum);
            long fires = agendaM.fireCounts.containsKey(ruleName)
                    ? agendaM.fireCounts.get(ruleName).get() : 0L;
            catActivations.merge(cat, fires, Long::sum);
            if (fires > 0) catFiredRules.merge(cat, 1L, Long::sum);
        }

        long totalEvts = allEvents.size();
        long totalActs = totalFired;
        System.out.printf("  %-35s  %6s  %8s  %10s  %8s  %8s%n",
                "Category", "Rules", "Fired", "Activns", "P(fire)", "% of total");
        System.out.println("  " + "-".repeat(82));
        catActivations.entrySet().stream()
                .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
                .forEach(e -> {
                    String cat = e.getKey();
                    long acts  = e.getValue();
                    long rules = catRuleCount.getOrDefault(cat, 0L);
                    long fired = catFiredRules.getOrDefault(cat, 0L);
                    double prob = totalEvts > 0 ? (double) acts / totalEvts : 0;
                    double pct  = totalActs > 0 ? 100.0 * acts / totalActs : 0;
                    System.out.printf("  %-35s  %6d  %8d  %10s  %8.4f  %7.2f%%%n",
                            cat, rules, fired,
                            String.format("%,d", acts), prob, pct);
                });

        // ── Section F: Chain-depth firing probabilities ─────────────────────
        // Uses ForwardChainFinder BFS from MarketEvent (the external entry fact).
        // Depth 0 = rules directly consuming MarketEvent (alpha filters).
        // Depth 1 = rules consuming facts PRODUCED by depth-0 rules, etc.
        // P(depth-d fires | event) = sum(activations at depth d) / total_events
        System.out.println();
        System.out.println("── [F] Forward Chain Depth Firing Probabilities ─────────");
        System.out.println("  Entry point: MarketEvent (externally inserted by Java replay loop)");
        System.out.println("  P(depth-d fires | event) = activations_at_depth_d / total_events");
        System.out.println();

        ForwardChainFinder fcf = new ForwardChainFinder(ruleMetas);
        ForwardChainFinder.ForwardChainResult fcResult = fcf.findForwardChain("MarketEvent");

        // Build depth → [rules] map
        Map<Integer, List<String>> byDepth = new TreeMap<>(fcResult.getChainsByDepth());

        // Rules NOT in the chain (BOOTSTRAP, CLEANUP, rules reading only singleton facts)
        Set<String> uncaptured = new LinkedHashSet<>(fcResult.getUncapturedRules());

        System.out.printf("  %-8s  %-6s  %-8s  %-12s  %-8s  %-8s%n",
                "Depth", "Rules", "Fired", "Activations", "P(fire)", "Cond prob");
        System.out.println("  " + "-".repeat(60));

        long depth0Acts = 0; // to compute conditional probability P(d+1 fires | d fired)
        for (Map.Entry<Integer, List<String>> depthEntry : byDepth.entrySet()) {
            int depth = depthEntry.getKey();
            List<String> rulesAtDepth = depthEntry.getValue();
            long depthActs  = rulesAtDepth.stream()
                    .mapToLong(r -> agendaM.fireCounts.containsKey(r)
                            ? agendaM.fireCounts.get(r).get() : 0L)
                    .sum();
            long firedCount = rulesAtDepth.stream()
                    .filter(agendaM.fireCounts::containsKey).count();
            double prob = totalEvts > 0 ? (double) depthActs / totalEvts : 0;
            double condProb = depth == 0 ? 1.0
                    : (depth0Acts > 0 ? (double) depthActs / depth0Acts : 0);
            if (depth == 0) depth0Acts = depthActs;
            System.out.printf("  Depth %-2d  %6d  %8d  %12s  %8.4f  %8.4f%n",
                    depth, rulesAtDepth.size(), firedCount,
                    String.format("%,d", depthActs), prob, condProb);
        }

        // Print uncaptured (not reachable from MarketEvent) summary
        long uncapturedActs = uncaptured.stream()
                .mapToLong(r -> agendaM.fireCounts.containsKey(r)
                        ? agendaM.fireCounts.get(r).get() : 0L)
                .sum();
        System.out.printf("  %-8s  %6d  %8d  %12s  %8.4f  %8s%n",
                "Other", uncaptured.size(),
                uncaptured.stream().filter(agendaM.fireCounts::containsKey).count(),
                String.format("%,d", uncapturedActs),
                totalEvts > 0 ? (double) uncapturedActs / totalEvts : 0, "N/A");

        System.out.println();
        System.out.println("  Depth-level rule membership:");
        for (Map.Entry<Integer, List<String>> e : byDepth.entrySet()) {
            System.out.printf("  Depth %d: %s%n", e.getKey(),
                    e.getValue().stream().sorted().collect(Collectors.joining(", ")));
        }
    }

    /** Count regex pattern occurrences in text */
    private static long count(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        long c = 0; while (m.find()) c++; return c;
    }
}
