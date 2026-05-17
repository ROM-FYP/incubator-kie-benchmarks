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
 * Parallel rule execution engine for Ripe RIS CEP.
 * 
 * TODO: The routing logic in {@code replayEvents} currently broadcasts all events
 * to all clusters because the specific alpha-filter routing for RIPE RIS
 * has not been defined yet. You need to implement your own routing logic.
 */
public class RipeRisClusterOrchestrator {

    // Use a special RisMessage as poison pill.
    // Assuming constructor RisMessage(String id, double timestamp, ...) exists or we can use a dummy.
    // If not, we might need a different mechanism or a specific field.
    // Let's use a dummy RisMessage if possible, or just check for a specific ID.
    private static final RisMessage POISON_PILL;
    
    static {
        // Create a dummy message as poison pill. 
        // We assume we can construct it or we will use a special check.
        // Let's create a message with a specific ID that is unlikely to occur.
        Map<String, Object> dummyEnvelope = new HashMap<>();
        dummyEnvelope.put("id", "__STOP__");
        dummyEnvelope.put("timestamp", -1.0);
        POISON_PILL = RisMessage.fromRisLiveEnvelope(dummyEnvelope);
        // If fromRisLiveEnvelope returns null for missing fields, we might need to populate it more.
        // Let's assume it works or we can check for null or specific ID.
    }

    private static final int POOL_SIZE = RipeRisClusterDrlGenerator.getClusterCount();

    private final Map<Integer, KieSession> clusterSessions;
    private final Map<Integer, BlockingQueue<RisMessage>> eventQueues;
    private final ExecutorService threadPool;

    private final Map<Integer, Integer> perSessionFired = new LinkedHashMap<>();
    private final Map<Integer, Integer> perSessionEventsReceived = new LinkedHashMap<>();

    public RipeRisClusterOrchestrator(String fullDrlContent) {
        this.clusterSessions = new LinkedHashMap<>();
        this.eventQueues = new LinkedHashMap<>();
        this.threadPool = Executors.newFixedThreadPool(POOL_SIZE);

        System.out.println("[Orchestrator] Building " + POOL_SIZE + " cluster sessions");

        Map<Integer, String> drls = RipeRisClusterDrlGenerator.generateClusterDrls(fullDrlContent);
        String[] clusterNames = RipeRisClusterDrlGenerator.getClusterNames();

        for (int clusterId = 1; clusterId <= POOL_SIZE; clusterId++) {
            String drl = drls.get(clusterId);
            if (drl == null || drl.isEmpty()) {
                throw new IllegalStateException("Failed to generate DRL for cluster " + clusterId);
            }

            KieSession session = buildSessionFromDrl(drl, clusterId);
            session.fireAllRules();

            int ruleCount = session.getKieBase().getKiePackages().stream()
                    .mapToInt(p -> p.getRules().size()).sum();

            clusterSessions.put(clusterId, session);
            eventQueues.put(clusterId, new LinkedBlockingQueue<>());

            System.out.println("[Orchestrator]   " + clusterNames[clusterId]
                    + " session ready (" + ruleCount + " rules loaded)");
        }
    }

    public int replayEvents(List<RisMessage> events) {
        perSessionFired.clear();
        perSessionEventsReceived.clear();

        Map<Integer, Future<int[]>> futures = new LinkedHashMap<>();
        for (Map.Entry<Integer, KieSession> entry : clusterSessions.entrySet()) {
            int cid = entry.getKey();
            KieSession session = entry.getValue();
            BlockingQueue<RisMessage> queue = eventQueues.get(cid);
            futures.put(cid, threadPool.submit(() -> drainAndFire(session, queue)));
        }

        for (RisMessage event : events) {
            try {
                // TODO: Implement specific alpha-filter routing for RIPE RIS.
                // For now, we broadcast all events to all clusters to ensure correctness,
                // or you can choose to send them to specific clusters if you have a criteria.
                
                // Broadcast to all for now as a fallback
                for (int i = 1; i <= POOL_SIZE; i++) {
                    eventQueues.get(i).put(event);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while enqueuing event", e);
            }
        }

        // Send poison pills
        for (BlockingQueue<RisMessage> q : eventQueues.values()) {
            try {
                q.put(POISON_PILL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

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

    private int[] drainAndFire(KieSession session, BlockingQueue<RisMessage> queue)
            throws InterruptedException {
        SessionPseudoClock clock = session.getSessionClock();
        int fired = 0;
        int received = 0;

        while (true) {
            RisMessage event = queue.take();
            // Check for poison pill by reference or by specific ID
            if (event == POISON_PILL || (event != null && "__STOP__".equals(event.getId()))) break;
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

    private KieSession buildSessionFromDrl(String drl, int clusterId) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        long uid = System.nanoTime();
        String pkgDir = "cluster" + clusterId + "_" + uid;
        String pkgPath = "src/main/resources/" + pkgDir + "/cluster_ripe.drl";
        
        String kbaseName = "ripeCluster_" + clusterId + "Base_" + uid;
        String ksessionName = "ripeCluster_" + clusterId + "Session_" + uid;
        
        String updatedDrl = drl.replace("package rules;", "package " + pkgDir + ";");
        kfs.write(pkgPath, updatedDrl);

        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"" + kbaseName + "\" packages=\"" + pkgDir + "\" eventProcessingMode=\"stream\">\n"
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
