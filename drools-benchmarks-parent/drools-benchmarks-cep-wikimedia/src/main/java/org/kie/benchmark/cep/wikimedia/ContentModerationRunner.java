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
        long duration = 2; // Default 2 minutes
        
        if (args.length > 0) {
            try {
                duration = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration, using default: 2 minutes");
            }
        }
        
        CepBenchmarkConfig config = new CepBenchmarkConfig(
                duration,
                "rules/wikimedia_content_moderation.drl",
                "https://stream.wikimedia.org/v2/stream/recentchange",
                true);
        
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
        
        WikimediaCepBenchmark benchmark = new WikimediaCepBenchmark(config);
        benchmark.run();
    }
}
