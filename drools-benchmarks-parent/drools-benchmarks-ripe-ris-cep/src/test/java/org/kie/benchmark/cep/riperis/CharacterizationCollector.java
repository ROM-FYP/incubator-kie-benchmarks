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
package org.kie.benchmark.cep.riperis;

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
import org.kie.benchmark.cep.riperis.analysis.DependencyGraphBuilder;
import org.kie.benchmark.cep.riperis.analysis.DrlRuleParser;
import org.kie.benchmark.cep.riperis.analysis.ForwardChainFinder;
import org.kie.benchmark.cep.riperis.analysis.RuleMeta;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark;
import org.kie.benchmark.cep.riperis.util.CepSessionFactory;
import org.kie.benchmark.cep.riperis.util.EnvConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
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
 * A1-A6 Static rule-base properties
 * B1-B4 Dependency graph / structural properties
 * C1-C7 Dynamic / runtime properties
 * D1-D4 Data / domain properties
 *
 * Run via:
 * mvn exec:java
 * -Dexec.mainClass=org.kie.benchmark.cep.riperis.CharacterizationCollector
 * -Dexec.classpathScope=test --no-transfer-progress
 */
public class CharacterizationCollector {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -----------------------------------------------------------------------
    // A) Agenda listener — coverage + conflict set + selectivity
    // -----------------------------------------------------------------------
    static class AgendaMetrics implements AgendaEventListener {
        // Per-rule fire counts
        final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();
        // Conflict set tracking (activations currently on the agenda)
        long currentConflictSet = 0L;
        long conflictSetSamples = 0L;
        long conflictSetSum = 0L;
        long peakConflictSet = 0L;
        // Selectivity: track if the current event triggered any match
        long selectiveEventsCount = 0L;
        boolean currentEventTriggered = false;

        @Override
        public void matchCreated(MatchCreatedEvent e) {
            currentConflictSet++;
            if (currentConflictSet > peakConflictSet)
                peakConflictSet = currentConflictSet;
            currentEventTriggered = true;
        }

        @Override
        public void matchCancelled(MatchCancelledEvent e) {
            currentConflictSet = Math.max(0, currentConflictSet - 1);
        }

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

        @Override
        public void agendaGroupPopped(AgendaGroupPoppedEvent e) {
        }

        @Override
        public void agendaGroupPushed(AgendaGroupPushedEvent e) {
        }

        @Override
        public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {
        }

        @Override
        public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent e) {
        }

        @Override
        public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {
        }

