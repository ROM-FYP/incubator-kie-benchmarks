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
package org.kie.benchmark.cep.wikimedia.parallel;

import java.util.*;

/**
 * Generates per-cluster DRL strings for the 4-cluster Wikimedia architecture.
 *
 * <p>Cluster assignment derived from Infomap community detection on the causal
 * trace of 13,914 events (271K trace events, 50 nodes, 88 edges).
 *
 * <ul>
 *   <li>C1 = Minor Edits Pipeline (6 rules + 1 dead-end)
 *   <li>C2 = Bot Pipeline (10 rules)
 *   <li>C3 = Content + Vandalism + Correlations (40 rules + 7 duplicated bot rules = 47)
 *   <li>C4 = Discussion Pipeline (6 rules)
 * </ul>
 *
 * <h3>Cross-cluster dependencies</h3>
 * <p>One dependency: CorrelateHighRiskUser in C3 reads BotHealthCheck produced by C2.
 * Resolved by <b>duplicating 7 bot rules</b> (Bot_Detect through Bot_HealthCheck_*) into C3,
 * so BotHealthCheck is produced locally and CorrelateHighRiskUser fires correctly.</p>
 */
public class WikimediaClusterDrlGenerator {

    // ========================================================================
    // C1: Minor Edits Pipeline
    // ========================================================================
    private static final List<String> C1_RULES = List.of(
            "Minor_Detect",
            "Minor_Classify",
            "Minor_Validate_Trusted",       // ftree dead-end: reads UserActivity (shared)
            "Minor_Validate_Standard",
            "Minor_Accept",
            "Minor_Track",
            "Minor_Complete"
    );

    // ========================================================================
    // C2: Bot Pipeline
    // ========================================================================
    private static final List<String> C2_RULES = List.of(
            "Bot_Detect",
            "Bot_Categorize_Registered",
            "Bot_Categorize_Unverified",
            "Bot_HealthCheck_Registered_RateLimitExceeded",
            "Bot_HealthCheck_Registered_Healthy",
            "Bot_HealthCheck_Unverified_NewBot",
            "Bot_HealthCheck_Unverified_Review",
            "Bot_Metrics",
            "Bot_Report",
            "Bot_Complete"
    );

    // ========================================================================
    // Bot rules duplicated into C3 to resolve BotHealthCheck dependency
    // (CorrelateHighRiskUser needs BotHealthCheck, which is produced by C2)
    // ========================================================================
    private static final List<String> C3_BOT_DUPLICATES = List.of(
            "Bot_Detect",
            "Bot_Categorize_Registered",
            "Bot_Categorize_Unverified",
            "Bot_HealthCheck_Registered_RateLimitExceeded",
            "Bot_HealthCheck_Registered_Healthy",
            "Bot_HealthCheck_Unverified_NewBot",
            "Bot_HealthCheck_Unverified_Review"
    );

    // ========================================================================
    // C3: Content + Vandalism + Correlations
    // ========================================================================
    private static final List<String> C3_RULES = List.of(
            // --- Content rules ---
            "Content_Detect",
            "Content_Review_Standard",
            "Content_Review_Major_New",
            "Content_Review_Major_Experienced",  // ftree dead-end
            "Content_Approve_Major",
            "Content_Approve_NewArticle",
            "Content_Approve_Standard",
            "Content_Index",
            "Content_Cache",
            "Content_Major_Log",
            "Content_Standard_Complete",
            // --- Vandalism rules ---
            "InitializeUserActivity",
            "Vandalism_Detect",
            "Vandalism_Analyze_MassiveDeletion_NewUser",
            "Vandalism_Analyze_MassiveDeletion_TrustedUser",  // ftree dead-end
            "Vandalism_Analyze_MassiveDeletion_RegularUser",
            "Vandalism_Analyze_LargeDeletion_NewUser",
            "Vandalism_Analyze_LargeDeletion_TrustedUser",    // ftree dead-end
            "Vandalism_Analyze_LargeDeletion_RegularUser",
            "Vandalism_Analyze_ModerateDeletion_NewUser",
            "Vandalism_Analyze_ModerateDeletion_TrustedUser", // ftree dead-end
            "Vandalism_Analyze_ModerateDeletion_RegularUser",
            "Vandalism_Verify_HighRisk_HighQualityArticle",   // ftree dead-end
            "Vandalism_Verify_HighRisk_LowQualityArticle",    // ftree dead-end
            "Vandalism_Verify_HighRisk_StandardArticle",
            "Vandalism_Verify_HighRisk_NoArticleData",
            "Vandalism_Verify_MediumRisk_HighQualityArticle", // ftree dead-end
            "Vandalism_Verify_MediumRisk_OtherArticles",
            "Vandalism_Verify_LowRisk",
            "Vandalism_Flag_HighSeverity_HighValue_RepeatOffender", // ftree dead-end
            "Vandalism_Flag_HighSeverity_HighValue_FirstTime",      // ftree dead-end
            "Vandalism_Flag_HighSeverity_Standard_RepeatOffender",
            "Vandalism_Flag_HighSeverity_Standard_FirstTime",
            "Vandalism_Flag_MediumSeverity_HighValue",        // ftree dead-end
            "Vandalism_NoFlag_LowSeverity",
            "Vandalism_Complete",
            "Vandalism_Logged_Complete",
            // --- Correlations ---
            "CorrelateHighRiskUser",
            "CorrelateArticleAttack",
            "BoostVandalismPriorityForAttackedArticles"
    );

    // ========================================================================
    // C4: Discussion Pipeline
    // ========================================================================
    private static final List<String> C4_RULES = List.of(
            "Discussion_Detect",
            "Discussion_Analyze",
            "Discussion_Sentiment",
            "Discussion_Route",
            "Discussion_Notify",
            "Discussion_Complete"
    );

    /**
     * Generates a map of clusterId → DRL string.
     * C1=1, C2=2, C3=3, C4=4
     */
    public static Map<Integer, String> generateClusterDrls(String fullDrl) {
        Map<Integer, String> drls = new HashMap<>();
        drls.put(1, DrlSplitter.buildDrlForRules(fullDrl, C1_RULES));
        drls.put(2, DrlSplitter.buildDrlForRules(fullDrl, C2_RULES));
        drls.put(3, DrlSplitter.buildDrlForRules(fullDrl, combine(C3_RULES, C3_BOT_DUPLICATES)));
        drls.put(4, DrlSplitter.buildDrlForRules(fullDrl, C4_RULES));
        return drls;
    }

    @SafeVarargs
    private static List<String> combine(List<String>... ruleLists) {
        List<String> combined = new java.util.ArrayList<>();
        for (List<String> rules : ruleLists) {
            combined.addAll(rules);
        }
        return combined;
    }

    public static String[] getClusterNames() {
        return new String[] { "", "C1 (Minor)", "C2 (Bot)", "C3 (Content+Vandalism)", "C4 (Discussion)" };
    }

    public static int getClusterCount() {
        return 4;
    }
}
