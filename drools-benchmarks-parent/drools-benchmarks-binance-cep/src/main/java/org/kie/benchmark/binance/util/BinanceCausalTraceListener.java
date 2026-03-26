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

package org.kie.benchmark.binance.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dual listener ({@link AgendaEventListener} +
 * {@link RuleRuntimeEventListener}) that
 * emits structured JSON-Lines traces suitable for constructing a
 * rule-interaction graph
 * over Binance CEP data.
 *
 * <h3>Event types emitted</h3>
 * <ul>
 * <li>FACT_INSERT / FACT_UPDATE / FACT_DELETE — with {@code fact_id},
 * {@code fact_type},
 * {@code producer} (producing rule or "EXTERNAL"), and {@code fact_data} (full
 * JSON payload)
 * <li>ACTIVATION_CREATED — with {@code rule}, {@code activation_id},
 * {@code supporting_facts} (the fact_ids that matched the LHS)
 * <li>ACTIVATION_FIRED — with {@code rule}, {@code activation_id}
 * </ul>
 *
 * <h3>Causal edge reconstruction</h3>
 * From these events you can derive a rule dependency edge:
 * 
 * <pre>
 *   Ri → Rj  iff  some fact_id F appears in
 *               ACTIVATION_CREATED.supporting_facts of Rj
 *             AND
 *               FACT_INSERT/UPDATE.producer = Ri for that F
 * </pre>
 *
 * <h3>Usage</h3>
 * 
 * <pre>
 * BinanceCausalTraceListener listener = new BinanceCausalTraceListener("trace.jsonl");
 * kieSession.addEventListener(listener.agendaListener());
 * kieSession.addEventListener(listener.runtimeListener());
 * // ... run benchmarks ...
 * listener.close();
 * </pre>
 *
 * <p>
 * This logger is entirely separate from {@link MiningTraceLogger}; both can
 * coexist.
 */
