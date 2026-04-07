package org.kie.benchmark.binance.parallel;

import org.drools.core.time.SessionPseudoClock;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.benchmark.binance.model.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Barrier-free 4-cluster parallel rule execution engine.
 *
 * <p>
 * Creates one persistent {@link KieSession} per cluster (1-4).
 * Events are routed precisely by eventType to only the sessions that need them.
 * </p>
 */
public class ClusterParallelOrchestratorV2 {

    /** Sentinel event used to signal workers to stop. */
    private static final MarketEvent POISON_PILL = new MarketEvent("__STOP__", -1L, "__STOP__", 0, 0, 0, "");

    private final Map<Integer, KieSession> clusterSessions;
    private final Map<Integer, BlockingQueue<MarketEvent>> eventQueues;
    private final ExecutorService threadPool;
    
    // Fixed pool size for 4 clusters
    private static final int POOL_SIZE = 4;

    // Per-session metrics (populated after replayEvents)
    private final Map<Integer, Integer> perSessionFired = new LinkedHashMap<>();
    private final Map<Integer, Integer> perSessionEventsReceived = new LinkedHashMap<>();
    
    private final Map<String, Integer> emittedSignals = new ConcurrentHashMap<>();

    public Map<String, Integer> getEmittedSignals() {
        return emittedSignals;
    }

    /**
     * Creates the cluster-parallel orchestrator V2.
     *
     * @param fullDrlContent full taxonomy.drl content to be split
     * @param symbols        set of symbols to create bootstrap facts for
     */
    public ClusterParallelOrchestratorV2(String fullDrlContent, Set<String> symbols) {
        this.clusterSessions = new LinkedHashMap<>();
        this.eventQueues = new LinkedHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        System.out.println("[ClusterOrchestratorV2] Building " + POOL_SIZE + " cluster sessions");

        // 1. Generate per-cluster DRLs
        Map<Integer, String> drls = ClusterDrlGenerator.generateClusterDrls(fullDrlContent);

        // 2. Build sessions
        for (int clusterId = 1; clusterId <= POOL_SIZE; clusterId++) {
            String drl = drls.get(clusterId);
            if (drl == null || drl.isEmpty()) {
                throw new IllegalStateException("Failed to generate DRL for cluster " + clusterId);
            }

            KieSession session = buildSessionFromDrl(drl, clusterId);
            attachSignalListener(session);
            insertBootstrapFacts(session, symbols);
            session.fireAllRules(); // fire bootstrap rules
            
            clusterSessions.put(clusterId, session);
            eventQueues.put(clusterId, new LinkedBlockingQueue<>());
            
            System.out.println("[ClusterOrchestratorV2]   Cluster " + clusterId + " session ready");
        }
    }

