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

package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/**
 * SSE client for ingesting Wikimedia real-time edit events.
 */
public class WikimediaEventSource implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(WikimediaEventSource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String streamUrl;
    private volatile boolean running = false;
    private Thread ingestionThread;
    private HttpURLConnection connection;

    public WikimediaEventSource(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    /**
     * Start ingesting events and pass them to the consumer.
     * This runs in a separate thread.
     */
    public void start(Consumer<WikiEvent> eventConsumer) {
        if (running) {
            throw new IllegalStateException("Event source already running");
        }

        running = true;
        ingestionThread = new Thread(() -> ingestEvents(eventConsumer), "wikimedia-ingestion");
        ingestionThread.setDaemon(false);
        ingestionThread.start();
        logger.info("Started Wikimedia event ingestion from: {}", streamUrl);
    }

    private void ingestEvents(Consumer<WikiEvent> eventConsumer) {
        BufferedReader reader = null;
        try {
            logger.info("Attempting to connect to: {}", streamUrl);
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("User-Agent", "DroolsCEPBenchmark/1.0 (Apache KIE Benchmarks; https://github.com/apache/incubator-kie-benchmarks)");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(0); // Infinite read timeout for streaming

            logger.info("Sending HTTP request...");
            int responseCode = connection.getResponseCode();
            logger.info("Received response code: {}", responseCode);
            
            if (responseCode != 200) {
                logger.error("Failed to connect to Wikimedia stream: HTTP {}", responseCode);
                return;
            }

            logger.info("✓ Connected to Wikimedia stream successfully! Streaming events...");

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder eventData = new StringBuilder();
            int lineCount = 0;
            int eventCount = 0;

            while (running && (line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount % 100 == 0) {
                    logger.debug("Read {} lines so far, {} events parsed", lineCount, eventCount);
                }
                
                if (line.startsWith("data: ")) {
                    // Extract JSON payload
                    String jsonData = line.substring(6);
                    eventData.append(jsonData);
                } else if (line.isEmpty() && eventData.length() > 0) {
                    // End of SSE event, process it
                    try {
                        WikiEvent event = parseEvent(eventData.toString());
                        if (event != null) {
                            eventConsumer.accept(event);
                            eventCount++;
                            if (eventCount <= 5) {
                                logger.info("Parsed event #{}: {}", eventCount, event.getTitle());
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to parse event: {}", e.getMessage());
                    }
                    eventData.setLength(0);
                }
            }
            
            logger.info("Ingestion loop ended. Running={}, Total lines={}, Events parsed={}", 
                        running, lineCount, eventCount);

        } catch (IOException e) {
            if (running) {
                logger.error("Error reading from Wikimedia stream", e);
            } else {
                logger.info("Stream closed gracefully");
            }
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    logger.warn("Error closing reader", e);
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
            logger.info("WikimediaEventSource ingestion thread terminated");
        }
    }

    /**
     * Parse JSON event and filter for edit events in namespace 0 (articles).
     */
    private WikiEvent parseEvent(String jsonData) throws IOException {
        JsonNode root = objectMapper.readTree(jsonData);

        // Filter: only "edit" type and namespace 0 (articles)
        String type = root.path("type").asText();
        int namespace = root.path("namespace").asInt(-1);

        if (!"edit".equals(type) || namespace != 0) {
            return null;
        }

        String title = root.path("title").asText();
        String user = root.path("user").asText();
        String comment = root.path("comment").asText("");
        boolean bot = root.path("bot").asBoolean(false);
        long timestamp = root.path("timestamp").asLong(System.currentTimeMillis());

        // Calculate size delta
        int lengthNew = root.path("length").path("new").asInt(0);
        int lengthOld = root.path("length").path("old").asInt(0);
        int sizeDelta = lengthNew - lengthOld;

        return new WikiEvent(title, user, comment, bot, timestamp, sizeDelta);
    }

    /**
     * Stop ingesting events.
     */
    public void stop() {
        running = false;
        if (connection != null) {
            connection.disconnect();
        }
        if (ingestionThread != null) {
            try {
                ingestionThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        logger.info("Stopped Wikimedia event ingestion");
    }

    @Override
    public void close() {
        stop();
    }
}
