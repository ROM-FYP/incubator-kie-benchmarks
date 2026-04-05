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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts {@code eventType} constraints from DRL {@code when} clauses.
 *
 * <p>Used by {@link ClusterPartitioner} to build the LHS routing table
 * that maps each event type to the set of cluster IDs that consume it.</p>
 *
 * <p><b>Logic:</b></p>
 * <ul>
 *   <li>If a rule's LHS contains {@code MarketEvent} with an {@code eventType == "X"} constraint,
 *       it consumes event type X.</li>
 *   <li>If a rule consumes {@code MarketEvent} without an {@code eventType} filter,
 *       it consumes ALL event types.</li>
 *   <li>If a rule does not reference {@code MarketEvent} at all, it returns an empty set
 *       (it's triggered by derived facts, not directly by events).</li>
 * </ul>
 */
public class LhsConstraintParser {

    /** All known MarketEvent.eventType values in the Binance CEP system. */
    public static final Set<String> ALL_EVENT_TYPES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("TRADE", "DEPTH", "MARK", "INDEX", "HEARTBEAT")));

    // Matches: eventType == "TRADE" or eventType=="DEPTH" etc.
    private static final Pattern EVENT_TYPE_PATTERN =
            Pattern.compile("eventType\\s*==\\s*\"(\\w+)\"");

    // Matches: rule "RuleName" ... when ... end
    private static final Pattern RULE_BLOCK_PATTERN =
            Pattern.compile("rule\\s+\"([^\"]+)\".*?when\\s*(.*?)\\s*then", Pattern.DOTALL);

    private LhsConstraintParser() {}

    /**
     * For a given rule, extract which MarketEvent.eventType values
     * would match its LHS pattern.
     *
     * @param ruleName   the rule name to look for
     * @param drlContent the full DRL content
     * @return set of event type strings, ALL_EVENT_TYPES if unfiltered MarketEvent,
     *         or empty set if rule doesn't consume MarketEvent
     */
    public static Set<String> extractEventTypes(String ruleName, String drlContent) {
        String whenClause = extractWhenClause(ruleName, drlContent);
        if (whenClause == null || !whenClause.contains("MarketEvent")) {
            return Collections.emptySet(); // doesn't consume MarketEvent directly
        }
        Matcher m = EVENT_TYPE_PATTERN.matcher(whenClause);
        Set<String> types = new LinkedHashSet<>();
        while (m.find()) {
            types.add(m.group(1));
        }
        // If rule references MarketEvent but has no eventType filter,
        // it matches ALL event types
        return types.isEmpty() ? ALL_EVENT_TYPES : types;
    }

    /**
     * Extract the 'when' clause for a specific rule from DRL content.
     *
     * @param ruleName   the rule name
     * @param drlContent the full DRL string
     * @return the when clause text, or null if not found
     */
    static String extractWhenClause(String ruleName, String drlContent) {
        Matcher m = RULE_BLOCK_PATTERN.matcher(drlContent);
        while (m.find()) {
            if (m.group(1).trim().equals(ruleName)) {
                return m.group(2);
            }
        }
        return null;
    }

    /**
     * Build per-cluster event type sets for a list of clusters.
     *
     * @param clusters   the list of cluster objects
     * @param drlContent the full DRL content
     * @return map of clusterId → set of consumed event types
     */
    public static Map<Integer, Set<String>> buildClusterEventTypes(
            List<PartitionPlan.SelfContainedCluster> clusters, String drlContent) {
        Map<Integer, Set<String>> result = new LinkedHashMap<>();
        for (PartitionPlan.SelfContainedCluster cluster : clusters) {
            Set<String> clusterEventTypes = new LinkedHashSet<>();
            for (String rule : cluster.getAllRules()) {
                Set<String> ruleTypes = extractEventTypes(rule, drlContent);
                clusterEventTypes.addAll(ruleTypes);
            }
            result.put(cluster.getClusterId(), clusterEventTypes);
        }
        return result;
    }

    /**
     * Build the routing table: eventType → list of cluster IDs that should receive it.
     *
     * @param clusterEventTypes map of clusterId → consumed event types
     * @return routing table (eventType → list of cluster IDs)
     */
    public static Map<String, List<Integer>> buildRoutingTable(
            Map<Integer, Set<String>> clusterEventTypes) {
        Map<String, List<Integer>> routingTable = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : clusterEventTypes.entrySet()) {
            int clusterId = entry.getKey();
            for (String eventType : entry.getValue()) {
                routingTable.computeIfAbsent(eventType, k -> new ArrayList<>()).add(clusterId);
            }
        }
        return routingTable;
    }
}
