package org.kie.benchmark.binance.parallel;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TxtDependencyAnalyzerDirected {

    public static void main(String[] args) throws Exception {
        String txtPath = "/home/maheshdila/mahesh/research/incubator-kie-drools/taxonomy_drl_dependency_graph.txt";
        List<String> lines = Files.readAllLines(Paths.get(txtPath));

        Map<String, Set<String>> ruleInputs = new HashMap<>();
        Map<String, Set<String>> ruleOutputs = new HashMap<>();
        String currentRule = null;

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
        
        // Cluster 1 (from directed ftree)
        for (String r : List.of(
            "K77_Beta_AssessMicroVolRisk", "K78_Beta_EmitMicroVolSignal", "BOOTSTRAP_MicroVolatilityRisk",
            "D32_TradesWhileBookStale", "K76_Beta_SpreadVelocity", "CLEANUP_RetractDepthUpdateTick",
            "K75_Alpha_DepthUpdate", "BOOTSTRAP_SpreadVelocityState"
        )) ruleToCluster.put(r, 1);
        
        // Cluster 2
        for (String r : List.of(
            "B12_TradeActiveBookSilent", "UPD_LastSeen_Trade", "UPD_LastSeen_Depth",
            "B13_BookActiveTradeSilent", "C25_BookAgeStale", "RECOVERY_ExitThrottledToNormal",
            "I68_EnterThrottled_OnDegraded", "B14_StaleMark", "UPD_LastSeen_Mark", "G59_MarkStaleButMarketActive"
        )) ruleToCluster.put(r, 2);
        
        // Cluster 3
        for (String r : List.of(
            "L81_Beta_AssessDislocation", "L82_Beta_EmitDislocationSignal", "F43_VolTiering",
            "BOOTSTRAP_DislocationEscalation", "F52_RegimeNormalizationEligibility", "BOOTSTRAP_VolState",
            "L80_Beta_MarkDivergence", "BOOTSTRAP_MarkDivergencePulsar", "CLEANUP_RetractMarkPriceTick",
            "L79_Alpha_MarkPriceUpdate", "E36_SpreadBlowout", "E33_SpreadComputeAndTier_Update",
            "C21_TopJumpNoTrades", "DERIVE_BestBidAsk_Update", "E33_SpreadComputeAndTier_New",
            "DERIVE_BestBidAsk_New", "E41_LiquidityStressCombine", "E35_DepthCollapse",
            "E34_DepthTiering", "E37_PersistentThinLiquidity", "BOOTSTRAP_DepthState"
        )) ruleToCluster.put(r, 3);
        
        // Cluster 4
        for (String r : List.of("CLEANUP_RetractSignificantTrade", "J71_Alpha_SignificantTrade")) ruleToCluster.put(r, 4);
        
        // Cluster 5
        for (String r : List.of("H61_LiqTiering", "H67_CascadeCooldownEligibility", "BOOTSTRAP_LiquidationStats")) ruleToCluster.put(r, 5);
        
        // Cluster 6
        for (String r : List.of("D30_TradeRateTiering", "BOOTSTRAP_TradeStats")) ruleToCluster.put(r, 6);

        System.out.println("=== DEPENDENCY ANALYSIS FOR DIRECTED GRAPH CLUSTERS (6 clusters) ===\n");
        runAnalysis(ruleToCluster, ruleInputs, ruleOutputs);
    }

    static void runAnalysis(Map<String, Integer> ruleToCluster, Map<String, Set<String>> ruleInputs, 
                            Map<String, Set<String>> ruleOutputs) {
        
        Map<Integer, List<String>> clusterRules = new TreeMap<>();
        for (var entry : ruleToCluster.entrySet()) {
            clusterRules.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
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

        System.out.println("=== INTER-CLUSTER DEPENDENCIES ===\n");
        boolean anyDep = false;
        Set<String> external = Set.of("MarketEvent", "RiskConfig", "RiskSignal");

        for (var consumer : inputSets.entrySet()) {
            for (var producer : outputSets.entrySet()) {
                if (consumer.getKey().equals(producer.getKey())) continue;
                Set<String> shared = new TreeSet<>(consumer.getValue());
                shared.retainAll(producer.getValue());
                shared.removeAll(external);
                
                if (!shared.isEmpty()) {
                    anyDep = true;
                    System.out.println("  ❌ Cluster " + consumer.getKey() + " needs fact(s) " + shared + " from Cluster " + producer.getKey());
                }
            }
        }
        if (!anyDep) {
            System.out.println("  ✅ ZERO inter-cluster dependencies.");
        }
        
    }
}
