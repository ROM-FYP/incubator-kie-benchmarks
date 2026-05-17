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
package org.kie.benchmark.cep.riperis.analysis;

import java.util.*;
import java.util.stream.Collectors;

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
     * @param entryFactType the fact type name to start from (e.g., "MarketEvent" or "RisMessage")
     * @return a ForwardChainResult containing captured rules with depths,
     *         connecting facts, and uncaptured rules
     */
    public ForwardChainResult findForwardChain(String entryFactType) {
        // Step 1: Find seed rules — rules whose inputs contain the entry fact type
        List<RuleMeta> seedRules = allRules.stream()
                .filter(r -> r.getInputs().contains(entryFactType))
                .collect(Collectors.toList());

        // Step 2: BFS from each seed rule
        Map<String, Integer> capturedRuleDepths = new LinkedHashMap<>();
        Map<String, Set<String>> connectingFacts = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();

        // Queue holds: (rule, depth)
        Deque<RuleDepthEntry> queue = new ArrayDeque<>();

        // Initialize seeds at depth 0
        for (RuleMeta seed : seedRules) {
            String name = seed.getRuleName();
            if (!visited.contains(name)) {
                visited.add(name);
                capturedRuleDepths.put(name, 0);
                queue.add(new RuleDepthEntry(seed, 0));
            }
        }

        // BFS traversal
        while (!queue.isEmpty()) {
            RuleDepthEntry current = queue.poll();
            RuleMeta currentRule = current.rule;
            int currentDepth = current.depth;

            Set<RuleMeta> successors = graphBuilder.getSuccessors(currentRule);
            for (RuleMeta successor : successors) {
                // Compute the connecting fact types (intersection of outputs → inputs)
                Set<String> connection = new HashSet<>(currentRule.getOutputs());
                connection.retainAll(successor.getInputs());

                String edgeKey = currentRule.getRuleName() + " → " + successor.getRuleName();
                connectingFacts.computeIfAbsent(edgeKey, k -> new LinkedHashSet<>()).addAll(connection);

                String successorName = successor.getRuleName();
                if (!visited.contains(successorName)) {
                    visited.add(successorName);
                    int newDepth = currentDepth + 1;
                    capturedRuleDepths.put(successorName, newDepth);
                    queue.add(new RuleDepthEntry(successor, newDepth));
                }
            }
        }

        // Step 3: Group rules by depth
        Map<Integer, List<String>> chainsByDepth = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : capturedRuleDepths.entrySet()) {
            chainsByDepth.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        // Step 4: Compute uncaptured rules
        Set<String> allRuleNames = allRules.stream()
                .map(RuleMeta::getRuleName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> uncapturedRules = new LinkedHashSet<>(allRuleNames);
        uncapturedRules.removeAll(visited);

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
