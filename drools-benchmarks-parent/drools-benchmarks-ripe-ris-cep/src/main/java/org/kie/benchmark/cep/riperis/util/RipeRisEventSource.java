/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License.  You may obtain a
 * copy of the License at
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

package org.kie.benchmark.cep.riperis.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.riperis.model.RisMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.function.Consumer;

/**
 * SSE client for ingesting RIPE RIS Live real-time BGP events.
 */
public class RipeRisEventSource implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(RipeRisEventSource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String streamUrl;
    private volatile boolean running = false;
    private Thread ingestionThread;
    private HttpURLConnection connection;

    public RipeRisEventSource(String streamUrl) {
        this.streamUrl = streamUrl;
    }

    /**
     * Start ingesting events and pass them to the consumer.
     * This runs in a separate thread.
     */
    public void start(Consumer<RisMessage> eventConsumer) {
        if (running) {
            throw new IllegalStateException("Event source already running");
        }

        running = true;
        ingestionThread = new Thread(() -> ingestEvents(eventConsumer), "riperis-ingestion");
        ingestionThread.setDaemon(false);
        ingestionThread.start();
        logger.info("Started RIPE RIS event ingestion from: {}", streamUrl);
    }

    private void ingestEvents(Consumer<RisMessage> eventConsumer) {
        BufferedReader reader = null;
        try {
            logger.info("Attempting to connect to: {}", streamUrl);
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("User-Agent", "DroolsCEPBenchmark/1.0 (Apache KIE Benchmarks)");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(0); // Infinite read timeout for streaming

            logger.info("Sending HTTP request...");
            int responseCode = connection.getResponseCode();
            logger.info("Received response code: {}", responseCode);
            
            if (responseCode != 200) {
                logger.error("Failed to connect to RIPE RIS stream: HTTP {}", responseCode);
                return;
            }

            logger.info("✓ Connected to RIPE RIS stream successfully! Streaming events...");

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
                        RisMessage event = parseEvent(eventData.toString());
                        if (event != null) {
                            eventConsumer.accept(event);
                            eventCount++;
                            if (eventCount <= 5) {
                                logger.info("Parsed event #{}: {}", eventCount, event.getId());
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
                logger.error("Error reading from RIPE RIS stream", e);
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
            logger.info("RipeRisEventSource ingestion thread terminated");
        }
    }

    /**
     * Parse JSON event and convert to RisMessage.
     */
    private RisMessage parseEvent(String jsonData) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(jsonData, Map.class);
        return RisMessage.fromRisLiveEnvelope(envelope);
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
        logger.info("Stopped RIPE RIS event ingestion");
    }

    @Override
    public void close() {
        stop();
    }
}
