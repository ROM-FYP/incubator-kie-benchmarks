package bench.opensky.router;

import bench.opensky.model.Params;
import org.drools.compiler.kie.builder.impl.DrlProject;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.time.SessionPseudoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages multiple KieSessions — one per Infomap cluster.
 *
 * <p>For each cluster, the full DRL is split into a cluster-specific DRL
 * containing only the shared header (package, imports, globals, declares)
 * plus the {@code rule "..." ... end} blocks whose names belong to that cluster.</p>
 */
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    /**
     * Regex that matches a complete rule block:
     * {@code rule "RuleName" ... end}
     * Uses DOTALL so the body can span multiple lines.
     * The 'end' keyword must appear at the start of a line (with optional leading whitespace)
     * to avoid false matches on words like 'depend' inside comments.
     */
    private static final Pattern RULE_BLOCK = Pattern.compile(
            "(rule\\s+\"([^\"]+)\".*?^\\s*end\\s*$)", Pattern.DOTALL | Pattern.MULTILINE);

    private final Map<String, KieSession> sessions = new LinkedHashMap<>();
    private final Map<String, SessionPseudoClock> clocks = new LinkedHashMap<>();

    /**
     * Initialize cluster sessions.
     *
     * @param drlResourceName classpath resource name for the DRL (e.g. "airTraffick_rules.drl")
     * @param ftreeFile       path to the .ftree file for cluster→rule mapping
     */
    public void init(String drlResourceName, String ftreeFile) throws IOException {
        // 1. Load full DRL
        String fullDrl = loadClasspathResource(drlResourceName);

        // 2. Parse cluster→rule mapping
        ClusterRuleMapping mapper = new ClusterRuleMapping();
        Map<String, List<String>> clusterRules = mapper.parse(ftreeFile);
        
        // TWO-SESSION CLUSTER OVERRIDE (Session A = conflict core, Session B = audit/quality)
        if (System.getProperty("opensky.cluster.two.session") != null) {
            LOG.info("SessionManager: Applying two-session cluster split (C1+C4+C6+C7+C8 vs C2+C3+C5+C9)");
            clusterRules.clear();

            // SESSION A: C1 (Pairing) + C4 (Conflict/Streaks) + C6 (Escalation) + C7 (Safety Alert) + C8 (Advisory)
            // R049 duplicated from C3 (PairCandidate quality gate — needs PairCandidate from C1)
            // R080 duplicated from C2 (RecordCPAForAnalysis — needs CpaMetrics from C1)
            // SESSION A: C1, C3, C4, C5, C6, C7, C8, C9
            // Includes R041 duplicated from C2
            List<String> sessionA = Arrays.asList(
                // C1
                "R000_LoadDefaultParams", "R041_AssignGridCell", "R042_PairWithinCell",
                "R043_NoPairIfSameAircraft", "R044_PairInhibitByCell", "R045_PairInhibitByFlight",
                "R046_PairInhibitByFlightB", "R047_PairInhibitByPairKey", "R048_SkipOnGroundPairs",
                "R050_SkipIfVerticalSeparationSufficient",
                "R051_PairDedupGuard", "R052_FinalPairReadyForConflictCheck", "R068b_ComputeCpaMetrics",
                
                // C3
                "R001_MissingPositionFlag", "R002_StalePositionFlag", "R004_BadAltitudeFlag",
                "R005_BadVelocityFlag", "R009_FilterTracksMissingPositionFromPairing",
                "R010_FilterStalePositionFromPairing", "R012_FilterBadAltitudeFromPairing",
                "R049_SkipIfAnyTrackQualityBad", "R049b_RetractConflictIfTrackQualityBad",

                // C5
                "R003_StaleContactFlag", "R013_FilterStaleContactFromPairing",

                // C9
                "R021_DeltaVelocityOver10s", "R022_SuddenSpeedJumpAudit",
                "R055_HighAccelerationAudit", "R011_FilterBadVelocityFromPairing",

                // C4
                "R056_BuildConflictCandidateBasic", "R057_DowngradeIfVerticalSeparationLarge",
                "R059_WarnIfBelowPrototypeRadarMinima", "R061_WarnIfTTCWithinWarningTime",
                "R063_SuppressIfNotClosingAndFar", "R064_SuppressIfAnyOnGround",
                "R065_ConflictCandidateAudit_WARN",
                "R067b_DedupeConflictCandidates_KeepFirst", "R068_ClearOldConflictCandidates",
                "R069_CreateConflictFromCpaMetrics", "R070_SuppressIfNotClosingOrPastCPA",
                "R071_UpgradeToWARN_UsingCPA", "R072_UpgradeToALERT_UsingCPA",
                "R073_InitPairRiskState", "R074a_UpdateStreaks_ALERT", "R074b_UpdateStreaks_WARN",
                "R074c_UpdateStreaks_INFO", "R099_IgnoreNonJustifiedAlertPlaceholder",

                // C6
                "R058_UpgradeIfVeryClose", "R060_AlertIfBelowPrototypeRadarMinima",
                "R062_AlertIfTTCWithinAlertTime", "R066_ConflictCandidateAudit_ALERT",
                "R067a_DedupeConflictCandidates_KeepALERT", "R079_MultiConflictHotspotEscalation",

                // C7
                "R014_StalePosClearsOldAlerts", "R015_StaleContactClearsOldAlerts",
                "R075_RaiseAlertsOnlyAfterPersistence", "R076_Hysteresis_ClearOnlyAfterSafePersistence",
                "R077_InhibitSafetyAlertsByPair", "R082_RaiseSafetyAlertOnALERT",
                "R083_AlertPersistsWhileConditionExists", "R084_AckSilencesAudibleEquivalent",
                "R085_ClearAlertAfterAckAndNoNewConflicts", "R086_InhibitAlertsByFlight",
                "R087_InhibitAlertsByPair", "R090_SafetyAlertPriorityAudit",
                "R092_DoNotSpamNuisanceAlerts", "R095_ClearOldAcks", "R096_RecordEverySafetyAlert",

                // C8
                "R081_RaiseTrafficAdvisoryOnWARN", "R091_TrafficAdvisoryAudit",
                "R093_EscalateFromAdvisoryToSafetyAlert", "R094_ClearOldAdvisories",
                "R097_RecordEveryTrafficAdvisory",
                
                // R080 belongs with conflict handling logically
                "R080_RecordCPAForAnalysis",
                
                // General
                "R078_InhibitMustBeKnown_Audit", "R088_InhibitionMustBeKnown_Audit",
                "R089_StatusWhenNotAvailable", "R100_EndOfCycleMarker"
            );

            // SESSION B: C2 Only
            List<String> sessionB = Arrays.asList(
                // C2 — Data Quality & Grid Auditing
                // R000 duplicated from C1
                "R000_LoadDefaultParams",
                
                // Pure C2 audit rules
                "R006_OnGroundButHighSpeed", "R007_MissingCallsignAudit", "R008_SquawkSpecialPurposeAudit",
                "R016_CategoryUnknownAudit", "R017_NonAdsbPositionSourceAudit",
                "R018_TooFastForAltitudeBandAudit", "R019_NullAltitudesAudit", "R020_TrackDegRangeAudit",
                "R023_SuddenVerticalRateAudit", "R024_SuddenTurnRateApproxAudit", "R025_AltitudeJumpAudit",
                "R026_OnGroundStateFlipAudit", "R027_UnknownVelButMovingAudit",
                "R028_UnknownTrackButMovingAudit", "R029_ClimbWhileOnGroundAudit",
                "R030_BaroGeoDisagreeAudit", "R031_SlowHoveringRotorcraftAudit",
                "R032_UAVAudit", "R033_GliderAudit", "R034_EmergencyVehicleAudit",
                "R035_PointObstacleAudit", "R036_SquawkChangedAudit", "R037_SpiSetAudit",
                "R038_CallsignWhitespaceAudit", "R039_OriginCountryMissingAudit",
                
                // R041 Assigns grid cell
                "R041_AssignGridCell",
                
                "R053_BusyAirspaceCellAudit", "R098_PerformanceCountersPlaceholder"
            );

            clusterRules.put("session_a", sessionA);
            clusterRules.put("session_b", sessionB);
        }

        // EMPIRICAL RESOLUTION OVERRIDE
        if (ftreeFile.contains("rule_graph_full.ftree") && System.getProperty("opensky.empirical.override") != null) {
            LOG.info("SessionManager: Applying 89/13 empirical resolution override");
            clusterRules.clear();
            
            List<String> monolith = Arrays.asList(
                "R000_LoadDefaultParams", "R041_AssignGridCell", "R042_PairWithinCell", 
                "R043_NoPairIfSameAircraft", "R044_PairInhibitByCell", "R045_PairInhibitByFlight",
                "R046_PairInhibitByFlightB", "R047_PairInhibitByPairKey", "R048_SkipOnGroundPairs", 
                "R049b_RetractConflictIfTrackQualityBad", "R051_PairDedupGuard", "R056_BuildConflictCandidateBasic",
                "R057_DowngradeIfVerticalSeparationLarge", "R059_WarnIfBelowPrototypeRadarMinima", "R061_WarnIfTTCWithinWarningTime", 
                "R063_SuppressIfNotClosingAndFar", "R064_SuppressIfAnyOnGround", "R067b_DedupeConflictCandidates_KeepFirst", 
                "R068_ClearOldConflictCandidates", "R068b_ComputeCpaMetrics", "R069_CreateConflictFromCpaMetrics", 
                "R070_SuppressIfNotClosingOrPastCPA", "R072_UpgradeToALERT_UsingCPA", "R073_InitPairRiskState",
                "R074a_UpdateStreaks_ALERT", "R074b_UpdateStreaks_WARN", "R074c_UpdateStreaks_INFO", "R076_Hysteresis_ClearOnlyAfterSafePersistence",
                "R006_OnGroundButHighSpeed", "R007_MissingCallsignAudit", "R008_SquawkSpecialPurposeAudit", "R016_CategoryUnknownAudit", 
                "R017_NonAdsbPositionSourceAudit", "R018_TooFastForAltitudeBandAudit", "R019_NullAltitudesAudit", "R020_TrackDegRangeAudit", 
                "R023_SuddenVerticalRateAudit", "R024_SuddenTurnRateApproxAudit", "R025_AltitudeJumpAudit", "R026_OnGroundStateFlipAudit", 
                "R027_UnknownVelButMovingAudit", "R028_UnknownTrackButMovingAudit", "R029_ClimbWhileOnGroundAudit", "R030_BaroGeoDisagreeAudit", 
                "R031_SlowHoveringRotorcraftAudit", "R032_UAVAudit", "R033_GliderAudit", "R034_EmergencyVehicleAudit", 
                "R035_PointObstacleAudit", "R036_SquawkChangedAudit", "R037_SpiSetAudit", "R038_CallsignWhitespaceAudit", 
                "R039_OriginCountryMissingAudit", "R052_FinalPairReadyForConflictCheck", "R053_BusyAirspaceCellAudit", "R058_UpgradeIfVeryClose", 
                "R060_AlertIfBelowPrototypeRadarMinima", "R062_AlertIfTTCWithinAlertTime", "R066_ConflictCandidateAudit_ALERT", "R067a_DedupeConflictCandidates_KeepALERT", 
                "R078_InhibitMustBeKnown_Audit", "R079_MultiConflictHotspotEscalation", "R080_RecordCPAForAnalysis", "R088_InhibitionMustBeKnown_Audit", 
                "R089_StatusWhenNotAvailable", "R098_PerformanceCountersPlaceholder", "R100_EndOfCycleMarker",
                "R065_ConflictCandidateAudit_WARN", "R071_UpgradeToWARN_UsingCPA", "R099_IgnoreNonJustifiedAlertPlaceholder",
                "R014_StalePosClearsOldAlerts", "R015_StaleContactClearsOldAlerts", "R075_RaiseAlertsOnlyAfterPersistence", "R077_InhibitSafetyAlertsByPair", 
                "R082_RaiseSafetyAlertOnALERT", "R083_AlertPersistsWhileConditionExists", "R084_AckSilencesAudibleEquivalent", "R085_ClearAlertAfterAckAndNoNewConflicts", 
                "R086_InhibitAlertsByFlight", "R087_InhibitAlertsByPair", "R090_SafetyAlertPriorityAudit", "R092_DoNotSpamNuisanceAlerts", 
                "R095_ClearOldAcks", "R096_RecordEverySafetyAlert",
                "R081_RaiseTrafficAdvisoryOnWARN", "R091_TrafficAdvisoryAudit", "R093_EscalateFromAdvisoryToSafetyAlert", "R094_ClearOldAdvisories", "R097_RecordEveryTrafficAdvisory"
            );
            
            List<String> independent = Arrays.asList(
                // C5
                "R001_MissingPositionFlag", "R002_StalePositionFlag", "R004_BadAltitudeFlag", "R005_BadVelocityFlag", 
                "R009_FilterTracksMissingPositionFromPairing", "R010_FilterStalePositionFromPairing", "R011_FilterBadVelocityFromPairing", "R012_FilterBadAltitudeFromPairing",
                // C7
                "R003_StaleContactFlag", "R013_FilterStaleContactFromPairing",
                // C9
                "R021_DeltaVelocityOver10s", "R022_SuddenSpeedJumpAudit", "R055_HighAccelerationAudit"
            );
            
            clusterRules.put("monolith", monolith);
            clusterRules.put("independent", independent);
        }

        LOG.info("SessionManager: {} clusters from ftree", clusterRules.size());

        // 3. Extract the shared DRL header (everything before the first rule block)
        Matcher firstRule = RULE_BLOCK.matcher(fullDrl);
        String header;
        if (firstRule.find()) {
            header = fullDrl.substring(0, firstRule.start()).trim();
        } else {
            throw new RuntimeException("No rule blocks found in DRL");
        }

        // 4. Index all rule blocks by name
        Map<String, String> ruleBlocks = new LinkedHashMap<>();
        Matcher allRules = RULE_BLOCK.matcher(fullDrl);
        while (allRules.find()) {
            String ruleBody = allRules.group(1);
            String ruleName = allRules.group(2);
            ruleBlocks.put(ruleName, ruleBody);
        }
        LOG.info("SessionManager: indexed {} rule blocks from DRL", ruleBlocks.size());

        // 5. Build per-cluster sessions
        KieServices ks = KieServices.Factory.get();

        for (Map.Entry<String, List<String>> entry : clusterRules.entrySet()) {
            String clusterId = entry.getKey();
            List<String> ruleNames = entry.getValue();

            // Assemble cluster-specific DRL
            StringBuilder clusterDrl = new StringBuilder(header);
            clusterDrl.append("\n\n// ---- Cluster: ").append(clusterId).append(" ----\n\n");

            int included = 0;
            for (String ruleName : ruleNames) {
                String block = ruleBlocks.get(ruleName);
                if (block != null) {
                    clusterDrl.append(block).append("\n\n");
                    included++;
                } else {
                    LOG.warn("Rule '{}' (cluster {}) not found in DRL — skipping", ruleName, clusterId);
                }
            }

            if (included == 0) {
                LOG.warn("Cluster {} has no valid rules — skipping session creation", clusterId);
                continue;
            }

            // Build KieBase from cluster DRL
            try {
                KieFileSystem kfs = ks.newKieFileSystem();
                kfs.write("src/main/resources/rules/" + clusterId + ".drl", clusterDrl.toString());

                KieBuilder kb = ks.newKieBuilder(kfs).buildAll(DrlProject.class);
                Results results = kb.getResults();
                if (results.hasMessages(Message.Level.ERROR)) {
                    for (Message msg : results.getMessages(Message.Level.ERROR)) {
                        LOG.error("[{}] DRL build error: {}", clusterId, msg);
                    }
                    LOG.error("Skipping cluster {} due to DRL errors", clusterId);
                    continue;
                }

                KieModule km = kb.getKieModule();
                KieContainer kc = ks.newKieContainer(km.getReleaseId());

                org.kie.api.KieBaseConfiguration kbConfig = ks.newKieBaseConfiguration();
                kbConfig.setOption(EventProcessingOption.STREAM);
                KieBase kieBase = kc.newKieBase(kbConfig);

                KieSessionConfiguration sessionConfig = ks.newKieSessionConfiguration();
                sessionConfig.setOption(ClockTypeOption.PSEUDO);
                KieSession session = kieBase.newKieSession(sessionConfig, null);

                SessionPseudoClock clock = session.getSessionClock();
                session.setGlobal("clock", clock);

                sessions.put(clusterId, session);
                clocks.put(clusterId, clock);

                LOG.info("Cluster {} session created with {} rules", clusterId, included);
            } catch (Exception e) {
                LOG.error("Failed to build session for cluster {}: {}", clusterId, e.getMessage());
            }
        }

        LOG.info("SessionManager initialized: {} active cluster sessions", sessions.size());
    }

    public KieSession getSession(String clusterId) {
        return sessions.get(clusterId);
    }

    public SessionPseudoClock getPseudoClock(String clusterId) {
        return clocks.get(clusterId);
    }

    /**
     * Advance all cluster pseudo-clocks to the given target time.
     */
    public void advanceAllClocks(long targetMs) {
        for (Map.Entry<String, SessionPseudoClock> entry : clocks.entrySet()) {
            SessionPseudoClock clock = entry.getValue();
            long nowMs = clock.getCurrentTime();
            if (targetMs > nowMs) {
                clock.advanceTime(targetMs - nowMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Get all active cluster IDs. */
    public Set<String> getActiveClusterIds() {
        return Collections.unmodifiableSet(sessions.keySet());
    }

    /** Dispose all sessions. */
    public void disposeAll() {
        for (KieSession session : sessions.values()) {
            try {
                session.dispose();
            } catch (Exception e) {
                LOG.warn("Error disposing session: {}", e.getMessage());
            }
        }
        sessions.clear();
        clocks.clear();
    }

    private String loadClasspathResource(String resourceName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
