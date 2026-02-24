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
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages a KieSession with pseudo clock and replays OpenSky snapshots.
 */
public class OpenSkyReplayEngine {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSkyReplayEngine.class);

    public enum UpdateStrategy {
        /** Upsert by icao24 — retract old fact for same icao24 before insert. */
        UPSERT_BY_ICAO24,
        /** Insert only — no retraction of previous state vectors. */
        INSERT_ONLY
    }

    public enum CleanupStrategy {
        /** No cleanup between snapshots. */
        NONE,
        /** Retract all state vectors from the previous snapshot before inserting new ones. */
        RETRACT_PREVIOUS_SNAPSHOT
    }

    public enum FireStrategy {
        /** Fire all rules after each snapshot. */
        FIRE_ALL_PER_SNAPSHOT
    }

    private KieBase kieBase;
    private KieSession session;
    private SessionPseudoClock pseudoClock;

    private final Map<String, FactHandle> handlesByIcao24 = new HashMap<>();
    private final List<FactHandle> previousSnapshotHandles = new ArrayList<>();

    private long lastSnapshotTimeSec = -1;

    private UpdateStrategy updateStrategy = UpdateStrategy.UPSERT_BY_ICAO24;
    private CleanupStrategy cleanupStrategy = CleanupStrategy.RETRACT_PREVIOUS_SNAPSHOT;
    private FireStrategy fireStrategy = FireStrategy.FIRE_ALL_PER_SNAPSHOT;

    public OpenSkyReplayEngine() {
    }

    /**
     * Initialize the engine: build KieBase from classpath DRL and create KieSession.
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

        // Configure KieBase for STREAM mode (required for @expires to work)
        org.kie.api.KieBaseConfiguration kbConfig = ks.newKieBaseConfiguration();
        kbConfig.setOption(EventProcessingOption.STREAM);
        kieBase = kc.newKieBase(kbConfig);

        // Create session with pseudo clock
        KieSessionConfiguration config = ks.newKieSessionConfiguration();
        config.setOption(ClockTypeOption.PSEUDO);
        session = kieBase.newKieSession(config, null);

        pseudoClock = session.getSessionClock();
        session.setGlobal("clock", pseudoClock);

        LOG.info("KieSession ready (pseudo clock at {}ms)", pseudoClock.getCurrentTime());
    }

    /**
     * Replay a single snapshot (list of state vectors for one snapshot_time).
     */
    public int replaySnapshot(List<OpenSkyStateVector> snapshot) {
        if (snapshot.isEmpty()) return 0;

        long snapshotTimeSec = snapshot.get(0).getSnapshotTime();

        // Advance pseudo clock
        if (snapshotTimeSec > lastSnapshotTimeSec) {
            long nowMs = pseudoClock.getCurrentTime();
            long targetMs = snapshotTimeSec * 1000L;
            if (targetMs > nowMs) {
                pseudoClock.advanceTime(targetMs - nowMs, TimeUnit.MILLISECONDS);
            }
            lastSnapshotTimeSec = snapshotTimeSec;
        }

        // Cleanup
        if (cleanupStrategy == CleanupStrategy.RETRACT_PREVIOUS_SNAPSHOT) {
            for (FactHandle fh : previousSnapshotHandles) {
                try { session.delete(fh); } catch (Exception ignore) { }
            }
            previousSnapshotHandles.clear();
        }

        // Insert new state vectors
        for (OpenSkyStateVector sv : snapshot) {
            if (updateStrategy == UpdateStrategy.UPSERT_BY_ICAO24) {
                FactHandle old = handlesByIcao24.get(sv.getIcao24());
                if (old != null) {
                    try { session.delete(old); } catch (Exception ignore) { }
                }
                FactHandle fh = session.insert(sv);
                handlesByIcao24.put(sv.getIcao24(), fh);
                previousSnapshotHandles.add(fh);
            } else {
                FactHandle fh = session.insert(sv);
                previousSnapshotHandles.add(fh);
            }
        }

        // Fire rules
        int fired = 0;
        if (fireStrategy == FireStrategy.FIRE_ALL_PER_SNAPSHOT) {
            fired = session.fireAllRules();
        }

        return fired;
    }

    /**
     * Dispose the KieSession.
     */
    public void dispose() {
        if (session != null) {
            session.dispose();
            session = null;
        }
    }

    // ---- config ----

    public void setUpdateStrategy(UpdateStrategy updateStrategy) { this.updateStrategy = updateStrategy; }
    public void setCleanupStrategy(CleanupStrategy cleanupStrategy) { this.cleanupStrategy = cleanupStrategy; }
    public void setFireStrategy(FireStrategy fireStrategy) { this.fireStrategy = fireStrategy; }

    public KieSession getSession() { return session; }
    public SessionPseudoClock getPseudoClock() { return pseudoClock; }

    // ---- internal ----

    private String loadDrl(String resourceName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new FileNotFoundException("DRL not found: " + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
