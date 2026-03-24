package bench.opensky.replay;

import bench.opensky.model.*;
import org.drools.compiler.kie.builder.impl.DrlProject;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * CEP stream engine: ingests one {@link OpenSkyStateVector} at a time,
 * advances the pseudo-clock to the event's timestamp, and fires all rules.
 *
 * <p>Fact lifecycle is handled entirely by {@code @Expires} annotations on
 * the model POJOs — no manual retraction is needed.</p>
 */
public class OpenSkyReplayEngine {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSkyReplayEngine.class);

    private KieBase kieBase;
    private KieSession session;
    private SessionPseudoClock pseudoClock;
    private CsvLoggingRuleListener csvLogger;
    private ProfilingRuleListener profilingLogger;
    private CausalTraceListener causalTraceListener;

    /** Timestamp (ms) of the last event ingested; used to advance the clock monotonically. */
    private long lastEventTimeMs = -1L;

    public OpenSkyReplayEngine() { }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Build the KieBase from the classpath DRL and create a pseudo-clock KieSession.
     * Inserts a singleton {@link Params} fact so rules that depend on it are ready.
     */
    public void init() {
        LOG.info("Building KieBase from DRL...");

        String drl = loadDrl("airTraffick_rules.drl");

        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules/airTraffick_rules.drl", drl);

        KieBuilder kb = ks.newKieBuilder(kfs).buildAll(DrlProject.class);
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            for (Message msg : results.getMessages(Message.Level.ERROR)) {
                LOG.error("DRL build error: {}", msg);
            }
            throw new RuntimeException("DRL build failed with errors. See logs.");
        }
        if (results.hasMessages(Message.Level.WARNING)) {
            for (Message msg : results.getMessages(Message.Level.WARNING)) {
                LOG.warn("DRL build warning: {}", msg);
            }
        }

        KieModule km = kb.getKieModule();
        KieContainer kc = ks.newKieContainer(km.getReleaseId());

        // STREAM mode — required for @Expires / temporal reasoning
        org.kie.api.KieBaseConfiguration kbConfig = ks.newKieBaseConfiguration();
        kbConfig.setOption(EventProcessingOption.STREAM);
        kieBase = kc.newKieBase(kbConfig);

        // Pseudo clock — we drive time ourselves
        KieSessionConfiguration config = ks.newKieSessionConfiguration();
        config.setOption(ClockTypeOption.PSEUDO);
        session = kieBase.newKieSession(config, null);

        pseudoClock = session.getSessionClock();
        session.setGlobal("clock", pseudoClock);

        LOG.info("KieSession ready (pseudo clock at {}ms)", pseudoClock.getCurrentTime());
    }

    /**
     * Ingest a single state-vector event into the CEP engine.
     *
     * <ol>
     *   <li>Advance the pseudo-clock to {@code sv.getSnapshotTime()} (seconds → ms),
     *       which triggers expiration of stale derived facts.</li>
     *   <li>Insert the event — {@code @Expires("10s")} on {@link OpenSkyStateVector}
     *       ensures it auto-expires without any manual retraction.</li>
     *   <li>Fire all rules and return the activation count.</li>
     * </ol>
     *
     * @param sv the state vector to ingest
     * @return number of rule activations fired
     */
    public int ingestEvent(OpenSkyStateVector sv) {
        // Advance clock monotonically
        long targetMs = sv.getSnapshotTime() * 1000L;
        if (targetMs > lastEventTimeMs) {
            long nowMs = pseudoClock.getCurrentTime();
            if (targetMs > nowMs) {
                pseudoClock.advanceTime(targetMs - nowMs, TimeUnit.MILLISECONDS);
            }
            lastEventTimeMs = targetMs;
        }

        // Insert — lifecycle managed by @Expires
        session.insert(sv);

        // Fire — capped to prevent runaway activations on dense timestamps
        return session.fireAllRules(5000);
    }

    /** Dispose the KieSession, releasing all resources. */
    public void dispose() {
        if (session != null) {
            session.dispose();
            session = null;
        }
        if (csvLogger != null) {
            csvLogger.close();
            csvLogger = null;
        }
        if (causalTraceListener != null) {
            causalTraceListener.close();
            causalTraceListener = null;
        }
        lastEventTimeMs = -1L;
    }

    /**
     * Optional: Enable CSV tracing of rule firings and Alerts. 
     * Must be called AFTER init() so the SessionPseudoClock is available, 
     * but BEFORE ingestEvent().
     */
    public void enableCsvLogging(String alertsFile, String rulesFile) {
        if (session == null || pseudoClock == null) {
            throw new IllegalStateException("Engine must be init() before enabling CSV logging");
        }
        this.csvLogger = new CsvLoggingRuleListener(alertsFile, rulesFile, pseudoClock);
        session.addEventListener(csvLogger.getRuleRuntimeEventListener());
        session.addEventListener(csvLogger.getAgendaEventListener());
        LOG.info("CSV Logging enabled: alerts={} rules={}", alertsFile, rulesFile);
    }

    /**
     * Optional: Enable CPU profiling of individual rule actions.
     */
    public void enableProfiling() {
        if (session == null) {
            throw new IllegalStateException("Engine must be init() before enabling profiling");
        }
        this.profilingLogger = new ProfilingRuleListener();
        session.addEventListener(profilingLogger);
        LOG.info("CPU Profiling enabled.");
    }

    public ProfilingRuleListener getProfilingLogger() {
        return profilingLogger;
    }

    /**
     * Optional: Enable causal trace logging to a JSON-lines file.
     * Each line is a structured event (FACT_INSERT, FACT_UPDATE, FACT_DELETE,
     * ACTIVATION_CREATED, ACTIVATION_FIRED) that captures full fact provenance.
     *
     * Must be called AFTER init() and BEFORE ingestEvent().
     *
     * @param outputFile path to the trace output file (will be overwritten)
     */
    public void enableCausalTracing(String outputFile) {
        if (session == null) {
            throw new IllegalStateException("Engine must be init() before enabling causal tracing");
        }
        this.causalTraceListener = new CausalTraceListener(outputFile);
        session.addEventListener(causalTraceListener.agendaListener());
        session.addEventListener(causalTraceListener.runtimeListener());
        LOG.info("Causal trace logging enabled → {}", outputFile);
    }

    public CausalTraceListener getCausalTraceListener() {
        return causalTraceListener;
    }

    // -------------------------------------------------------------------------
    // Accessors (used by tests / profiling)
    // -------------------------------------------------------------------------

    public KieSession getSession()          { return session; }
    public SessionPseudoClock getPseudoClock() { return pseudoClock; }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String loadDrl(String resourceName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new FileNotFoundException("DRL not found: " + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
