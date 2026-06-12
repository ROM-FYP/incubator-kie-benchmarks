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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates per-cluster DRL strings for the 3-cluster RIPE RIS parallel architecture.
 *
 * <p>Cluster assignment derived from Infomap community detection (undirected mode,
 * 6 raw top-level modules) on the runtime causal graph of the 79-rule BGP/RFC 4271
 * rule base, followed by cross-cluster dependency analysis and self-sufficiency
 * resolution via bridge-rule duplication.
 *
 * <h3>Final clusters (after Infomap + dependency resolution)</h3>
 * <ul>
 *   <li><b>C1 — General / Non-UPDATE</b> (19 rules, all native):
 *       {@code RisMessage} normalization → {@code BgpUpdateFact} creation →
 *       header-level field validation (peer ASN, peer IP, payload type flags,
 *       community, aggregator, burst accumulation).
 *       Self-sufficient: only consumes {@code RisMessage} (external) and produces
 *       only terminal facts ({@code RouteAnomaly}, {@code RouteValidationResult},
 *       {@code PeerSessionFact}, {@code CommunityFact}, …).
 *       Receives events with <em>no announcement and no withdrawal payload</em>.</li>
 *
 *   <li><b>C2 — Announcement Pipeline</b> (68 rules: 3 duplicated + 65 native):
 *       Full announcement processing chain — path/origin/MED/prefix/next-hop
 *       extraction → attribute validation → route candidate creation → scoring →
 *       route decision → RIB event generation → aggregation hints → burst detection.
 *       Merges Infomap clusters IM-3, IM-5, IM-6, the announcement portion of IM-1,
 *       and announcement-side unclustered rules.
 *       Duplicates rules 001, 002, 003 to bootstrap {@code BgpUpdateFact} locally.
 *       Receives events with a non-empty {@code announcements} list
 *       (including mixed announcement + withdrawal events).</li>
 *
 *   <li><b>C3 — Withdrawal Pipeline</b> (27 rules: 4 duplicated + 23 native):
 *       Withdrawal extraction → prefix extraction/validation → RIB withdrawal
 *       event generation → temporal cross-type pattern detection (rules 078/079).
 *       Absorbs Infomap cluster IM-4 and withdrawal-side unclustered rules.
 *       Duplicates rules 001, 002, 003 for {@code BgpUpdateFact} bootstrap and
 *       rule 019 to produce {@code RouteAnnouncement} locally (required by
 *       rules 078 and 079 which join {@code RouteWithdrawal} with
 *       {@code RouteAnnouncement}).
 *       Receives events with a non-empty {@code withdrawals} list
 *       AND an empty/null {@code announcements} list.</li>
 * </ul>
 *
 * <h3>Routing</h3>
 * <p>See {@link RipeRisEventRouter} — single-target routing:
 * each {@code RisMessage} goes to exactly one cluster.
 */
public class RipeRisClusterDrlGenerator {

    private static final int CLUSTER_COUNT = 3;

    // =========================================================================
    // C1: General / Non-UPDATE  (19 rules, all native)
    //
    // Consumes: RisMessage (external)
    // Produces: NormalizedRisMessage, BgpUpdateFact, PeerSessionFact,
    //           CollectorFact, TimeBucketFact, CommunityFact, AggregatorFact,
    //           RouteAnomaly (terminal), RouteValidationResult (terminal)
    // =========================================================================
    private static final List<String> C1_RULES = List.of(
            "001_normalize_ris_message",
            "002_detect_non_ris_envelope",
            "003_create_bgp_update_fact",
            "004_detect_non_update_message",
            "005_extract_peer_session",
            "006_extract_collector",
            "007_create_time_bucket",
            "008_detect_missing_peer_asn",
            "009_detect_missing_peer_ip",
            "010_detect_update_without_route_payload",
            "011_detect_update_with_announcement_payload",
            "012_detect_update_with_withdrawal_payload",
            "013_detect_mixed_announcement_and_withdrawal",
            "014_detect_missing_path_on_announcement_update",
            "015_detect_missing_origin_on_announcement_update",
            "016_detect_community_presence",
            "017_detect_empty_community",
            "018_detect_aggregator_presence",
            "076_accumulate_peer_update_burst"
    );

