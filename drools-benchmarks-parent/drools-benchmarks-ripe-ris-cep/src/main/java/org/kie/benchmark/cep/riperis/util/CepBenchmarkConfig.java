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

/**
 * Configuration holder for the RIPE RIS CEP benchmark.
 */
public class CepBenchmarkConfig {

    private final long durationMinutes;
    private final String rulesPath;
    private final String risLiveUrl;
    private final boolean verbose;
    private final boolean partitioned;

    public CepBenchmarkConfig(long durationMinutes, String rulesPath, String risLiveUrl, boolean verbose) {
        this(durationMinutes, rulesPath, risLiveUrl, verbose, false);
    }

    public CepBenchmarkConfig(
            long durationMinutes,
            String rulesPath,
            String risLiveUrl,
            boolean verbose,
            boolean partitioned) {
        this.durationMinutes = durationMinutes;
        this.rulesPath = rulesPath;
        this.risLiveUrl = risLiveUrl;
        this.verbose = verbose;
        this.partitioned = partitioned;
    }

    public static CepBenchmarkConfig getDefault() {
        return new CepBenchmarkConfig(
                1,
                EnvConfig.get("RIPERIS_RULES_FILE"),
                EnvConfig.get("RIPERIS_STREAM_URL"),
                true,
                false);
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public String getRulesPath() {
        return rulesPath;
    }

    public String getRisLiveUrl() {
        return risLiveUrl;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isPartitioned() {
        return partitioned;
    }
}
