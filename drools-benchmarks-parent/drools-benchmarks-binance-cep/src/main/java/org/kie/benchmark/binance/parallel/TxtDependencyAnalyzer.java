package org.kie.benchmark.binance.parallel;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TxtDependencyAnalyzer {

    // Same 5-cluster assignments from Infomap Phase 2
    static final Map<Integer, String> CLUSTER_NAMES = Map.of(
        1, "Feed Health & Mode Transitions",
        2, "Depth / Spread / Micro-Volatility",
        3, "Trade Alpha",
        4, "Liquidation Monitoring",
        5, "Trade Rate"
    );

    public static void main(String[] args) throws Exception {
        String txtPath = "/home/maheshdila/mahesh/research/incubator-kie-drools/taxonomy_drl_dependency_graph.txt";
        List<String> lines = Files.readAllLines(Paths.get(txtPath));

        Map<String, Set<String>> ruleInputs = new HashMap<>();
        Map<String, Set<String>> ruleOutputs = new HashMap<>();

        String currentRule = null;

        // Parse the text file
        for (String line : lines) {
            line = line.trim();
            if (line.matches("\\d+\\.\\s+\\w+")) {
                currentRule = line.substring(line.indexOf('.') + 1).trim();
                ruleInputs.put(currentRule, new HashSet<>());
                ruleOutputs.put(currentRule, new HashSet<>());
            } else if (currentRule != null && line.startsWith("Inputs:")) {
                String[] parts = line.substring(7).split(",");
                for (String p : parts) {
                    p = p.trim();
                    if (!p.isEmpty() && !p.equals("(none)")) ruleInputs.get(currentRule).add(p);
                }
            } else if (currentRule != null && line.startsWith("Outputs:")) {
                String[] parts = line.substring(8).split(",");
                for (String p : parts) {
                    p = p.trim();
                    if (!p.isEmpty() && !p.equals("(none)")) ruleOutputs.get(currentRule).add(p);
                }
            }
        }

        Map<String, Integer> ruleToCluster = new LinkedHashMap<>();
        // Cluster 1
        for (String r : List.of("B12_TradeActiveBookSilent", "UPD_LastSeen_Trade", "D32_TradesWhileBookStale", "UPD_LastSeen_Depth", "RECOVERY_ExitThrottledToNormal", "B13_BookActiveTradeSilent", "I68_EnterThrottled_OnDegraded", "C25_BookAgeStale", "B14_StaleMark", "UPD_LastSeen_Mark", "G59_MarkStaleButMarketActive")) ruleToCluster.put(r, 1);
        // Cluster 2
        for (String r : List.of("K77_Beta_AssessMicroVolRisk", "E33_SpreadComputeAndTier_Update", "K78_Beta_EmitMicroVolSignal", "E34_DepthTiering", "E41_LiquidityStressCombine", "E36_SpreadBlowout", "BOOTSTRAP_MicroVolatilityRisk", "E35_DepthCollapse", "K76_Beta_SpreadVelocity", "DERIVE_BestBidAsk_Update", "K75_Alpha_DepthUpdate", "CLEANUP_RetractDepthUpdateTick", "C21_TopJumpNoTrades", "BOOTSTRAP_SpreadVelocityState", "L81_Beta_AssessDislocation", "L82_Beta_EmitDislocationSignal", "F43_VolTiering", "BOOTSTRAP_DislocationEscalation", "L80_Beta_MarkDivergence", "L79_Alpha_MarkPriceUpdate", "CLEANUP_RetractMarkPriceTick", "BOOTSTRAP_MarkDivergencePulsar", "BOOTSTRAP_VolState", "F52_RegimeNormalizationEligibility", "E33_SpreadComputeAndTier_New", "DERIVE_BestBidAsk_New", "BOOTSTRAP_DepthState", "E37_PersistentThinLiquidity")) ruleToCluster.put(r, 2);
        // Cluster 3
        for (String r : List.of("CLEANUP_RetractSignificantTrade", "J71_Alpha_SignificantTrade")) ruleToCluster.put(r, 3);
        // Cluster 4
        for (String r : List.of("BOOTSTRAP_LiquidationStats", "H61_LiqTiering", "H67_CascadeCooldownEligibility")) ruleToCluster.put(r, 4);
        // Cluster 5
        for (String r : List.of("BOOTSTRAP_TradeStats", "D30_TradeRateTiering")) ruleToCluster.put(r, 5);

        System.out.println("┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  ANALYSIS 1: ALL RULES (including bootstraps)            │");
        System.out.println("└──────────────────────────────────────────────────────────┘\n");
        runAnalysis(ruleToCluster, ruleInputs, ruleOutputs, false);

        System.out.println("\n\n┌──────────────────────────────────────────────────────────┐");
        System.out.println("│  ANALYSIS 2: RUNTIME ONLY (bootstraps stripped)           │");
        System.out.println("└──────────────────────────────────────────────────────────┘\n");
        runAnalysis(ruleToCluster, ruleInputs, ruleOutputs, true);
    }

    static void runAnalysis(Map<String, Integer> ruleToCluster, Map<String, Set<String>> ruleInputs, 
                            Map<String, Set<String>> ruleOutputs, boolean stripBootstraps) {
        
        Map<Integer, List<String>> clusterRules = new TreeMap<>();
        
        for (var entry : ruleToCluster.entrySet()) {
            String ruleName = entry.getKey();
            int clusterId = entry.getValue();
            if (stripBootstraps && ruleName.startsWith("BOOTSTRAP_")) {
                continue; // skip
            }
            clusterRules.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(ruleName);
        }

        Map<Integer, Set<String>> inputSets = new TreeMap<>();
        Map<Integer, Set<String>> outputSets = new TreeMap<>();
        
        for (var entry : clusterRules.entrySet()) {
            Set<String> ins = new TreeSet<>(), outs = new TreeSet<>();
            for (String rn : entry.getValue()) {
                if (ruleInputs.containsKey(rn)) ins.addAll(ruleInputs.get(rn));
                if (ruleOutputs.containsKey(rn)) outs.addAll(ruleOutputs.get(rn));
            }
            inputSets.put(entry.getKey(), ins);
            outputSets.put(entry.getKey(), outs);
        }

        System.out.println("=== INTER-CLUSTER DEPENDENCIES ===");
        boolean anyDep = false;
        
        // Remove External orchestration facts from dependency graph
        Set<String> external = Set.of("MarketEvent", "RiskConfig", "RiskSignal");

        for (var consumer : inputSets.entrySet()) {
            for (var producer : outputSets.entrySet()) {
                if (consumer.getKey().equals(producer.getKey())) continue;
                Set<String> shared = new TreeSet<>(consumer.getValue());
                shared.retainAll(producer.getValue());
                shared.removeAll(external);
                
                if (!shared.isEmpty()) {
                    anyDep = true;
                    System.out.println("  ❌ Cluster " + consumer.getKey() + " ← Cluster " + producer.getKey() + " via: " + shared);
                }
            }
        }
        if (!anyDep) {
            System.out.println("  ✅ VERIFIED: ZERO inter-cluster dependencies found between the 5 clusters.");
        }
        
        System.out.println("\n=== SELF-CONTAINMENT CHECK ===");
        for (var entry : clusterRules.entrySet()) {
            int cid = entry.getKey();
            Set<String> unmet = new TreeSet<>(inputSets.get(cid));
            unmet.removeAll(outputSets.get(cid));
            unmet.removeAll(external);
            if (unmet.isEmpty()) {
                System.out.println("  Cluster " + cid + ": ✅ Self-contained");
            } else {
                System.out.println("  Cluster " + cid + ": ❌ Missing producers for: " + unmet);
            }
        }
    }
}