    /**
     * Replays all events through barrier-free parallel cluster sessions.
     *
     * @param events the list of MarketEvents in time order
     * @return total number of rules fired across all sessions
     */
    public int replayEvents(List<MarketEvent> events) {
        perSessionFired.clear();
        perSessionEventsReceived.clear();

        // Start worker threads
        Map<Integer, Future<int[]>> futures = new LinkedHashMap<>();
        for (Map.Entry<Integer, KieSession> entry : clusterSessions.entrySet()) {
            int cid = entry.getKey();
            KieSession session = entry.getValue();
            BlockingQueue<MarketEvent> queue = eventQueues.get(cid);
            futures.put(cid, threadPool.submit(() -> drainAndFire(session, queue)));
        }

        // Main thread: route events to queues precisely
        for (MarketEvent event : events) {
            String type = event.getEventType();
            int targets = ClusterEventRouter.route(type);
            
            if (targets == 0) {
                // Event inherently not needed by any cluster (e.g. DECODE_ERROR, RECONNECT)
                continue;
            }

            try {
                if (ClusterEventRouter.targetsC1(targets)) eventQueues.get(1).put(event);
                if (ClusterEventRouter.targetsC2(targets)) eventQueues.get(2).put(event);
                if (ClusterEventRouter.targetsC3(targets)) eventQueues.get(3).put(event);
                if (ClusterEventRouter.targetsC4(targets)) eventQueues.get(4).put(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while enqueuing event", e);
            }
        }

        // Send poison pills to all 4 queues
        for (BlockingQueue<MarketEvent> q : eventQueues.values()) {
            try {
                q.put(POISON_PILL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Collect results
        int totalFired = 0;
        for (Map.Entry<Integer, Future<int[]>> entry : futures.entrySet()) {
            try {
                int[] result = entry.getValue().get();
                int fired = result[0];
                int received = result[1];
                totalFired += fired;
                perSessionFired.put(entry.getKey(), fired);
                perSessionEventsReceived.put(entry.getKey(), received);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Worker failed for session " + entry.getKey(), e);
            }
        }

        return totalFired;
    }

    /**
     * Worker loop: drain queue, advance clock, insert events, fire rules.
     */
    private int[] drainAndFire(KieSession session, BlockingQueue<MarketEvent> queue)
            throws InterruptedException {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;
        int received = 0;

        while (true) {
            MarketEvent event = queue.take();
            if (event == POISON_PILL)
                break;

            received++;
            
            long currentTime = clock.getCurrentTime();
            if (event.getTsMs() > currentTime) {
                clock.advanceTime(event.getTsMs() - currentTime,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            session.insert(event);
            fired += session.fireAllRules();
        }

        return new int[] { fired, received };
    }

    /**
     * Build a KieSession from a DRL string using programmatic KieFileSystem.
     * Configures STREAM mode so @expires annotations work.
     */
    private KieSession buildSessionFromDrl(String drl, int clusterId) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        String resourcePath = "src/main/resources/cluster_" + clusterId + ".drl";
        kfs.write(resourcePath, drl);

        // Use stable names for kbase and ksession so we can look them up
        long uid = System.nanoTime();
        String kbaseName = "cluster" + clusterId + "Base_" + uid;
        String ksessionName = "cluster" + clusterId + "Session_" + uid;

        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"" + kbaseName + "\""
                + " eventProcessingMode=\"stream\">\n"
                + "    <ksession name=\"" + ksessionName + "\"/>\n"
                + "  </kbase>\n"
                + "</kmodule>";
        kfs.write("src/main/resources/META-INF/kmodule.xml", kmoduleXml);

        ReleaseId rid = ks.newReleaseId("org.kie.cluster",
                "c-" + clusterId + "-" + uid, "1.0");
        kfs.generateAndWritePomXML(rid);

        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();

        if (kb.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("DRL compilation failed for cluster " + clusterId
                    + ": " + kb.getResults().getMessages(Message.Level.ERROR)
                    + "\n--- DRL (first 500 chars) ---\n"
                    + drl.substring(0, Math.min(500, drl.length())));
        }

        KieContainer kc = ks.newKieContainer(rid);
        KieSessionConfiguration cfg = ks.newKieSessionConfiguration();
        cfg.setOption(ClockTypeOption.PSEUDO);
        return kc.newKieSession(ksessionName, cfg);
    }

    private void attachSignalListener(KieSession session) {
        session.addEventListener(new org.kie.api.event.rule.DefaultRuleRuntimeEventListener() {
            @Override
            public void objectInserted(org.kie.api.event.rule.ObjectInsertedEvent event) {
                if (event.getObject() instanceof RiskSignal) {
                    RiskSignal s = (RiskSignal) event.getObject();
                    String key = s.getSymbol() + "-" + s.getKind() + "-" + s.getSeverity();
                    emittedSignals.merge(key, 1, Integer::sum);
                }
            }
        });
    }

    private void insertBootstrapFacts(KieSession session, Set<String> symbols) {
        for (String sym : symbols) {
            session.insert(new RiskConfig(sym));
            session.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
            session.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
        }
    }

    // =========================================================================
    // Metrics accessors
    // =========================================================================

    public Map<Integer, Integer> getPerSessionFired() {
        return Collections.unmodifiableMap(perSessionFired);
    }

    public Map<Integer, Integer> getPerSessionEventsReceived() {
        return Collections.unmodifiableMap(perSessionEventsReceived);
    }

    public int getPoolSize() {
        return POOL_SIZE;
    }

    public void dispose() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        clusterSessions.values().forEach(KieSession::dispose);
    }
}
