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
 * Splits a DRL file into subsets, each containing only the specified rules
 * plus the shared preamble (package, imports, declares, functions).
 */
public class DrlSplitter {

    // Matches 'rule "name"' or "rule 'name'" or 'rule name'
    private static final Pattern RULE_START_PATTERN =
            Pattern.compile("^\\s*rule\\s+[\"']?([^\"']+?)[\"']?\\s*$", Pattern.MULTILINE);

    // Matches 'end' as a standalone keyword
    private static final Pattern RULE_END_PATTERN =
            Pattern.compile("^\\s*end\\s*$", Pattern.MULTILINE);

    private DrlSplitter() {}

    /**
     * Extracts the shared preamble from a DRL file (everything before the first rule).
     * This includes package, imports, declare blocks, and function definitions.
     *
     * @param fullDrl the complete DRL content
     * @return the preamble text
     */
    public static String extractPreamble(String fullDrl) {
        Matcher matcher = RULE_START_PATTERN.matcher(fullDrl);
        if (matcher.find()) {
            return fullDrl.substring(0, matcher.start()).trim();
        }
        return fullDrl; // No rules found, return everything
    }

    /**
     * Extracts specific rules from a DRL file by name.
     *
     * @param fullDrl   the complete DRL content
     * @param ruleNames the set of rule names to extract
     * @return a map of rule name → rule text (including 'rule ...' to 'end')
     */
    public static Map<String, String> extractRulesByName(String fullDrl, Set<String> ruleNames) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = fullDrl.split("\n");
        
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

    /**
     * Generates a complete DRL subset containing only the specified rules
     * plus the shared preamble from the original DRL.
     *
     * <p>The preamble is sanitized: {@code @expires} and {@code @role(event)}
     * annotations are stripped because the Drools CEP expiration machinery
     * @param fullDrl   the complete DRL content
     * @param ruleNames the names of rules to include
     * @return a complete DRL string with preamble + selected rules
     */
    public static String buildDrlForRules(String fullDrl, List<String> ruleNames) {
        String preamble = extractPreamble(fullDrl);
        // Keep @expires/@role for cluster sessions (Executors.newFixedThreadPool is safe).
        // Only strip for ForkJoinPool-based sessions via buildDrlForRulesLegacy().

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

    /**
     * Legacy variant for ForkJoinPool-based parallel sessions.
     * Strips {@code @expires} and {@code @role(event)} to avoid NPE in
     * DefaultAgenda.registerExpiration when running in ForkJoinPool threads.
     * Use {@link #buildDrlForRules} for Executors.newFixedThreadPool sessions.
     */
    public static String buildDrlForRulesLegacy(String fullDrl, List<String> ruleNames) {
        String preamble = extractPreamble(fullDrl);
        preamble = sanitizePreamble(preamble);

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

    /**
     * Strips CEP annotations (@expires, @role) from declare blocks in the preamble.
     * These cause NullPointerException in DefaultAgenda.registerExpiration when
     * sessions run inside ForkJoinPool threads (lazy init of expirationContexts).
     * Since event lifecycle is managed manually, these annotations are unnecessary.
     */
    private static String sanitizePreamble(String preamble) {
        // Remove @expires(...) lines
        preamble = preamble.replaceAll("(?m)^\\s*@expires\\s*\\(.*?\\)\\s*$", "");
        // Remove @role( event ) lines
        preamble = preamble.replaceAll("(?m)^\\s*@role\\s*\\(\\s*event\\s*\\)\\s*$", "");
        // Clean up any resulting blank lines (collapse multiple to one)
        preamble = preamble.replaceAll("\n{3,}", "\n\n");
        return preamble;
    }
}
