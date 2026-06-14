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
package org.kie.benchmark.cep.riperis.parallel;

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
import org.kie.benchmark.cep.riperis.model.RisMessage;

import java.util.*;
import java.util.concurrent.*;

/**
 * Alpha-filter routed 3-cluster parallel rule execution engine for RIPE RIS CEP.
 *
 * <p>Architecture: routed model — each {@link RisMessage} is sent to exactly one
 * session based on its payload type (see {@link RipeRisEventRouter}):
 *
 * <ul>
 *   <li>C1 = General / Non-UPDATE (19 rules) — receives: events with no announcement
 *       or withdrawal payload</li>
 *   <li>C2 = Announcement Pipeline (68 rules, incl. 3 duplicated) — receives: events
 *       with non-empty announcements (including mixed ann+wd events)</li>
 *   <li>C3 = Withdrawal Pipeline (27 rules, incl. 4 duplicated) — receives: events
 *       with non-empty withdrawals AND empty announcements</li>
 * </ul>
 */
public class RipeRisClusterOrchestrator {

    private static final RisMessage POISON_PILL =
            new RisMessage("__STOP__", "__STOP__", -1.0, null, null, null,
                    null, null, null, null, null, null, null, null);

    private static final int POOL_SIZE = RipeRisClusterDrlGenerator.getClusterCount();

    private final Map<Integer, KieSession> clusterSessions;
    private final Map<Integer, BlockingQueue<RisMessage>> eventQueues;
    private ExecutorService threadPool;

    private final Map<Integer, KieContainer> clusterContainers = new LinkedHashMap<>();
    private final Map<Integer, String> clusterSessionNames = new LinkedHashMap<>();

    private final Map<Integer, Integer> perSessionFired = new LinkedHashMap<>();
    private final Map<Integer, Integer> perSessionEventsReceived = new LinkedHashMap<>();

    public RipeRisClusterOrchestrator(String fullDrlContent) {
        this.clusterSessions = new LinkedHashMap<>();
        this.eventQueues = new LinkedHashMap<>();

        System.out.println("[Orchestrator] Building " + POOL_SIZE + " cluster containers");

        Map<Integer, String> drls = RipeRisClusterDrlGenerator.generateClusterDrls(fullDrlContent);
        String[] clusterNames = RipeRisClusterDrlGenerator.getClusterNames();

        for (int clusterId = 1; clusterId <= POOL_SIZE; clusterId++) {
            String drl = drls.get(clusterId);
            if (drl == null || drl.isEmpty()) {
                throw new IllegalStateException("Failed to generate DRL for cluster " + clusterId);
            }

            buildContainerFromDrl(drl, clusterId);

            KieContainer kc = clusterContainers.get(clusterId);
            int ruleCount = kc.getKieBase(kc.getKieBaseNames().iterator().next()).getKiePackages().stream()
                    .mapToInt(p -> p.getRules().size()).sum();

            System.out.println("[Orchestrator]   " + clusterNames[clusterId]
                    + " container ready (" + ruleCount + " rules loaded)");
        }

        // Initialize sessions and thread pool initially so the orchestrator is ready for single-run use cases
        startInvocation();
    }

    /**
     * Replays all events through the parallel cluster architecture.
     * Each event is routed to exactly one cluster via {@link RipeRisEventRouter}.
     *
     * @param events the list of {@link RisMessage} events to replay
     * @return total number of rules fired across all clusters
     */
    public int replayEvents(List<RisMessage> events) {
        perSessionFired.clear();
        perSessionEventsReceived.clear();

        // Launch consumer threads
        Map<Integer, Future<int[]>> futures = new LinkedHashMap<>();
        for (Map.Entry<Integer, KieSession> entry : clusterSessions.entrySet()) {
            int cid = entry.getKey();
            KieSession session = entry.getValue();
            BlockingQueue<RisMessage> queue = eventQueues.get(cid);
            futures.put(cid, threadPool.submit(() -> drainAndFire(session, queue)));
        }

        // Alpha-filter routing: each event goes to one or more clusters based on event router
        for (RisMessage event : events) {
            List<ClusterId> targets = RipeRisEventRouter.route(event);
            for (ClusterId target : targets) {
                int clusterId = clusterIdOrdinal(target);
                try {
                    eventQueues.get(clusterId).put(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while enqueuing event", e);
                }
            }
        }

        // Send poison pills to all clusters
        for (BlockingQueue<RisMessage> q : eventQueues.values()) {
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
                totalFired += result[0];
                perSessionFired.put(entry.getKey(), result[0]);
                perSessionEventsReceived.put(entry.getKey(), result[1]);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Worker failed for session " + entry.getKey(), e);
            }
        }
        return totalFired;
    }