        @Override
        public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent e) {
        }

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

        WMMetrics(KieSession session) {
            this.session = session;
        }

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
            if (fc > peakFactCount)
                peakFactCount = fc;
            factCountSum += fc;
            factCountSamples++;
        }

        double avgFactCount() {
            return factCountSamples > 0 ? (double) factCountSum / factCountSamples : 0;
        }

        long totalWMChanges() {
            return insertions.get() + retractions.get() + updates.get();
        }
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        System.out.println("+======================================================+");
        System.out.println("| Ripe RIS CEP Benchmark - Characterization Collector  |");
        System.out.println("+======================================================+");
        System.out.println();

        // -- Load DRL text for static analysis ------------------------------
        String drlContent;
        try (InputStream is = CharacterizationCollector.class
                .getResourceAsStream("/" + EnvConfig.get("RIPERIS_RULES_FILE"))) {
            drlContent = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        }

        // -- A: Static rule-base properties ---------------------------------
        System.out.println("== [A] Static Rule-Base Properties =====================");
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> ruleMetas;
        try {
            ruleMetas = parser.parse(drlContent);
        } catch (DroolsParserException ex) {
            System.err.println("DRL parse error: " + ex.getMessage());
            ruleMetas = Collections.emptyList();
        }

        long ruleCount = ruleMetas.size();

        // A2: Conditions per rule — count PatternDescr inputs per RuleMeta
        IntSummaryStatistics condStats = ruleMetas.stream()
                .mapToInt(rm -> rm.getInputs().size())
                .summaryStatistics();

        // A6: Distinct input fact types across all rules
        Set<String> allInputTypes = ruleMetas.stream()
                .flatMap(rm -> rm.getInputs().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        // C8/C9: Pattern complexity — regex scan of DRL text
        long windowTimeCount = count(drlContent, "window:time");
        long windowLenCount = count(drlContent, "window:length");
        long afterCount = count(drlContent, "\\bafter\\b");
        long notCount = count(drlContent, "\\bnot\\b");
        long accumCount = count(drlContent, "\\baccumulate\\b");
        long evalCount = count(drlContent, "\\beval\\b");

        // A5: Alpha sharing — count unique alpha patterns (EventType + condition
        // combos)
        // Proxy: distinct (factType, constraint) strings extracted from LHS
        // Approximate as unique input type occurrences vs total
        long totalInputOccurrences = ruleMetas.stream().mapToLong(rm -> rm.getInputs().size()).sum();
        long uniqueInputTypes = allInputTypes.size();
        double alphaSharingRatio = totalInputOccurrences > 0
                ? 1.0 - ((double) uniqueInputTypes / totalInputOccurrences)
                : 0;

        // Condition distribution histogram
        Map<String, Long> condHistogram = new LinkedHashMap<>();
        condHistogram.put("1 cond", ruleMetas.stream().filter(r -> r.getInputs().size() == 1).count());
        condHistogram.put("2 cond", ruleMetas.stream().filter(r -> r.getInputs().size() == 2).count());
        condHistogram.put("3 cond", ruleMetas.stream().filter(r -> r.getInputs().size() == 3).count());
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

        // -- B: Dependency graph metrics -------------------------------------
        System.out.println("== [B] Dependency / Structural Properties ===============");
        double graphDensity = 0;
        long numComponents = 0L;
        double lccPct = 0;
        long maxChainDepth = 0L;
        if (!ruleMetas.isEmpty()) {
            DependencyGraphBuilder dgb = new DependencyGraphBuilder(ruleMetas);
            Graph<RuleMeta, DefaultEdge> g = dgb.getGraph();
            long V = g.vertexSet().size();
            long E = g.edgeSet().size();
            double maxEdges = (double) V * (V - 1);
            graphDensity = maxEdges > 0 ? E / maxEdges : 0;

            ConnectivityInspector<RuleMeta, DefaultEdge> ci = new ConnectivityInspector<>(g);
            List<Set<RuleMeta>> components = ci.connectedSets();
            numComponents = components.size();
            long lccSize = components.stream().mapToLong(Set::size).max().orElse(0L);
            lccPct = V > 0 ? 100.0 * lccSize / V : 0;

            // B4: chaining depth — longest shortest path in the DAG (proxy)
            for (RuleMeta src : g.vertexSet()) {
                for (RuleMeta dst : g.vertexSet()) {
                    if (src == dst)
                        continue;
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

        // -- D: Dataset / domain properties ---------------------------------
        System.out.println("== [D] Data / Domain Properties =========================");
        String dataFileKey = (args.length > 0) ? args[0] : "RIPERIS_DEFAULT_DATA_FILE";
        String dataFile = EnvConfig.get(dataFileKey);

        long eventCount = 0L;
        Set<String> peers = new TreeSet<>();
        Map<String, Long> eventTypeDist = new LinkedHashMap<>();
        double minTimestamp = Double.MAX_VALUE;
        double maxTimestamp = Double.MIN_VALUE;
        List<Double> allIATs = new ArrayList<>();
        Map<String, Long> lastTsPerPeer = new HashMap<>();

        CepSessionFactory factory = new CepSessionFactory(EnvConfig.get("RIPERIS_RULES_FILE"));
        KieSession session = factory.createSession(true);
        AgendaMetrics agendaM = new AgendaMetrics();
        WMMetrics wmM = new WMMetrics(session);
        session.addEventListener(agendaM);
        session.addEventListener(wmM);

        // Get all rule names from KieBase
        Set<String> allRuleNames = session.getKieBase().getKiePackages().stream()
                .flatMap(pkg -> pkg.getRules().stream())
                .map(Rule::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        long t0 = System.currentTimeMillis();
        long totalFired = 0;
        org.drools.core.time.SessionPseudoClock clock = session.getSessionClock();

        System.out.println("  Streaming and replaying events from: " + dataFile);
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(dataFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> envelope = MAPPER.readValue(line, Map.class);
                    RisMessage ev = RisMessage.fromRisLiveEnvelope(envelope);
                    if (ev != null) {
                        eventCount++;

                        // Dynamic statistics collection
                        String peer = ev.getPeer();
                        if (peer != null) {
                            peers.add(peer);
                            long currentTsMs = (long) (ev.getTimestamp() * 1000);
                            Long lastTs = lastTsPerPeer.get(peer);
                            if (lastTs != null) {
                                allIATs.add((double) (currentTsMs - lastTs));
                            }
                            lastTsPerPeer.put(peer, currentTsMs);
                        }

                        String bgpType = ev.getBgpType();
                        if (bgpType != null) {
                            eventTypeDist.merge(bgpType, 1L, Long::sum);
                        }

                        double ts = ev.getTimestamp();
                        if (ts < minTimestamp) {
                            minTimestamp = ts;
                        }
                        if (ts > maxTimestamp) {
                            maxTimestamp = ts;
                        }

                        // Replay into session
                        long eventTimeMs = (long) (ts * 1000);
                        long currentTime = clock.getCurrentTime();
                        if (eventTimeMs > currentTime) {
                            clock.advanceTime(eventTimeMs - currentTime, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }

                        agendaM.currentEventTriggered = false;
                        session.insert(ev);
                        totalFired += session.fireAllRules();
                        if (agendaM.currentEventTriggered) {
                            agendaM.selectiveEventsCount++;
                        }

                        if (eventCount % 100000 == 0) {
                            System.out.printf("    %,d events processed...%n", eventCount);
                        }
                    }
                } catch (Exception e) {
                    // Skip malformed lines
                }
            }
        }

        long distinctPeers = peers.size();
        long distinctEventTypes = eventTypeDist.size();
        long spanMs = (eventCount > 0 && maxTimestamp >= minTimestamp) ? (long) ((maxTimestamp - minTimestamp) * 1000) : 0;
        double arrivalRatePerSec = spanMs > 0 ? eventCount * 1000.0 / spanMs : 0;

        Collections.sort(allIATs);
        double medianIAT = allIATs.isEmpty() ? 0 : allIATs.get(allIATs.size() / 2);
        double sumIAT = 0;
        double sumIAT2 = 0;
        for (double iat : allIATs) {
            sumIAT += iat;
            sumIAT2 += iat * iat;
        }
        double meanIAT = allIATs.isEmpty() ? 0 : sumIAT / allIATs.size();
        double varIAT = allIATs.isEmpty() ? 0 : (sumIAT2 / allIATs.size()) - (meanIAT * meanIAT);
        double stdIAT = Math.sqrt(Math.max(0, varIAT));
        double cvIAT = meanIAT > 0 ? stdIAT / meanIAT : 0;
        String velocityClass = cvIAT > 1.0 ? "BURSTY" : (cvIAT > 0.5 ? "MODERATE" : "STEADY/PERIODIC");

        final long finalEventCount = eventCount;
        System.out.printf("  D1  Total events in dataset:      %,d%n", eventCount);
        System.out.printf("  D1  Time span:                    %,d ms (%.1f min)%n", spanMs, spanMs / 60000.0);
        System.out.printf("  D2  Distinct peers:               %d → %s%n", distinctPeers, peers);
        System.out.printf("  D2  Distinct event types:         %d%n", distinctEventTypes);
        System.out.printf("  D3  Event type distribution:      %s%n",
                eventTypeDist.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> e.getKey() + "=" + String.format("%.1f%%", 100.0 * e.getValue() / finalEventCount))
                        .collect(Collectors.joining(", ")));
        System.out.printf("  C1  Event arrival rate (dataset): %.0f events/sec%n", arrivalRatePerSec);
        System.out.printf("  C2  Dataset wall-clock span:      %,d ms (%.1f min)%n", spanMs, spanMs / 60000.0);
        System.out.printf("  C2  Mean per-peer IAT:            %.2f ms%n", meanIAT);
        System.out.printf("  C2  Median per-peer IAT:          %.2f ms%n", medianIAT);
        System.out.printf("  C2  IAT std dev (per-peer):       %.2f ms%n", stdIAT);
        System.out.printf("  C2  Coeff of variation (CV):      %.3f → %s%n", cvIAT, velocityClass);
        System.out.println();

        // -- C: Runtime metrics (full replay) -------------------------------
        System.out.println("== [C] Runtime Metrics (full replay) ====================");
        System.out.printf("  Replaying %,d events...%n", eventCount);

        long replayMs = System.currentTimeMillis() - t0;
        long peakWM = wmM.peakFactCount;
        long endWM = session.getFactCount();

        // Coverage
        Set<String> firedRuleNames = agendaM.fireCounts.keySet();
        long coveredCount = allRuleNames.stream().filter(firedRuleNames::contains).count();
        double coveragePct = allRuleNames.isEmpty() ? 0 : 100.0 * coveredCount / allRuleNames.size();
        long selectiveEvents = agendaM.selectiveEventsCount;
        double selectivity = eventCount == 0 ? 0 : 100.0 * selectiveEvents / eventCount;

        System.out.printf("  C1  Replay throughput:            %.0f events/sec%n",
                replayMs > 0 ? eventCount * 1000.0 / replayMs : 0);
        System.out.printf("  C3  Selectivity:                  %.1f%% of events trigger ≥1 rule (%,d/%,d)%n",
                selectivity, selectiveEvents, eventCount);
        System.out.printf("  C4  Peak WM size:                 %,d facts%n", peakWM);
        System.out.printf("  C4  End-of-replay WM size:        %,d facts%n", endWM);
        System.out.printf("  C4  Avg WM size (sampled):        %.1f facts%n", wmM.avgFactCount());
        System.out.printf("  C5  Total WM changes:             %,d (ins=%,d  ret=%,d  upd=%,d)%n",
                wmM.totalWMChanges(), wmM.insertions.get(), wmM.retractions.get(), wmM.updates.get());
        System.out.printf("  C5  Avg WM changes/event:         %.2f%n",
                eventCount == 0 ? 0 : (double) wmM.totalWMChanges() / eventCount);
        System.out.printf("  C6  Avg conflict set size:        %.2f activations%n", agendaM.avgConflictSet());
        System.out.printf("  C6  Peak conflict set size:       %d activations%n", agendaM.peakConflictSet);
        System.out.printf("  C7  Total rule activations:       %,d%n", totalFired);
        System.out.printf("  C7  Rules fired per event:        %.2f%n",
                eventCount == 0 ? 0 : (double) totalFired / eventCount);
        System.out.printf("  C3  Rule coverage:                %.1f%% (%d/%d rules fired ≥1x)%n",
                coveragePct, coveredCount, allRuleNames.size());
        System.out.println();

        session.dispose();

        // -- Full characterization table -------------------------------------
        System.out.println("+======================================================+");
        System.out.println("|         CHARACTERIZATION TABLE (Paper-ready)         |");
        System.out.println("+======================================================+");
        System.out.printf("| %-36s | %-15s |%n", "Property", "Ripe RIS CEP");
        System.out.println("+--------------------------------------+-----------------+");
        System.out.printf("| %-36s | %-15d |%n", "A1  Rules (R)", ruleCount);
        System.out.printf("| %-36s | %-15.2f |%n", "A2  Avg conditions/rule", condStats.getAverage());
        System.out.printf("| %-36s | %-15d |%n", "A2  Max conditions/rule", condStats.getMax());
        System.out.printf("| %-36s | %-15d |%n", "A6  Distinct input fact types", allInputTypes.size());
        System.out.printf("| %-36s | %-15.3f |%n", "A5  Alpha sharing ratio (proxy)", alphaSharingRatio);
        System.out.printf("| %-36s | %-15.4f |%n", "B1  Dep. graph density", graphDensity);
        System.out.printf("| %-36s | %-14.1f%% |%n", "B2  Largest connected component", lccPct);
        System.out.printf("| %-36s | %-15d |%n", "B3  Connected components", numComponents);
        System.out.printf("| %-36s | %-15d |%n", "B4  Max chaining depth", maxChainDepth);
        System.out.printf("| %-36s | %-15.0f |%n", "C1  Event arrival rate (ev/s)", arrivalRatePerSec);
        System.out.printf("| %-36s | %-15.3f |%n", "C2  IAT coeff of variation (CV)", cvIAT);
        System.out.printf("| %-36s | %-15s |%n", "C2  Velocity class", velocityClass);
        System.out.printf("| %-36s | %-14.1f%% |%n", "C3  Selectivity", selectivity);
        System.out.printf("| %-36s | %-15d |%n", "C4  Peak WM size (facts)", peakWM);
        System.out.printf("| %-36s | %-15.1f |%n", "C4  Avg WM size (facts)", wmM.avgFactCount());
        System.out.printf("| %-36s | %-15.2f |%n", "C5  Avg WM changes/event",
                eventCount == 0 ? 0 : (double) wmM.totalWMChanges() / eventCount);
        System.out.printf("| %-36s | %-15.2f |%n", "C6  Avg conflict set size", agendaM.avgConflictSet());
        System.out.printf("| %-36s | %-15d |%n", "C6  Peak conflict set size", agendaM.peakConflictSet);
        System.out.printf("| %-36s | %-15.2f |%n", "C7  Rules fired per event", eventCount == 0 ? 0 : (double) totalFired / eventCount);
        System.out.printf("| %-36s | %-14.1f%% |%n", "C3  Rule coverage on dataset", coveragePct);
        System.out.printf("| %-36s | %-15d |%n", "C8  window:time rules", windowTimeCount);
        System.out.printf("| %-36s | %-15d |%n", "C8  window:length rules", windowLenCount);
        System.out.printf("| %-36s | %-15d |%n", "C9  Temporal CEP patterns", afterCount + notCount + accumCount);
        System.out.printf("| %-36s | %-15s |%n", "D1  Dataset size (events)", String.format("%,d", eventCount));
        System.out.printf("| %-36s | %-15d |%n", "D2  Distinct peers", distinctPeers);
        System.out.printf("| %-36s | %-15d |%n", "D2  Distinct event types", distinctEventTypes);
        System.out.printf("| %-36s | %-15s |%n", "D4  Data provenance", "RIPE RIS Live");
        System.out.println("+--------------------------------------+-----------------+");

        // Per-rule fire counts (top 20)
        System.out.println();
        System.out.println("== Per-Rule Activation Counts (top 20) =================");
        agendaM.fireCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(20)
                .forEach(e -> System.out.printf("  %-55s %,10d%n", e.getKey(), e.getValue().get()));

        System.out.println();
        System.out.println("== Rules That NEVER Fired ===============================");
        allRuleNames.stream()
                .filter(r -> !firedRuleNames.contains(r))
                .sorted()
                .forEach(r -> System.out.println("  [X] " + r));

        // -- Section E: Category firing probabilities ------------------------
        // P(category fires | event arrives) = category_activations / total_events
        // Categories derived from rule name prefix (section letter A-N + INFRA).
        System.out.println();
        System.out.println("== [E] Category Firing Probabilities ====================");
        System.out.println("  P(category fires | event) = category_activations / total_events");
        System.out.println();

        // Category classifier based on rule name prefix
        java.util.function.Function<String, String> categorize = name -> {
            if (name.startsWith("BOOTSTRAP_") || name.startsWith("CLEANUP_"))
                return "INFRA / LIFECYCLE";
            if (name.startsWith("R"))
                return "BGP Route Logic";
            return "OTHER";
        };

        // Aggregate activations per category
        Map<String, Long> catActivations = new TreeMap<>();
        Map<String, Long> catRuleCount = new TreeMap<>();
        Map<String, Long> catFiredRules = new TreeMap<>();
        for (String ruleName : allRuleNames) {
            String cat = categorize.apply(ruleName);
            catRuleCount.merge(cat, 1L, Long::sum);
            long fires = agendaM.fireCounts.containsKey(ruleName)
                    ? agendaM.fireCounts.get(ruleName).get()
                    : 0L;
            catActivations.merge(cat, fires, Long::sum);
            if (fires > 0)
                catFiredRules.merge(cat, 1L, Long::sum);
        }

        long totalEvts = eventCount;
        long totalActs = totalFired;
        System.out.printf("  %-35s  %6s  %8s  %10s  %8s  %8s%n",
                "Category", "Rules", "Fired", "Activns", "P(fire)", "% of total");
        System.out.println("  " + "-".repeat(82));
        catActivations.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> {
                    String cat = e.getKey();
                    long acts = e.getValue();
                    long rules = catRuleCount.getOrDefault(cat, 0L);
                    long fired = catFiredRules.getOrDefault(cat, 0L);
                    double prob = totalEvts > 0 ? (double) acts / totalEvts : 0;
                    double pct = totalActs > 0 ? 100.0 * acts / totalActs : 0;
                    System.out.printf("  %-35s  %6d  %8d  %10s  %8.4f  %7.2f%%%n",
                            cat, rules, fired,
                            String.format("%,d", acts), prob, pct);
                });

        // -- Section F: Chain-depth firing probabilities ---------------------
        // Uses ForwardChainFinder BFS from RisMessage (the external entry fact).
        // Depth 0 = rules directly consuming RisMessage (alpha filters).
        // Depth 1 = rules consuming facts PRODUCED by depth-0 rules, etc.
        // P(depth-d fires | event) = sum(activations at depth d) / total_events
        System.out.println();
        System.out.println("== [F] Forward Chain Depth Firing Probabilities =========");
        System.out.println("  Entry point: RisMessage (externally inserted by Java replay loop)");
        System.out.println("  P(depth-d fires | event) = activations_at_depth_d / total_events");
        System.out.println();

        ForwardChainFinder fcf = new ForwardChainFinder(ruleMetas);
        ForwardChainFinder.ForwardChainResult fcResult = fcf.findForwardChain("RisMessage");

        // Build depth → [rules] map
        Map<Long, List<String>> byDepth = new TreeMap<>(fcResult.getChainsByDepth());

        // Rules NOT in the chain (BOOTSTRAP, CLEANUP, rules reading only singleton
        // facts)
        Set<String> uncaptured = new LinkedHashSet<>(fcResult.getUncapturedRules());

        System.out.printf("  %-8s  %-6s  %-8s  %-12s  %-8s  %-8s%n",
                "Depth", "Rules", "Fired", "Activations", "P(fire)", "Cond prob");
        System.out.println("  " + "-".repeat(60));

        long depth0Acts = 0; // to compute conditional probability P(d+1 fires | d fired)
        for (Map.Entry<Long, List<String>> depthEntry : byDepth.entrySet()) {
            long depth = depthEntry.getKey();
            List<String> rulesAtDepth = depthEntry.getValue();
            long depthActs = rulesAtDepth.stream()
                    .mapToLong(r -> agendaM.fireCounts.containsKey(r)
                            ? agendaM.fireCounts.get(r).get()
                            : 0L)
                    .sum();
            long firedCount = rulesAtDepth.stream()
                    .filter(agendaM.fireCounts::containsKey).count();
            double prob = totalEvts > 0 ? (double) depthActs / totalEvts : 0;
            double condProb = depth == 0 ? 1.0
                    : (depth0Acts > 0 ? (double) depthActs / depth0Acts : 0);
            if (depth == 0)
                depth0Acts = depthActs;
            System.out.printf("  Depth %-2d  %6d  %8d  %12s  %8.4f  %8.4f%n",
                    depth, rulesAtDepth.size(), firedCount,
                    String.format("%,d", depthActs), prob, condProb);
        }

        // Print uncaptured (not reachable from RisMessage) summary
        long uncapturedActs = uncaptured.stream()
                .mapToLong(r -> agendaM.fireCounts.containsKey(r)
                        ? agendaM.fireCounts.get(r).get()
                        : 0L)
                .sum();
        System.out.printf("  %-8s  %6d  %8d  %12s  %8.4f  %8s%n",
                "Other", uncaptured.size(),
                uncaptured.stream().filter(agendaM.fireCounts::containsKey).count(),
                String.format("%,d", uncapturedActs),
                totalEvts > 0 ? (double) uncapturedActs / totalEvts : 0, "N/A");

        System.out.println();
        System.out.println("  Depth-level rule membership:");
        for (Map.Entry<Long, List<String>> e : byDepth.entrySet()) {
            System.out.printf("  Depth %d: %s%n", e.getKey(),
                    e.getValue().stream().sorted().collect(Collectors.joining(", ")));
        }
    }

    /** Count regex pattern occurrences in text */
    private static long count(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        long c = 0;
        while (m.find())
            c++;
        return c;
    }
}
