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
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.kie.api.time.SessionPseudoClock;
import java.util.function.LongConsumer;

/**
 * Main benchmark runner for Wikimedia CEP.
 * Ingests real-time events, processes them through Drools CEP,
 * and tracks performance metrics.
 */
public class WikimediaCepBenchmark {
    private static final Logger logger = LoggerFactory.getLogger(WikimediaCepBenchmark.class);

    private final CepBenchmarkConfig config;
    private final CepSessionFactory sessionFactory;
    private final CepPartitionedSessionFactory partitionedSessionFactory;
    private final EventRouter eventRouter;
    
    // Baseline Session
    private KieSession kieSession;
    
    // Partitioned Sessions
    private Map<ClusterId, KieSession> partitionedSessions;
    private FactForwardingListener forwardingListener;
    private EnumSet<ClusterId> activeClusters = EnumSet.noneOf(ClusterId.class);

    private WikimediaEventSource eventSource;

    private final AtomicLong eventsIngested = new AtomicLong(0);
    private final AtomicLong rulesFired = new AtomicLong(0);
    private final AtomicLong alertsGenerated = new AtomicLong(0);

    private volatile boolean running = false;

    private MiningTraceLogger traceLogger;

    public WikimediaCepBenchmark(CepBenchmarkConfig config) {
        this.config = config;
        if (config.isPartitioned()) {
            this.sessionFactory = null;
            this.partitionedSessionFactory = new CepPartitionedSessionFactory();
            this.eventRouter = new EventRouter();
        } else {
            this.sessionFactory = new CepSessionFactory(config.getRulesPath());
            this.partitionedSessionFactory = null;
            this.eventRouter = null;
        }
    }

    /**
     * Run the benchmark for the configured duration.
     */
    public void run() {
        logger.info("========================================");
        logger.info("Wikimedia CEP Benchmark");
        logger.info("Duration: {} minutes", config.getDurationMinutes());
        logger.info("Mode: {}", config.isPartitioned() ? "PARTITIONED" : "BASELINE");
        logger.info("Rules: {}", config.getRulesPath());
        logger.info("Stream: {}", config.getWikimediaUrl());
        logger.info("========================================");

        running = true;

        if (config.isPartitioned()) {
            initializePartitionedSessions(false);
        } else {
            initializeBaselineSession(false);
        }

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
        reportFinalStats(config.getDurationMinutes() * 60.0);
    }
    
