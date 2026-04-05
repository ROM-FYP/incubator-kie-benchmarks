package org.kie.benchmark.binance.parallel;
import org.kie.benchmark.binance.analysis.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AnalyzeFallback {
    public static void main(String[] args) throws Exception {
        String drlContent;
        try (InputStream is = AnalyzeFallback.class.getResourceAsStream("/rules/taxonomy_no_god_objects.drl")) {
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        PartitionPlan.ClusterPlan plan;
        try (InputStream ftreeIs = AnalyzeFallback.class.getResourceAsStream("/clusters/binance_rule_graph.ftree")) {
            plan = ClusterPartitioner.buildClusterPlanFromResources(drlContent, ftreeIs);
        }
        PartitionPlan.SelfContainedCluster fallback = plan.getFallbackCluster();
        
        System.out.println("Fallback contains " + fallback.getOriginalRules().size() + " rules.");
        System.out.println("Sample of 10 fallback rules:");
        fallback.getOriginalRules().stream().limit(10).forEach(r -> System.out.println("  " + r));
        
        System.out.println("\nCluster 2 Depth/Spread rules that aren't firing:");
        PartitionPlan.SelfContainedCluster c2 = plan.getClusters().stream().filter(c -> c.getClusterId()==2).findFirst().orElseThrow();
        c2.getOriginalRules().stream().limit(5).forEach(r -> System.out.println("  " + r));
    }
}