public class BinanceCausalTraceListener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final BinanceFactRegistry registry = new BinanceFactRegistry();
    private final BufferedWriter writer;

    /**
     * Sequence counter — guarantees strict ordering even within the same ms
     * timestamp.
     */
    private final AtomicLong seq = new AtomicLong(0);

    /**
     * Activation-ID map: Drools does not provide a stable activation ID, so we
     * generate a UUID on matchCreated and look it up on afterMatchFired.
     * Key: the Match object's identity hash. Value: our generated UUID string.
     */
    private final Map<Integer, String> activationIds = new ConcurrentHashMap<>();

    /**
     * The rule that is currently firing (null when no rule is executing).
     * Used to attribute INSERT / UPDATE events to the correct producer rule.
     */
    private final ThreadLocal<String> currentFiringRule = new ThreadLocal<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Create a new listener that writes JSON-Lines output to {@code outputFile}.
     * The file is opened in overwrite (non-append) mode.
     *
     * @param outputFile path to the trace output file
     * @throws RuntimeException if the file cannot be opened
     */
    public BinanceCausalTraceListener(String outputFile) {
        try {
            this.writer = new BufferedWriter(new FileWriter(outputFile, /* append = */ false));
        } catch (IOException e) {
            throw new RuntimeException("Cannot open causal trace output file: " + outputFile, e);
        }
    }

    // -------------------------------------------------------------------------
    // Public listener accessors — register both on the KieSession
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link AgendaEventListener} side of this tracer.
     * Tracks activation lifecycle: ACTIVATION_CREATED and ACTIVATION_FIRED.
     */
    public AgendaEventListener agendaListener() {
        return new DefaultAgendaEventListener() {

            @Override
            public void matchCreated(MatchCreatedEvent event) {
                Match match = event.getMatch();
                String activationId = UUID.randomUUID().toString();
                activationIds.put(System.identityHashCode(match), activationId);

                // Collect the IDs of all facts bound in the LHS pattern match
                List<String> factIds = extractFactIds(match);

                emit(buildActivationCreated(match.getRule().getName(), activationId, factIds));
            }

            @Override
            public void beforeMatchFired(BeforeMatchFiredEvent event) {
                currentFiringRule.set(event.getMatch().getRule().getName());
            }

            @Override
            public void afterMatchFired(AfterMatchFiredEvent event) {
                Match match = event.getMatch();
                String activationId = activationIds.remove(System.identityHashCode(match));
                if (activationId == null)
                    activationId = "UNKNOWN";

                emit(buildActivationFired(match.getRule().getName(), activationId));
                currentFiringRule.remove();
            }

            @Override
            public void matchCancelled(MatchCancelledEvent event) {
                // Clean up orphaned activation IDs to avoid memory leak
                activationIds.remove(System.identityHashCode(event.getMatch()));
            }
        };
    }

    /**
     * Returns the {@link RuleRuntimeEventListener} side of this tracer.
     * Tracks fact lifecycle: FACT_INSERT, FACT_UPDATE, FACT_DELETE.
     */
    public RuleRuntimeEventListener runtimeListener() {
        return new DefaultRuleRuntimeEventListener() {

            @Override
            public void objectInserted(ObjectInsertedEvent event) {
                Object fact = event.getObject();
                String producer = currentProducer();
                registry.setProducer(fact, producer);
                emit(buildFactEvent("FACT_INSERT", fact, producer));
            }

            @Override
            public void objectUpdated(ObjectUpdatedEvent event) {
                Object fact = event.getObject();
                String producer = currentProducer();
                registry.setProducer(fact, producer);
                emit(buildFactEvent("FACT_UPDATE", fact, producer));
            }

            @Override
            public void objectDeleted(ObjectDeletedEvent event) {
                Object fact = event.getOldObject();
                String producer = registry.getProducer(fact);
                emit(buildFactEvent("FACT_DELETE", fact, producer));
                registry.remove(fact);
            }
        };
    }

    /**
     * Flush and close the output file. Call once after all events have been
     * processed.
     */
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Clear all internal state (fact registry + activation ID map).
     * Call between benchmark trials if reusing this listener across sessions.
     */
    public void reset() {
        registry.clear();
        activationIds.clear();
    }

    // -------------------------------------------------------------------------
    // JSON builders (hand-written — no external library required for this part)
    // -------------------------------------------------------------------------

    private String buildActivationCreated(String rule, String activationId, List<String> factIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"ACTIVATION_CREATED\"");
        sb.append(",\"seq\":").append(seq.incrementAndGet());
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"tid\":").append(Thread.currentThread().getId());
        sb.append(",\"rule\":\"").append(escapeJson(rule)).append("\"");
        sb.append(",\"activation_id\":\"").append(activationId).append("\"");
        sb.append(",\"supporting_facts\":[");
        for (int i = 0; i < factIds.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append("\"").append(factIds.get(i)).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildActivationFired(String rule, String activationId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"ACTIVATION_FIRED\"");
        sb.append(",\"seq\":").append(seq.incrementAndGet());
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"tid\":").append(Thread.currentThread().getId());
        sb.append(",\"rule\":\"").append(escapeJson(rule)).append("\"");
        sb.append(",\"activation_id\":\"").append(activationId).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String buildFactEvent(String type, Object fact, String producer) {
        String factId = registry.getOrAssign(fact);
        String factType = fact.getClass().getSimpleName();

        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\"");
        sb.append(",\"seq\":").append(seq.incrementAndGet());
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"tid\":").append(Thread.currentThread().getId());
        sb.append(",\"fact_id\":\"").append(factId).append("\"");
        sb.append(",\"fact_type\":\"").append(escapeJson(factType)).append("\"");
        sb.append(",\"producer\":\"").append(escapeJson(producer)).append("\"");

        // Serialize full fact payload via Jackson — fails gracefully
        try {
            String factData = MAPPER.writeValueAsString(fact);
            sb.append(",\"fact_data\":").append(factData);
        } catch (Exception e) {
            sb.append(",\"fact_data\":{}");
        }

        sb.append("}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Who is currently firing? Returns "EXTERNAL" if called from outside a rule
     * action.
     */
    private String currentProducer() {
        String rule = currentFiringRule.get();
        return (rule != null) ? rule : "EXTERNAL";
    }

    /**
     * Extract fact IDs for all fact handles bound by the match.
     * We iterate {@link Match#getFactHandles()} which returns the full LHS
     * bindings.
     */
    private List<String> extractFactIds(Match match) {
        Collection<? extends FactHandle> handles = match.getFactHandles();
        List<String> ids = new ArrayList<>(handles.size());
        for (FactHandle fh : handles) {
            Object obj = ((org.drools.core.common.InternalFactHandle) fh).getObject();
            if (obj != null) {
                ids.add(registry.getOrAssign(obj));
            }
        }
        return ids;
    }

    private void emit(String json) {
        try {
            writer.write(json);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Minimal JSON string escaping (covers the most common problem characters). */
    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