    public void record(String outputFile) {
        logger.info("Starting Recording to: {}", outputFile);
        EventRecorder recorder = new EventRecorder(outputFile);
        recorder.start();
        
        running = true;
        eventSource = new WikimediaEventSource(config.getWikimediaUrl());
        eventSource.start(event -> {
            recorder.record(event);
            eventsIngested.incrementAndGet();
            if (eventsIngested.get() % 100 == 0) {
                logger.info("Recorded {} events", eventsIngested.get());
            }
        });
        
        try {
            Thread.sleep(config.getDurationMinutes() * 60 * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        eventSource.stop();
        recorder.stop();
        logger.info("Recording complete.");
    }
    
    public void replay(String inputFile) {
        logger.info("Starting Replay from: {}", inputFile);
        EventReplayer replayer = new EventReplayer(inputFile);
        replayer.loadEvents();
        
        running = true;
        
        if (config.isPartitioned()) {
            initializePartitionedSessions(true);
        } else {
            initializeBaselineSession(true);
        }
        
        // Define Time Advancer
        LongConsumer timeAdvancer;
        if (config.isPartitioned()) {
            timeAdvancer = (time) -> {
                // Lazy advancement: We don't advance all sessions here.
                // Advancement for pipeline sessions happens in eventProcessor.
                // Advancement for Correlation happens in FactForwardingListener.
            };
        } else {
            timeAdvancer = (time) -> {
                SessionPseudoClock clock = kieSession.getSessionClock();
                clock.advanceTime(time, TimeUnit.MILLISECONDS);
            };
        }
        
        // Define Event Processor
        java.util.function.BiConsumer<WikiEvent, Long> eventProcessor;
        if (config.isPartitioned()) {
             eventProcessor = (event, time) -> {
                  activeClusters.clear();
                  forwardingListener.reset();
                  EnumSet<ClusterId> clusters = eventRouter.route(event);
                  for (ClusterId id : clusters) {
                      KieSession session = partitionedSessions.get(id);
                      if (session != null) {
                          // Lazy clock advancement for active pipeline session
                          SessionPseudoClock clock = session.getSessionClock();
                          long clockTime = clock.getCurrentTime();
                          if (time > clockTime) {
                              clock.advanceTime(time - clockTime, TimeUnit.MILLISECONDS);
                          }
                          session.insert(event);
                          activeClusters.add(id);
                      }
                  }
                  eventsIngested.incrementAndGet();
             };
        } else {
            eventProcessor = (event, time) -> {
                kieSession.insert(event);
                eventsIngested.incrementAndGet();
            };
        }
        
        // Define Rule Firer
        Runnable ruleFirer;
        if (config.isPartitioned()) {
            ruleFirer = () -> {
                int fired = 0;
                // 1. Fire active pipeline sessions
                for (ClusterId id : activeClusters) {
                    fired += partitionedSessions.get(id).fireAllRules();
                }
                
                // 2. Fire Correlation if facts were forwarded
                if (forwardingListener.hasForwarded()) {
                    fired += partitionedSessions.get(ClusterId.S_CORRELATION).fireAllRules();
                }
                
                rulesFired.addAndGet(fired);
            };
        } else {
            ruleFirer = () -> {
                int fired = kieSession.fireAllRules();
                rulesFired.addAndGet(fired);
            };
        }
        
        // Run Replay
        long start = System.nanoTime();
        replayer.replay(timeAdvancer, eventProcessor, ruleFirer);
        long end = System.nanoTime();
        double durationSeconds = (end - start) / 1_000_000_000.0;
        
        shutdown();
        reportFinalStats(durationSeconds);
    }
    
    private void initializeBaselineSession(boolean pseudoClock) {
        // Initialize Drools session
        kieSession = sessionFactory.createSession(pseudoClock);
        kieSession.addEventListener(new RuleFiringListener());

        // Initialize Mining Trace Logger
        traceLogger = new MiningTraceLogger("mining_trace.csv");
        traceLogger.startNewTransaction();
        kieSession.addEventListener(traceLogger);
    }
    
    private void initializePartitionedSessions(boolean pseudoClock) {
        partitionedSessions = partitionedSessionFactory.createSessions(pseudoClock);
        
        KieSession correlationSession = partitionedSessions.get(ClusterId.S_CORRELATION);
        if (correlationSession == null) {
            throw new RuntimeException("S_CORRELATION session missing from partitioned sessions map");
        }

        // Initialize Fact Forwarding Listener
        this.forwardingListener = new FactForwardingListener(correlationSession);

        // Initialize Mining Trace Logger (Single shared instance for all partitions)
        traceLogger = new MiningTraceLogger("mining_trace.csv");
        traceLogger.startNewTransaction(); // Start global transaction for this run
        
        // Add listener to all sessions
        RuleFiringListener firingListener = new RuleFiringListener();
        for (Map.Entry<ClusterId, KieSession> entry : partitionedSessions.entrySet()) {
            ClusterId id = entry.getKey();
            KieSession session = entry.getValue();
            
            session.addEventListener(firingListener);
            session.addEventListener(traceLogger); // Shared trace logger
            
            // Attached forwarding listener ONLY to pipeline sessions
            if (id != ClusterId.S_CORRELATION) {
                session.addEventListener(forwardingListener);
            }
        }
    }

    private void handleEvent(WikiEvent event) {
        if (!running)
            return;

        try {
            if (config.isPartitioned()) {
                // Route event
                EnumSet<ClusterId> clusters = eventRouter.route(event);
                
                if (!clusters.isEmpty()) {
                    for (ClusterId id : clusters) {
                        KieSession session = partitionedSessions.get(id);
                        if (session != null) {
                            session.insert(event);
                        }
                    }
                }
            } else {
                kieSession.insert(event);
            }
            
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
                int fired = 0;
                if (config.isPartitioned()) {
                    for (KieSession session : partitionedSessions.values()) {
                        fired += session.fireAllRules();
                    }
                } else {
                    fired = kieSession.fireAllRules();
                }
                
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
        // Stats represent a snapshot
        logger.info("Stats - Events: {}, Rules Fired: {}, Viral Alerts: {}",
                eventsIngested.get(), rulesFired.get(), alertsGenerated.get());
    }

    private void reportFinalStats(double durationSeconds) {
        long totalEvents = eventsIngested.get();
        long totalRulesFired = rulesFired.get();
        long totalAlerts = alertsGenerated.get();

        double eventsPerSecond = durationSeconds > 0 ? totalEvents / durationSeconds : 0;
        double rulesPerSecond = durationSeconds > 0 ? totalRulesFired / durationSeconds : 0;

        logger.info("========================================");
        logger.info("FINAL BENCHMARK RESULTS ({})", config.isPartitioned() ? "PARTITIONED" : "BASELINE");
        logger.info("========================================");
        logger.info("Total Events Ingested: {}", totalEvents);
        logger.info("Total Rules Fired: {}", totalRulesFired);
        logger.info("Total Viral Alerts: {}", totalAlerts);
        logger.info("Duration: {} seconds", String.format("%.2f", durationSeconds));
        logger.info("Events/sec: {}", String.format("%.2f", eventsPerSecond));
        logger.info("Rules/sec: {}", String.format("%.2f", rulesPerSecond));
        logger.info("========================================");
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
        
        if (partitionedSessions != null) {
            for (KieSession session : partitionedSessions.values()) {
                session.dispose();
            }
        }

        if (traceLogger != null) {
            traceLogger.close();
        }
    }

    /**
     * Listener to track rule firings and count alerts.
     */
    private class RuleFiringListener extends DefaultAgendaEventListener {
        @Override
        public void afterMatchFired(AfterMatchFiredEvent event) {
            String ruleName = event.getMatch().getRule().getName();
            
            // Increment alerts for terminal rules
            if (ruleName.endsWith("_Log") || ruleName.endsWith("_Report") || 
                ruleName.endsWith("_Track") || ruleName.endsWith("_Cache") ||
                ruleName.endsWith("_Notify")) {
                alertsGenerated.incrementAndGet();
            }

            if (config.isVerbose()) {
                if (ruleName.contains("Detect") || ruleName.contains("Complete") || ruleName.contains("Log") || ruleName.contains("Report")) {
                    logger.debug("Rule fired: {}", ruleName);
                }
            }
        }
    }

    public static void main(String[] args) {
        ContentModerationRunner.main(args);
    }
}
