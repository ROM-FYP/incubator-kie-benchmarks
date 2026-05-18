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
        // Selectivity: set of event indices that triggered at least one rule
        final Set<Long> selectiveEventTs = new HashSet<>();
        // Current event timestamp being processed (set by replay loop)
        long currentEventTs = -1;

        @Override
        public void matchCreated(MatchCreatedEvent e) {
            currentConflictSet++;
            if (currentConflictSet > peakConflictSet)
                peakConflictSet = currentConflictSet;
            if (currentEventTs >= 0)
                selectiveEventTs.add(currentEventTs);
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
    // Static CEP / temporal-pattern analysis
    // -----------------------------------------------------------------------
    static class StaticTemporalMetrics {
        long windowTimeCount;
        long windowLengthCount;
        long afterCount;
        long beforeCount;
        long coincidesCount;
        long duringCount;
        long includesCount;
        long overlapsCount;
        long overlappedByCount;
        long startsCount;
        long startedByCount;
        long finishesCount;
        long finishedByCount;
        long meetsCount;
        long metByCount;
        long notCount;
        long accumulateCount;
        long evalCount;

        long eventDeclarations;
        long expiresAnnotations;
        long timestampAnnotations;
        long durationAnnotations;
        long roleEventAnnotations;

        long accumulateCountFn;
        long accumulateSumFn;
        long accumulateAverageFn;
        long accumulateMinFn;
        long accumulateMaxFn;
        long accumulateCollectListFn;
        long accumulateCollectSetFn;

        long rulesWithWindowTime;
        long rulesWithWindowLength;
        long rulesWithTemporalOperator;
        long rulesWithAccumulate;
        long rulesWithNegativeTemporal;
        long rulesWithAnyCepPattern;

        final Map<String, Long> windowTimeArguments = new TreeMap<>();
        final Map<String, Long> windowLengthArguments = new TreeMap<>();
        final Map<String, Long> eventFactTypes = new TreeMap<>();
        final Map<String, Long> temporalOperatorHistogram = new LinkedHashMap<>();
        final Map<String, Long> topTemporalRules = new LinkedHashMap<>();

        long totalTemporalOperators() {
            return afterCount + beforeCount + coincidesCount + duringCount + includesCount
                    + overlapsCount + overlappedByCount + startsCount + startedByCount
                    + finishesCount + finishedByCount + meetsCount + metByCount;
        }

        long totalCepPatterns() {
            return windowTimeCount + windowLengthCount + totalTemporalOperators() + accumulateCount;
        }

        long intervalOperatorCount() {
            return duringCount + includesCount + overlapsCount + overlappedByCount
                    + startsCount + startedByCount + finishesCount + finishedByCount
                    + meetsCount + metByCount;
        }
    }

    private static StaticTemporalMetrics analyzeTemporalPatterns(String drlContent) {
        StaticTemporalMetrics m = new StaticTemporalMetrics();

        m.windowTimeCount = count(drlContent, "\\bover\\s+window:time\\s*\\(");
        m.windowLengthCount = count(drlContent, "\\bover\\s+window:length\\s*\\(");

        m.afterCount = count(drlContent, "\\bafter\\b");
        m.beforeCount = count(drlContent, "\\bbefore\\b");
        m.coincidesCount = count(drlContent, "\\bcoincides\\b");
        m.duringCount = count(drlContent, "\\bduring\\b");
        m.includesCount = count(drlContent, "\\bincludes\\b");
        m.overlapsCount = count(drlContent, "\\boverlaps\\b");
        m.overlappedByCount = count(drlContent, "\\boverlappedby\\b");
        m.startsCount = count(drlContent, "\\bstarts\\b");
        m.startedByCount = count(drlContent, "\\bstartedby\\b");
        m.finishesCount = count(drlContent, "\\bfinishes\\b");
        m.finishedByCount = count(drlContent, "\\bfinishedby\\b");
        m.meetsCount = count(drlContent, "\\bmeets\\b");
        m.metByCount = count(drlContent, "\\bmetby\\b");

        m.notCount = count(drlContent, "\\bnot\\b");
        m.accumulateCount = count(drlContent, "\\baccumulate\\b");
        m.evalCount = count(drlContent, "\\beval\\b");

        m.eventDeclarations = count(drlContent, "(?s)declare\\s+\\w+.*?@role\\s*\\(\\s*event\\s*\\).*?end");
        m.expiresAnnotations = count(drlContent, "@expires\\s*\\(");
        m.timestampAnnotations = count(drlContent, "@timestamp\\s*\\(");
        m.durationAnnotations = count(drlContent, "@duration\\s*\\(");
        m.roleEventAnnotations = count(drlContent, "@role\\s*\\(\\s*event\\s*\\)");

        m.accumulateCountFn = count(drlContent, "\\bcount\\s*\\(");
        m.accumulateSumFn = count(drlContent, "\\bsum\\s*\\(");
        m.accumulateAverageFn = count(drlContent, "\\baverage\\s*\\(");
        m.accumulateMinFn = count(drlContent, "\\bmin\\s*\\(");
        m.accumulateMaxFn = count(drlContent, "\\bmax\\s*\\(");
        m.accumulateCollectListFn = count(drlContent, "\\bcollectList\\s*\\(");
        m.accumulateCollectSetFn = count(drlContent, "\\bcollectSet\\s*\\(");

        collectArguments(drlContent, "\\bover\\s+window:time\\s*\\(([^)]*)\\)", m.windowTimeArguments);
        collectArguments(drlContent, "\\bover\\s+window:length\\s*\\(([^)]*)\\)", m.windowLengthArguments);
        collectEventFactTypes(drlContent, m.eventFactTypes);

        m.temporalOperatorHistogram.put("after", m.afterCount);
        m.temporalOperatorHistogram.put("before", m.beforeCount);
        m.temporalOperatorHistogram.put("coincides", m.coincidesCount);
        m.temporalOperatorHistogram.put("during", m.duringCount);
        m.temporalOperatorHistogram.put("includes", m.includesCount);
        m.temporalOperatorHistogram.put("overlaps", m.overlapsCount);
        m.temporalOperatorHistogram.put("overlappedby", m.overlappedByCount);
        m.temporalOperatorHistogram.put("starts", m.startsCount);
        m.temporalOperatorHistogram.put("startedby", m.startedByCount);
        m.temporalOperatorHistogram.put("finishes", m.finishesCount);
        m.temporalOperatorHistogram.put("finishedby", m.finishedByCount);
        m.temporalOperatorHistogram.put("meets", m.meetsCount);
        m.temporalOperatorHistogram.put("metby", m.metByCount);

        analyzeTemporalRules(drlContent, m);

        return m;
    }

    private static void collectArguments(String text, String regex, Map<String, Long> target) {
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
        while (matcher.find()) {
            String arg = matcher.group(1).replaceAll("\\s+", " ").trim();
            if (!arg.isEmpty()) {
                target.merge(arg, 1L, Long::sum);
            }
        }
    }

    private static void collectEventFactTypes(String drlContent, Map<String, Long> target) {
        Pattern p = Pattern.compile("(?s)declare\\s+(\\w+).*?@role\\s*\\(\\s*event\\s*\\).*?end",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = p.matcher(drlContent);
        while (matcher.find()) {
            target.merge(matcher.group(1), 1L, Long::sum);
        }
    }

    private static void analyzeTemporalRules(String drlContent, StaticTemporalMetrics metrics) {
        Pattern rulePattern = Pattern.compile(
                "(?s)rule\\s+[\"']([^\"']+)[\"'](.*?)\\bend\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = rulePattern.matcher(drlContent);

        Map<String, Long> temporalScoreByRule = new LinkedHashMap<>();

        while (matcher.find()) {
            String ruleName = matcher.group(1).trim();
            String body = matcher.group(2);

            long wt = count(body, "\\bover\\s+window:time\\s*\\(");
            long wl = count(body, "\\bover\\s+window:length\\s*\\(");
            long acc = count(body, "\\baccumulate\\b");
            long nt = count(body, "\\bnot\\b");

            long temporalOps = count(body, "\\bafter\\b")
                    + count(body, "\\bbefore\\b")
                    + count(body, "\\bcoincides\\b")
                    + count(body, "\\bduring\\b")
                    + count(body, "\\bincludes\\b")
                    + count(body, "\\boverlaps\\b")
                    + count(body, "\\boverlappedby\\b")
                    + count(body, "\\bstarts\\b")
                    + count(body, "\\bstartedby\\b")
                    + count(body, "\\bfinishes\\b")
                    + count(body, "\\bfinishedby\\b")
                    + count(body, "\\bmeets\\b")
                    + count(body, "\\bmetby\\b");

            long total = wt + wl + acc + temporalOps;

            if (wt > 0)
                metrics.rulesWithWindowTime++;
            if (wl > 0)
                metrics.rulesWithWindowLength++;
            if (temporalOps > 0)
                metrics.rulesWithTemporalOperator++;
            if (acc > 0)
                metrics.rulesWithAccumulate++;
            if (nt > 0 && temporalOps > 0)
                metrics.rulesWithNegativeTemporal++;
            if (total > 0) {
                metrics.rulesWithAnyCepPattern++;
                temporalScoreByRule.put(ruleName, total);
            }
        }

        temporalScoreByRule.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(15)
                .forEach(e -> metrics.topTemporalRules.put(e.getKey(), e.getValue()));
    }

    private static void printMapInline(String label, Map<String, Long> map) {
        String rendered = map.isEmpty()
                ? "{}"
                : map.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", ", "{", "}"));
        System.out.printf("  %-36s %s%n", label + ":", rendered);
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║ Ripe RIS CEP Benchmark — Characterization Collector  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // ── Load DRL text for static analysis ──────────────────────────────
        String drlContent;
        try (InputStream is = CharacterizationCollector.class.getResourceAsStream(
                "/" + EnvConfig.get("RIPERIS_RULES_FILE"))) {
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

        long ruleCount = ruleMetas.size();

        // A2: Conditions per rule — count PatternDescr inputs per RuleMeta
        IntSummaryStatistics condStats = ruleMetas.stream()
                .mapToInt(rm -> rm.getInputs().size())
                .summaryStatistics();

        // A6: Distinct input fact types across all rules
        Set<String> allInputTypes = ruleMetas.stream()
                .flatMap(rm -> rm.getInputs().stream())
                .collect(Collectors.toCollection(TreeSet::new));

        // C8/C9: CEP / temporal-pattern complexity — regex scan of DRL text
        StaticTemporalMetrics temporal = analyzeTemporalPatterns(drlContent);

        long windowTimeCount = temporal.windowTimeCount;
        long windowLenCount = temporal.windowLengthCount;
        long afterCount = temporal.afterCount;
        long beforeCount = temporal.beforeCount;
        long coincidesCount = temporal.coincidesCount;
        long intervalOperatorCount = temporal.intervalOperatorCount();
        long temporalOperatorCount = temporal.totalTemporalOperators();
        long notCount = temporal.notCount;
        long accumCount = temporal.accumulateCount;
        long evalCount = temporal.evalCount;
        long totalCepPatternCount = temporal.totalCepPatterns();

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
        System.out.printf("  C8  event declarations:           %d%n", temporal.eventDeclarations);
        System.out.printf("  C8  @expires annotations:         %d%n", temporal.expiresAnnotations);
        System.out.printf("  C8  @timestamp annotations:       %d%n", temporal.timestampAnnotations);
        System.out.printf("  C8  @duration annotations:        %d%n", temporal.durationAnnotations);
        System.out.printf("  C8  window:time occurrences:      %d  (rules=%d)%n",
                windowTimeCount, temporal.rulesWithWindowTime);
        System.out.printf("  C8  window:length occurrences:    %d  (rules=%d)%n",
                windowLenCount, temporal.rulesWithWindowLength);
        System.out.printf("  C9  'after' patterns:             %d%n", afterCount);
        System.out.printf("  C9  'before' patterns:            %d%n", beforeCount);
        System.out.printf("  C9  'coincides' patterns:         %d%n", coincidesCount);
        System.out.printf("  C9  interval temporal operators:  %d%n", intervalOperatorCount);
        System.out.printf("  C9  all temporal operators:       %d  (rules=%d)%n",
                temporalOperatorCount, temporal.rulesWithTemporalOperator);
        System.out.printf("  C9  negation 'not' patterns:      %d%n", notCount);
        System.out.printf("  C9  negative temporal rules:      %d%n", temporal.rulesWithNegativeTemporal);
        System.out.printf("  C9  'accumulate' patterns:        %d  (rules=%d)%n",
                accumCount, temporal.rulesWithAccumulate);
        System.out.printf("  C9  'eval' patterns:              %d%n", evalCount);
        System.out.printf("  C9  total CEP pattern occurrences:%d  (rules=%d)%n",
                totalCepPatternCount, temporal.rulesWithAnyCepPattern);
        printMapInline("  C8  window:time args", temporal.windowTimeArguments);
        printMapInline("  C8  window:length args", temporal.windowLengthArguments);
        printMapInline("  C9  temporal op histogram", temporal.temporalOperatorHistogram);
        printMapInline("  C9  accumulate functions",
                new LinkedHashMap<String, Long>() {
                    {
                        put("count", temporal.accumulateCountFn);
                        put("sum", temporal.accumulateSumFn);
                        put("average", temporal.accumulateAverageFn);
                        put("min", temporal.accumulateMinFn);
                        put("max", temporal.accumulateMaxFn);
                        put("collectList", temporal.accumulateCollectListFn);
                        put("collectSet", temporal.accumulateCollectSetFn);
                    }
                });
        printMapInline("  C8  event fact types", temporal.eventFactTypes);
        printMapInline("  C9  top temporal rules", temporal.topTemporalRules);
        System.out.println();

        // ── B: Dependency graph metrics ─────────────────────────────────────
        System.out.println("── [B] Dependency / Structural Properties ───────────────");
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

        // ── D: Dataset / domain properties ─────────────────────────────────
        System.out.println("── [D] Data / Domain Properties ─────────────────────────");
        String dataFile;
        if (args != null && args.length > 0) {
            dataFile = EnvConfig.get(args[0]);
        } else {
            dataFile = EnvConfig.get("RIPERIS_DEFAULT_DATA_FILE");
        }
        List<RisMessage> allEvents = RipeRisBaselineBenchmark.loadEvents(dataFile, Long.MAX_VALUE);
        Set<String> peers = allEvents.stream().map(RisMessage::getPeer).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        // D2: Attribute cardinality
        long distinctPeers = peers.size();
        long distinctEventTypes = allEvents.stream().map(RisMessage::getBgpType).filter(Objects::nonNull).distinct()
                .count();

        // D3 / C1 / C2: Temporal distribution and velocity profile.
        // Sort by timestamp to find dataset wall-clock span.
        // Use per-peer IAT to avoid cross-peer interleaving gaps skewing CV.
        long[] timestamps = allEvents.stream().mapToLong(e -> (long) (e.getTimestamp() * 1000)).toArray();
        Arrays.sort(timestamps);
        long spanMs = timestamps.length > 0 ? timestamps[timestamps.length - 1] - timestamps[0] : 0;
        // C1: arrival rate from dataset time span (how fast events arrive in real-time)
        double arrivalRatePerSec = spanMs > 0 ? allEvents.size() * 1000.0 / spanMs : 0;

        // C2: Compute per-peer IATs to get meaningful velocity profile.
        // Cross-peer interleaving creates artificially large IAT gaps.
        List<Double> allIATs = new ArrayList<>();
        Map<String, List<Long>> perPeerTs = new LinkedHashMap<>();
        for (RisMessage ev : allEvents) {
            if (ev.getPeer() != null) {
                perPeerTs.computeIfAbsent(ev.getPeer(), k -> new ArrayList<>()).add((long) (ev.getTimestamp() * 1000));
            }
        }
        for (List<Long> tsList : perPeerTs.values()) {
            Collections.sort(tsList);
            for (int i = 1; i < tsList.size(); i++) {
                allIATs.add((double) (tsList.get(i) - tsList.get(i - 1)));
            }
        }
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

        // Event type distribution
        Map<String, Long> eventTypeDist = allEvents.stream()
                .filter(e -> e.getBgpType() != null)
                .collect(Collectors.groupingBy(RisMessage::getBgpType, Collectors.counting()));

        System.out.printf("  D1  Total events in dataset:      %,d%n", allEvents.size());
        System.out.printf("  D1  Time span:                    %,d ms (%.1f min)%n", spanMs, spanMs / 60000.0);
        System.out.printf("  D2  Distinct peers:               %d → %s%n", distinctPeers, peers);
        System.out.printf("  D2  Distinct event types:         %d%n", distinctEventTypes);
        System.out.printf("  D3  Event type distribution:      %s%n",
                eventTypeDist.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(e -> e.getKey() + "=" + String.format("%.1f%%", 100.0 * e.getValue() / allEvents.size()))
                        .collect(Collectors.joining(", ")));
        System.out.printf("  C1  Event arrival rate (dataset): %.0f events/sec%n", arrivalRatePerSec);
        System.out.printf("  C2  Dataset wall-clock span:      %,d ms (%.1f min)%n", spanMs, spanMs / 60000.0);
        System.out.printf("  C2  Mean per-peer IAT:            %.2f ms%n", meanIAT);
        System.out.printf("  C2  Median per-peer IAT:          %.2f ms%n", medianIAT);
        System.out.printf("  C2  IAT std dev (per-peer):       %.2f ms%n", stdIAT);
        System.out.printf("  C2  Coeff of variation (CV):      %.3f → %s%n", cvIAT, velocityClass);
        System.out.println();

        // ── C: Runtime metrics (full replay) ───────────────────────────────
        System.out.println("── [C] Runtime Metrics (full replay) ────────────────────");
        System.out.printf("  Replaying %,d events...%n", allEvents.size());

        CepSessionFactory factory = new CepSessionFactory(
                EnvConfig.get("RIPERIS_RULES_FILE"));
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

        // Replay with per-event ts tracking for selectivity
        long t0 = System.currentTimeMillis();
        // Manual replay to track per-event selectivity
        long totalFired = 0;
        org.drools.core.time.SessionPseudoClock clock = session.getSessionClock();
        long prevTs = allEvents.isEmpty() ? 0L : (long) (allEvents.get(0).getTimestamp() * 1000);
        for (RisMessage ev : allEvents) {
            long evTs = (long) (ev.getTimestamp() * 1000);
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
        long endWM = session.getFactCount();

        // Coverage
        Set<String> firedRuleNames = agendaM.fireCounts.keySet();
        long coveredCount = allRuleNames.stream().filter(firedRuleNames::contains).count();
        double coveragePct = allRuleNames.isEmpty() ? 0 : 100.0 * coveredCount / allRuleNames.size();
        long selectiveEvents = agendaM.selectiveEventTs.size();
        double selectivity = allEvents.isEmpty() ? 0 : 100.0 * selectiveEvents / allEvents.size();

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

        // ── Full characterization table ─────────────────────────────────────
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         CHARACTERIZATION TABLE (Paper-ready)         ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("│ %-36s │ %-15s │%n", "Property", "Ripe RIS CEP");
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
        System.out.printf("│ %-36s │ %-15.2f │%n", "C5  Avg WM changes/event",
                (double) wmM.totalWMChanges() / allEvents.size());
        System.out.printf("│ %-36s │ %-15.2f │%n", "C6  Avg conflict set size", agendaM.avgConflictSet());
        System.out.printf("│ %-36s │ %-15d │%n", "C6  Peak conflict set size", agendaM.peakConflictSet);
        System.out.printf("│ %-36s │ %-15.2f │%n", "C7  Rules fired per event", (double) totalFired / allEvents.size());
        System.out.printf("│ %-36s │ %-14.1f%% │%n", "C3  Rule coverage on dataset", coveragePct);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  Event declarations", temporal.eventDeclarations);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  @expires annotations", temporal.expiresAnnotations);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:time occurrences", windowTimeCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:time rules", temporal.rulesWithWindowTime);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:length occurrences", windowLenCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C8  window:length rules", temporal.rulesWithWindowLength);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  after patterns", afterCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  before patterns", beforeCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  coincides patterns", coincidesCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  interval operators", intervalOperatorCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  all temporal operators", temporalOperatorCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  accumulate patterns", accumCount);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  negative temporal rules", temporal.rulesWithNegativeTemporal);
        System.out.printf("│ %-36s │ %-15d │%n", "C9  Total CEP patterns", totalCepPatternCount);
        System.out.printf("│ %-36s │ %-15s │%n", "D1  Dataset size (events)", String.format("%,d", allEvents.size()));
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct peers", distinctPeers);
        System.out.printf("│ %-36s │ %-15d │%n", "D2  Distinct event types", distinctEventTypes);
        System.out.printf("│ %-36s │ %-15s │%n", "D4  Data provenance", "RIPE RIS Live");
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

        long totalEvts = allEvents.size();
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

        // ── Section F: Chain-depth firing probabilities ─────────────────────
        // Uses ForwardChainFinder BFS from RisMessage (the external entry fact).
        // Depth 0 = rules directly consuming RisMessage (alpha filters).
        // Depth 1 = rules consuming facts PRODUCED by depth-0 rules, etc.
        // P(depth-d fires | event) = sum(activations at depth d) / total_events
        System.out.println();
        System.out.println("── [F] Forward Chain Depth Firing Probabilities ─────────");
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
