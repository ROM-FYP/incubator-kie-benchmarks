package org.kie.benchmark.binance.parallel;

import org.kie.benchmark.binance.analysis.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes inter-cluster dependencies using the NEW 5-cluster Infomap partition
 * (I69 removed, re-run 2026-04-06).
 * 
 * Runs TWO analyses:
 *   1. ALL rules (including bootstraps)
 *   2. RUNTIME-ONLY rules (bootstraps stripped)
 */
public class CrossClusterDependencyAnalyzer {

    // Cluster names
    static final Map<Integer, String> CLUSTER_NAMES = Map.of(
        1, "Feed Health & Mode Transitions",
        2, "Depth / Spread / Micro-Volatility",
        3, "Trade Alpha",
        4, "Liquidation Monitoring",
        5, "Trade Rate"
    );

    public static void main(String[] args) throws Exception {
        String drlPath = "src/main/resources/rules/taxonomy.drl";
        String drlContent = new String(Files.readAllBytes(Paths.get(drlPath)), StandardCharsets.UTF_8);
        
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> allRules = parser.parse(drlContent);
        
        // Build rule name -> RuleMeta map
        Map<String, RuleMeta> ruleMetaMap = new LinkedHashMap<>();
        for (RuleMeta rm : allRules) ruleMetaMap.put(rm.getRuleName(), rm);

        // New 5-cluster assignments from verified Infomap re-run (I69 removed)
        Map<String, Integer> ruleToCluster = new LinkedHashMap<>();
        
        // Cluster 1: Feed Health & Mode Transitions (11 rules)
        for (String r : List.of(
            "B12_TradeActiveBookSilent", "UPD_LastSeen_Trade", "D32_TradesWhileBookStale",
            "UPD_LastSeen_Depth", "RECOVERY_ExitThrottledToNormal", "B13_BookActiveTradeSilent",
            "I68_EnterThrottled_OnDegraded", "C25_BookAgeStale",
            "B14_StaleMark", "UPD_LastSeen_Mark", "G59_MarkStaleButMarketActive"
        )) ruleToCluster.put(r, 1);
        
        // Cluster 2: Depth/Spread/MicroVol (28 rules)
        for (String r : List.of(
            "K77_Beta_AssessMicroVolRisk", "E33_SpreadComputeAndTier_Update",
            "K78_Beta_EmitMicroVolSignal", "E34_DepthTiering",
            "E41_LiquidityStressCombine", "E36_SpreadBlowout",
            "BOOTSTRAP_MicroVolatilityRisk", "E35_DepthCollapse",
            "K76_Beta_SpreadVelocity", "DERIVE_BestBidAsk_Update",
            "K75_Alpha_DepthUpdate", "CLEANUP_RetractDepthUpdateTick",
            "C21_TopJumpNoTrades", "BOOTSTRAP_SpreadVelocityState",
            "L81_Beta_AssessDislocation", "L82_Beta_EmitDislocationSignal",
            "F43_VolTiering", "BOOTSTRAP_DislocationEscalation",
            "L80_Beta_MarkDivergence", "L79_Alpha_MarkPriceUpdate",
            "CLEANUP_RetractMarkPriceTick", "BOOTSTRAP_MarkDivergencePulsar",
            "BOOTSTRAP_VolState", "F52_RegimeNormalizationEligibility",
            "E33_SpreadComputeAndTier_New", "DERIVE_BestBidAsk_New",
            "BOOTSTRAP_DepthState", "E37_PersistentThinLiquidity"
        )) ruleToCluster.put(r, 2);
        
        // Cluster 3: Trade Alpha (2 rules)
        for (String r : List.of(
            "CLEANUP_RetractSignificantTrade", "J71_Alpha_SignificantTrade"
        )) ruleToCluster.put(r, 3);
        
        // Cluster 4: Liquidation Monitoring (3 rules)
        for (String r : List.of(
            "BOOTSTRAP_LiquidationStats", "H61_LiqTiering", "H67_CascadeCooldownEligibility"
        )) ruleToCluster.put(r, 4);
        
        // Cluster 5: Trade Rate (2 rules)
        for (String r : List.of(
            "BOOTSTRAP_TradeStats", "D30_TradeRateTiering"
        )) ruleToCluster.put(r, 5);

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  INTER-CLUSTER DEPENDENCY ANALYSIS — NEW 5-CLUSTER PARTITION ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        // --- ANALYSIS 1: ALL RULES (including bootstraps) ---
        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  ANALYSIS 1: ALL RULES (including bootstraps)            │");
        System.out.println("└──────────────────────────────────────────────────────────┘\n");
        runAnalysis(ruleToCluster, ruleMetaMap, allRules, false);

        // --- ANALYSIS 2: RUNTIME ONLY (bootstraps stripped) ---
        System.out.println("\n\n┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  ANALYSIS 2: RUNTIME ONLY (bootstraps stripped)           │");
        System.out.println("└──────────────────────────────────────────────────────────┘\n");
        runAnalysis(ruleToCluster, ruleMetaMap, allRules, true);
    }

