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

import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.CepSessionFactory;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.kie.benchmark.cep.wikimedia.runner.WikimediaBaselineBenchmark;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Unified JMH benchmark for Wikimedia CEP — baseline and built-in parallel modes.
 *
 * <p>Covers three execution modes via the {@code mode} parameter:
 * <ul>
 *   <li>{@code baseline}            — single-session sequential evaluation</li>
 *   <li>{@code PARALLEL_EVALUATION} — Drools built-in parallel LHS evaluation</li>
 *   <li>{@code FULLY_PARALLEL}      — Drools built-in fully parallel (LHS + RHS)</li>
 * </ul>
 *
 * <p>Symmetric with {@code BinanceFullDatasetBenchmark} and
 * {@code OpenSkyFullReplayBenchmark} across the three CEP benchmark domains.
 *
 * <p>Benchmark Configuration:
 * <ul>
 *   <li>Rules: wikimedia_content_moderation_join_heavy.drl</li>
 *   <li>Dataset: split_400k / 800k / 1200k / 1600k (JSONL)</li>
 *   <li>Mode: Event-time replay with SessionPseudoClock</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, batchSize = 1)
@Measurement(iterations = 5, batchSize = 1)
@Fork(value = 1, jvmArgs = {"-Xms4g", "-Xmx4g"})
public class WikimediaJmhBenchmark {

    private static final String DRL_PATH = "rules/wikimedia_content_moderation_join_heavy.drl";

    /** Classpath-relative path to the JSONL dataset file. */
    @Param({"src/main/resources/data/data/split_400k.jsonl"})
    private String dataFile;

    /**
     * Execution mode.
     * <ul>
     *   <li>{@code baseline}            — sequential single-session</li>
     *   <li>{@code PARALLEL_EVALUATION} — parallel LHS evaluation only</li>
     *   <li>{@code FULLY_PARALLEL}      — parallel LHS + RHS</li>
     * </ul>
     */
    @Param({"baseline", "PARALLEL_EVALUATION", "FULLY_PARALLEL"})
    private String mode;

    private CepSessionFactory factory;
    private List<WikiEvent> events;

    // Per-invocation state
    private KieSession session;
    private int lastRulesFired;
    private long invocationStartTime;

    // Cumulative trial-level metrics
    private long totalRulesFired;
    private long totalTimeElapsed;
    private int invocationCount;

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        System.out.printf("%n=== Wikimedia JMH Setup [mode=%s] ===%n", mode);
        events = WikimediaBaselineBenchmark.loadEvents(dataFile, Integer.MAX_VALUE);

        factory = "baseline".equals(mode)
                ? new CepSessionFactory(DRL_PATH)
                : new CepSessionFactory(DRL_PATH, mode);

        totalRulesFired  = 0;
        totalTimeElapsed = 0;
        invocationCount  = 0;

        System.out.println("Events per invocation: " + events.size());
        System.out.println("DRL:                   " + DRL_PATH);
        System.out.println("Mode:                  " + mode);
        System.out.println("=============================================\n");
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        session = factory.createSession(true);
        invocationStartTime = System.currentTimeMillis();
    }

    @Benchmark
    public int replay() {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;

        for (WikiEvent event : events) {
            long eventTime   = event.getTimestamp();
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
        long   duration   = System.currentTimeMillis() - invocationStartTime;
        double throughput = (duration > 0) ? (events.size() * 1000.0) / duration : 0;

        invocationCount++;
        totalRulesFired  += lastRulesFired;
        totalTimeElapsed += duration;

        System.out.printf("[%s Invocation %d] Events: %d | Rules fired: %,d | Duration: %d ms | Throughput: %.2f ev/s%n",
                mode, invocationCount, events.size(), lastRulesFired, duration, throughput);

        if (session != null) {
            session.dispose();
            session = null;
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() {
        double avgThroughput = (totalTimeElapsed > 0)
                ? (invocationCount * events.size() * 1000.0) / totalTimeElapsed : 0;

        System.out.printf("%n=== Wikimedia Trial Summary [%s] ===%n", mode);
        System.out.println("Total invocations:     " + invocationCount);
        System.out.println("Events per invocation: " + events.size());
        System.out.printf("Total rules fired:     %,d%n", totalRulesFired);
        System.out.println("Total time elapsed:    " + totalTimeElapsed + " ms");
        System.out.printf("Avg throughput:        %.2f events/sec%n", avgThroughput);
        System.out.println("==========================================\n");
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(WikimediaJmhBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
