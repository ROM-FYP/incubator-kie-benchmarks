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

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Runner for the Editorial Intelligence ruleset with join-heavy beta joins.
 * Uses LIVE Wikimedia stream and generates mining trace.
 * 
 * Output: join_heavy_traces.csv
 */
public class EditorialIntelligenceRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(EditorialIntelligenceRunner.class);
    private static final String RULES_PATH = "rules/wikimedia_clustered_moderation.drl";
    private static final String OUTPUT_TRACE = "join_heavy_traces.csv";
    private static final String WIKIMEDIA_URL = "https://stream.wikimedia.org/v2/stream/recentchange";
    
    private final AtomicLong eventsProcessed = new AtomicLong(0);
    private final AtomicLong rulesFired = new AtomicLong(0);
    
    private volatile boolean running = false;
    private KieSession kieSession;
    private WikimediaEventSource eventSource;
    private MiningTraceLogger traceLogger;
    
    private int maxEvents;
    private int durationSeconds;
    
    public static void main(String[] args) {
        int maxEvents = 500;      // Max events to process
        int durationSeconds = 60; // Max duration in seconds
        
        if (args.length > 0) {
            try {
                maxEvents = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid maxEvents, using default: 500");
            }
        }
        if (args.length > 1) {
            try {
                durationSeconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid durationSeconds, using default: 60");
            }
        }
        
        EditorialIntelligenceRunner runner = new EditorialIntelligenceRunner();
        runner.run(maxEvents, durationSeconds);
    }
    
    public void run(int maxEvents, int durationSeconds) {
        this.maxEvents = maxEvents;
        this.durationSeconds = durationSeconds;
        
        System.out.println("==============================================");
        System.out.println("Editorial Intelligence Benchmark - Join Heavy");
        System.out.println("==============================================");
        System.out.println("Rules: " + RULES_PATH);
        System.out.println("Stream: " + WIKIMEDIA_URL + " (LIVE)");
        System.out.println("Max Events: " + maxEvents);
        System.out.println("Duration: " + durationSeconds + " seconds");
        System.out.println("Output Trace: " + OUTPUT_TRACE);
        System.out.println("==============================================");
        System.out.println();
        System.out.println("Clusters:");
        System.out.println("  A. Bot Monitoring (Salience 600-590)");
        System.out.println("  B. Major Content (Salience 560-550)");
        System.out.println("  C. Minor Edits (Salience 520-510)");
        System.out.println("  D. Deletions (Salience 480-470)");
        System.out.println("  E. Medium Changes (Salience 440-430)");
        System.out.println();
        System.out.println("Beta Joins: Internal cluster joins on user/title");
        System.out.println("==============================================");
        
        running = true;
        
        // Use existing CepSessionFactory which handles STREAM mode properly
        CepSessionFactory sessionFactory = new CepSessionFactory(RULES_PATH);
        kieSession = sessionFactory.createSession();
        
        // Initialize Mining Trace Logger
        traceLogger = new MiningTraceLogger(OUTPUT_TRACE);
        traceLogger.startNewTransaction();
        kieSession.addEventListener(traceLogger);
        
        // Start background rule firing thread
        Thread firingThread = new Thread(this::fireRulesContinuously, "rule-firing");
        firingThread.setDaemon(true);
        firingThread.start();
        
        System.out.println("\nConnecting to LIVE Wikimedia stream...");
        
        // Start LIVE event ingestion
        eventSource = new WikimediaEventSource(WIKIMEDIA_URL);
        eventSource.start(this::handleEvent);
        
        // Run for configured duration or until max events reached
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        
        while (running && System.currentTimeMillis() < endTime && eventsProcessed.get() < maxEvents) {
            try {
                Thread.sleep(1000);
                System.out.println("Events: " + eventsProcessed.get() + ", Rules Fired: " + rulesFired.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Shutdown
        shutdown();
        
        // Report results
        System.out.println();
        System.out.println("==============================================");
        System.out.println("BENCHMARK COMPLETE");
        System.out.println("==============================================");
        System.out.println("Total Events Processed: " + eventsProcessed.get());
        System.out.println("Total Rules Fired: " + rulesFired.get());
        System.out.println("Trace File: " + OUTPUT_TRACE);
        System.out.println("==============================================");
    }
    
    private void handleEvent(WikiEvent event) {
        if (!running || eventsProcessed.get() >= maxEvents) {
            return;
        }
        
        try {
            // Start new transaction for each batch of events
            if (eventsProcessed.get() % 50 == 0) {
                traceLogger.startNewTransaction();
            }
            
            kieSession.insert(event);
            eventsProcessed.incrementAndGet();
            
        } catch (Exception e) {
            logger.error("Error inserting event", e);
        }
    }
    
    private void fireRulesContinuously() {
        while (running) {
            try {
                int fired = kieSession.fireAllRules(100); // Fire up to 100 rules per iteration
                if (fired > 0) {
                    rulesFired.addAndGet(fired);
                }
                Thread.sleep(10); // Brief pause to prevent CPU spin
            } catch (Exception e) {
                if (running) {
                    logger.error("Error firing rules", e);
                }
            }
        }
    }
    
    private void shutdown() {
        running = false;
        
        if (eventSource != null) {
            eventSource.stop();
        }
        
        // Final rule firing
        if (kieSession != null) {
            int finalFired = kieSession.fireAllRules();
            rulesFired.addAndGet(finalFired);
            kieSession.dispose();
        }
        
        if (traceLogger != null) {
            traceLogger.close();
        }
    }
}
