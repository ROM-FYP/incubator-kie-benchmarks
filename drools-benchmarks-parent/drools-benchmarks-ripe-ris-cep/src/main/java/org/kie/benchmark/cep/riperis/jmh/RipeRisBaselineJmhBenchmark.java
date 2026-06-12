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

import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.runner.RipeRisBaselineBenchmark;
import org.kie.benchmark.cep.riperis.util.CepSessionFactory;
import org.kie.benchmark.cep.riperis.util.EnvConfig;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the single-session RIPE RIS CEP baseline.
 *
 * <p>
 * Replays all events through a single KieSession with a pseudo clock,
 * firing rules after each event insertion.
 *
 * <p>
 * Important: this benchmark does not calculate forward-chain depth from
 * synthetic facts such as ChainStage. The rule base no longer inserts such
 * facts. Runtime causal depth should be measured using the causal trace
 * listener / causal graph tooling.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(value = 1, jvmArgs = { "-Xms4g", "-Xmx4g" })
public class RipeRisBaselineJmhBenchmark {

    private static final String DRL_PATH = EnvConfig.get("RIPERIS_RULES_FILE");

    private static final String DEFAULT_DATA_FILE = "RIPERIS_DEFAULT_DATA_FILE";

    @Param({ DEFAULT_DATA_FILE })
    private String dataFile;

    private CepSessionFactory factory;
    private List<RisMessage> events;

    // Per-invocation state
    private KieSession session;
    private long lastRulesFired;
    private long invocationStartTime;

    // Cumulative trial-level metrics
    private long totalRulesFired;
    private long totalTimeElapsed;
    private long invocationCount;

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        System.out.println("\n=== RIPE RIS Baseline JMH Setup ===");

        events = RipeRisBaselineBenchmark.loadEvents(EnvConfig.get(dataFile), Long.MAX_VALUE);
        factory = new CepSessionFactory(DRL_PATH);

        totalRulesFired = 0;
        totalTimeElapsed = 0;
        invocationCount = 0;

        System.out.println("Events per invocation: " + events.size());
        System.out.println("DRL: " + DRL_PATH);
        System.out.println("====================================\n");
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        session = factory.createSession(true);
        lastRulesFired = 0L;
        invocationStartTime = System.currentTimeMillis();
    }

    @Benchmark
    public long baselineReplay() {
        SessionPseudoClock clock = session.getSessionClock();
        long fired = 0L;

        for (RisMessage event : events) {
            advanceClock(clock, event);
            session.insert(event);
            fired += session.fireAllRules();
        }

        lastRulesFired = fired;
        return fired;
    }

    private void advanceClock(SessionPseudoClock clock, RisMessage event) {
        long eventTime = (long) (event.getTimestamp() * 1000);
        long currentTime = clock.getCurrentTime();

        if (eventTime > currentTime) {
            clock.advanceTime(eventTime - currentTime, TimeUnit.MILLISECONDS);
        }
    }

    @TearDown(Level.Invocation)
    public void teardownInvocation() {
        long duration = System.currentTimeMillis() - invocationStartTime;
        double throughput = duration > 0
                ? (events.size() * 1000.0) / duration
                : 0.0;

        invocationCount++;
        totalRulesFired += lastRulesFired;
        totalTimeElapsed += duration;

        System.out.printf(
                "[Baseline Invocation %d] Events: %d | Rules fired: %,d | Duration: %d ms | Throughput: %.2f events/sec%n",
                invocationCount,
                events.size(),
                lastRulesFired,
                duration,
                throughput);

        if (session != null) {
            session.dispose();
            session = null;
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = totalTimeElapsed > 0
                ? (invocationCount * events.size() * 1000.0) / totalTimeElapsed
                : 0.0;

        System.out.println("\n=== Baseline Trial Summary ===");
        System.out.println("Total invocations:      " + invocationCount);
        System.out.println("Events per invocation:  " + events.size());
        System.out.printf("Total rules fired:      %,d%n", totalRulesFired);
        System.out.println("Total time elapsed:     " + totalTimeElapsed + " ms");
        System.out.printf("Avg throughput:         %.2f events/sec%n", avgThroughput);
        System.out.println("==============================\n");
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(RipeRisBaselineJmhBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
