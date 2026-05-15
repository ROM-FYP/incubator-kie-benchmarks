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
package bench.opensky.replay;

import bench.opensky.model.*;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
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
 *
 * <p>Supports three execution modes via {@link #init(String)}:
 * <ul>
 *   <li>{@code baseline}            — sequential single-session</li>
 *   <li>{@code PARALLEL_EVALUATION} — Drools built-in parallel LHS evaluation</li>
 *   <li>{@code FULLY_PARALLEL}      — Drools built-in fully parallel (LHS + RHS)</li>
 * </ul>
 */
public class OpenSkyReplayEngine {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSkyReplayEngine.class);

    private KieBase kieBase;
    private KieSession session;
    private SessionPseudoClock pseudoClock;

    /** Timestamp (ms) of the last event ingested; used to advance the clock monotonically. */
    private long lastEventTimeMs = -1L;

    public OpenSkyReplayEngine() { }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialise with sequential (baseline) execution mode.
     */
    public void init() {
        init("baseline");
    }

    /**
     * Build the KieBase from the classpath DRL and create a pseudo-clock KieSession.
     * Inserts a singleton {@link Params} fact so rules that depend on it are ready.
     *
     * @param mode one of {@code "baseline"}, {@code "PARALLEL_EVALUATION"}, {@code "FULLY_PARALLEL"}
     */
    public void init(String mode) {
        LOG.info("Building KieBase from DRL [mode={}]...", mode);

        String drl = loadDrl("airTraffick_rules.drl");

        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write("src/main/resources/rules/airTraffick_rules.drl", drl);

        // Programmatic kmodule.xml — same approach as Binance BinanceRulesProvider
        String multithreadAttr = ("PARALLEL_EVALUATION".equals(mode) || "FULLY_PARALLEL".equals(mode))
                ? " multithread=\"true\"" : "";
        if ("FULLY_PARALLEL".equals(mode)) System.setProperty("drools.parallelAgenda", "true");
        String kmoduleXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<kmodule xmlns=\"http://www.drools.org/xsd/kmodule\">\n"
                + "  <kbase name=\"openSkyBase\" eventProcessingMode=\"stream\""
                + multithreadAttr + ">\n"
                + "    <ksession name=\"openSkySession\"/>\n"
                + "  </kbase>\n"
                + "</kmodule>";
        kfs.write("src/main/resources/META-INF/kmodule.xml", kmoduleXml);

        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        Results results = kb.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            for (Message msg : results.getMessages(Message.Level.ERROR)) {
                LOG.error("DRL build error: {}", msg);
            }
            throw new RuntimeException("DRL build failed with errors. See logs.");
        }

        KieModule km = kb.getKieModule();
        KieContainer kc = ks.newKieContainer(km.getReleaseId());

        kieBase = kc.newKieBase("openSkyBase", ks.newKieBaseConfiguration());

        KieSessionConfiguration config = ks.newKieSessionConfiguration();
        config.setOption(ClockTypeOption.PSEUDO);
        session = kieBase.newKieSession(config, null);

        pseudoClock = session.getSessionClock();
        session.setGlobal("clock", pseudoClock);

        LOG.info("KieSession ready [mode={}] (pseudo clock at {}ms)", mode, pseudoClock.getCurrentTime());
    }

    // -------------------------------------------------------------------------
    // Event ingestion
    // -------------------------------------------------------------------------

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
        long targetMs = sv.getSnapshotTime() * 1000L;
        if (targetMs > lastEventTimeMs) {
            long nowMs = pseudoClock.getCurrentTime();
            if (targetMs > nowMs) {
                pseudoClock.advanceTime(targetMs - nowMs, TimeUnit.MILLISECONDS);
            }
            lastEventTimeMs = targetMs;
        }

        session.insert(sv);
        return session.fireAllRules(5000);
    }

    // -------------------------------------------------------------------------
    // Lifecycle — dispose
    // -------------------------------------------------------------------------

    /** Dispose the KieSession, releasing all resources. */
    public void dispose() {
        if (session != null) {
            session.dispose();
            session = null;
        }
        lastEventTimeMs = -1L;
        System.clearProperty("drools.parallelAgenda");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public KieSession getSession()             { return session; }
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
