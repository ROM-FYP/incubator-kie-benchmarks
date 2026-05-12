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

import java.util.*;

/**
 * Data model describing how rules are partitioned into sequential phases,
 * each containing parallel session groups (Infomap clusters).
 */
public class PartitionPlan {

    private final List<Phase> phases;

    public PartitionPlan(List<Phase> phases) {
        this.phases = Collections.unmodifiableList(new ArrayList<>(phases));
    }

    public List<Phase> getPhases() {
        return phases;
    }

    public int totalRules() {
        return phases.stream()
                .flatMap(p -> p.getGroups().stream())
                .mapToInt(g -> g.getRuleNames().size())
                .sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PartitionPlan {\n");
        for (Phase phase : phases) {
            sb.append("  Phase ").append(phase.getPhaseNumber())
                    .append(" (").append(phase.getGroups().size()).append(" groups):\n");
            for (SessionGroup g : phase.getGroups()) {
                sb.append("    Cluster ").append(g.getClusterId())
                        .append(": ").append(g.getRuleNames().size()).append(" rules ")
                        .append(g.getRuleNames()).append("\n");
                sb.append("      inputs:  ").append(g.getRequiredInputFacts()).append("\n");
                sb.append("      outputs: ").append(g.getProducedOutputFacts()).append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // =========================================================================
    // Inner classes
    // =========================================================================

    /**
     * A sequential execution phase. All groups within a phase can run in parallel.
     */
    public static class Phase {
        private final int phaseNumber;
        private final List<SessionGroup> groups;

        public Phase(int phaseNumber, List<SessionGroup> groups) {
            this.phaseNumber = phaseNumber;
            this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
        }

        public int getPhaseNumber() {
            return phaseNumber;
        }

        public List<SessionGroup> getGroups() {
            return groups;
        }
    }

    /**
     * A group of rules assigned to one KieSession within a phase.
     */
    public static class SessionGroup {
        private final int clusterId;
        private final int phaseNumber;
        private final List<String> ruleNames;
        private final Set<String> requiredInputFacts;
        private final Set<String> producedOutputFacts;
        private String drlContent;

        public SessionGroup(int clusterId, int phaseNumber, List<String> ruleNames,
                Set<String> requiredInputFacts, Set<String> producedOutputFacts) {
            this.clusterId = clusterId;
            this.phaseNumber = phaseNumber;
            this.ruleNames = Collections.unmodifiableList(new ArrayList<>(ruleNames));
            this.requiredInputFacts = Collections.unmodifiableSet(new LinkedHashSet<>(requiredInputFacts));
            this.producedOutputFacts = Collections.unmodifiableSet(new LinkedHashSet<>(producedOutputFacts));
        }

        public int getClusterId() {
            return clusterId;
        }

        public int getPhaseNumber() {
            return phaseNumber;
        }

        public List<String> getRuleNames() {
            return ruleNames;
        }

        public Set<String> getRequiredInputFacts() {
            return requiredInputFacts;
        }

        public Set<String> getProducedOutputFacts() {
            return producedOutputFacts;
        }

        public String getDrlContent() {
            return drlContent;
        }

        public void setDrlContent(String drlContent) {
            this.drlContent = drlContent;
        }
    }

    // =========================================================================
    // Cluster-level parallel execution data model
    // =========================================================================

    /**
     * A self-contained Infomap cluster with original rules, bridge-duplicated
     * rules,
     * event types consumed, and pre-built DRL content.
     */
    public static class SelfContainedCluster {
        private final int clusterId;
        private final String name;
        private final List<String> originalRules;
        private final List<String> duplicatedRules;
        private final List<String> allRules;
        private final Set<String> consumedEventTypes;
        private final Set<String> inputFacts;
        private final Set<String> outputFacts;
        private String drlContent;

        public SelfContainedCluster(int clusterId, String name,
                List<String> originalRules, List<String> duplicatedRules,
                Set<String> consumedEventTypes,
                Set<String> inputFacts, Set<String> outputFacts) {
            this.clusterId = clusterId;
            this.name = name;
            this.originalRules = Collections.unmodifiableList(new ArrayList<>(originalRules));
            this.duplicatedRules = Collections.unmodifiableList(new ArrayList<>(duplicatedRules));
            List<String> all = new ArrayList<>(originalRules);
            all.addAll(duplicatedRules);
            this.allRules = Collections.unmodifiableList(all);
            this.consumedEventTypes = Collections.unmodifiableSet(new LinkedHashSet<>(consumedEventTypes));
            this.inputFacts = Collections.unmodifiableSet(new LinkedHashSet<>(inputFacts));
            this.outputFacts = Collections.unmodifiableSet(new LinkedHashSet<>(outputFacts));
        }

        public int getClusterId() {
            return clusterId;
        }

        public String getName() {
            return name;
        }

        public List<String> getOriginalRules() {
            return originalRules;
        }

        public List<String> getDuplicatedRules() {
            return duplicatedRules;
        }

        public List<String> getAllRules() {
            return allRules;
        }

        public Set<String> getConsumedEventTypes() {
            return consumedEventTypes;
        }

        public Set<String> getInputFacts() {
            return inputFacts;
        }

        public Set<String> getOutputFacts() {
            return outputFacts;
        }

        public String getDrlContent() {
            return drlContent;
        }

        public void setDrlContent(String drlContent) {
            this.drlContent = drlContent;
        }

        @Override
        public String toString() {
            return "Cluster " + clusterId + " (" + name + "): "
                    + originalRules.size() + " original + "
                    + duplicatedRules.size() + " duplicated = "
                    + allRules.size() + " total, events=" + consumedEventTypes;
        }
    }

    /**
     * Complete cluster-level parallel execution plan: Infomap clusters + fallback +
     * routing table.
     */
    public static class ClusterPlan {
        private final List<SelfContainedCluster> clusters;
        private final SelfContainedCluster fallbackCluster;
        private final Map<String, List<Integer>> routingTable;

        public ClusterPlan(List<SelfContainedCluster> clusters,
                SelfContainedCluster fallbackCluster,
                Map<String, List<Integer>> routingTable) {
            this.clusters = Collections.unmodifiableList(new ArrayList<>(clusters));
            this.fallbackCluster = fallbackCluster;
            this.routingTable = Collections.unmodifiableMap(new LinkedHashMap<>(routingTable));
        }

        public List<SelfContainedCluster> getClusters() {
            return clusters;
        }

        public SelfContainedCluster getFallbackCluster() {
            return fallbackCluster;
        }

        public Map<String, List<Integer>> getRoutingTable() {
            return routingTable;
        }

        public int totalOriginalRules() {
            return clusters.stream().mapToInt(c -> c.getOriginalRules().size()).sum();
        }

        public int totalDuplicatedRules() {
            return clusters.stream().mapToInt(c -> c.getDuplicatedRules().size()).sum();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("ClusterPlan {\n");
            for (SelfContainedCluster c : clusters) {
                sb.append("  ").append(c).append("\n");
            }
            sb.append("  ").append(fallbackCluster).append("\n");
            sb.append("  Routing: ").append(routingTable).append("\n");
            sb.append("}");
            return sb.toString();
        }
    }
}
