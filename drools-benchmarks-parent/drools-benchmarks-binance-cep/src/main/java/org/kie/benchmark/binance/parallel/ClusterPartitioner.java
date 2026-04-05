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
 * <p><b>Algorithm:</b></p>
 * <ol>
 *   <li>Parse DRL → List&lt;RuleMeta&gt; (rule name, inputs, outputs)</li>
 *   <li>Stratifier → execution phases (topological sort + SCC)</li>
 *   <li>Merge phases with Infomap clusters → PartitionPlan</li>
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
     * @param drlContent       the full DRL file content
     * @param infomapClusters  map of rule name → cluster ID from .ftree
     */
    public ClusterPartitioner(String drlContent, Map<String, Integer> infomapClusters) {
        this.drlContent = drlContent;
        this.infomapClusters = infomapClusters;
    }

    /**
     * Builds the partition plan by combining structural phases with Infomap clusters.
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
     * Convenience method: build plan from DRL classpath resource and .ftree classpath resource.
     *
     * @param drlContent        the full DRL string
     * @param ftreeInputStream  input stream of the .ftree file
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
}
