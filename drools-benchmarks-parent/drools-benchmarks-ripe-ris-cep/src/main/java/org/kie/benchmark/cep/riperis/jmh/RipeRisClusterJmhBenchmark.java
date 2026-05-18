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
package org.kie.benchmark.cep.riperis.jmh;

import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.parallel.RipeRisClusterDrlGenerator;
import org.kie.benchmark.cep.riperis.parallel.RipeRisClusterOrchestrator;
import org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark;
import org.kie.benchmark.cep.riperis.util.EnvConfig;
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
 * JMH benchmark for the parallel Ripe RIS CEP execution.
 *
 * <p>
 * Replays all events through the cluster architecture
 * using {@link RipeRisClusterOrchestrator}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(value = 1, jvmArgs = { "-Xms4g", "-Xmx4g" })
public class RipeRisClusterJmhBenchmark {

    private static final String DRL_PATH = EnvConfig.get("RIPERIS_RULES_FILE");

    @Param({ "RIPERIS_DEFAULT_DATA_FILE" })
    private String dataFile;

    private List<RisMessage> events;
    private String drlContent;

    // Per-invocation state
    private RipeRisClusterOrchestrator orchestrator;
    private long lastRulesFired;
    private long invocationStartTime;

    // Cumulative trial-level metrics
    private long totalRulesFired;
    private long totalTimeElapsed;
    private long invocationCount;

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        System.out.println("\n=== Ripe RIS Cluster JMH Setup ===");
        events = RipeRisBaselineBenchmark.loadEvents(EnvConfig.get(dataFile), Long.MAX_VALUE);

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null)
                throw new RuntimeException("Cannot find " + DRL_PATH);
            drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        orchestrator = new RipeRisClusterOrchestrator(drlContent);

        totalRulesFired = 0;
        totalTimeElapsed = 0;
        invocationCount = 0;

        System.out.println("Events per invocation: " + events.size());
        System.out.println("DRL: " + DRL_PATH);
        System.out.println("Clusters: " + RipeRisClusterDrlGenerator.getClusterCount());
        System.out.println("===================================\n");
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        invocationStartTime = System.currentTimeMillis();
    }

    @Benchmark
    public long clusterReplay() {
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
        Map<Integer, Long> perFired = orchestrator.getPerSessionFired();
        Map<Integer, Long> perEvents = orchestrator.getPerSessionEventsReceived();
        String[] names = RipeRisClusterDrlGenerator.getClusterNames();

        System.out.printf("[Cluster Invocation %d] Rules fired: %,d | Duration: %d ms | Throughput: %.2f events/sec%n",
                invocationCount, lastRulesFired, duration, throughput);
        for (Map.Entry<Integer, Long> entry : perFired.entrySet()) {
            int cid = entry.getKey();
            System.out.printf("  %s:  Events=%,d  Fired=%,d%n",
                    names[cid], perEvents.getOrDefault(cid, 0L), entry.getValue());
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = (totalTimeElapsed > 0)
                ? (invocationCount * events.size() * 1000.0) / totalTimeElapsed
                : 0;

        System.out.println("\n=== Cluster Trial Summary ===");
        System.out.println("Pool size:              " + RipeRisClusterDrlGenerator.getClusterCount());
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
                .include(RipeRisClusterJmhBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
