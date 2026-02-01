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

/**
 * Runner for the semantically meaningful Wikimedia Content Moderation ruleset.
 * Uses wikimedia_content_moderation.drl for real content analysis.
 */
public class ContentModerationRunner {

    public static void main(String[] args) {
        // CLI Modes:
        // 1. Standard: [duration] [partitioned?] [inputFile (optional)]
        // 2. Record: "record" [duration] [outputFile]
        // 3. Replay: "replay" [inputFile] [partitioned?]
        
        if (args.length > 0) {
            String mode = args[0];
            
            if ("record".equalsIgnoreCase(mode)) {
                long duration = args.length > 1 ? Long.parseLong(args[1]) : 1;
                String outputFile = args.length > 2 ? args[2] : "events.json";
                runRecord(duration, outputFile);
                return;
            } else if ("replay".equalsIgnoreCase(mode)) {
                String inputFile = args.length > 1 ? args[1] : "events.json";
                boolean partitioned = args.length > 2 && Boolean.parseBoolean(args[2]);
                runReplay(inputFile, partitioned);
                return;
            }
        }
        
        // Default / Legacy Mode
        long duration = 2; 
        boolean partitioned = false;
        
        if (args.length > 0) {
            try {
                duration = Long.parseLong(args[0]);
                if (args.length > 1) {
                    partitioned = Boolean.parseBoolean(args[1]);
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration, using default: 2 minutes");
            }
        }
        
        CepBenchmarkConfig config = new CepBenchmarkConfig(
                duration,
                "rules/wikimedia_content_moderation.drl",
                "https://stream.wikimedia.org/v2/stream/recentchange",
                true,
                partitioned);
        
        printHeader();
        
        WikimediaCepBenchmark benchmark = new WikimediaCepBenchmark(config);
        benchmark.run();
    }
    
    private static void runRecord(long duration, String outputFile) {
        System.out.println("Starting Recording for " + duration + " minutes to " + outputFile);
        CepBenchmarkConfig config = new CepBenchmarkConfig(
                duration,
                "rules/wikimedia_content_moderation.drl",
                "https://stream.wikimedia.org/v2/stream/recentchange",
                true,
                false); // Partitioning doesn't matter for recording
        
        WikimediaCepBenchmark benchmark = new WikimediaCepBenchmark(config);
        benchmark.record(outputFile);
    }
    
    private static void runReplay(String inputFile, boolean partitioned) {
        System.out.println("Starting Replay from " + inputFile + " (Partitioned=" + partitioned + ")");
        CepBenchmarkConfig config = new CepBenchmarkConfig(
                0, // Duration determined by file
                "rules/wikimedia_content_moderation.drl",
                null, // No stream URL needed
                true,
                partitioned);
        
        WikimediaCepBenchmark benchmark = new WikimediaCepBenchmark(config);
        benchmark.replay(inputFile);
    }
    
    private static void printHeader() {
        System.out.println("==============================================");
        System.out.println("Wikimedia Content Moderation Benchmark");
        System.out.println("==============================================");
        System.out.println("Pipelines:");
        System.out.println("  - Vandalism Detection (sizeDelta < -100)");
        System.out.println("  - Bot Activity Monitoring (bot == true)");
        System.out.println("  - Content Growth (sizeDelta > 200)");
        System.out.println("  - Minor Edits (-50 <= sizeDelta <= 50)");
        System.out.println("  - Discussion Analysis (Talk: pages)");
        System.out.println("==============================================");
    }
}
