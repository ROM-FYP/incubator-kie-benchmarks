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

package org.kie.benchmark.cep.riperis.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone utility to record raw RIPE RIS Live SSE stream data to a JSONL
 * file.
 * Each line in the output file is a complete JSON event from the stream.
 * 
 * Output is saved to the 'data' directory under the project root.
 * 
 * Usage: java RipeRisStreamRecorder [durationSeconds]
 * Default duration is 300 seconds (5 minutes).
 * Example: RipeRisStreamRecorder 75  → records for 1 minute 15 seconds.
 */
public class RipeRisStreamRecorder {

    private static final String RIPE_RIS_STREAM_URL = EnvConfig.get("RIPERIS_STREAM_URL");
    private static final long DEFAULT_DURATION_SECONDS = 300;

    public static void main(String[] args) {
        long durationSeconds = DEFAULT_DURATION_SECONDS;
        if (args.length > 0) {
            try {
                durationSeconds = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration, using default: " + DEFAULT_DURATION_SECONDS + " seconds");
            }
        }

        RipeRisStreamRecorder recorder = new RipeRisStreamRecorder();
        recorder.recordSeconds(durationSeconds);
    }

    /**
     * Record raw stream data for the specified number of whole minutes.
     * Kept for backward compatibility.
     */
    public void record(int durationMinutes) {
        recordSeconds(durationMinutes * 60L);
    }

    /**
     * Record raw stream data for the specified number of seconds.
     */
    public void recordSeconds(long durationSeconds) {
        // Generate output filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "riperis_stream_" + timestamp + ".jsonl";

        // Determine output path - save to data directory
        Path outputPath = getOutputPath(filename);

        System.out.println("========================================");
        System.out.println("RIPE RIS Stream Recorder");
        System.out.println("========================================");
        System.out.printf("Duration: %d min %02d sec%n", durationSeconds / 60, durationSeconds % 60);
        System.out.println("Output: " + outputPath.toAbsolutePath());
        System.out.println("Stream: " + RIPE_RIS_STREAM_URL);
        System.out.println("========================================");

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        AtomicLong eventCount = new AtomicLong(0);

        HttpURLConnection connection = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;

        try {
            // Ensure output directory exists
            Files.createDirectories(outputPath.getParent());
            // Open in append mode in case of reconnects
            writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true));

            while (System.currentTimeMillis() < endTime) {
                try {
                    // Connect to stream
                    System.out.println("Connecting to RIPE RIS stream...");
                    URL url = new URL(RIPE_RIS_STREAM_URL);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Accept", "text/event-stream");
                    connection.setRequestProperty("User-Agent", "RipeRisStreamRecorder/1.0 (Apache KIE Benchmarks)");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(0); // Infinite for streaming

                    int responseCode = connection.getResponseCode();
                    if (responseCode != 200) {
                        System.err.println("Failed to connect: HTTP " + responseCode + ". Retrying in 5 seconds...");
                        Thread.sleep(5000);
                        continue;
                    }

                    System.out.println("✓ Connected! Recording stream data...");
                    System.out.println();

                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                    String line;
                    StringBuilder eventData = new StringBuilder();
                    long lastProgressTime = System.currentTimeMillis();

                    while (System.currentTimeMillis() < endTime && (line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            // Extract raw JSON payload
                            String jsonData = line.substring(6);
                            eventData.append(jsonData);
                        } else if (line.isEmpty() && eventData.length() > 0) {
                            // End of SSE event - write to file
                            String rawJson = eventData.toString().trim();
                            if (!rawJson.isEmpty()) {
                                writer.write(rawJson);
                                writer.newLine();
                                eventCount.incrementAndGet();

                                // Flush periodically to avoid data loss
                                if (eventCount.get() % 100 == 0) {
                                    writer.flush();
                                }
                            }
                            eventData.setLength(0);

                            // Progress update every 10 seconds
                            long now = System.currentTimeMillis();
                            if (now - lastProgressTime >= 10000) {
                                long elapsedSec = (now - startTime) / 1000;
                                long remainingSec = (endTime - now) / 1000;
                                double eventsPerSec = eventCount.get() / (double) Math.max(1, elapsedSec);
                                System.out.printf("Progress: %d events recorded (%.1f/sec), %d seconds remaining%n",
                                        eventCount.get(), eventsPerSec, remainingSec);
                                lastProgressTime = now;
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Stream interrupted: " + e.getMessage() + ". Reconnecting in 3 seconds...");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } finally {
                    // Cleanup connection and reader before retrying
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException e) {
                            /* ignore */ }
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }

            // Final flush
            if (writer != null) {
                writer.flush();
            }

        } catch (IOException e) {
            System.err.println("Fatal Error initializing recorder: " + e.getMessage());
            e.printStackTrace();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Final cleanup of writer
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    /* ignore */ }
            }
        }

        // Final report
        long totalDurationSec = (System.currentTimeMillis() - startTime) / 1000;
        double eventsPerSec = eventCount.get() / (double) Math.max(1, totalDurationSec);

        System.out.println();
        System.out.println("========================================");
        System.out.println("RECORDING COMPLETE");
        System.out.println("========================================");
        System.out.println("Total Events: " + eventCount.get());
        System.out.println("Duration: " + totalDurationSec + " seconds");
        System.out.println("Rate: " + String.format("%.2f", eventsPerSec) + " events/sec");
        System.out.println("Output File: " + outputPath.toAbsolutePath());

        try {
            long fileSize = Files.size(outputPath);
            System.out.println("File Size: " + formatFileSize(fileSize));
        } catch (IOException e) {
            // ignore
        }
        System.out.println("========================================");
    }

    private Path getOutputPath(String filename) {
        String dir = EnvConfig.get("RIPERIS_RECORDED_EVENT_DIR");
        Path path = Paths.get(dir, filename);
        return path;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
