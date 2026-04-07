package org.kie.benchmark.binance.parallel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates per-cluster DRL strings by extracting specific rules from the monolithic taxonomy.drl.
 * Uses hardcoded cluster rule assignments based on fact-type affinity analysis.
 */
public class ClusterDrlGenerator {

    // 10 Generic validation/cleanup rules present in ALL clusters
    private static final List<String> GENERIC_RULES = List.of(
            "A01_MissingRequiredFields",
            "A02_InvalidNumerics",
            "A03_TimestampSkewBound",
            "A04_SymbolAllowlist",
            "A06_PriceQtyPrecisionBounds",
            "A07_DecodeErrorsQuarantine",
            "A08_UnexpectedMessageType",
            "B10_ReconnectStorm",
            "B16_LateEventRateHigh",
            "CLEANUP_RetractProcessedEvent"
    );

    // C1: Feed Health & Mode Transitions (26 rules)
    private static final List<String> C1_RULES = List.of(
            "B12_TradeActiveBookSilent",
            "UPD_LastSeen_Trade",
            "D32_TradesWhileBookStale",
            "UPD_LastSeen_Depth",
            "RECOVERY_ExitThrottledToNormal",
            "B13_BookActiveTradeSilent",
            "I68_EnterThrottled_OnDegraded",
            "C25_BookAgeStale",
            "B14_StaleMark",
            "UPD_LastSeen_Mark",
            "G59_MarkStaleButMarketActive",
            "BOOTSTRAP_ModeState",
            "BOOTSTRAP_FeedHealth",
            "A05_MonotonicPerStreamDetect",
            "B09_HeartbeatMissing",
            "B11_StreamSilenceButOpen",
            "B15_StaleIndex",
            "B17_OutOfOrderBurst",
            "B18_PersistentBookGaps",
            "C26_BookSequenceDiscontinuityUnrecovered",
            "D29_TradeTimestampRegression",
            "UPD_LastSeen_Heartbeat",
            "UPD_LastSeen_Index",
            "G58_IndexStaleButMarkMoving",
            "I70_EnterHalted_KillSwitchLatch",
            "RECOVERY_ExitSafeToThrottled"
    );

    // C2: Market Microstructure (Depth/Spread/Vol/TradeAlpha) (52 rules)
    private static final List<String> C2_RULES = List.of(
            "K77_Beta_AssessMicroVolRisk",
            "E33_SpreadComputeAndTier_Update",
            "K78_Beta_EmitMicroVolSignal",
            "E34_DepthTiering",
            "E41_LiquidityStressCombine",
            "E36_SpreadBlowout",
            "BOOTSTRAP_MicroVolatilityRisk",
            "E35_DepthCollapse",
            "K76_Beta_SpreadVelocity",
            "DERIVE_BestBidAsk_Update",
            "K75_Alpha_DepthUpdate",
            "CLEANUP_RetractDepthUpdateTick",
            "C21_TopJumpNoTrades",
            "BOOTSTRAP_SpreadVelocityState",
            "L81_Beta_AssessDislocation",
            "L82_Beta_EmitDislocationSignal",
            "F43_VolTiering",
            "BOOTSTRAP_DislocationEscalation",
            "L80_Beta_MarkDivergence",
            "L79_Alpha_MarkPriceUpdate",
            "CLEANUP_RetractMarkPriceTick",
            "BOOTSTRAP_MarkDivergencePulsar",
            "BOOTSTRAP_VolState",
            "F52_RegimeNormalizationEligibility",
            "E33_SpreadComputeAndTier_New",
            "DERIVE_BestBidAsk_New",
            "BOOTSTRAP_DepthState",
            "E37_PersistentThinLiquidity",
            "J71_Alpha_SignificantTrade",
            "CLEANUP_RetractSignificantTrade",
            "C19_CrossedBook",
            "C20_NegativeOrZeroSpreadPersistent",
            "D27_TradePriceOutOfBandVsMid",
            "D28_TradeSizeOutlier",
            "E38_ImbalanceComputeTier_New",
            "E38_ImbalanceComputeTier_Update",
            "E39_ImbalancePersistence",
            "E40_ImbalanceFlipFlop",
            "F47_VolSpike",
            "F48_VolPersistenceHigh",
            "F50_BookDrivenMove",
            "F51_RegimeShiftToSafe",
            "G53_MarkIndexDivergence_New",
            "G53_MarkIndexDivergence_Update",
            "G54_ComputeMarkMidDivergence",
            "G55_DislocationPersistence",
            "G56_DislocationPlusThinBook",
            "G60_SuddenDivergenceReversalFlag",
            "BOOTSTRAP_TradeSweepImpact",
            "BOOTSTRAP_MicrostructureStress",
            "J72_Beta_ComputeSweepImpact",
            "J73_Beta_AssessMicrostructureStress",
            "J74_Beta_EmitStressSignal"
    );

    // C3: Liquidation Monitoring (6 rules)
    private static final List<String> C3_RULES = List.of(
            "BOOTSTRAP_LiquidationStats",
            "UPD_LiqCount10s",
            "H61_LiqTiering",
            "H62_LiqBurstJump",
            "H66_CascadePersistenceEscalate",
            "H67_CascadeCooldownEligibility"
    );

    // C4: Trade Rate (3 rules)
    private static final List<String> C4_RULES = List.of(
            "BOOTSTRAP_TradeStats",
            "UPD_TradeRate1s",
            "D30_TradeRateTiering"
    );

    /**
     * Generates a map of clusterId -> DRL string.
     */
    public static Map<Integer, String> generateClusterDrls(String fullDrl) {
        Map<Integer, String> clusterDrls = new HashMap<>();

        clusterDrls.put(1, DrlSplitter.buildDrlForRules(fullDrl, combine(C1_RULES, GENERIC_RULES)));
        clusterDrls.put(2, DrlSplitter.buildDrlForRules(fullDrl, combine(C2_RULES, GENERIC_RULES)));
        clusterDrls.put(3, DrlSplitter.buildDrlForRules(fullDrl, combine(C3_RULES, GENERIC_RULES)));
        clusterDrls.put(4, DrlSplitter.buildDrlForRules(fullDrl, combine(C4_RULES, GENERIC_RULES)));

        return clusterDrls;
    }

    private static List<String> combine(List<String> clusterRules, List<String> genericRules) {
        java.util.List<String> combined = new java.util.ArrayList<>(clusterRules);
        combined.addAll(genericRules);
        return combined;
    }
}
