package org.kie.benchmark.cep.riperis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.kie.benchmark.cep.riperis.util.CepBenchmarkConfig;
import org.kie.benchmark.cep.riperis.util.CepSessionFactory;
import org.kie.benchmark.cep.riperis.util.RipeRisEventSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main benchmark runner for Ripe RIS CEP.
 * Adapted from WikimediaCepBenchmark.
 */
public class RipeRisCepBenchmark {
    private static final Logger logger = LoggerFactory.getLogger(RipeRisCepBenchmark.class);

    // --- Configuration and Factories ---
    private final CepBenchmarkConfig config;
    private final CepSessionFactory sessionFactory;

    // --- Drools Sessions ---
    private KieSession kieSession; // Single session for baseline

    // --- Metrics ---
    private final AtomicLong eventsIngested = new AtomicLong(0);
    private final AtomicLong rulesFired = new AtomicLong(0);
    private final AtomicLong alertsGenerated = new AtomicLong(0);

    // --- Execution Control ---
    private volatile boolean running = false;
    private RipeRisEventSource eventSource;

    public RipeRisCepBenchmark(CepBenchmarkConfig config) {
        this.config = config;
        this.sessionFactory = new CepSessionFactory(config.getRulesPath());
    }

    public void run() {
        if (config.isPartitioned()) {
            logger.warn("Partitioned mode not yet implemented for Ripe RIS. Running baseline instead.");
            runBaseline();
        } else {
            runBaseline();
        }
    }

    private void runBaseline() {
        logger.info("Starting BASELINE Mode...");
        running = true;
        kieSession = sessionFactory.createSession();
        kieSession.addEventListener(new RuleFiringListener());

        Thread firingThread = new Thread(this::fireRulesBaseline, "baseline-firing");
        firingThread.setDaemon(true);
        firingThread.start();

        startIngestion();
        waitAndShutdown();
    }

    private void startIngestion() {
        eventSource = new RipeRisEventSource(config.getRisLiveUrl());
        eventSource.start(this::handleEvent);

        ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        statsExecutor.scheduleAtFixedRate(this::reportStats, 10, 10, TimeUnit.SECONDS);
    }

    private void handleEvent(RisMessage event) {
        if (!running)
            return;
        try {
            kieSession.insert(event);
            eventsIngested.incrementAndGet();
        } catch (Exception e) {
            logger.error("Error inserting event", e);
        }
    }

    private void fireRulesBaseline() {
        while (running) {
            rulesFired.addAndGet(kieSession.fireAllRules());
            try {
                Thread.sleep(10);
            } catch (Exception e) {
            }
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

        setupBaselineReplay();

        long startTime = System.currentTimeMillis();

        try (BufferedReader reader = new BufferedReader(new FileReader(new File(inputFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> envelope = mapper.readValue(line, Map.class);
                RisMessage e = RisMessage.fromRisLiveEnvelope(envelope);
                if (e != null) {
                    processReplayEvent(e);
                    eventsIngested.incrementAndGet();
                }
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
        kieSession = sessionFactory.createSession(true); // True for pseudo-clock
        kieSession.addEventListener(new RuleFiringListener());
    }

    private void processReplayEvent(RisMessage event) {
        advanceClock(kieSession, (long) (event.getTimestamp() * 1000));
        kieSession.insert(event);
        rulesFired.addAndGet(kieSession.fireAllRules());
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
        logger.info("Events/sec: {}",
                String.format("%.2f", eventsIngested.get() / (durationSeconds > 0 ? durationSeconds : 1)));
        logger.info("========================================");
    }

    private void shutdown() {
        running = false;
        if (kieSession != null)
            kieSession.dispose();
        if (eventSource != null)
            eventSource.stop();
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

    public static void main(String[] args) {
        CepBenchmarkConfig config = CepBenchmarkConfig.getDefault();
        RipeRisCepBenchmark benchmark = new RipeRisCepBenchmark(config);
        benchmark.run();
    }
}
