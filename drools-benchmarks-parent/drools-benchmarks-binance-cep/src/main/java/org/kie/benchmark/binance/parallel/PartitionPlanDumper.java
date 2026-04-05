/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.benchmark.binance.parallel;

import org.kie.benchmark.binance.analysis.DrlRuleParser;
import org.kie.benchmark.binance.analysis.RuleMeta;
import org.kie.benchmark.binance.analysis.Stratifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Standalone utility to inspect and dump the partition plan.
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * mvn exec:java -f drools-benchmarks-parent/drools-benchmarks-binance-cep/pom.xml \
 *   -Dexec.mainClass="org.kie.benchmark.binance.parallel.PartitionPlanDumper" \
 *   -Dexec.args="output/stratified_drls"
 * </pre>
 *
 * <p>This will:
 * <ol>
 *   <li>Parse taxonomy.drl, compute dependency graph, stratify into phases</li>
 *   <li>Parse .ftree for Infomap clusters</li>
 *   <li>Merge → PartitionPlan</li>
 *   <li>Print the full plan to stdout</li>
 *   <li>Write each session-group's DRL to a separate file in the output dir</li>
 * </ol>
 * </p>
 */
public class PartitionPlanDumper {

    public static void main(String[] args) {
        try {
            // Determine output directory
            String outputDir = args.length > 0 ? args[0] : "output/stratified_drls";
            Path outputPath = Paths.get(outputDir);

            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║  Partition Plan Dumper — Stratified DRL Inspector ║");
            System.out.println("╚══════════════════════════════════════════════════╝\n");

            // ── Step 1: Load DRL ──────────────────────────────────────
            String drlContent;
            String drlPath = System.getProperty("binance.rules.file", "/rules/taxonomy.drl");
            try (InputStream is = PartitionPlanDumper.class.getResourceAsStream(drlPath)) {
                if (is == null) throw new RuntimeException("DRL not found: " + drlPath);
                drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            System.out.println("Loaded DRL: " + drlPath);

            // ── Step 2: Parse rules and show metadata ─────────────────
            DrlRuleParser parser = new DrlRuleParser();
            List<RuleMeta> allRules = parser.parse(drlContent);
            System.out.println("Total rules parsed: " + allRules.size());
            System.out.println();

            // ── Step 3: Show dependency graph stratification ──────────
            Stratifier stratifier = new Stratifier(allRules);
            List<Set<String>> rawPhases = stratifier.stratify();
            System.out.println("═══ Structural Stratification (phases from dependency graph) ═══");
            System.out.println("Has cycles: " + stratifier.hasCycles());
            System.out.println("Total phases: " + rawPhases.size());
            System.out.println();
            for (int i = 0; i < rawPhases.size(); i++) {
                Set<String> phase = rawPhases.get(i);
                System.out.println("  Phase " + (i + 1) + " (" + phase.size() + " rules):");
                for (String rule : phase) {
                    System.out.println("    • " + rule);
                }
                System.out.println();
            }

            // ── Step 4: Load Infomap clusters ─────────────────────────
            String ftreePath = System.getProperty("binance.ftree.file",
                    "/clusters/binance_rule_graph.ftree");
            Map<String, Integer> clusters;
            try (InputStream ftreeIs = PartitionPlanDumper.class.getResourceAsStream(ftreePath)) {
                if (ftreeIs == null) throw new RuntimeException("Ftree not found: " + ftreePath);
                clusters = FtreeParser.parse(ftreeIs);
            }
            System.out.println("═══ Infomap Clusters ═══");
            System.out.println("Rules with cluster assignment: " + clusters.size());
            Map<Integer, List<String>> byCluster = new TreeMap<>();
            for (Map.Entry<String, Integer> e : clusters.entrySet()) {
                byCluster.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            }
            for (Map.Entry<Integer, List<String>> e : byCluster.entrySet()) {
                System.out.println("  Cluster " + e.getKey() + " (" + e.getValue().size()
                        + " rules): " + e.getValue());
            }
            System.out.println();

            // ── Step 5: Build partition plan ──────────────────────────
            PartitionPlan plan = ClusterPartitioner.buildFromResources(
                    drlContent,
                    PartitionPlanDumper.class.getResourceAsStream(ftreePath));

            System.out.println("═══ Final Partition Plan ═══");
            System.out.println(plan);
            System.out.println();

            // ── Step 6: Dump stratified DRLs to files ─────────────────
            Files.createDirectories(outputPath);
            System.out.println("═══ Writing Stratified DRLs to: " + outputPath.toAbsolutePath()
                    + " ═══");

            for (PartitionPlan.Phase phase : plan.getPhases()) {
                for (PartitionPlan.SessionGroup group : phase.getGroups()) {
                    String fileName = String.format("phase%d_cluster%d.drl",
                            phase.getPhaseNumber(), group.getClusterId());
                    Path filePath = outputPath.resolve(fileName);
                    Files.writeString(filePath, group.getDrlContent(), StandardCharsets.UTF_8);

                    System.out.println("  ✓ " + fileName + " ("
                            + group.getRuleNames().size() + " rules, "
                            + group.getDrlContent().length() + " bytes)");
                }
            }

            // ── Step 7: Write plan summary JSON ───────────────────────
            Path summaryPath = outputPath.resolve("partition_summary.txt");
            try (PrintWriter pw = new PrintWriter(
                    Files.newBufferedWriter(summaryPath, StandardCharsets.UTF_8))) {
                pw.println("Partition Plan Summary");
                pw.println("======================");
                pw.println("DRL Source: " + drlPath);
                pw.println("Ftree Source: " + ftreePath);
                pw.println("Total Rules: " + plan.totalRules());
                pw.println("Total Phases: " + plan.getPhases().size());
                pw.println();
                pw.println(plan);
                pw.println();
                pw.println("Rule Details:");
                for (RuleMeta rm : allRules) {
                    pw.println("  " + rm.getRuleName());
                    pw.println("    inputs:  " + rm.getInputs());
                    pw.println("    outputs: " + rm.getOutputs());
                    pw.println("    cluster: "
                            + clusters.getOrDefault(rm.getRuleName(), 0));
                }
            }
            System.out.println("  ✓ " + "partition_summary.txt (full plan + rule metadata)");

            System.out.println("\nDone! Inspect the DRL files in: " + outputPath.toAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
