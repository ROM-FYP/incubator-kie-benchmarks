/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.benchmark.binance.parallel;

import org.drools.drl.parser.DroolsParserException;
import org.kie.benchmark.binance.analysis.DrlRuleParser;
import org.kie.benchmark.binance.analysis.RuleMeta;
import org.kie.benchmark.binance.analysis.Stratifier;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core orchestration planner that merges structural phases (from Stratifier)
 * with Infomap community-detection clusters (from .ftree) to produce
 * a {@link PartitionPlan}.
 *
 * <p>
 * <b>Algorithm:</b>
 * </p>
 * <ol>
 * <li>Parse DRL → List&lt;RuleMeta&gt; (rule name, inputs, outputs)</li>
 * <li>Stratifier → execution phases (topological sort + SCC)</li>
 * <li>Merge phases with Infomap clusters → PartitionPlan</li>
 * </ol>
 */
public class ClusterPartitioner {

    /** Catch-all cluster ID for rules not found in the Infomap .ftree */
    private static final int CATCH_ALL_CLUSTER = 0;

    private final String drlContent;
    private final Map<String, Integer> infomapClusters;

    /**
     * Creates a ClusterPartitioner.
     *
     * @param drlContent      the full DRL file content
     * @param infomapClusters map of rule name → cluster ID from .ftree
     */
    public ClusterPartitioner(String drlContent, Map<String, Integer> infomapClusters) {
        this.drlContent = drlContent;
        this.infomapClusters = infomapClusters;
    }

    /**
     * Builds the partition plan by combining structural phases with Infomap
     * clusters.
     *
     * @return the PartitionPlan
     * @throws DroolsParserException if the DRL cannot be parsed
     */
    public PartitionPlan buildPlan() throws DroolsParserException {
        // Step 1: Parse DRL to extract rule metadata
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> allRules = parser.parse(drlContent);

        // Build a lookup: ruleName → RuleMeta
        Map<String, RuleMeta> ruleMetaMap = new LinkedHashMap<>();
        for (RuleMeta rm : allRules) {
            ruleMetaMap.put(rm.getRuleName(), rm);
        }

        // Step 2: Stratify into execution phases
        Stratifier stratifier = new Stratifier(allRules);
        List<Set<String>> phases = stratifier.stratify();

        System.out.println("[ClusterPartitioner] Parsed " + allRules.size() + " rules into "
                + phases.size() + " phases");
        System.out.println("[ClusterPartitioner] Infomap clusters cover "
                + infomapClusters.size() + " rules");

        // Step 3: For each phase, group rules by Infomap cluster
        List<PartitionPlan.Phase> planPhases = new ArrayList<>();
        int phaseNum = 1;

        for (Set<String> phaseRuleNames : phases) {
            // Group rules in this phase by their Infomap cluster
            Map<Integer, List<String>> clusterGroups = new LinkedHashMap<>();

            for (String ruleName : phaseRuleNames) {
                int clusterId = infomapClusters.getOrDefault(ruleName, CATCH_ALL_CLUSTER);
                clusterGroups.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(ruleName);
            }

            // Build SessionGroups
            List<PartitionPlan.SessionGroup> sessionGroups = new ArrayList<>();

            for (Map.Entry<Integer, List<String>> entry : clusterGroups.entrySet()) {
                int clusterId = entry.getKey();
                List<String> ruleNames = entry.getValue();

                // Compute aggregate inputs/outputs for this group
                Set<String> inputs = new LinkedHashSet<>();
                Set<String> outputs = new LinkedHashSet<>();

                for (String ruleName : ruleNames) {
                    RuleMeta rm = ruleMetaMap.get(ruleName);
                    if (rm != null) {
                        inputs.addAll(rm.getInputs());
                        outputs.addAll(rm.getOutputs());
                    }
                }

                PartitionPlan.SessionGroup group = new PartitionPlan.SessionGroup(
                        clusterId, phaseNum, ruleNames, inputs, outputs);

                // Generate DRL content for this group
                String groupDrl = DrlSplitter.buildDrlForRules(drlContent, ruleNames);
                group.setDrlContent(groupDrl);

                sessionGroups.add(group);
            }

            planPhases.add(new PartitionPlan.Phase(phaseNum, sessionGroups));
            phaseNum++;
        }

        PartitionPlan plan = new PartitionPlan(planPhases);
        System.out.println("[ClusterPartitioner] Built plan: " + plan.getPhases().size()
                + " phases, " + plan.totalRules() + " total rules assigned");

        return plan;
    }

