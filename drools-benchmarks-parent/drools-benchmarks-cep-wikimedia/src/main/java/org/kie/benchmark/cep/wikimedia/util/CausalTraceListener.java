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

package org.kie.benchmark.cep.wikimedia.util;

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
 * {@link RuleRuntimeEventListener}) that emits structured JSON-Lines traces
 * suitable for constructing a rule-interaction graph.
 *
 * <h3>Event types emitted</h3>
 * <ul>
 * <li>FACT_INSERT / FACT_UPDATE / FACT_DELETE — with fact_id, fact_type,
 *     producer (producing rule or "EXTERNAL"), and fact_data (full JSON payload)
 * <li>ACTIVATION_CREATED — with rule, activation_id,
 *     supporting_facts (the fact_ids that matched the LHS)
 * <li>ACTIVATION_FIRED — with rule, activation_id
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * CausalTraceListener listener = new CausalTraceListener("trace.jsonl");
 * kieSession.addEventListener(listener.agendaListener());
 * kieSession.addEventListener(listener.runtimeListener());
 * // ... run benchmarks ...
 * listener.close();
 * </pre>
 */
public class CausalTraceListener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FactRegistry registry = new FactRegistry();
    private final BufferedWriter writer;
    private final AtomicLong seq = new AtomicLong(0);

    /** Match identity → generated activation UUID */
    private final Map<Integer, String> activationIds = new ConcurrentHashMap<>();

    /** The rule that is currently firing (null when outside a rule action). */
    private final ThreadLocal<String> currentFiringRule = new ThreadLocal<>();

    /**
     * Create a new listener that writes JSON-Lines output to {@code outputFile}.
     */
    public CausalTraceListener(String outputFile) {
        try {
            this.writer = new BufferedWriter(new FileWriter(outputFile, false));
        } catch (IOException e) {
            throw new RuntimeException("Cannot open causal trace output file: " + outputFile, e);
        }
    }

    /**
     * Returns the {@link AgendaEventListener} side of this tracer.
     */
    public AgendaEventListener agendaListener() {
        return new DefaultAgendaEventListener() {

            @Override
            public void matchCreated(MatchCreatedEvent event) {
                Match match = event.getMatch();
                String activationId = UUID.randomUUID().toString();
                activationIds.put(System.identityHashCode(match), activationId);
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
                activationIds.remove(System.identityHashCode(event.getMatch()));
            }
        };
    }

    /**
     * Returns the {@link RuleRuntimeEventListener} side of this tracer.
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

    /** Flush and close the output file. */
    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Clear all internal state. */
    public void reset() {
        registry.clear();
        activationIds.clear();
    }

    // ---- JSON builders ----

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

        try {
            String factData = MAPPER.writeValueAsString(fact);
            sb.append(",\"fact_data\":").append(factData);
        } catch (Exception e) {
            sb.append(",\"fact_data\":{}");
        }

        sb.append("}");
        return sb.toString();
    }

    // ---- Helpers ----

    private String currentProducer() {
        String rule = currentFiringRule.get();
        return (rule != null) ? rule : "EXTERNAL";
    }

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

    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
