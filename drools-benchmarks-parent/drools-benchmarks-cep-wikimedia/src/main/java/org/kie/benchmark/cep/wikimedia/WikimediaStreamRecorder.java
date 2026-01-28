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
 * Standalone utility to record raw Wikimedia SSE stream data to a JSONL file.
 * Each line in the output file is a complete JSON event from the stream.
 * 
 * Output is saved to the 'data' directory under the project root.
 * 
 * Usage: java WikimediaStreamRecorder [durationMinutes]
 * Default duration is 5 minutes.
 */
public class WikimediaStreamRecorder {

    private static final String WIKIMEDIA_STREAM_URL = "https://stream.wikimedia.org/v2/stream/recentchange";
    private static final int DEFAULT_DURATION_MINUTES = 5;

    public static void main(String[] args) {
        int durationMinutes = DEFAULT_DURATION_MINUTES;
        if (args.length > 0) {
            try {
                durationMinutes = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration, using default: " + DEFAULT_DURATION_MINUTES + " minutes");
            }
        }

        WikimediaStreamRecorder recorder = new WikimediaStreamRecorder();
        recorder.record(durationMinutes);
    }

    /**
     * Record raw stream data for the specified duration.
     */
    public void record(int durationMinutes) {
        // Generate output filename with timestamp
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "wikimedia_stream_" + timestamp + ".jsonl";
        
        // Determine output path - save to data directory
        Path outputPath = getOutputPath(filename);
        
        System.out.println("========================================");
        System.out.println("Wikimedia Stream Recorder");
        System.out.println("========================================");
        System.out.println("Duration: " + durationMinutes + " minutes");
        System.out.println("Output: " + outputPath.toAbsolutePath());
        System.out.println("Stream: " + WIKIMEDIA_STREAM_URL);
        System.out.println("========================================");
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationMinutes * 60 * 1000L);
        AtomicLong eventCount = new AtomicLong(0);
        
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;
        
        try {
            // Ensure output directory exists
            Files.createDirectories(outputPath.getParent());
            
            // Connect to stream
            System.out.println("Connecting to Wikimedia stream...");
            URL url = new URL(WIKIMEDIA_STREAM_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("User-Agent", "WikimediaStreamRecorder/1.0 (Apache KIE Benchmarks)");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(0); // Infinite for streaming
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.err.println("Failed to connect: HTTP " + responseCode);
                return;
            }
            
            System.out.println("✓ Connected! Recording stream data...");
            System.out.println();
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
            
            String line;
            StringBuilder eventData = new StringBuilder();
            long lastProgressTime = startTime;
            
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
                        double eventsPerSec = eventCount.get() / (double) elapsedSec;
                        System.out.printf("Progress: %d events recorded (%.1f/sec), %d seconds remaining%n",
                                eventCount.get(), eventsPerSec, remainingSec);
                        lastProgressTime = now;
                    }
                }
            }
            
            // Final flush
            writer.flush();
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (reader != null) {
                try { reader.close(); } catch (IOException e) { /* ignore */ }
            }
            if (writer != null) {
                try { writer.close(); } catch (IOException e) { /* ignore */ }
            }
            if (connection != null) {
                connection.disconnect();
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
        // Save to src/main/java/org/kie/benchmark/cep/wikimedia/data directory
        Path[] possiblePaths = {
            Paths.get("drools-benchmarks-parent/drools-benchmarks-cep-wikimedia/src/main/java/org/kie/benchmark/cep/wikimedia/data", filename),
            Paths.get("src/main/java/org/kie/benchmark/cep/wikimedia/data", filename),
            Paths.get("data", filename)
        };
        
        for (Path path : possiblePaths) {
            Path parent = path.getParent();
            if (parent != null && Files.exists(parent)) {
                return path;
            }
        }
        
        // Default: create 'data' folder in current working directory
        return Paths.get("data", filename);
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
