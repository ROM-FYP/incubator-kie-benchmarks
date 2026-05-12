package org.kie.benchmark.binance.parallel;

import org.kie.benchmark.binance.analysis.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class StripGodObjects {
    public static void main(String[] args) throws Exception {
        String path = "/home/maheshdila/mahesh/research/incubator-kie-benchmarks/drools-benchmarks-parent/drools-benchmarks-binance-cep/src/main/resources/rules/taxonomy.drl";
        String drlContent = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        
        DrlRuleParser parser = new DrlRuleParser();
        List<RuleMeta> allRules = parser.parse(drlContent);
        
        List<String> rulesToKeep = new ArrayList<>();
        Set<String> badFacts = Set.of("FeedHealth", "ModeState");
        
        for (RuleMeta rm : allRules) {
            boolean keep = true;
            for (String input : rm.getInputs()) {
                if (badFacts.contains(input)) keep = false;
            }
            for (String output : rm.getOutputs()) {
                if (badFacts.contains(output)) keep = false;
            }
            
            // Also kill bootstrap rules for them
            if (rm.getRuleName().equals("BOOTSTRAP_FeedHealth")) keep = false;
            if (rm.getRuleName().equals("BOOTSTRAP_ModeState")) keep = false;
            
            if (keep) {
                rulesToKeep.add(rm.getRuleName());
            } else {
                System.out.println("Removed: " + rm.getRuleName());
            }
        }
        
        String newDrl = DrlSplitter.buildDrlForRules(drlContent, rulesToKeep);
        Files.write(Paths.get(path), newDrl.getBytes(StandardCharsets.UTF_8));
        
        System.out.println("\nKept " + rulesToKeep.size() + " out of " + allRules.size() + " rules.");
        System.out.println("Rewrote taxonomy.drl to test pure parallelism.");
    }
}
