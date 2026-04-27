import re

# Data from opensky_cluster_dependency_analysis.md

rules_monolith = [
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
]

rules_c5 = [
    "R001_MissingPositionFlag", "R002_StalePositionFlag", "R004_BadAltitudeFlag", "R005_BadVelocityFlag", 
    "R009_FilterTracksMissingPositionFromPairing", "R010_FilterStalePositionFromPairing", "R011_FilterBadVelocityFromPairing", "R012_FilterBadAltitudeFromPairing"
]

rules_c7 = [
    "R003_StaleContactFlag", "R013_FilterStaleContactFromPairing"
]

rules_c9 = [
    "R021_DeltaVelocityOver10s", "R022_SuddenSpeedJumpAudit", "R055_HighAccelerationAudit"
]

out = ["*Nodes"]
idx = 1
for r in rules_monolith:
    out.append(f"1:{idx} 1.0 \"{r}\" 1")
    idx += 1

idx = 1
for r in rules_c5:
    out.append(f"5:{idx} 1.0 \"{r}\" 5")
    idx += 1

idx = 1
for r in rules_c7:
    out.append(f"7:{idx} 1.0 \"{r}\" 7")
    idx += 1
    
idx = 1
for r in rules_c9:
    out.append(f"9:{idx} 1.0 \"{r}\" 9")
    idx += 1

with open("D:\\projects\\incubator-kie-benchmarks\\drools-benchmarks-parent\\drools-benchmarks-opensky-airTrafficking\\src\\main\\resources\\rule_graph_empirical.ftree", "w") as f:
    f.write("\n".join(out))