    /**
     * Convenience method: build plan from DRL classpath resource and .ftree
     * classpath resource.
     *
     * @param drlContent       the full DRL string
     * @param ftreeInputStream input stream of the .ftree file
     * @return the PartitionPlan
     * @throws DroolsParserException if DRL parsing fails
     * @throws IOException           if .ftree reading fails
     */
    public static PartitionPlan buildFromResources(String drlContent,
            java.io.InputStream ftreeInputStream)
            throws DroolsParserException, IOException {
        Map<String, Integer> clusters = FtreeParser.parse(ftreeInputStream);
        ClusterPartitioner partitioner = new ClusterPartitioner(drlContent, clusters);
        return partitioner.buildPlan();
    }

    // =========================================================================
    // Cluster-level parallel execution (self-contained clusters)
    // =========================================================================

    /** Cluster name labels keyed by top-level Infomap module ID. */
    private static final Map<Integer, String> CLUSTER_NAMES = Map.of(
            1, "Feed Health & Mode Transitions",
            2, "Depth/Spread",
            3, "Trade Alpha",
            5, "Liquidation",
            6, "Trade Rate");

    /** Maximum recursion depth for bridge rule duplication. */
    private static final int BRIDGE_DEPTH_CAP = 3;

    /**
     * Fact types to SKIP during bridge rule duplication.
     * These are "shared mutable singletons" — many rules both read and modify them,
     * creating O(n²) re-evaluation cascades when duplicated into cluster sessions.
     * Rules producing these facts are left to fire in their native cluster or
     * fallback.
     */
    private static final Set<String> BRIDGE_SKIP_FACT_TYPES = Set.of(
            "FeedHealth", // 15+ rules read/modify — biggest cycle source
            "ModeState" // mode transition rules read/modify
    );

    /**
     * Builds self-contained Infomap clusters with bridge rule duplication,
     * a fallback session for uncaptured rules, and an LHS-parsed routing table.
     *
     * <p>
     * <b>Algorithm:</b>
     * </p>
     * <ol>
     * <li>Assign 47 .ftree rules to clusters 1–6</li>
     * <li>For each cluster, recursively duplicate producer rules for missing input
     * facts (depth cap = 3)</li>
     * <li>Remaining ~63 rules go into a fallback session</li>
     * <li>Parse each cluster's rule LHS to build eventType → cluster routing
     * table</li>
     * </ol>
     *
     * @return a ClusterPlan with all clusters, fallback, and routing table
     * @throws DroolsParserException if DRL parsing fails
     */
    public PartitionPlan.ClusterPlan buildSelfContainedClusters() throws DroolsParserException {
        // Step 1: Parse DRL to get rule metadata (inputs/outputs)
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> allRules = parser.parse(drlContent);

        Map<String, RuleMeta> ruleMetaMap = new LinkedHashMap<>();
        for (RuleMeta rm : allRules) {
            ruleMetaMap.put(rm.getRuleName(), rm);
        }

        // Step 2: Build per-cluster rule lists from Infomap assignments
        Map<Integer, List<String>> clusterRuleMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : infomapClusters.entrySet()) {
            int clusterId = entry.getValue();

            // OPTIMIZATION: Merge Cluster 4 (Mode Transitions) into Cluster 1 (Feed Health)
            // They are tightly coupled via FeedHealth/ModeState facts. Merging them
            // keeps these heavy-traffic facts in one session, eliminating bridge
            // duplication cycles.
            if (clusterId == 4) {
                clusterId = 1;
            }

            clusterRuleMap.computeIfAbsent(clusterId, k -> new ArrayList<>())
                    .add(entry.getKey());
        }

        System.out.println("[ClusterPartitioner] Building self-contained clusters from "
                + infomapClusters.size() + " Infomap rules across "
                + clusterRuleMap.size() + " clusters");

        // Step 3: Build each self-contained cluster with bridge rule duplication
        List<PartitionPlan.SelfContainedCluster> clusters = new ArrayList<>();

