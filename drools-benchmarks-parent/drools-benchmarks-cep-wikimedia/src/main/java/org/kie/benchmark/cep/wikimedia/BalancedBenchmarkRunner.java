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
 * Runner for the new balanced graph partitioning benchmark.
 * Uses the new_graph_partitioning_benchmark.drl ruleset.
 */
public class BalancedBenchmarkRunner {

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
                "rules/new_graph_partitioning_benchmark.drl",
                "https://stream.wikimedia.org/v2/stream/recentchange",
                true);
        
        System.out.println("Running benchmark with NEW balanced ruleset...");
        WikimediaCepBenchmark benchmark = new WikimediaCepBenchmark(config);
        benchmark.run();
    }
}