    // =========================================================================
    // C2: Announcement Pipeline  (3 duplicated + 65 native = 68 rules)
    //
    // Consumes: RisMessage → (001→002→003) → BgpUpdateFact → announcement chain
    // Produces: RouteAnnouncement, BgpPath, OriginFact, MedFact, PrefixFact,
    //           NextHopFact, RouteCandidate, RouteScore, RouteDecision,
    //           RibEvent, AggregationHint + terminal facts
    // =========================================================================

    /** Bootstrap rules duplicated into C2 to produce BgpUpdateFact locally. */
    private static final List<String> C2_DUPLICATED = List.of(
            "001_normalize_ris_message",
            "002_detect_non_ris_envelope",
            "003_create_bgp_update_fact"
    );

    /** Native rules belonging exclusively to C2. */
    private static final List<String> C2_NATIVE = List.of(
            // Header-level validation (also in C1, duplicated here so C2 is self-sufficient)
            "005_extract_peer_session",
            "006_extract_collector",
            "007_create_time_bucket",
            "008_detect_missing_peer_asn",
            "009_detect_missing_peer_ip",
            "010_detect_update_without_route_payload",
            "011_detect_update_with_announcement_payload",
            "012_detect_update_with_withdrawal_payload",
            "013_detect_mixed_announcement_and_withdrawal",
            "014_detect_missing_path_on_announcement_update",
            "015_detect_missing_origin_on_announcement_update",
            "016_detect_community_presence",
            "017_detect_empty_community",
            "018_detect_aggregator_presence",
            // Announcement extraction (IM-3 anchor)
            "019_extract_route_announcements",
            // Path / Origin / MED / Prefix / NextHop extraction (IM-5 core + IM-2)
            "021_extract_bgp_path_from_announcement",
            "022_extract_origin_from_announcement",
            "023_extract_med_from_announcement",
            "024_extract_prefix_from_announcement",
            "025_extract_next_hop_from_announcement",
            // Announcement anomaly detection (unclustered)
            "027_detect_announcement_with_missing_next_hop",
            "028_detect_announcement_with_missing_prefix",
            // AS-PATH analysis (IM-3)
            "029_validate_as_path_present",
            "030_detect_empty_as_path",
            "031_detect_as_set_in_path",
            "032_detect_duplicate_asn_in_path",
            "033_detect_as_path_prepending",
            "034_detect_private_asn_in_path",
            "035_validate_first_as_matches_peer_asn",
            "036_detect_first_as_peer_mismatch",
            // Origin validation (IM-3)
            "037_validate_origin_attribute",
            "038_detect_invalid_origin_attribute",
            // MED validation (IM-1 / unclustered)
            "039_validate_med_presence",
            "040_detect_negative_med",
            // Announcement prefix validation (IM-1 / unclustered)
            "041_validate_prefix_syntax_for_announcement",
            "042_detect_invalid_announcement_prefix",
            // Next-hop validation (IM-1 / unclustered)
            "045_validate_next_hop_syntax",
            "046_detect_invalid_next_hop",
            "047_detect_next_hop_equal_peer",
            "048_detect_multi_value_next_hop",
            // Prefix specificity observation (IM-1 / unclustered)
            "049_detect_ipv4_more_specific_prefix",
            "050_detect_ipv6_more_specific_prefix",
            // Route shape validation (IM-3 → feeds candidate creation)
            "051_validate_complete_route_shape",
            "052_detect_incomplete_route_shape",
            // Route candidate creation (IM-5)
            "053_create_feasible_route_candidate",
            "054_create_infeasible_route_candidate",
            // Scoring (IM-6)
            "055_score_feasible_candidate",
            "056_detect_infeasible_candidate",
            // Candidate observation / accumulation (IM-1 / IM-5)
            "057_accumulate_long_as_path_pressure",
            "058_accumulate_incomplete_origin_candidates",
            "059_accumulate_med_candidate_presence",
            "060_accumulate_missing_med_candidates",
            // RIB events (IM-1 / IM-6)
            "061_create_adj_rib_in_event_for_candidate",
            "062_create_loc_rib_event_for_scored_candidate",
            "063_create_adj_rib_out_candidate_event",
            // Route decision (IM-6)
            "066_decide_shorter_as_path",
            "067_decide_lower_origin_rank",
            "068_decide_lower_med_same_neighbor",
            "069_decide_lower_composite_score",
            "070_decide_tie_same_score_lower_message_id",
            "071_create_best_path_rib_event",
            "072_create_advertisable_rib_event",
            // Aggregation hints (IM-5)
            "073_hint_identical_as_path_aggregation_candidate",
            "074_hint_same_origin_aggregation_candidate",
            "075_detect_med_blocks_aggregation",
            // Burst detection (IM-2 / IM-3)
            "076_accumulate_peer_update_burst",
            "077_accumulate_prefix_announcement_burst"
    );