        for (Map.Entry<Integer, List<String>> entry : clusterRuleMap.entrySet()) {
            int clusterId = entry.getKey();
            List<String> originalRules = entry.getValue();
            String clusterName = CLUSTER_NAMES.getOrDefault(clusterId, "Cluster " + clusterId);

            // Bridge rule duplication: find missing input facts and duplicate producers.
            // Skip producers of high-cycle-risk facts (shared mutable singletons that
            // many rules both read and modify — causes cascading re-evaluation storms).
            Set<String> allClusterRuleNames = new LinkedHashSet<>(originalRules);
            List<String> duplicatedRules = new ArrayList<>();

            Queue<String> missingFacts = new LinkedList<>();
            Set<String> missingInitial = findMissingInputFacts(allClusterRuleNames, ruleMetaMap);
            missingFacts.addAll(missingInitial);
            System.out.println("[BridgeDebug] Cluster " + clusterId + " initially missing: " + missingInitial);

            int depth = 0;
            while (!missingFacts.isEmpty() && depth < BRIDGE_DEPTH_CAP) {
                String missingFact = missingFacts.poll();
                // Skip bridge duplication for high-cycle-risk fact types
                if (BRIDGE_SKIP_FACT_TYPES.contains(missingFact)) {
                    System.out.println("[BridgeDebug]   -> SKIPPING high-risk fact: " + missingFact);
                    continue;
                }
                List<String> producers = findProducersOf(missingFact, ruleMetaMap);
                System.out.println("[BridgeDebug]   -> Fact " + missingFact + " has producers: " + producers);
                for (String producer : producers) {
                    if (!allClusterRuleNames.contains(producer)) {
                        // Skip if this producer outputs any high-cycle-risk fact
                        RuleMeta pm = ruleMetaMap.get(producer);
                        if (pm != null && pm.getOutputs().stream()
                                .anyMatch(BRIDGE_SKIP_FACT_TYPES::contains)) {
                            continue;
                        }
                        allClusterRuleNames.add(producer);
                        duplicatedRules.add(producer);
                        // Check if duplicated rule introduces new missing facts
                        if (pm != null) {
                            for (String input : pm.getInputs()) {
                                if (!isProducedWithinCluster(input, allClusterRuleNames, ruleMetaMap)) {
                                    missingFacts.add(input);
                                }
                            }
                        }
                    }
                }
                depth++;
            }

            // Compute aggregate inputs/outputs
            Set<String> inputs = new LinkedHashSet<>();
            Set<String> outputs = new LinkedHashSet<>();
            for (String ruleName : allClusterRuleNames) {
                RuleMeta rm = ruleMetaMap.get(ruleName);
                if (rm != null) {
                    inputs.addAll(rm.getInputs());
                    outputs.addAll(rm.getOutputs());
                }
            }

            // Extract event types consumed by this cluster
            Set<String> eventTypes = new LinkedHashSet<>();
            for (String ruleName : allClusterRuleNames) {
                eventTypes.addAll(LhsConstraintParser.extractEventTypes(ruleName, drlContent));
            }

            PartitionPlan.SelfContainedCluster cluster = new PartitionPlan.SelfContainedCluster(
                    clusterId, clusterName, originalRules, duplicatedRules,
                    eventTypes, inputs, outputs);

            // Generate DRL content
            cluster.setDrlContent(DrlSplitter.buildDrlForRules(drlContent,
                    new ArrayList<>(allClusterRuleNames)));

            clusters.add(cluster);
            System.out.println("[ClusterPartitioner]   " + cluster);
        }

        // Step 4: Build fallback session (all rules NOT in any Infomap cluster)
        Set<String> allClusterOriginalRules = new LinkedHashSet<>();
        for (PartitionPlan.SelfContainedCluster c : clusters) {
            allClusterOriginalRules.addAll(c.getOriginalRules());
        }

        List<String> fallbackRules = new ArrayList<>();
        for (RuleMeta rm : allRules) {
            if (!allClusterOriginalRules.contains(rm.getRuleName())) {
                fallbackRules.add(rm.getRuleName());
            }
        }

        Set<String> allFallbackRuleNames = new LinkedHashSet<>(fallbackRules);
        List<String> duplicatedFallbackRules = new ArrayList<>();

        Queue<String> missingFacts = new LinkedList<>();
        missingFacts.addAll(findMissingInputFacts(allFallbackRuleNames, ruleMetaMap));

