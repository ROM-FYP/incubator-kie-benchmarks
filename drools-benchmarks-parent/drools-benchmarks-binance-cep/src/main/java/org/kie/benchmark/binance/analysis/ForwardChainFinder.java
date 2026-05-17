/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.benchmark.binance.analysis;

import java.util.*;
import java.util.stream.Collectors;

import org.jgrapht.graph.DefaultEdge;

/**
 * Finds all rules in the transitive forward chain starting from rules
 * that consume a given entry-point fact type.
 *
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Identify "seed rules" — rules whose LHS inputs contain the entry-point fact type</li>
 *   <li>BFS traversal from each seed following outgoing edges in the dependency graph</li>
 *   <li>Track depth and connecting fact types for each rule reached</li>
 *   <li>Compute uncaptured rules — rules not reachable from any seed</li>
 * </ol>
 */
public class ForwardChainFinder {

    private final DependencyGraphBuilder graphBuilder;
    private final List<RuleMeta> allRules;

    /**
     * Creates a ForwardChainFinder from a pre-built dependency graph and the full rule list.
     *
     * @param graphBuilder the dependency graph
     * @param allRules     the complete list of parsed rules
     */
    public ForwardChainFinder(DependencyGraphBuilder graphBuilder, List<RuleMeta> allRules) {
        this.graphBuilder = graphBuilder;
        this.allRules = allRules;
    }

    /**
     * Creates a ForwardChainFinder directly from parsed rules.
     *
     * @param allRules the complete list of parsed rules
     */
    public ForwardChainFinder(List<RuleMeta> allRules) {
        this.graphBuilder = new DependencyGraphBuilder(allRules);
        this.allRules = allRules;
    }