    static void runAnalysis(Map<String, Integer> ruleToCluster, Map<String, RuleMeta> ruleMetaMap, 
                            List<RuleMeta> allRules, boolean stripBootstraps) {
        
        // Group rules by cluster, optionally filtering bootstraps
        Map<Integer, List<String>> clusterRules = new TreeMap<>();
        Set<String> excluded = new TreeSet<>();
        
        for (var entry : ruleToCluster.entrySet()) {
            String ruleName = entry.getKey();
            int clusterId = entry.getValue();
            if (stripBootstraps && ruleName.startsWith("BOOTSTRAP_")) {
                excluded.add(ruleName);
                continue;
            }
            clusterRules.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(ruleName);
        }
        
        if (stripBootstraps) {
            System.out.println("Stripped " + excluded.size() + " bootstrap rules: " + excluded + "\n");
        }

        // Print per-cluster summary
        for (var entry : clusterRules.entrySet()) {
            int cid = entry.getKey();
            List<String> rules = entry.getValue();
            Set<String> inputs = new TreeSet<>();
            Set<String> outputs = new TreeSet<>();
            for (String rn : rules) {
                RuleMeta rm = ruleMetaMap.get(rn);
                if (rm != null) { inputs.addAll(rm.getInputs()); outputs.addAll(rm.getOutputs()); }
            }
            System.out.println("Cluster " + cid + " (" + CLUSTER_NAMES.get(cid) + ") — " + rules.size() + " rules");
            System.out.println("  Rules: " + rules);
            System.out.println("  IN:  " + inputs);
            System.out.println("  OUT: " + outputs);
            System.out.println();
        }

        // Build per-cluster I/O
        Map<Integer, Set<String>> outputSets = new TreeMap<>();
        Map<Integer, Set<String>> inputSets = new TreeMap<>();
        for (var entry : clusterRules.entrySet()) {
            Set<String> ins = new TreeSet<>(), outs = new TreeSet<>();
            for (String rn : entry.getValue()) {
                RuleMeta rm = ruleMetaMap.get(rn);
                if (rm != null) { ins.addAll(rm.getInputs()); outs.addAll(rm.getOutputs()); }
            }
            inputSets.put(entry.getKey(), ins);
            outputSets.put(entry.getKey(), outs);
        }

        // Inter-cluster dependency check
        System.out.println("=== INTER-CLUSTER DEPENDENCIES ===\n");
        boolean anyDep = false;
        for (var consumer : inputSets.entrySet()) {
            for (var producer : outputSets.entrySet()) {
                if (consumer.getKey().equals(producer.getKey())) continue;
                Set<String> shared = new TreeSet<>(consumer.getValue());
                shared.retainAll(producer.getValue());
                // Ignore external facts
                shared.removeAll(Set.of("MarketEvent", "RiskConfig", "RiskSignal"));
                if (!shared.isEmpty()) {
                    anyDep = true;
                    System.out.println("  Cluster " + consumer.getKey() + " ← Cluster " + producer.getKey() 
                        + " via: " + shared);
                    for (String fact : shared) {
                        List<String> producers = clusterRules.get(producer.getKey()).stream()
                            .filter(rn -> ruleMetaMap.get(rn) != null && ruleMetaMap.get(rn).getOutputs().contains(fact))
                            .collect(Collectors.toList());
                        List<String> consumers = clusterRules.get(consumer.getKey()).stream()
                            .filter(rn -> ruleMetaMap.get(rn) != null && ruleMetaMap.get(rn).getInputs().contains(fact))
                            .collect(Collectors.toList());
                        System.out.println("      " + fact + ": " + producers + " → " + consumers);
                    }
                }
            }
        }
        if (!anyDep) {
            System.out.println("  ✅ ZERO inter-cluster dependencies (excluding MarketEvent/RiskConfig/RiskSignal)");
        }

        // Self-containment check
        System.out.println("\n=== SELF-CONTAINMENT CHECK ===\n");
        Set<String> EXTERNAL = Set.of("MarketEvent", "RiskConfig", "RiskSignal");
        for (var entry : clusterRules.entrySet()) {
            int cid = entry.getKey();
            Set<String> ins = inputSets.get(cid);
            Set<String> outs = outputSets.get(cid);
            Set<String> unmet = new TreeSet<>(ins);
            unmet.removeAll(outs);
            unmet.removeAll(EXTERNAL);
            if (unmet.isEmpty()) {
                System.out.println("  Cluster " + cid + " (" + CLUSTER_NAMES.get(cid) + "): ✅ Self-contained");
            } else {
                System.out.println("  Cluster " + cid + " (" + CLUSTER_NAMES.get(cid) + "): ❌ Missing producers for: " + unmet);
            }
        }

        // Fallback analysis
        System.out.println("\n=== FALLBACK (UNCLUSTERED) DEPENDENCIES ===\n");
        Set<String> clustered = new HashSet<>(ruleToCluster.keySet());
        if (stripBootstraps) clustered.removeAll(excluded);
        
        List<String> fallbackRules = allRules.stream()
            .map(RuleMeta::getRuleName)
            .filter(n -> !clustered.contains(n))
            .filter(n -> !stripBootstraps || !n.startsWith("BOOTSTRAP_"))
            .collect(Collectors.toList());
        
        Set<String> fbIns = new TreeSet<>(), fbOuts = new TreeSet<>();
        for (String rn : fallbackRules) {
            RuleMeta rm = ruleMetaMap.get(rn);
            if (rm != null) { fbIns.addAll(rm.getInputs()); fbOuts.addAll(rm.getOutputs()); }
        }
        
        System.out.println("Fallback rules: " + fallbackRules.size());
        
        for (var prod : outputSets.entrySet()) {
            Set<String> shared = new TreeSet<>(fbIns);
            shared.retainAll(prod.getValue());
            shared.removeAll(EXTERNAL);
            if (!shared.isEmpty()) {
                System.out.println("  Fallback ← Cluster " + prod.getKey() + " via: " + shared);
            }
        }
        
        for (var cons : inputSets.entrySet()) {
            Set<String> shared = new TreeSet<>(cons.getValue());
            shared.retainAll(fbOuts);
            shared.removeAll(EXTERNAL);
            if (!shared.isEmpty()) {
                System.out.println("  Cluster " + cons.getKey() + " ← Fallback via: " + shared);
            }
        }
    }
}
