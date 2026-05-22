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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.kie.benchmark.binance.model.MarketEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Reads JSONL.gz segment files and converts them to MarketEvent instances.
 * Handles decompression and JSON parsing of Binance WebSocket event envelopes.
 */
public class SegmentReader {

    private static final Gson gson = new Gson();

    /**
     * Read a single segment file and return all events.
     * 
     * @param segmentPath Path to the .jsonl.gz file
     * @return List of MarketEvent instances
     * @throws IOException if file reading fails
     */
    public static List<MarketEvent> readSegment(Path segmentPath) throws IOException {
        List<MarketEvent> events = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(segmentPath))))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    MarketEvent event = parseEventEnvelope(line);
                    if (event != null) {
                        events.add(event);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to parse event: " + e.getMessage());
                    // Continue processing other events
                }
            }
        }

        return events;
    }

    /**
     * Parse a JSON event envelope and extract fields into MarketEvent.
     * 
     * Event envelope format:
     * {
     * "dataset_id": "run_20260216_0632_10sym",
     * "source_stream": "trade",
     * "symbol": "BTCUSDT",
     * "exchange_ts": 1771223557369,
     * "recv_ts": 1771223557516173,
     * "local_seq": 0,
     * "raw": { ... }
     * }
     */
    private static MarketEvent parseEventEnvelope(String json) {
        JsonObject envelope = gson.fromJson(json, JsonObject.class);

        // Skip events with parse errors
        if (envelope.has("parse_error")) {
            return null;
        }

        String symbol = envelope.get("symbol").getAsString();
        long exchangeTs = envelope.get("exchange_ts").getAsLong();
        String sourceStream = envelope.get("source_stream").getAsString();
        JsonObject raw = envelope.getAsJsonObject("raw");

        // Map source_stream to eventType for DRL
        String eventType = mapStreamToEventType(sourceStream);

        // Extract payload fields based on stream type
        double p1 = 0.0, p2 = 0.0, p3 = 0.0;
        String s1 = "";

        switch (sourceStream) {
            case "trade":
                p1 = parseDouble(raw, "p"); // price
                p2 = parseDouble(raw, "q"); // quantity
                s1 = raw.has("m") ? String.valueOf(raw.get("m").getAsBoolean()) : "";
                break;

            case "book":
                // For book events, we'll use the first bid/ask prices
                if (raw.has("b") && raw.getAsJsonArray("b").size() > 0) {
                    p1 = Double.parseDouble(raw.getAsJsonArray("b").get(0).getAsJsonArray().get(0).getAsString());
                }
                if (raw.has("a") && raw.getAsJsonArray("a").size() > 0) {
                    p2 = Double.parseDouble(raw.getAsJsonArray("a").get(0).getAsJsonArray().get(0).getAsString());
                }
                break;

            case "mark":
                p1 = parseDouble(raw, "p"); // mark price
                p2 = parseDouble(raw, "i"); // index price (if available)
                p3 = parseDouble(raw, "r"); // funding rate
                break;

            case "index":
                p1 = parseDouble(raw, "p"); // index price
                break;

            case "liquidation":
                if (raw.has("o")) {
                    JsonObject order = raw.getAsJsonObject("o");
                    p1 = parseDouble(order, "p"); // price
                    p2 = parseDouble(order, "q"); // quantity
                    s1 = order.has("S") ? order.get("S").getAsString() : ""; // side
                }
                break;
        }

        return new MarketEvent(symbol, exchangeTs, eventType, p1, p2, p3, s1);
    }

    /**
     * Map Binance stream name to DRL eventType.
     */
    private static String mapStreamToEventType(String sourceStream) {
        switch (sourceStream) {
            case "trade":
                return "TRADE";
            case "book":
                return "DEPTH";
            case "mark":
                return "MARK";
            case "index":
                return "INDEX";
            case "liquidation":
                return "LIQ";
            default:
                return sourceStream.toUpperCase();
        }
    }

    /**
     * Safely parse a double from JSON, handling string and numeric formats.
     */
    private static double parseDouble(JsonObject obj, String key) {
        if (!obj.has(key)) {
            return 0.0;
        }

        try {
            if (obj.get(key).isJsonPrimitive() && obj.get(key).getAsJsonPrimitive().isString()) {
                return Double.parseDouble(obj.get(key).getAsString());
            } else {
                return obj.get(key).getAsDouble();
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Read all segments from a directory and merge them in chronological order.
     * 
     * @param segmentsDir Path to the segments directory
     * @return List of all events sorted by (exchange_ts, local_seq)
     * @throws IOException if directory reading fails
     */
    public static List<MarketEvent> readAllSegments(Path segmentsDir) throws IOException {
        List<MarketEvent> allEvents = new ArrayList<>();

        // Read all .jsonl.gz files
        Files.list(segmentsDir)
                .filter(p -> p.toString().endsWith(".jsonl.gz"))
                .sorted() // Lexicographic sort = chronological order
                .forEach(segmentPath -> {
                    try {
                        allEvents.addAll(readSegment(segmentPath));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to read segment: " + segmentPath, e);
                    }
                });

        // Sort by exchange_ts for event-time replay
        allEvents.sort((e1, e2) -> Long.compare(e1.getTsMs(), e2.getTsMs()));

        return allEvents;
    }
}
