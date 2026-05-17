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
package org.kie.benchmark.cep.riperis.parallel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates per-cluster DRL strings for the parallel architecture.
 *
 * <p>
 * The previous implementation hardcoded the original 79 rule names. That became
 * fragile after removing ChainStage / BenchmarkMetric rules and rewriting the
 * DRL around real causal data dependencies.
 *
 * <p>
 * This implementation extracts rule names directly from the supplied DRL and
 * partitions them by their numeric prefix:
 *
 * <pre>
 * C1: 001-020  Normalization / UPDATE extraction
 * C2: 021-040  Path, prefix, next-hop, and attribute extraction
 * C3: 041-060  Validation, anomaly, and candidate preparation
 * C4: 061-079  Scoring, RIB events, aggregation, and terminal analysis
 * </pre>
 *
 * <p>
 * If a rule does not begin with a numeric prefix, it is assigned by declaration
 * order as a fallback.
 */
public class RipeRisClusterDrlGenerator {

    private static final int CLUSTER_COUNT = 4;

    private static final Pattern RULE_START_PATTERN =
            Pattern.compile("^\\s*rule\\s+[\"']([^\"']+)[\"'].*$", Pattern.MULTILINE);

    private RipeRisClusterDrlGenerator() {
        // Utility class
    }

    public static Map<Integer, String> generateClusterDrls(String fullDrl) {
        if (fullDrl == null || fullDrl.trim().isEmpty()) {
            throw new IllegalArgumentException("fullDrl must not be null or empty");
        }

        List<String> allRuleNames = extractRuleNames(fullDrl);
        if (allRuleNames.isEmpty()) {
            throw new IllegalArgumentException("No rules were found in the supplied DRL");
        }

        Map<Integer, List<String>> clusterRules = partitionRules(allRuleNames);

        Map<Integer, String> drls = new LinkedHashMap<>();
        for (int clusterId = 1; clusterId <= CLUSTER_COUNT; clusterId++) {
            List<String> selectedRules = clusterRules.getOrDefault(clusterId, Collections.emptyList());
            String clusterDrl = DrlSplitter.buildDrlForRules(fullDrl, selectedRules);

            if (clusterDrl == null || clusterDrl.trim().isEmpty()) {
                throw new IllegalStateException("Generated empty DRL for cluster " + clusterId);
            }

            drls.put(clusterId, clusterDrl);
        }

        return drls;
    }

    private static List<String> extractRuleNames(String fullDrl) {
        List<String> ruleNames = new ArrayList<>();
        Matcher matcher = RULE_START_PATTERN.matcher(fullDrl);

        while (matcher.find()) {
            ruleNames.add(matcher.group(1).trim());
        }

        return ruleNames;
    }

    private static Map<Integer, List<String>> partitionRules(List<String> ruleNames) {
        Map<Integer, List<String>> clusters = new LinkedHashMap<>();
        for (int clusterId = 1; clusterId <= CLUSTER_COUNT; clusterId++) {
            clusters.put(clusterId, new ArrayList<>());
        }

        for (int i = 0; i < ruleNames.size(); i++) {
            String ruleName = ruleNames.get(i);
            int ruleNumber = extractLeadingRuleNumber(ruleName);

            int clusterId;
            if (ruleNumber > 0) {
                clusterId = clusterForRuleNumber(ruleNumber);
            } else {
                clusterId = clusterForDeclarationIndex(i, ruleNames.size());
            }

            clusters.get(clusterId).add(ruleName);
        }

        return clusters;
    }

    private static int extractLeadingRuleNumber(String ruleName) {
        if (ruleName == null || ruleName.length() < 3) {
            return -1;
        }

        String prefix = ruleName.substring(0, Math.min(3, ruleName.length()));
        try {
            return Integer.parseInt(prefix);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static int clusterForRuleNumber(int ruleNumber) {
        if (ruleNumber <= 20) {
            return 1;
        }
        if (ruleNumber <= 40) {
            return 2;
        }
        if (ruleNumber <= 60) {
            return 3;
        }
        return 4;
    }

    private static int clusterForDeclarationIndex(int index, int totalRules) {
        if (totalRules <= 0) {
            return 1;
        }

        int zeroBasedCluster = (int) Math.floor((index * (double) CLUSTER_COUNT) / totalRules);
        return Math.min(CLUSTER_COUNT, zeroBasedCluster + 1);
    }

    public static String[] getClusterNames() {
        return new String[] {
                "",
                "C1 (Normalize/Update)",
                "C2 (Extract/Attributes)",
                "C3 (Validate/Candidate)",
                "C4 (Score/RIB/Terminal)"
        };
    }

    public static int getClusterCount() {
        return CLUSTER_COUNT;
    }
}
