package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main benchmark runner for Wikimedia CEP.
 */
public class WikimediaCepBenchmark {
    private static final Logger logger = LoggerFactory.getLogger(WikimediaCepBenchmark.class);

    // --- Configuration and Factories ---
    private final CepBenchmarkConfig config;
    private final CepSessionFactory sessionFactory;
    private final CepPartitionedSessionFactory partitionedSessionFactory;

    // --- Drools Sessions ---
    private KieSession kieSession; // Single session for baseline
    private Map<ClusterId, KieSession> partitionedSessions; // Multi-sessions for partitioned
    private FactForwardingListener forwardingListener;

    // --- Metrics ---
    private final AtomicLong eventsIngested = new AtomicLong(0);
    private final AtomicLong rulesFired = new AtomicLong(0);
    private final AtomicLong alertsGenerated = new AtomicLong(0);

    // --- Execution Control ---
    private volatile boolean running = false;
    private MiningTraceLogger traceLogger;
    private WikimediaEventSource eventSource;

    public WikimediaCepBenchmark(CepBenchmarkConfig config) {
        this.config = config;
        this.sessionFactory = new CepSessionFactory(config.getRulesPath());
        this.partitionedSessionFactory = new CepPartitionedSessionFactory();
    }

    public void run() {
        if (config.isPartitioned()) {
            runPartitioned();
        } else {
            runBaseline();
        }
    }

    private void runBaseline() {
        logger.info("Starting BASELINE Mode...");
        running = true;
        kieSession = sessionFactory.createSession();
        kieSession.addEventListener(new RuleFiringListener());
        setupTraceLogger(kieSession);
        
        Thread firingThread = new Thread(this::fireRulesBaseline, "baseline-firing");
        firingThread.setDaemon(true);
        firingThread.start();

        startIngestion();
        waitAndShutdown();
    }

    private void runPartitioned() {
        logger.info("Starting PARTITIONED Mode with Correlation Session...");
        running = true;
        partitionedSessions = partitionedSessionFactory.createSessions(false);
        
        KieSession correlationSession = partitionedSessions.get(ClusterId.S_CORRELATION);
        forwardingListener = new FactForwardingListener(correlationSession);
        
        for (Map.Entry<ClusterId, KieSession> entry : partitionedSessions.entrySet()) {
            KieSession session = entry.getValue();
            session.addEventListener(new RuleFiringListener());
            setupTraceLogger(session);
            
            if (entry.getKey() != ClusterId.S_CORRELATION) {
                session.addEventListener(forwardingListener);
            }
        }

        Thread firingThread = new Thread(this::fireRulesPartitioned, "partitioned-firing");
        firingThread.setDaemon(true);
        firingThread.start();

        startIngestion();
        waitAndShutdown();
    }

    private void setupTraceLogger(KieSession session) {
        if (traceLogger == null) {
            traceLogger = new MiningTraceLogger("mining_trace.csv");
            traceLogger.startNewTransaction();
        }
        session.addEventListener(traceLogger);
    }

    private void startIngestion() {
        eventSource = new WikimediaEventSource(config.getWikimediaUrl());
        eventSource.start(this::handleEvent);
        
        ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(this::reportStats, 10, 10, TimeUnit.SECONDS);
    }

    private void handleEvent(WikiEvent event) {
        if (!running) return;
        try {
            // Convert to milliseconds for Drools CEP
            event.setTimestamp(event.getTimestamp() * 1000);
            
            if (config.isPartitioned()) {
                ClusterId cluster = EventRouter.route(event);
                partitionedSessions.get(cluster).insert(event);
            } else {
                kieSession.insert(event);
            }
            eventsIngested.incrementAndGet();
        } catch (Exception e) {
            logger.error("Error inserting event", e);
        }
    }

    private void fireRulesBaseline() {
        while (running) {
            rulesFired.addAndGet(kieSession.fireAllRules());
            try { Thread.sleep(10); } catch (Exception e) {}
        }
    }

    private void fireRulesPartitioned() {
        while (running) {
            long fired = 0;
            forwardingListener.reset();
            
            for (Map.Entry<ClusterId, KieSession> entry : partitionedSessions.entrySet()) {
                if (entry.getKey() != ClusterId.S_CORRELATION) {
                    fired += entry.getValue().fireAllRules();
                }
            }
            
            if (forwardingListener.hasForwarded()) {
                fired += partitionedSessions.get(ClusterId.S_CORRELATION).fireAllRules();
            }
            
            rulesFired.addAndGet(fired);
            try { Thread.sleep(10); } catch (Exception e) {}
        }
    }

