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

package org.kie.benchmark.cep.wikimedia;

import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.benchmark.cep.wikimedia.model.ViralTopicAlert;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main benchmark runner for Wikimedia CEP.
 * Ingests real-time events, processes them through Drools CEP,
 * and tracks performance metrics.
 */
public class WikimediaCepBenchmark {
    private static final Logger logger = LoggerFactory.getLogger(WikimediaCepBenchmark.class);

    private final CepBenchmarkConfig config;
    private final CepSessionFactory sessionFactory;
    private KieSession kieSession;
    private WikimediaEventSource eventSource;

    private final AtomicLong eventsIngested = new AtomicLong(0);
    private final AtomicLong rulesFired = new AtomicLong(0);
    private final AtomicLong alertsGenerated = new AtomicLong(0);

    private volatile boolean running = false;

    private MiningTraceLogger traceLogger;

    public WikimediaCepBenchmark(CepBenchmarkConfig config) {
        this.config = config;
        this.sessionFactory = new CepSessionFactory(config.getRulesPath());
    }

    /**
     * Run the benchmark for the configured duration.
     */
    public void run() {
        logger.info("========================================");
        logger.info("Wikimedia CEP Benchmark");
        logger.info("Duration: {} minutes", config.getDurationMinutes());
        logger.info("Rules: {}", config.getRulesPath());
        logger.info("Stream: {}", config.getWikimediaUrl());
        logger.info("========================================");

        running = true;

        // Initialize Drools session
        kieSession = sessionFactory.createSession();
        kieSession.addEventListener(new RuleFiringListener());

        // Initialize Mining Trace Logger
        traceLogger = new MiningTraceLogger("mining_trace.csv");
        traceLogger.startNewTransaction();
        kieSession.addEventListener(traceLogger);

        // Start background rule firing thread
        Thread firingThread = new Thread(this::fireRulesContinuously, "rule-firing");
        firingThread.setDaemon(true);
        firingThread.start();

        // Start event ingestion
        eventSource = new WikimediaEventSource(config.getWikimediaUrl());
        eventSource.start(this::handleEvent);

        // Schedule periodic stats reporting
        ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(this::reportStats, 10, 10, TimeUnit.SECONDS);

        // Run for configured duration
        try {
            Thread.sleep(config.getDurationMinutes() * 60 * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Shutdown
        shutdown();
        statsExecutor.shutdown();

        // Final report
        reportFinalStats();
    }

    private void handleEvent(WikiEvent event) {
        if (!running)
            return;

        try {
            kieSession.insert(event);
            eventsIngested.incrementAndGet();

            if (config.isVerbose() && eventsIngested.get() % 100 == 0) {
                logger.debug("Ingested {} events", eventsIngested.get());
            }
        } catch (Exception e) {
            logger.error("Error inserting event", e);
        }
    }

    private void fireRulesContinuously() {
        while (running) {
            try {
                int fired = kieSession.fireAllRules();
                if (fired > 0) {
                    rulesFired.addAndGet(fired);
                }
                Thread.sleep(100); // Fire every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error firing rules", e);
            }
        }
    }

    private void reportStats() {
        // Count viral alerts in working memory
        long alerts = countViralAlerts();
        alertsGenerated.set(alerts);

        logger.info("Stats - Events: {}, Rules Fired: {}, Viral Alerts: {}",
                eventsIngested.get(), rulesFired.get(), alertsGenerated.get());
    }

    private void reportFinalStats() {
        long totalEvents = eventsIngested.get();
        long totalRulesFired = rulesFired.get();
        long totalAlerts = countViralAlerts();

        double durationSeconds = config.getDurationMinutes() * 60.0;
        double eventsPerSecond = totalEvents / durationSeconds;
        double rulesPerSecond = totalRulesFired / durationSeconds;

        logger.info("========================================");
        logger.info("FINAL BENCHMARK RESULTS");
        logger.info("========================================");
        logger.info("Total Events Ingested: {}", totalEvents);
        logger.info("Total Rules Fired: {}", totalRulesFired);
        logger.info("Total Viral Alerts: {}", totalAlerts);
        logger.info("Duration: {} minutes", config.getDurationMinutes());
        logger.info("Events/sec: {}", String.format("%.2f", eventsPerSecond));
        logger.info("Rules/sec: {}", String.format("%.2f", rulesPerSecond));
        logger.info("========================================");
    }

    private long countViralAlerts() {
        Collection<FactHandle> handles = kieSession.getFactHandles(
                obj -> obj instanceof ViralTopicAlert);
        return handles.size();
    }

    private void shutdown() {
        running = false;
        logger.info("Shutting down benchmark...");

        if (eventSource != null) {
            eventSource.stop();
        }

        if (kieSession != null) {
            kieSession.dispose();
        }

        if (traceLogger != null) {
            traceLogger.close();
        }
    }

    /**
     * Listener to track rule firings.
     */
    private class RuleFiringListener extends DefaultAgendaEventListener {
        @Override
        public void afterMatchFired(AfterMatchFiredEvent event) {
            if (config.isVerbose()) {
                String ruleName = event.getMatch().getRule().getName();
                if (ruleName.contains("Viral")) {
                    logger.debug("Rule fired: {}", ruleName);
                }
            }
        }
    }

    public static void main(String[] args) {
        CepBenchmarkConfig config;

        if (args.length > 0) {
            long duration = Long.parseLong(args[0]);
            config = new CepBenchmarkConfig(
                    duration,
                    "rules/graph_partitioning_benchmark.drl",
                    "https://stream.wikimedia.org/v2/stream/recentchange",
                    true);
        } else {
            config = CepBenchmarkConfig.getDefault();
        }

        WikimediaCepBenchmark benchmark = new WikimediaCepBenchmark(config);
        benchmark.run();
    }
}
