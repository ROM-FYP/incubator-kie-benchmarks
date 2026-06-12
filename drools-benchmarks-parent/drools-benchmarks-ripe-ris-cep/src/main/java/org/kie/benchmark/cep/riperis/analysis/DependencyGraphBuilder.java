/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * ... (License header remains the same)
 */
package org.kie.benchmark.cep.riperis.analysis;

import org.kie.benchmark.cep.riperis.util.EnvConfig;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

public class DependencyGraphBuilder {

    private final Graph<RuleMeta, DefaultEdge> graph;

    // Define "Control Facts" that manage state but don't represent data flow.
    // These cause false cycles if treated as dependencies.
    private static final Set<String> CONTROL_FACTS = new HashSet<>(Arrays.asList("Stage", "Illegal"));

    public DependencyGraphBuilder(List<RuleMeta> rules) {
        this.graph = new DefaultDirectedGraph<>(DefaultEdge.class);
        buildGraph(rules);
    }

    private void buildGraph(List<RuleMeta> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }

        // Add all rules as vertices
        for (RuleMeta rule : rules) {
            graph.addVertex(rule);
        }

        // Add edges based on output/input intersection (FILTERED)
        for (RuleMeta ruleA : rules) {
            for (RuleMeta ruleB : rules) {
                // Skip self-loops
                if (ruleA == ruleB) {
                    continue;
                }

                // Explicitly calculate intersection to apply filtering
                Set<String> dependencyIntersection = new HashSet<>(ruleA.getOutputs());
                dependencyIntersection.retainAll(ruleB.getInputs());

                // Remove Control Facts from the intersection
                // If the only thing connecting Rule A and B is "Stage", we ignore the link.
                dependencyIntersection.removeAll(CONTROL_FACTS);

                // Create edge only if there is a REAL data dependency remaining
                if (!dependencyIntersection.isEmpty()) {
                    graph.addEdge(ruleA, ruleB);

                    // Optional: You could log what connects them here for debugging
                    // System.out.println(ruleA.getRuleName() + " -> " + ruleB.getRuleName() + " via
                    // " + dependencyIntersection);
                }
            }
        }
    }

    public Graph<RuleMeta, DefaultEdge> getGraph() {
        return graph;
    }

    public Set<RuleMeta> getRules() {
        return graph.vertexSet();
    }

    public Set<RuleMeta> getPredecessors(RuleMeta rule) {
        return graph.incomingEdgesOf(rule).stream().map(graph::getEdgeSource).collect(Collectors.toSet());
    }

    public Set<RuleMeta> getSuccessors(RuleMeta rule) {
        return graph.outgoingEdgesOf(rule).stream().map(graph::getEdgeTarget).collect(Collectors.toSet());
    }

    public boolean hasEdge(RuleMeta ruleA, RuleMeta ruleB) {
        return graph.containsEdge(ruleA, ruleB);
    }

    public boolean isIsolated(RuleMeta rule) {
        return graph.inDegreeOf(rule) == 0 && graph.outDegreeOf(rule) == 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Dependency Graph (Adjacency List) [Control Facts Ignored: " + CONTROL_FACTS + "]:\n");
        sb.append("=================================\n");

        for (RuleMeta rule : graph.vertexSet()) {
            sb.append(rule.getRuleName()).append(" -> ");
            Set<RuleMeta> successors = getSuccessors(rule);
            if (successors.isEmpty()) {
                sb.append("(no outgoing edges)");
            } else {
                sb.append(successors.stream().map(RuleMeta::getRuleName).collect(Collectors.joining(", ")));
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        String rulesFile = EnvConfig.get(args.length > 0 ? args[0] : "RIPERIS_RULES_FILE");
        String drlContent;
        try (InputStream is = DependencyGraphBuilder.class.getResourceAsStream("/" + rulesFile)) {
            drlContent = new String(Objects.requireNonNull(is).readAllBytes(), StandardCharsets.UTF_8);
        }

        DrlRuleParser parser = new DrlRuleParser();
        System.out.println("Parsing DRL file: " + rulesFile);
        List<RuleMeta> ruleMetas = parser.parse(drlContent);

        System.out.println("Extracted " + ruleMetas.size() + " rules.");
        DependencyGraphBuilder builder = new DependencyGraphBuilder(ruleMetas);

        System.out.println("\n" + builder.toString());
    }
}