    private void waitAndShutdown() {
        try {
            Thread.sleep(config.getDurationMinutes() * 60 * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        shutdown();
        reportFinalStats();
    }

    public void replay(String inputFile) {
        logger.info("Starting Replay from {} (Partitioned={})", inputFile, config.isPartitioned());
        ObjectMapper mapper = new ObjectMapper();
        
        if (config.isPartitioned()) setupPartitionedReplay();
        else setupBaselineReplay();

        long startTime = System.currentTimeMillis();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(inputFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                WikiEvent e = mapper.readValue(line, WikiEvent.class);
                processReplayEvent(e);
                eventsIngested.incrementAndGet();
            }
        } catch (Exception e) {
            logger.error("Replay failed", e);
        }

        running = false;
        long duration = System.currentTimeMillis() - startTime;
        reportFinalStatsReplay(duration);
        shutdown();
    }

    private void setupBaselineReplay() {
        running = true;
        kieSession = sessionFactory.createSession(true);
        kieSession.addEventListener(new RuleFiringListener());
        setupTraceLogger(kieSession);
    }

    private void setupPartitionedReplay() {
        running = true;
        partitionedSessions = partitionedSessionFactory.createSessions(true);
        KieSession correlationSession = partitionedSessions.get(ClusterId.S_CORRELATION);
        forwardingListener = new FactForwardingListener(correlationSession);
        
        for (Map.Entry<ClusterId, KieSession> entry : partitionedSessions.entrySet()) {
            KieSession session = entry.getValue();
            session.addEventListener(new RuleFiringListener());
            setupTraceLogger(session);
            if (entry.getKey() != ClusterId.S_CORRELATION) {
                session.addEventListener(forwardingListener);
            }
        }
    }

    private void processReplayEvent(WikiEvent event) {
        if (config.isPartitioned()) {
            ClusterId cluster = EventRouter.route(event);
            KieSession session = partitionedSessions.get(cluster);
            advanceClock(session, event.getTimestamp());
            session.insert(event);
            
            forwardingListener.reset();
            rulesFired.addAndGet(session.fireAllRules());
            
            if (forwardingListener.hasForwarded()) {
                advanceClock(partitionedSessions.get(ClusterId.S_CORRELATION), event.getTimestamp());
                rulesFired.addAndGet(partitionedSessions.get(ClusterId.S_CORRELATION).fireAllRules());
            }
        } else {
            advanceClock(kieSession, event.getTimestamp());
            kieSession.insert(event);
            rulesFired.addAndGet(kieSession.fireAllRules());
        }
    }

    private void advanceClock(KieSession session, long targetTime) {
        SessionPseudoClock clock = session.getSessionClock();
        long currentTime = clock.getCurrentTime();
        if (targetTime > currentTime) {
            clock.advanceTime(targetTime - currentTime, TimeUnit.MILLISECONDS);
        }
    }

    private void reportStats() {
        logger.info("Stats - Events: {}, Rules Fired: {}, Terminal Alerts: {}",
                eventsIngested.get(), rulesFired.get(), alertsGenerated.get());
    }

    private void reportFinalStats() {
        reportFinalStatsReplay(config.getDurationMinutes() * 60 * 1000);
    }

    private void reportFinalStatsReplay(long durationMs) {
        double durationSeconds = durationMs / 1000.0;
        logger.info("========================================");
        logger.info("FINAL BENCHMARK RESULTS");
        logger.info("========================================");
        logger.info("Total Events Ingested: {}", eventsIngested.get());
        logger.info("Total Rules Fired: {}", rulesFired.get());
        logger.info("Total Terminal Alerts: {}", alertsGenerated.get());
        logger.info("Duration: {} seconds", String.format("%.2f", durationSeconds));
        logger.info("Events/sec: {}", String.format("%.2f", eventsIngested.get() / (durationSeconds > 0 ? durationSeconds : 1)));
        logger.info("========================================");
    }

    public void record(String outputFile) {
        logger.info("Recording functionality not implemented in this version.");
    }

    private void shutdown() {
        running = false;
        if (kieSession != null) kieSession.dispose();
        if (partitionedSessions != null) {
            partitionedSessions.values().forEach(KieSession::dispose);
        }
        if (traceLogger != null) traceLogger.close();
        if (eventSource != null) eventSource.stop();
    }

    private class RuleFiringListener extends DefaultAgendaEventListener {
        @Override
        public void afterMatchFired(AfterMatchFiredEvent event) {
            String ruleName = event.getMatch().getRule().getName();
            if (ruleName.endsWith("_Log") || ruleName.endsWith("_Report") || ruleName.endsWith("_Notify")) {
                alertsGenerated.incrementAndGet();
            }
        }
    }
}
