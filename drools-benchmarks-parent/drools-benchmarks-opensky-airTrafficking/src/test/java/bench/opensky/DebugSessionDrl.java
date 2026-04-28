package bench.opensky;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Debug tool: extracts the session_a rule blocks from the DRL using the same
 * regex as SessionManager and dumps the assembled DRL to stdout for inspection.
 */
public class DebugSessionDrl {

    private static final Pattern RULE_BLOCK = Pattern.compile(
            "(rule\\s+\"([^\"]+)\".*?end)", Pattern.DOTALL);

    public static void main(String[] args) throws Exception {
        String fullDrl;
        try (InputStream is = DebugSessionDrl.class.getClassLoader().getResourceAsStream("airTraffick_rules.drl")) {
            fullDrl = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Index all rule blocks
        Map<String, String> ruleBlocks = new LinkedHashMap<>();
        Matcher m = RULE_BLOCK.matcher(fullDrl);
        while (m.find()) {
            String body = m.group(1);
            String name = m.group(2);
            ruleBlocks.put(name, body);
        }
        System.out.println("Total rule blocks found: " + ruleBlocks.size());

        // Session A rules (same list as SessionManager)
        List<String> sessionA = Arrays.asList(
            "R000_LoadDefaultParams", "R041_AssignGridCell", "R042_PairWithinCell",
            "R043_NoPairIfSameAircraft", "R044_PairInhibitByCell", "R045_PairInhibitByFlight",
            "R046_PairInhibitByFlightB", "R047_PairInhibitByPairKey", "R048_SkipOnGroundPairs",
            "R050_SkipIfVerticalSeparationSufficient",
            "R051_PairDedupGuard", "R052_FinalPairReadyForConflictCheck", "R068b_ComputeCpaMetrics",
            "R049_SkipIfAnyTrackQualityBad",
            "R049b_RetractConflictIfTrackQualityBad",
            "R056_BuildConflictCandidateBasic", "R057_DowngradeIfVerticalSeparationLarge",
            "R059_WarnIfBelowPrototypeRadarMinima", "R061_WarnIfTTCWithinWarningTime",
            "R063_SuppressIfNotClosingAndFar", "R064_SuppressIfAnyOnGround",
            "R065_ConflictCandidateAudit_WARN",
            "R067b_DedupeConflictCandidates_KeepFirst", "R068_ClearOldConflictCandidates",
            "R069_CreateConflictFromCpaMetrics", "R070_SuppressIfNotClosingOrPastCPA",
            "R071_UpgradeToWARN_UsingCPA", "R072_UpgradeToALERT_UsingCPA",
            "R073_InitPairRiskState", "R074a_UpdateStreaks_ALERT", "R074b_UpdateStreaks_WARN",
            "R074c_UpdateStreaks_INFO", "R099_IgnoreNonJustifiedAlertPlaceholder",
            "R058_UpgradeIfVeryClose", "R060_AlertIfBelowPrototypeRadarMinima",
            "R062_AlertIfTTCWithinAlertTime", "R066_ConflictCandidateAudit_ALERT",
            "R067a_DedupeConflictCandidates_KeepALERT", "R079_MultiConflictHotspotEscalation",
            "R014_StalePosClearsOldAlerts", "R015_StaleContactClearsOldAlerts",
            "R075_RaiseAlertsOnlyAfterPersistence", "R076_Hysteresis_ClearOnlyAfterSafePersistence",
            "R077_InhibitSafetyAlertsByPair", "R082_RaiseSafetyAlertOnALERT",
            "R083_AlertPersistsWhileConditionExists", "R084_AckSilencesAudibleEquivalent",
            "R085_ClearAlertAfterAckAndNoNewConflicts", "R086_InhibitAlertsByFlight",
            "R087_InhibitAlertsByPair", "R090_SafetyAlertPriorityAudit",
            "R092_DoNotSpamNuisanceAlerts", "R095_ClearOldAcks", "R096_RecordEverySafetyAlert",
            "R081_RaiseTrafficAdvisoryOnWARN", "R091_TrafficAdvisoryAudit",
            "R093_EscalateFromAdvisoryToSafetyAlert", "R094_ClearOldAdvisories",
            "R097_RecordEveryTrafficAdvisory",
            "R080_RecordCPAForAnalysis"
        );

        // Extract header
        Matcher firstRule = RULE_BLOCK.matcher(fullDrl);
        String header = "";
        if (firstRule.find()) {
            header = fullDrl.substring(0, firstRule.start()).trim();
        }

        // Find the R092 block specifically
        String r092Block = ruleBlocks.get("R092_DoNotSpamNuisanceAlerts");
        if (r092Block != null) {
            System.out.println("\n=== R092 BLOCK (length=" + r092Block.length() + ") ===");
            System.out.println(r092Block);
            System.out.println("=== END R092 BLOCK ===");
        } else {
            System.out.println("R092 NOT FOUND in ruleBlocks!");
        }

        // Check if any rule blocks contain 'end' within comments that could cause early termination
        int missing = 0;
        for (String rn : sessionA) {
            if (!ruleBlocks.containsKey(rn)) {
                System.out.println("MISSING: " + rn);
                missing++;
            }
        }
        System.out.println("Missing rules from session A: " + missing);

        // Now look for any rule block that has the word 'end' inside a comment
        for (Map.Entry<String, String> e : ruleBlocks.entrySet()) {
            String body = e.getValue();
            // Check if there's an 'end' that's NOT the final 'end' keyword
            String withoutLastEnd = body.substring(0, body.lastIndexOf("end"));
            if (withoutLastEnd.matches("(?s).*\\bend\\b.*")) {
                System.out.println("WARNING: Rule '" + e.getKey() + "' has 'end' within body (possible regex truncation)");
                // Show the line containing 'end'
                for (String line : withoutLastEnd.split("\\n")) {
                    if (line.matches(".*\\bend\\b.*")) {
                        System.out.println("  -> " + line.trim());
                    }
                }
            }
        }

        // Dump lines around 600 of session_a.drl
        StringBuilder clusterDrl = new StringBuilder(header);
        clusterDrl.append("\n\n// ---- Cluster: session_a ----\n\n");
        for (String rn : sessionA) {
            String block = ruleBlocks.get(rn);
            if (block != null) {
                clusterDrl.append(block).append("\n\n");
            }
        }
        String[] lines = clusterDrl.toString().split("\\n");
        System.out.println("\nTotal lines in session_a.drl: " + lines.length);
        int start = Math.max(0, 595);
        int end = Math.min(lines.length, 610);
        System.out.println("Lines " + (start+1) + "-" + end + ":");
        for (int i = start; i < end; i++) {
            System.out.println((i+1) + ": " + lines[i]);
        }
    }
}
