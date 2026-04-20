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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns stable, unique IDs to fact objects and tracks which rule last
 * produced (inserted or updated) each fact, for use by
 * {@link CausalTraceListener}.
 *
 * <p>
 * Two maps are maintained:
 * <ul>
 * <li>{@code factIds} — Object identity → fact_id string (e.g. "F12345")
 * <li>{@code provenance} — fact_id → name of the rule that last wrote the fact,
 * or {@code "EXTERNAL"} for externally inserted facts.
 * </ul>
 *
 * <p>
 * Thread-safe: all maps are {@link ConcurrentHashMap}; the ID counter is
 * {@link AtomicLong}.
 */
public class FactRegistry {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    /** Object identity → fact ID (e.g. "F1", "F2", ...) */
    private final Map<Integer, String> factIds = new ConcurrentHashMap<>();

    /** fact_id → last producer rule name (or "EXTERNAL") */
    private final Map<String, String> provenance = new ConcurrentHashMap<>();

    /**
     * Return the existing fact_id for the object, assigning a new one if not seen
     * before.
     */
    public String getOrAssign(Object fact) {
        return factIds.computeIfAbsent(
                System.identityHashCode(fact),
                k -> "F" + COUNTER.incrementAndGet());
    }

    /** Return an already-assigned fact_id, or {@code null} if unknown. */
    public String get(Object fact) {
        return factIds.get(System.identityHashCode(fact));
    }

    /** Remove all records for a fact (should be called on FACT_DELETE). */
    public void remove(Object fact) {
        String id = factIds.remove(System.identityHashCode(fact));
        if (id != null) {
            provenance.remove(id);
        }
    }

    /**
     * Record that {@code producerRule} is responsible for this fact's current
     * state.
     */
    public void setProducer(Object fact, String producerRule) {
        String id = getOrAssign(fact);
        provenance.put(id, producerRule);
    }

    /**
     * Return the rule name that last produced the fact, or {@code "EXTERNAL"} if
     * the fact was inserted from outside the rule engine.
     */
    public String getProducer(Object fact) {
        String id = factIds.get(System.identityHashCode(fact));
        if (id == null)
            return "EXTERNAL";
        return provenance.getOrDefault(id, "EXTERNAL");
    }

    /** Clear all state (call between benchmark trials if reusing the listener). */
    public void clear() {
        factIds.clear();
        provenance.clear();
    }
}