    /**
     * Worker thread: drains the queue and fires rules for each event.
     */
    private int[] drainAndFire(KieSession session, BlockingQueue<RisMessage> queue)
            throws InterruptedException {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;
        int received = 0;

        while (true) {
            RisMessage event = queue.take();
            if (event == POISON_PILL) break;
            received++;

            long currentTime = clock.getCurrentTime();
            long eventTimeMs = (long) (event.getTimestamp() * 1000);
            if (eventTimeMs > currentTime) {
                clock.advanceTime(eventTimeMs - currentTime, TimeUnit.MILLISECONDS);
            }

            session.insert(event);
            fired += session.fireAllRules();
        }
        return new int[] { fired, received };
    }

    /**
     * Starts a new invocation by setting up fresh sessions and a thread pool.
     */
    public void startInvocation() {
        if (threadPool == null || threadPool.isShutdown()) {
            this.threadPool = Executors.newFixedThreadPool(POOL_SIZE);
        }

        // Dispose any existing sessions just in case
        for (KieSession session : clusterSessions.values()) {
            if (session != null) {
                session.dispose();
            }
        }
        clusterSessions.clear();

        // Clear and initialize event queues
        eventQueues.clear();

        KieServices ks = KieServices.Factory.get();
        for (Map.Entry<Integer, KieContainer> entry : clusterContainers.entrySet()) {
            int clusterId = entry.getKey();
            KieContainer kc = entry.getValue();
            String ksessionName = clusterSessionNames.get(clusterId);

            KieSessionConfiguration cfg = ks.newKieSessionConfiguration();
            cfg.setOption(ClockTypeOption.PSEUDO);
            KieSession session = kc.newKieSession(ksessionName, cfg);

            // Warm up / fire initial rules
            session.fireAllRules();

            clusterSessions.put(clusterId, session);
            eventQueues.put(clusterId, new LinkedBlockingQueue<>());
        }
    }

    /**
     * Ends an invocation by disposing current sessions and shutting down the thread pool.
     */
    public void endInvocation() {
        for (KieSession session : clusterSessions.values()) {
            if (session != null) {
                session.dispose();
            }
        }
        clusterSessions.clear();
        eventQueues.clear();

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
            threadPool = null;
        }
    }

    private void buildContainerFromDrl(String drl, int clusterId) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        long uid = System.nanoTime();
        // Put each cluster's DRL in a completely unique directory to prevent classpath leakage
        String pkgDir = "cluster" + clusterId + "_" + uid;
        String pkgPath = "src/main/resources/" + pkgDir + "/cluster_ripe.drl";

        String kbaseName  = "ripeCluster_" + clusterId + "Base_"    + uid;
        String ksessionName = "ripeCluster_" + clusterId + "Session_" + uid;

        // Replace the package declaration so it matches the unique directory
        String updatedDrl = drl.replace(
                "package org.kie.benchmark.cep.riperis.rules",
                "package " + pkgDir);
        kfs.write(pkgPath, updatedDrl);

        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"" + kbaseName + "\" packages=\"" + pkgDir
                + "\" eventProcessingMode=\"stream\">\n"
                + "    <ksession name=\"" + ksessionName + "\"/>\n"
                + "  </kbase>\n"
                + "</kmodule>";
        kfs.write("src/main/resources/META-INF/kmodule.xml", kmoduleXml);

        ReleaseId rid = ks.newReleaseId("org.kie.ripe.cluster",
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
        clusterContainers.put(clusterId, kc);
        clusterSessionNames.put(clusterId, ksessionName);
    }

    /** Maps ClusterId enum to the 1-based integer key used in the session map. */
    private static int clusterIdOrdinal(ClusterId id) {
        switch (id) {
            case C1_GENERAL:      return 1;
            case C2_ANNOUNCEMENT: return 2;
            case C3_WITHDRAWAL:   return 3;
            default: throw new IllegalArgumentException("Unknown ClusterId: " + id);
        }
    }

    public Map<Integer, Integer> getPerSessionFired() {
        return Collections.unmodifiableMap(perSessionFired);
    }

    public Map<Integer, Integer> getPerSessionEventsReceived() {
        return Collections.unmodifiableMap(perSessionEventsReceived);
    }

    public void dispose() {
        endInvocation();
    }
}
