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
package org.kie.benchmark.cep.wikimedia.parallel;

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
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.util.*;
import java.util.concurrent.*;

/**
 * Alpha-filter routed 4-cluster parallel rule execution engine for Wikimedia CEP.
 *
 * <p>Architecture: routed model — each WikiEvent is sent only to sessions whose
 * entry-point rules have matching alpha filters.
 *
 * <ul>
 *   <li>C1 = Minor Edits Pipeline (7 rules) — receives: bot==false AND sizeDelta∈[-50,50]
 *   <li>C2 = Bot Pipeline (10 rules) — receives: bot==true
 *   <li>C3 = Content + Vandalism + Correlations (47 rules, includes 7 duplicated bot rules) — receives: ALL events
 *   <li>C4 = Discussion Pipeline (6 rules) — receives: title starts with "Talk:"
 * </ul>
 */
public class WikimediaClusterOrchestrator {

    private static final WikiEvent POISON_PILL =
            new WikiEvent("__STOP__", "__STOP__", "", false, -1L, 0);

    private static final int POOL_SIZE = WikimediaClusterDrlGenerator.getClusterCount();

    private final Map<Integer, KieSession> clusterSessions;
    private final Map<Integer, BlockingQueue<WikiEvent>> eventQueues;
    private final ExecutorService threadPool;

    private final Map<Integer, Integer> perSessionFired = new LinkedHashMap<>();
    private final Map<Integer, Integer> perSessionEventsReceived = new LinkedHashMap<>();

    public WikimediaClusterOrchestrator(String fullDrlContent) {
        this.clusterSessions = new LinkedHashMap<>();
        this.eventQueues = new LinkedHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        System.out.println("[Orchestrator] Building " + POOL_SIZE + " cluster sessions");

        Map<Integer, String> drls = WikimediaClusterDrlGenerator.generateClusterDrls(fullDrlContent);
        String[] clusterNames = WikimediaClusterDrlGenerator.getClusterNames();

        for (int clusterId = 1; clusterId <= POOL_SIZE; clusterId++) {
            String drl = drls.get(clusterId);
            if (drl == null || drl.isEmpty()) {
                throw new IllegalStateException("Failed to generate DRL for cluster " + clusterId);
            }

            KieSession session = buildSessionFromDrl(drl, clusterId);
            session.fireAllRules();

            // Log rule count for debugging DRL splitting
            int ruleCount = session.getKieBase().getKiePackages().stream()
                    .mapToInt(p -> p.getRules().size()).sum();

            clusterSessions.put(clusterId, session);
            eventQueues.put(clusterId, new LinkedBlockingQueue<>());

            System.out.println("[Orchestrator]   " + clusterNames[clusterId]
                    + " session ready (" + ruleCount + " rules loaded)");
        }
    }

    /**
     * Replay all events through the parallel cluster architecture.
     * Events are broadcast to all sessions; each session's worker thread
     * consumes from its queue independently.
     */
    public int replayEvents(List<WikiEvent> events) {
        perSessionFired.clear();
        perSessionEventsReceived.clear();

        // Launch consumer threads
        Map<Integer, Future<int[]>> futures = new LinkedHashMap<>();
        for (Map.Entry<Integer, KieSession> entry : clusterSessions.entrySet()) {
            int cid = entry.getKey();
            KieSession session = entry.getValue();
            BlockingQueue<WikiEvent> queue = eventQueues.get(cid);
            futures.put(cid, threadPool.submit(() -> drainAndFire(session, queue)));
        }

        // Alpha-filter routing: route events to matching sessions
        for (WikiEvent event : events) {
            try {
                // C3 always receives all events (InitializeUserActivity matches all)
                eventQueues.get(3).put(event);

                // C2: Bot pipeline — bot == true
                if (event.isBot()) {
                    eventQueues.get(2).put(event);
                }

                // C1: Minor edits — bot == false AND sizeDelta ∈ [-50, 50]
                if (!event.isBot()
                        && event.getSizeDelta() >= -50
                        && event.getSizeDelta() <= 50) {
                    eventQueues.get(1).put(event);
                }

                // C4: Discussion — title starts with "Talk:"
                if (event.getTitle() != null && event.getTitle().startsWith("Talk:")) {
                    eventQueues.get(4).put(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while enqueuing event", e);
            }
        }

        // Send poison pills
        for (BlockingQueue<WikiEvent> q : eventQueues.values()) {
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

    private int[] drainAndFire(KieSession session, BlockingQueue<WikiEvent> queue)
            throws InterruptedException {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;
        int received = 0;

        while (true) {
            WikiEvent event = queue.take();
            if (event == POISON_PILL) break;
            received++;

            long currentTime = clock.getCurrentTime();
            if (event.getTimestamp() > currentTime) {
                clock.advanceTime(event.getTimestamp() - currentTime, TimeUnit.MILLISECONDS);
            }

            session.insert(event);
            fired += session.fireAllRules();
        }
        return new int[] { fired, received };
    }

    private KieSession buildSessionFromDrl(String drl, int clusterId) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        long uid = System.nanoTime();
        // Put each cluster's DRL in a completely unique directory to prevent classpath leakage
        // The default kbase config scans the whole classpath unless packages is specified.
        String pkgDir = "cluster" + clusterId + "_" + uid;
        String pkgPath = "src/main/resources/" + pkgDir + "/cluster_wiki.drl";
        
        String kbaseName = "wikiCluster_" + clusterId + "Base_" + uid;
        String ksessionName = "wikiCluster_" + clusterId + "Session_" + uid;
        
        // We must also update the package declaration in the DRL string so it matches
        String updatedDrl = drl.replace("package rules;", "package " + pkgDir + ";");
        kfs.write(pkgPath, updatedDrl);

        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"" + kbaseName + "\" packages=\"" + pkgDir + "\" eventProcessingMode=\"stream\">\n"
                + "    <ksession name=\"" + ksessionName + "\"/>\n"
                + "  </kbase>\n"
                + "</kmodule>";
        kfs.write("src/main/resources/META-INF/kmodule.xml", kmoduleXml);

        ReleaseId rid = ks.newReleaseId("org.kie.wiki.cluster",
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

    public Map<Integer, Integer> getPerSessionFired() {
        return Collections.unmodifiableMap(perSessionFired);
    }

    public Map<Integer, Integer> getPerSessionEventsReceived() {
        return Collections.unmodifiableMap(perSessionEventsReceived);
    }

    public void dispose() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        clusterSessions.values().forEach(KieSession::dispose);
    }
}
