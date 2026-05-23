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

package org.kie.benchmark.binance.provider;

import org.kie.benchmark.binance.model.MarketEvent;
import org.kie.benchmark.binance.util.SegmentReader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Provides Binance market events for JMH benchmarks.
 * Loads events from the sample dataset in resources/data/.
 */
public class BinanceEventProvider {

    private static final String DEFAULT_DATASET = System.getProperty("binance.dataset",
            "split_1600k");
    private List<MarketEvent> events;
    private String datasetId;

    /**
     * Load events from the default dataset.
     */
    public BinanceEventProvider() {
        this(DEFAULT_DATASET);
    }

    /**
     * Load events from a specific dataset.
     * 
     * @param datasetId Dataset directory name (e.g., "run_20260216_0632_10sym")
     */
    public BinanceEventProvider(String datasetId) {
        this.datasetId = datasetId;
        loadEvents();
    }

    /**
     * Load all events from the dataset's segments directory.
     * Handles both filesystem (development) and JAR (production) resources.
     */
    private void loadEvents() {
        try {
            // Load from classpath: /data/{datasetId}/segments/
            String resourcePath = "/data/" + datasetId + "/segments";
            URL resourceUrl = getClass().getResource(resourcePath);

            if (resourceUrl == null) {
                throw new RuntimeException("Dataset not found: " + resourcePath);
            }

            Path segmentsDir;

            // Handle JAR resources
            if (resourceUrl.toURI().getScheme().equals("jar")) {
                // Create filesystem for JAR if needed
                try {
                    segmentsDir = Paths.get(resourceUrl.toURI());
                } catch (java.nio.file.FileSystemNotFoundException e) {
                    // JAR filesystem doesn't exist yet, create it
                    java.nio.file.FileSystem fs = java.nio.file.FileSystems.newFileSystem(
                            resourceUrl.toURI(),
                            java.util.Collections.emptyMap());
                    segmentsDir = fs.getPath(resourcePath);
                }
            } else {
                // Regular filesystem
                segmentsDir = Paths.get(resourceUrl.toURI());
            }

            this.events = SegmentReader.readAllSegments(segmentsDir);

            System.out.println("Loaded " + events.size() + " events from dataset: " + datasetId);

        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to load dataset: " + datasetId, e);
        }
    }

    /**
     * Get all loaded events (sorted by exchange_ts).
     */
    public List<MarketEvent> getEvents() {
        return events;
    }

    /**
     * Get events for a specific symbol.
     */
    public List<MarketEvent> getEventsForSymbol(String symbol) {
        return events.stream()
                .filter(e -> symbol.equals(e.getSymbol()))
                .toList();
    }

    /**
     * Get events for a specific stream type.
     */
    public List<MarketEvent> getEventsForStream(String eventType) {
        return events.stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .toList();
    }

    /**
     * Get total event count.
     */
    public int getEventCount() {
        return events.size();
    }

    /**
     * Get dataset ID.
     */
    public String getDatasetId() {
        return datasetId;
    }
}
