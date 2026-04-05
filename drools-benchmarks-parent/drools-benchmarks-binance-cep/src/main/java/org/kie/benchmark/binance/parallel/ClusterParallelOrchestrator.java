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
 * Barrier-free cluster-level parallel rule execution engine.
 *
 * <p>
 * Creates one persistent {@link KieSession} per Infomap cluster (1–6) plus one
 * fallback session. Each session has its own {@link BlockingQueue} and a
 * long-lived
 * worker thread that drains events independently.
 * </p>
 *
 * <h3>Why barrier-free?</h3>
 * <ul>
 * <li><b>Self-contained clusters</b>: Bridge rule duplication ensures no
 * cross-session
 * data dependencies.</li>
 * <li><b>No shared mutable state</b>: Each KieSession has its own Rete network,
 * pseudo-clock, and working memory.</li>
 * <li><b>Event ordering preserved</b>: Each session's BlockingQueue is FIFO —
 * events arrive in the same time-order as the baseline.</li>
 * <li><b>Performance</b>: Eliminates ~335K thread context switches compared to
 * per-event {@code invokeAll()} barriers.</li>
 * </ul>
 *
 * <h3>Execution flow</h3>
 * <ol>
 * <li>Constructor: build persistent KieSessions from DRL strings, insert
 * bootstrap facts</li>
 * <li>{@link #replayEvents}: start worker threads, route events to queues, send
 * poison pills, collect totals</li>
 * <li>Workers drain their queues independently: advance clock → insert →
 * fireAllRules</li>
 * </ol>
 */
public class ClusterParallelOrchestrator {

    /** Sentinel event used to signal workers to stop. */
    private static final MarketEvent POISON_PILL = new MarketEvent("__STOP__", -1L, "__STOP__", 0, 0, 0, "");

    /** Cluster ID assigned to the fallback session. */
    private static final int FALLBACK_ID = -1;

    /**
     * Maximum rules fired per event per cluster session.
     * Prevents infinite cascading from bridge-duplicated rule cycles
     * (e.g. FeedHealth modify loops). Baseline fires ~8 rules/event
     * across 110 rules, so 30 provides ~4x headroom per cluster for
     * legitimate forward chains while capping cascade storms.
     */
    private static final int MAX_FIRINGS_PER_EVENT = 30;

    private final Map<Integer, KieSession> clusterSessions;
    private final KieSession fallbackSession;
    private final Map<String, List<Integer>> routingTable;
    private final Map<Integer, BlockingQueue<MarketEvent>> eventQueues;
    private final ExecutorService threadPool;
    private final int poolSize;

    // Per-session metrics (populated after replayEvents)
    private final Map<Integer, Integer> perSessionFired = new LinkedHashMap<>();
    private final Map<Integer, Integer> perSessionEventsReceived = new LinkedHashMap<>();

    /**
     * Creates the cluster-parallel orchestrator.
     *
     * <p>
     * Builds a persistent {@link KieSession} for each Infomap cluster and the
     * fallback,
     * inserts bootstrap facts, and fires initial rules. Sessions remain alive
     * across all events.
     * </p>
     *
     * @param plan     the ClusterPlan containing clusters, fallback, and routing
     *                 table
     * @param poolSize number of threads in the executor pool
     * @param symbols  set of symbols to create bootstrap facts for
     */
    public ClusterParallelOrchestrator(PartitionPlan.ClusterPlan plan, int poolSize,
            Set<String> symbols) {
        this.poolSize = poolSize;
        this.clusterSessions = new LinkedHashMap<>();
        this.eventQueues = new LinkedHashMap<>();

        System.out.println("[ClusterOrchestrator] Building " + plan.getClusters().size()
                + " cluster sessions + 1 fallback (pool=" + poolSize + ")");

        // Build cluster sessions
        for (PartitionPlan.SelfContainedCluster c : plan.getClusters()) {
            KieSession session = buildSessionFromDrl(c.getDrlContent(), c.getClusterId());
            insertBootstrapFacts(session, symbols);
            session.fireAllRules();
            clusterSessions.put(c.getClusterId(), session);
            eventQueues.put(c.getClusterId(), new LinkedBlockingQueue<>());
            System.out.println("[ClusterOrchestrator]   Cluster " + c.getClusterId()
                    + " (" + c.getName() + "): " + c.getAllRules().size() + " rules, session ready");
        }

        // Build fallback session
        PartitionPlan.SelfContainedCluster fb = plan.getFallbackCluster();
        fallbackSession = buildSessionFromDrl(fb.getDrlContent(), FALLBACK_ID);
        insertBootstrapFacts(fallbackSession, symbols);
        fallbackSession.fireAllRules();
        eventQueues.put(FALLBACK_ID, new LinkedBlockingQueue<>());
        System.out.println("[ClusterOrchestrator]   Fallback: " + fb.getAllRules().size()
                + " rules, session ready");

        this.routingTable = plan.getRoutingTable();
        this.threadPool = Executors.newFixedThreadPool(poolSize);
    }

    /**
     * Replays all events through barrier-free parallel cluster sessions.
     *
     * <p>
     * Flow:
     * </p>
     * <ol>
     * <li>Start a long-lived worker thread per session (drains its
     * BlockingQueue)</li>
     * <li>Main thread routes events to queues based on routing table (ns per
     * enqueue)</li>
     * <li>Fallback always receives every event</li>
     * <li>Send poison pills to all queues, then collect totals via
     * Future.get()</li>
     * </ol>
     *
     * @param events the list of MarketEvents in time order
     * @return total number of rules fired across all sessions
     */
    public int replayEvents(List<MarketEvent> events) {
        perSessionFired.clear();
        perSessionEventsReceived.clear();

        // Build combined session map (clusters + fallback)
        Map<Integer, KieSession> allSessions = new LinkedHashMap<>(clusterSessions);
        allSessions.put(FALLBACK_ID, fallbackSession);

        // Start worker threads
        Map<Integer, Future<int[]>> futures = new LinkedHashMap<>();
        for (Map.Entry<Integer, KieSession> entry : allSessions.entrySet()) {
            int cid = entry.getKey();
            KieSession session = entry.getValue();
            BlockingQueue<MarketEvent> queue = eventQueues.get(cid);
            futures.put(cid, threadPool.submit(() -> drainAndFire(cid, session, queue)));
        }

        // Main thread: route events to queues
        for (MarketEvent event : events) {
            // Broadcast to absolutely every queue for synchronization (Watermarks)
            for (BlockingQueue<MarketEvent> q : eventQueues.values()) {
                try {
                    q.put(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while enqueuing event", e);
                }
            }
        }

        // Send poison pills
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
     *
     * @return array of [totalFired, eventsReceived]
     */
    private int[] drainAndFire(int clusterId, KieSession session, BlockingQueue<MarketEvent> queue)
            throws InterruptedException {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;
        int received = 0;

        while (true) {
            MarketEvent event = queue.take();
            if (event == POISON_PILL)
                break;

            long currentTime = clock.getCurrentTime();
            if (event.getTsMs() > currentTime) {
                clock.advanceTime(event.getTsMs() - currentTime,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
            }

            // Determine if this cluster is actually a target for evaluating this event
            String eType = event.getEventType() != null ? event.getEventType() : "UNKNOWN";
            List<Integer> targets = routingTable.getOrDefault(eType, Collections.emptyList());
            boolean isTarget = (clusterId == FALLBACK_ID) || targets.contains(clusterId);

            if (isTarget) {
                received++;
                session.insert(event);
                fired += session.fireAllRules();
            }
        }

        return new int[] { fired, received };
    }

    /**
     * Build a KieSession from a DRL string using programmatic KieFileSystem.
     * Configures STREAM mode so @expires annotations work (events auto-retract).
     */
    private KieSession buildSessionFromDrl(String drl, int clusterId) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        String resourcePath = "src/main/resources/cluster_" + clusterId + ".drl";
        kfs.write(resourcePath, drl);

        // Configure KieBase for STREAM mode (required for @expires / @role(event))
        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"cluster" + clusterId + "Base\""
                + " eventProcessingMode=\"stream\">\n"
                + "    <ksession name=\"cluster" + clusterId + "Session\"/>\n"
                + "  </kbase>\n"
                + "</kmodule>";
        kfs.write("src/main/resources/META-INF/kmodule.xml", kmoduleXml);

        ReleaseId rid = ks.newReleaseId("org.kie.cluster",
                "c-" + clusterId + "-" + System.nanoTime(), "1.0");
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
        return kc.newKieSession("cluster" + clusterId + "Session", cfg);
    }

    /**
     * Insert bootstrap facts for all symbols into a session.
     * Matches the bootstrap pattern from
     * {@link org.kie.benchmark.binance.BinanceFullDatasetBenchmark}.
     */
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

    /** Returns per-session rules fired counts after replayEvents(). */
    public Map<Integer, Integer> getPerSessionFired() {
        return Collections.unmodifiableMap(perSessionFired);
    }

    /** Returns per-session events received counts after replayEvents(). */
    public Map<Integer, Integer> getPerSessionEventsReceived() {
        return Collections.unmodifiableMap(perSessionEventsReceived);
    }

    /** Returns the pool size used. */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Dispose all sessions and shut down the thread pool.
     */
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
        fallbackSession.dispose();
    }
}
