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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe store for cross-phase state sharing between parallel KieSessions.
 *
 * <p>Key format: "{FactType}:{symbol}" — e.g. "FeedHealth:BTCUSDT"</p>
 *
 * <p>After each phase completes, modified state facts are published here.
 * Before the next phase fires, sessions inject the latest facts from this store.</p>
 */
public class SharedFactStore {

    private final ConcurrentHashMap<String, Object> store = new ConcurrentHashMap<>();

    /**
     * Publish a fact to the shared store.
     *
     * @param factType the simple class name of the fact (e.g. "FeedHealth")
     * @param symbol   the symbol key (e.g. "BTCUSDT")
     * @param fact     the fact object
     */
    public void publish(String factType, String symbol, Object fact) {
        store.put(makeKey(factType, symbol), fact);
    }

    /**
     * Read a fact from the shared store.
     *
     * @param factType the simple class name of the fact
     * @param symbol   the symbol key
     * @param clazz    the expected class type
     * @param <T>      the fact type
     * @return the fact, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T read(String factType, String symbol, Class<T> clazz) {
        Object obj = store.get(makeKey(factType, symbol));
        return clazz.isInstance(obj) ? (T) obj : null;
    }

    /**
     * Get all facts for a given symbol across all fact types.
     *
     * @param symbol the symbol key
     * @return collection of all facts matching the symbol
     */
    public Collection<Object> getAllForSymbol(String symbol) {
        String suffix = ":" + symbol;
        return store.entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix))
                .map(java.util.Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Get all facts in the store.
     *
     * @return collection of all stored facts
     */
    public Collection<Object> getAll() {
        return store.values();
    }

    /**
     * Clear all facts from the store.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Get the current size of the store.
     */
    public int size() {
        return store.size();
    }

    private static String makeKey(String factType, String symbol) {
        return factType + ":" + symbol;
    }
}
