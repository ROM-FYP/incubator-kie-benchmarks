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
package org.kie.benchmark.cep.riperis.runners;

import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.conf.ParallelExecutionOption;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark;
import org.kie.benchmark.cep.riperis.util.EnvConfig;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH runner for the RIPE RIS CEP built-in parallel evaluation architecture.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0, batchSize = 1)
@Measurement(iterations = 1, batchSize = 1)
@Fork(value = 1, jvmArgs = { "-Xms1g", "-Xmx4g" })
public class RipeRisParallelEvalJmhRunner {

    private static final String DRL_PATH = EnvConfig.get("RIPERIS_RULES_FILE");

    @Param({
        "RIPERIS_DATA_FILE_0_4M",
        "RIPERIS_DATA_FILE_0_8M",
        "RIPERIS_DATA_FILE_1_2M",
        "RIPERIS_DATA_FILE_1_6M"
    })
    private String dataFile;

    private KieBase kieBase;
    private List<RisMessage> events;

    // Per-invocation state
    private KieSession session;
    private long lastRulesFired;
    private long invocationStartTime;

    // Cumulative trial-level metrics
    private long totalRulesFired;
    private long totalTimeElapsed;
    private int invocationCount;

    // Custom iteration metrics tracking
    private final List<Long> iterationDurations = new java.util.ArrayList<>();
    private final List<Double> iterationThroughputs = new java.util.ArrayList<>();
    private final List<Long> iterationPeakHeaps = new java.util.ArrayList<>();

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        System.out.println("\n=== RIPE RIS Parallel Eval JMH Setup ===");
        String resolvedPath = EnvConfig.get(dataFile);
        if (resolvedPath == null) {
            resolvedPath = dataFile;
        }
        events = RipeRisBaselineBenchmark.loadEvents(resolvedPath, Long.MAX_VALUE);

        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        byte[] drlBytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(DRL_PATH)) {
            if (is == null) {
                throw new RuntimeException("Cannot find " + DRL_PATH);
            }
            drlBytes = is.readAllBytes();
        }
        kfs.write("src/main/resources/" + DRL_PATH, ResourceFactory.newByteArrayResource(drlBytes));
        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Build errors: " + kb.getResults().toString());
        }

        KieBaseConfiguration config = ks.newKieBaseConfiguration();
        config.setOption(EventProcessingOption.STREAM);
        config.setOption(ParallelExecutionOption.PARALLEL_EVALUATION);

        KieContainer container = ks.newKieContainer(ks.getRepository().getDefaultReleaseId());
        kieBase = container.newKieBase(config);

        totalRulesFired = 0;
        totalTimeElapsed = 0;
        invocationCount = 0;

        iterationDurations.clear();
        iterationThroughputs.clear();
        iterationPeakHeaps.clear();

        System.out.println("Events per invocation: " + events.size());
        System.out.println("DRL: " + DRL_PATH);
        System.out.println("====================================\n");
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        KieServices ks = KieServices.Factory.get();
        KieSessionConfiguration sessionConfig = ks.newKieSessionConfiguration();
        sessionConfig.setOption(org.kie.api.runtime.conf.ClockTypeOption.get("pseudo"));
        session = kieBase.newKieSession(sessionConfig, null);
        lastRulesFired = 0L;

        // Reset peak heap usage for this invocation
        for (java.lang.management.MemoryPoolMXBean pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                pool.resetPeakUsage();
            }
        }

        invocationStartTime = System.currentTimeMillis();
    }

    @Benchmark
    public long parallelEvalReplay() {
        SessionPseudoClock clock = session.getSessionClock();
        long fired = 0L;

        for (RisMessage event : events) {
            long eventTime = (long) (event.getTimestamp() * 1000);
            long currentTime = clock.getCurrentTime();
            if (eventTime > currentTime) {
                clock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
            }
            session.insert(event);
            fired += session.fireAllRules();
        }

        lastRulesFired = fired;
        return fired;
    }

    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        long duration = System.currentTimeMillis() - invocationStartTime;
        double throughput = duration > 0 ? (events.size() * 1000.0) / duration : 0.0;

        invocationCount++;
        totalRulesFired += lastRulesFired;
        totalTimeElapsed += duration;

        // Collect peak heap usage
        long peakHeapBytes = 0;
        for (java.lang.management.MemoryPoolMXBean pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                peakHeapBytes += pool.getPeakUsage().getUsed();
            }
        }
        long peakHeapMb = peakHeapBytes / (1024 * 1024);

        iterationDurations.add(duration);
        iterationThroughputs.add(throughput);
        iterationPeakHeaps.add(peakHeapMb);

        System.out.printf(
                "[ParallelEval Invocation %d] Events: %d | Rules fired: %,d | Duration: %d ms | Throughput: %.2f events/sec | Peak Heap: %d MB%n",
                invocationCount, events.size(), lastRulesFired, duration, throughput, peakHeapMb);

        if (session != null) {
            session.dispose();
            session = null;
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        int totalSize = iterationDurations.size();
        int startIndex = Math.max(0, totalSize - 1);
        int numMeasurement = totalSize - startIndex;

        long measurementTimeElapsed = 0;
        for (int i = startIndex; i < totalSize; i++) {
            measurementTimeElapsed += iterationDurations.get(i);
        }

        double avgThroughput = measurementTimeElapsed > 0
                ? (numMeasurement * events.size() * 1000.0) / measurementTimeElapsed
                : 0.0;
        double avgTime = numMeasurement > 0 ? (double) measurementTimeElapsed / numMeasurement : 0.0;

        double avgPeakHeap = 0;
        if (numMeasurement > 0) {
            long sum = 0;
            for (int i = startIndex; i < totalSize; i++) {
                sum += iterationPeakHeaps.get(i);
            }
            avgPeakHeap = (double) sum / numMeasurement;
        }

        System.out.println("\n=== Parallel Eval Trial Summary (Measurement Iterations) ===");
        System.out.println("Total invocations:      " + invocationCount);
        System.out.println("Measurement iterations: " + numMeasurement);
        System.out.println("Events per invocation:  " + events.size());
        System.out.printf("Total rules fired:      %,d%n", totalRulesFired);
        System.out.println("Measurement time elapsed: " + measurementTimeElapsed + " ms");
        System.out.printf("Avg throughput:         %.2f events/sec%n", avgThroughput);
        System.out.printf("Avg peak heap:          %.2f MB%n", avgPeakHeap);
        System.out.println("===================================\n");

        writeResultsJson("parallel_eval", avgTime, avgThroughput, avgPeakHeap, startIndex);
    }

    private void writeResultsJson(String archName, double avgTime, double avgThroughput, double avgPeakHeap, int startIndex) {
        try {
            java.io.File dir = new java.io.File("results");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String dataCount = getDataCountName(dataFile);
            java.io.File file = new java.io.File(dir, archName + "_" + dataCount + ".json");

            int totalSize = iterationDurations.size();
            int numMeasurement = totalSize - startIndex;

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append(String.format(java.util.Locale.US, "  \"architecture\": \"%s\",\n", archName));
            sb.append(String.format(java.util.Locale.US, "  \"dataset\": \"%s\",\n", dataCount));
            sb.append("  \"warmup_iterations\": 0,\n");
            sb.append(String.format(java.util.Locale.US, "  \"measurement_iterations\": %d,\n", numMeasurement));
            sb.append(String.format(java.util.Locale.US, "  \"average_execution_time_ms\": %.2f,\n", avgTime));
            sb.append(String.format(java.util.Locale.US, "  \"average_throughput_events_s\": %.2f,\n", avgThroughput));
            sb.append(String.format(java.util.Locale.US, "  \"average_peak_heap_mb\": %.2f,\n", avgPeakHeap));
            sb.append("  \"iterations\": [\n");

            for (int i = startIndex; i < totalSize; i++) {
                sb.append("    {\n");
                sb.append(String.format(java.util.Locale.US, "      \"iteration\": %d,\n", i - startIndex + 1));
                sb.append(String.format(java.util.Locale.US, "      \"execution_time_ms\": %d,\n", iterationDurations.get(i)));
                sb.append(String.format(java.util.Locale.US, "      \"throughput_events_s\": %.2f,\n", iterationThroughputs.get(i)));
                sb.append(String.format(java.util.Locale.US, "      \"peak_heap_mb\": %d\n", iterationPeakHeaps.get(i)));
                if (i < totalSize - 1) {
                    sb.append("    },\n");
                } else {
                    sb.append("    }\n");
                }
            }
            sb.append("  ]\n");
            sb.append("}\n");

            java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("[JMH] Saved custom results to: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[JMH] Failed to write custom results JSON: " + e.getMessage());
        }
    }

    private String getDataCountName(String fileKey) {
        if (fileKey == null) return "unknown";
        if (fileKey.contains("0_4M") || fileKey.contains("400K")) return "400K";
        if (fileKey.contains("0_8M") || fileKey.contains("800K")) return "800K";
        if (fileKey.contains("1_2M") || fileKey.contains("1200K")) return "1200K";
        if (fileKey.contains("1_6M") || fileKey.contains("1600K")) return "1600K";
        return fileKey;
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(RipeRisParallelEvalJmhRunner.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
