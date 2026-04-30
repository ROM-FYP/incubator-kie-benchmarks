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
package org.kie.benchmark.cep.wikimedia.jmh;

import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.kie.benchmark.cep.wikimedia.parallel.WikimediaClusterDrlGenerator;
import org.kie.benchmark.cep.wikimedia.parallel.WikimediaClusterOrchestrator;
import org.kie.benchmark.cep.wikimedia.runner.WikimediaBaselineBenchmark;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the 4-cluster parallel Wikimedia CEP execution.
 *
 * <p>Replays all events through the alpha-filter routed cluster architecture
 * using {@link WikimediaClusterOrchestrator}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
public class WikimediaClusterJmhBenchmark {

    private static final String DRL_PATH = "rules/wikimedia_content_moderation_join_heavy.drl";
    private static final String DEFAULT_DATA_FILE =
            "src/main/resources/data/wikimedia_stream_20260421_104232.jsonl";

    @Param({DEFAULT_DATA_FILE})
    private String dataFile;

    private List<WikiEvent> events;
    private String drlContent;

    // Per-invocation state
    private WikimediaClusterOrchestrator orchestrator;
    private int lastRulesFired;
    private long invocationStartTime;

    // Cumulative trial-level metrics
    private long totalRulesFired;
    private long totalTimeElapsed;
    private int invocationCount;

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        System.out.println("\n=== Wikimedia Cluster JMH Setup ===");
        events = WikimediaBaselineBenchmark.loadEvents(dataFile, Integer.MAX_VALUE);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) throw new RuntimeException("Cannot find " + DRL_PATH);
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        orchestrator = new WikimediaClusterOrchestrator(drlContent);

        totalRulesFired = 0;
        totalTimeElapsed = 0;
        invocationCount = 0;

        System.out.println("Events per invocation: " + events.size());
        System.out.println("DRL: " + DRL_PATH);
        System.out.println("Clusters: " + WikimediaClusterDrlGenerator.getClusterCount());
        System.out.println("===================================\n");
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        invocationStartTime = System.currentTimeMillis();
    }

    @Benchmark
    public int clusterReplay() {
        lastRulesFired = orchestrator.replayEvents(events);
        return lastRulesFired;
    }

    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        long duration = System.currentTimeMillis() - invocationStartTime;
        double throughput = (duration > 0) ? (events.size() * 1000.0) / duration : 0;

        invocationCount++;
        totalRulesFired += lastRulesFired;
        totalTimeElapsed += duration;

        // Log per-session breakdown
        Map<Integer, Integer> perFired = orchestrator.getPerSessionFired();
        Map<Integer, Integer> perEvents = orchestrator.getPerSessionEventsReceived();
        String[] names = WikimediaClusterDrlGenerator.getClusterNames();

        System.out.printf("[Cluster Invocation %d] Rules fired: %,d | Duration: %d ms | Throughput: %.2f events/sec%n",
                invocationCount, lastRulesFired, duration, throughput);
        for (Map.Entry<Integer, Integer> entry : perFired.entrySet()) {
            int cid = entry.getKey();
            System.out.printf("  %s:  Events=%,d  Fired=%,d%n",
                    names[cid], perEvents.getOrDefault(cid, 0), entry.getValue());
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = (totalTimeElapsed > 0)
                ? (invocationCount * events.size() * 1000.0) / totalTimeElapsed : 0;

        System.out.println("\n=== Cluster Trial Summary ===");
        System.out.println("Pool size:              " + WikimediaClusterDrlGenerator.getClusterCount());
        System.out.println("Total invocations:      " + invocationCount);
        System.out.println("Events per invocation:  " + events.size());
        System.out.printf("Total rules fired:      %,d%n", totalRulesFired);
        System.out.println("Total time elapsed:     " + totalTimeElapsed + " ms");
        System.out.printf("Avg throughput:         %.2f events/sec%n", avgThroughput);
        System.out.println("=============================\n");

        if (orchestrator != null) {
            orchestrator.dispose();
            orchestrator = null;
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(WikimediaClusterJmhBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