    // =========================================================================
    // C3: Withdrawal Pipeline  (4 duplicated + 23 native = 27 rules)
    //
    // Consumes: RisMessage → (001→002→003) → BgpUpdateFact
    //           → (019) RouteAnnouncement (for temporal rules 078/079)
    //           → (020) RouteWithdrawal → prefix/RIB/temporal pipeline
    // =========================================================================

    /** Bootstrap + bridge rules duplicated into C3. */
    private static final List<String> C3_DUPLICATED = List.of(
            "001_normalize_ris_message",
            "002_detect_non_ris_envelope",
            "003_create_bgp_update_fact",
            "019_extract_route_announcements"   // needed so rules 078/079 can join RouteWithdrawal + RouteAnnouncement
    );

    /** Native rules belonging exclusively to C3. */
    private static final List<String> C3_NATIVE = List.of(
            // Header-level validation (duplicated for self-sufficiency)
            "005_extract_peer_session",
            "006_extract_collector",
            "007_create_time_bucket",
            "008_detect_missing_peer_asn",
            "009_detect_missing_peer_ip",
            "010_detect_update_without_route_payload",
            "011_detect_update_with_announcement_payload",
            "012_detect_update_with_withdrawal_payload",
            "013_detect_mixed_announcement_and_withdrawal",
            "014_detect_missing_path_on_announcement_update",
            "015_detect_missing_origin_on_announcement_update",
            "016_detect_community_presence",
            "017_detect_empty_community",
            "018_detect_aggregator_presence",
            // Withdrawal extraction (IM-4 anchor)
            "020_extract_route_withdrawals",
            // Withdrawal prefix extraction / validation (IM-1 / unclustered)
            "026_extract_prefix_from_withdrawal",
            "043_validate_prefix_syntax_for_withdrawal",
            "044_detect_invalid_withdrawal_prefix",
            // RIB withdrawal events (IM-4)
            "064_create_withdrawal_adj_rib_event",
            "065_create_withdrawal_loc_rib_event",
            // Burst detection
            "076_accumulate_peer_update_burst",
            // Temporal cross-type patterns (IM-4) — require RouteAnnouncement (019 duplicated above)
            "078_detect_withdrawal_after_announcement_same_peer_prefix",
            "079_detect_announcement_after_withdrawal_same_peer_prefix"
    );

    private RipeRisClusterDrlGenerator() {
        // Utility class
    }

    /**
     * Generates a map of clusterId (1-based) → DRL string.
     *
     * @param fullDrl the complete monolithic DRL content
     * @return map with keys 1..{@link #CLUSTER_COUNT}, each a compilable DRL string
     */
    public static Map<Integer, String> generateClusterDrls(String fullDrl) {
        if (fullDrl == null || fullDrl.trim().isEmpty()) {
            throw new IllegalArgumentException("fullDrl must not be null or empty");
        }

        Map<Integer, String> drls = new LinkedHashMap<>();
        drls.put(1, DrlSplitter.buildDrlForRules(fullDrl, C1_RULES));
        drls.put(2, DrlSplitter.buildDrlForRules(fullDrl, combine(C2_DUPLICATED, C2_NATIVE)));
        drls.put(3, DrlSplitter.buildDrlForRules(fullDrl, combine(C3_DUPLICATED, C3_NATIVE)));

        for (Map.Entry<Integer, String> entry : drls.entrySet()) {
            if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                throw new IllegalStateException(
                        "Generated empty DRL for cluster " + entry.getKey());
            }
        }
        return drls;
    }

    /**
     * Returns human-readable names for each cluster (1-based; index 0 is unused).
     */
    public static String[] getClusterNames() {
        return new String[] {
                "",
                "C1 (General/Non-UPDATE)",
                "C2 (Announcement Pipeline)",
                "C3 (Withdrawal Pipeline)"
        };
    }

    public static int getClusterCount() {
        return CLUSTER_COUNT;
    }

    @SafeVarargs
    private static List<String> combine(List<String>... ruleLists) {
        List<String> combined = new ArrayList<>();
        for (List<String> rules : ruleLists) {
            combined.addAll(rules);
        }
        return combined;
    }
}
