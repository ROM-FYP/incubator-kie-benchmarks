/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.benchmark.cep.riperis.analysis;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a static directed rule-dependency graph from parsed DRL metadata.
 *
 * <p>
 * An edge Rule A -> Rule B is created when Rule A outputs a fact type that
 * Rule B consumes on its LHS.
 *
 * <p>
 * Control-only fact types can be ignored so they do not create artificial
 * dependencies. The current RIPE RIS ruleset no longer uses ChainStage or
 * benchmark-depth facts, so those are not included here.
 */
public class DependencyGraphBuilder {

    private final Graph<RuleMeta, DefaultEdge> graph;

    private static final Set<String> CONTROL_FACTS = new HashSet<>(Arrays.asList(
            "Stage",
            "Illegal"));

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

        for (RuleMeta producerRule : rules) {
            for (RuleMeta consumerRule : rules) {
                if (producerRule == consumerRule) {
                    continue;
                }

                Set<String> dependencyIntersection = new HashSet<>(producerRule.getOutputs());
                dependencyIntersection.retainAll(consumerRule.getInputs());
                dependencyIntersection.removeAll(CONTROL_FACTS);

                if (!dependencyIntersection.isEmpty()) {
                    graph.addEdge(producerRule, consumerRule);
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
        return graph.incomingEdgesOf(rule).stream()
                .map(graph::getEdgeSource)
                .collect(Collectors.toSet());
    }

    public Set<RuleMeta> getSuccessors(RuleMeta rule) {
        return graph.outgoingEdgesOf(rule).stream()
                .map(graph::getEdgeTarget)
                .collect(Collectors.toSet());
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
        sb.append("Dependency Graph (Adjacency List) [Control Facts Ignored: ")
                .append(CONTROL_FACTS)
                .append("]:\n");
        sb.append("=================================\n");

        for (RuleMeta rule : graph.vertexSet()) {
            sb.append(rule.getRuleName()).append(" -> ");

            Set<RuleMeta> successors = getSuccessors(rule);
            if (successors.isEmpty()) {
                sb.append("(no outgoing edges)");
            } else {
                sb.append(successors.stream()
                        .map(RuleMeta::getRuleName)
                        .collect(Collectors.joining(", ")));
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