    /**
     * Finds the complete forward chain starting from the given entry-point fact type.
     *
     * @param entryFactType the fact type name to start from (e.g., "MarketEvent")
     * @return a ForwardChainResult containing captured rules with depths,
     *         connecting facts, and uncaptured rules
     */
    public ForwardChainResult findForwardChain(String entryFactType) {
        // Step 1: Find seed rules — rules whose inputs contain the entry fact type
        List<RuleMeta> seedRules = allRules.stream()
                .filter(r -> r.getInputs().contains(entryFactType))
                .collect(Collectors.toList());

        // Step 2: BFS to find the sub-graph reachable from seed rules
        Set<RuleMeta> reachable = new LinkedHashSet<>();
        Deque<RuleMeta> bfsQueue = new ArrayDeque<>(seedRules);
        while (!bfsQueue.isEmpty()) {
            RuleMeta cur = bfsQueue.poll();
            if (reachable.add(cur)) {
                bfsQueue.addAll(graphBuilder.getSuccessors(cur));
            }
        }

        // Step 3: Build the induced sub-graph of reachable rules
        org.jgrapht.graph.DefaultDirectedGraph<RuleMeta, DefaultEdge> subGraph =
                new org.jgrapht.graph.DefaultDirectedGraph<>(DefaultEdge.class);
        for (RuleMeta r : reachable) subGraph.addVertex(r);
        Map<String, Set<String>> connectingFacts = new LinkedHashMap<>();
        for (RuleMeta r : reachable) {
            for (RuleMeta suc : graphBuilder.getSuccessors(r)) {
                if (reachable.contains(suc)) {
                    subGraph.addEdge(r, suc);
                    Set<String> conn = new HashSet<>(r.getOutputs());
                    conn.retainAll(suc.getInputs());
                    connectingFacts.computeIfAbsent(
                            r.getRuleName() + " → " + suc.getRuleName(),
                            k -> new LinkedHashSet<>()).addAll(conn);
                }
            }
        }

        // Step 4: SCC condensation — the textbook method for longest path in
        // directed graphs with cycles (Cormen et al., CLRS Ch. 22).
        // - Kosaraju/Tarjan finds Strongly Connected Components
        // - Each SCC collapses to a single depth level (mutually recursive rules
        //   fire at the same inference level in the Rete network)
        // - The condensation graph is a true DAG
        // - Longest path on this DAG gives the correct chaining depth
        org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector<RuleMeta, DefaultEdge>
                sccInspector = new org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector<>(subGraph);
        List<Set<RuleMeta>> sccs = sccInspector.stronglyConnectedSets();

        // Map each rule to its SCC index
        Map<RuleMeta, Integer> ruleToScc = new HashMap<>();
        for (int i = 0; i < sccs.size(); i++) {
            for (RuleMeta r : sccs.get(i)) ruleToScc.put(r, i);
        }

        // Build condensation DAG: nodes = SCC indices, edges = cross-SCC edges
        int sccCount = sccs.size();
        Map<Integer, Set<Integer>> condensationAdj = new HashMap<>();
        Map<Integer, Integer> sccInDegree = new HashMap<>();
        for (int i = 0; i < sccCount; i++) {
            condensationAdj.put(i, new LinkedHashSet<>());
            sccInDegree.put(i, 0);
        }
        for (RuleMeta r : reachable) {
            int srcScc = ruleToScc.get(r);
            for (RuleMeta suc : graphBuilder.getSuccessors(r)) {
                if (!reachable.contains(suc)) continue;
                int dstScc = ruleToScc.get(suc);
                if (srcScc != dstScc && condensationAdj.get(srcScc).add(dstScc)) {
                    sccInDegree.merge(dstScc, 1, Integer::sum);
                }
            }
        }

        // Step 5: Kahn's topological sort + longest-path on the condensation DAG
        Map<Integer, Integer> sccDepth = new HashMap<>();
        Deque<Integer> topoQueue = new ArrayDeque<>();

        Set<Integer> seedSccIds = seedRules.stream()
                .filter(reachable::contains)
                .map(ruleToScc::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (int sccId = 0; sccId < sccCount; sccId++) {
            if (sccInDegree.get(sccId) == 0) {
                sccDepth.put(sccId, 0);
                topoQueue.add(sccId);
            }
        }

        while (!topoQueue.isEmpty()) {
            int curScc = topoQueue.poll();
            int curDepth = sccDepth.getOrDefault(curScc, 0);
            for (int sucScc : condensationAdj.get(curScc)) {
                sccDepth.merge(sucScc, curDepth + 1, Math::max);
                int remaining = sccInDegree.merge(sucScc, -1, Integer::sum);
                if (remaining == 0) topoQueue.add(sucScc);
            }
        }

        // Step 6: Map SCC depths back to individual rules
        Map<String, Integer> capturedRuleDepths = new LinkedHashMap<>();
        for (RuleMeta r : reachable) {
            Integer sccId = ruleToScc.get(r);
            Integer depth = sccDepth.get(sccId);
            if (depth != null) {
                capturedRuleDepths.put(r.getRuleName(), depth);
            }
        }

        // Group rules by depth
        Map<Integer, List<String>> chainsByDepth = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : capturedRuleDepths.entrySet()) {
            chainsByDepth.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // Compute uncaptured rules (not reachable from seeds at all)
        Set<String> allRuleNames = allRules.stream()
                .map(RuleMeta::getRuleName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> uncapturedRules = new LinkedHashSet<>(allRuleNames);
        uncapturedRules.removeAll(capturedRuleDepths.keySet());

        return new ForwardChainResult(entryFactType, seedRules.size(),
                capturedRuleDepths, chainsByDepth, uncapturedRules, connectingFacts);
    }


    // =========================================================================
    // Inner classes
    // =========================================================================

    /**
     * Internal BFS queue entry pairing a rule with its traversal depth.
     */
    private static class RuleDepthEntry {
        final RuleMeta rule;
        final int depth;

        RuleDepthEntry(RuleMeta rule, int depth) {
            this.rule = rule;
            this.depth = depth;
        }
    }

    /**
     * Holds the results of a forward chain analysis.
     */
    public static class ForwardChainResult {
        private final String entryFactType;
        private final int seedCount;
        private final Map<String, Integer> capturedRuleDepths;
        private final Map<Integer, List<String>> chainsByDepth;
        private final Set<String> uncapturedRules;
        private final Map<String, Set<String>> connectingFacts;

        public ForwardChainResult(String entryFactType, int seedCount,
                                  Map<String, Integer> capturedRuleDepths,
                                  Map<Integer, List<String>> chainsByDepth,
                                  Set<String> uncapturedRules,
                                  Map<String, Set<String>> connectingFacts) {
            this.entryFactType = entryFactType;
            this.seedCount = seedCount;
            this.capturedRuleDepths = capturedRuleDepths;
            this.chainsByDepth = chainsByDepth;
            this.uncapturedRules = uncapturedRules;
            this.connectingFacts = connectingFacts;
        }

        /** The entry-point fact type used for the analysis. */
        public String getEntryFactType() { return entryFactType; }

        /** Number of seed rules (rules directly consuming the entry fact type). */
        public int getSeedCount() { return seedCount; }

        /** Map of rule name → depth in the forward chain (0 = seed). */
        public Map<String, Integer> getCapturedRuleDepths() {
            return Collections.unmodifiableMap(capturedRuleDepths);
        }

        /** Rules grouped by depth level. */
        public Map<Integer, List<String>> getChainsByDepth() {
            return Collections.unmodifiableMap(chainsByDepth);
        }

        /** Rules not reachable from any seed (bootstrap, cleanup, orphans). */
        public Set<String> getUncapturedRules() {
            return Collections.unmodifiableSet(uncapturedRules);
        }

        /** For each edge "RuleA → RuleB", the set of fact types connecting them. */
        public Map<String, Set<String>> getConnectingFacts() {
            return Collections.unmodifiableMap(connectingFacts);
        }

        /** Total number of rules captured in the forward chain. */
        public int getCapturedCount() { return capturedRuleDepths.size(); }

        /** Maximum depth of the forward chain. */
        public int getMaxDepth() {
            return chainsByDepth.isEmpty() ? 0 :
                    Collections.max(chainsByDepth.keySet());
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Forward Chain Result for entry fact: ").append(entryFactType).append("\n");
            sb.append("Seed rules: ").append(seedCount).append("\n");
            sb.append("Total captured: ").append(getCapturedCount()).append("\n");
            sb.append("Max depth: ").append(getMaxDepth()).append("\n");
            sb.append("Uncaptured: ").append(uncapturedRules.size()).append("\n");
            return sb.toString();
        }
    }
}
