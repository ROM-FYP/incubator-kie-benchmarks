package org.kie.benchmark.binance.parallel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates per-cluster DRL strings for the 2-cluster (V3) architecture.
 * 
 * CA = C1 (Feed Health) + C3 (Liquidation) + C4 (Trade Rate)
 * CB = C2 (Market Microstructure)
 */
public class ClusterDrlGeneratorV3 {

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

    // CA: Feed Health (C1) + Liquidation (C3) + Trade Rate (C4)  = 35 rules
    private static final List<String> CA_RULES = List.of(
            // --- C1: Feed Health & Mode Transitions (26 rules) ---
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
            "RECOVERY_ExitSafeToThrottled",
            // --- C3: Liquidation (6 rules) ---
            "BOOTSTRAP_LiquidationStats",
            "UPD_LiqCount10s",
            "H61_LiqTiering",
            "H62_LiqBurstJump",
            "H66_CascadePersistenceEscalate",
            "H67_CascadeCooldownEligibility",
            // --- C4: Trade Rate (3 rules) ---
            "BOOTSTRAP_TradeStats",
            "UPD_TradeRate1s",
            "D30_TradeRateTiering"
    );

    // CB: Market Microstructure (C2) = 52 rules
    private static final List<String> CB_RULES = List.of(
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

    /**
     * Generates a map of clusterId -> DRL string.
     * CA = 1, CB = 2
     */
    public static Map<Integer, String> generateClusterDrls(String fullDrl) {
        Map<Integer, String> clusterDrls = new HashMap<>();

        clusterDrls.put(1, DrlSplitter.buildDrlForRules(fullDrl, combine(CA_RULES, GENERIC_RULES)));
        clusterDrls.put(2, DrlSplitter.buildDrlForRules(fullDrl, combine(CB_RULES, GENERIC_RULES)));

        return clusterDrls;
    }

    private static List<String> combine(List<String> clusterRules, List<String> genericRules) {
        java.util.List<String> combined = new java.util.ArrayList<>(clusterRules);
        combined.addAll(genericRules);
        return combined;
    }
}
