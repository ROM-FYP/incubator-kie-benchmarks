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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a DRL file into subsets, each containing only the specified rules
 * plus the shared preamble (package, imports, declares, functions).
 */
public class DrlSplitter {

    private static final Pattern RULE_START_PATTERN =
            Pattern.compile("^\\s*rule\\s+[\"']([^\"']+)[\"'].*$", Pattern.MULTILINE);

    private static final Pattern RULE_END_PATTERN =
            Pattern.compile("^\\s*end\\s*$", Pattern.MULTILINE);

    private DrlSplitter() {}

    public static String extractPreamble(String fullDrl) {
        Matcher matcher = RULE_START_PATTERN.matcher(fullDrl);
        if (matcher.find()) {
            return fullDrl.substring(0, matcher.start()).trim();
        }
        return fullDrl;
    }

    public static Map<String, String> extractRulesByName(String fullDrl, Set<String> ruleNames) {
        Map<String, String> result = new LinkedHashMap<>();
        String normalized = fullDrl.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n");

        String currentRuleName = null;
        StringBuilder currentRuleText = null;

        for (String line : lines) {
            Matcher startMatcher = RULE_START_PATTERN.matcher(line);
            if (startMatcher.matches()) {
                String ruleName = startMatcher.group(1).trim();
                if (ruleNames.contains(ruleName)) {
                    currentRuleName = ruleName;
                    currentRuleText = new StringBuilder();
                    currentRuleText.append(line).append("\n");
                }
                continue;
            }

            if (currentRuleName != null) {
                currentRuleText.append(line).append("\n");
                Matcher endMatcher = RULE_END_PATTERN.matcher(line);
                if (endMatcher.matches()) {
                    result.put(currentRuleName, currentRuleText.toString());
                    currentRuleName = null;
                    currentRuleText = null;
                }
            }
        }

        return result;
    }

    public static String buildDrlForRules(String fullDrl, List<String> ruleNames) {
        String preamble = extractPreamble(fullDrl);
        Set<String> ruleNameSet = new LinkedHashSet<>(ruleNames);
        Map<String, String> rules = extractRulesByName(fullDrl, ruleNameSet);

        StringBuilder drl = new StringBuilder();
        drl.append(preamble).append("\n\n");

        for (String ruleName : ruleNames) {
            String ruleText = rules.get(ruleName);
            if (ruleText != null) {
                drl.append(ruleText).append("\n");
            } else {
                System.err.println("[DrlSplitter] WARNING: Rule not found in DRL: " + ruleName);
            }
        }

        return drl.toString();
    }
}