        int depth = 0;
        while (!missingFacts.isEmpty() && depth < BRIDGE_DEPTH_CAP) {
            String missingFact = missingFacts.poll();
            if (BRIDGE_SKIP_FACT_TYPES.contains(missingFact)) {
                continue; // Prevent infinite re-assessment of FeedHealth loops
            }
            List<String> producers = findProducersOf(missingFact, ruleMetaMap);
            for (String producer : producers) {
                if (!allFallbackRuleNames.contains(producer)) {
                    RuleMeta pm = ruleMetaMap.get(producer);
                    if (pm != null && pm.getOutputs().stream().anyMatch(BRIDGE_SKIP_FACT_TYPES::contains)) {
                        continue;
                    }
                    allFallbackRuleNames.add(producer);
                    duplicatedFallbackRules.add(producer);
                    if (pm != null) {
                        for (String input : pm.getInputs()) {
                            if (!isProducedWithinCluster(input, allFallbackRuleNames, ruleMetaMap)) {
                                missingFacts.add(input);
                            }
                        }
                    }
                }
            }
            depth++;
        }

        PartitionPlan.SelfContainedCluster fallback = new PartitionPlan.SelfContainedCluster(
                -1, "Fallback", fallbackRules, duplicatedFallbackRules,
                LhsConstraintParser.ALL_EVENT_TYPES,
                Collections.emptySet(), Collections.emptySet());
        fallback.setDrlContent(DrlSplitter.buildDrlForRules(drlContent, new ArrayList<>(allFallbackRuleNames)));

        System.out.println("[ClusterPartitioner]   " + fallback);

        // Step 5: Build routing table
        Map<Integer, Set<String>> clusterEventTypes = new LinkedHashMap<>();
        for (PartitionPlan.SelfContainedCluster c : clusters) {
            clusterEventTypes.put(c.getClusterId(), c.getConsumedEventTypes());
        }
        Map<String, List<Integer>> routingTable = LhsConstraintParser.buildRoutingTable(clusterEventTypes);

        System.out.println("[ClusterPartitioner] Routing table: " + routingTable);

        return new PartitionPlan.ClusterPlan(clusters, fallback, routingTable);
    }

    // =========================================================================
    // Bridge duplication helpers
    // =========================================================================

    /**
     * Find facts consumed by cluster rules but not produced by any rule within the
     * cluster.
     */
    private Set<String> findMissingInputFacts(Set<String> clusterRuleNames,
            Map<String, RuleMeta> ruleMetaMap) {
        Set<String> produced = new LinkedHashSet<>();
        Set<String> consumed = new LinkedHashSet<>();
        for (String ruleName : clusterRuleNames) {
            RuleMeta rm = ruleMetaMap.get(ruleName);
            if (rm != null) {
                consumed.addAll(rm.getInputs());
                produced.addAll(rm.getOutputs());
            }
        }
        consumed.removeAll(produced);
        // Remove "primitive" fact types that are always available (bootstrap/external)
        consumed.removeAll(Set.of("RiskConfig", "MarketEvent"));
        return consumed;
    }

    /**
     * Find all rules that produce (insert/modify) a given fact type.
     */
    private List<String> findProducersOf(String factType, Map<String, RuleMeta> ruleMetaMap) {
        List<String> producers = new ArrayList<>();
        for (Map.Entry<String, RuleMeta> entry : ruleMetaMap.entrySet()) {
            if (entry.getValue().getOutputs().contains(factType)) {
                producers.add(entry.getKey());
            }
        }
        return producers;
    }

    /**
     * Check if a fact type is produced by any rule already in the cluster.
     */
    private boolean isProducedWithinCluster(String factType, Set<String> clusterRuleNames,
            Map<String, RuleMeta> ruleMetaMap) {
        for (String ruleName : clusterRuleNames) {
            RuleMeta rm = ruleMetaMap.get(ruleName);
            if (rm != null && rm.getOutputs().contains(factType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convenience method: build ClusterPlan from DRL classpath resource and .ftree
     * classpath resource.
     *
     * @param drlContent       the full DRL string
     * @param ftreeInputStream input stream of the .ftree file
     * @return the ClusterPlan
     * @throws DroolsParserException if DRL parsing fails
     * @throws IOException           if .ftree reading fails
     */
    public static PartitionPlan.ClusterPlan buildClusterPlanFromResources(
            String drlContent, java.io.InputStream ftreeInputStream)
            throws DroolsParserException, IOException {
        Map<String, Integer> clusters = FtreeParser.parse(ftreeInputStream);
        ClusterPartitioner partitioner = new ClusterPartitioner(drlContent, clusters);
        return partitioner.buildSelfContainedClusters();
    }
}
