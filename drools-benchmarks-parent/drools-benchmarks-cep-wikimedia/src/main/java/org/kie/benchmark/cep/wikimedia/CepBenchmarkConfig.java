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
 * Configuration holder for the CEP benchmark.
 */
public class CepBenchmarkConfig {
    private final long durationMinutes;
    private final String rulesPath;
    private final String wikimediaUrl;
    private final boolean verbose;
    private final boolean partitioned;
    private final boolean memoizationEnabled;
    private final int cacheMaxEntries;
    private final long cacheTTLms;

    public CepBenchmarkConfig(long durationMinutes, String rulesPath, String wikimediaUrl, boolean verbose) {
        this(durationMinutes, rulesPath, wikimediaUrl, verbose, false, false, 2000, -1);
    }

    public CepBenchmarkConfig(long durationMinutes, String rulesPath, String wikimediaUrl, boolean verbose, boolean partitioned, boolean memoizationEnabled, int cacheMaxEntries, long cacheTTLms) {
        this.durationMinutes = durationMinutes;
        this.rulesPath = rulesPath;
        this.wikimediaUrl = wikimediaUrl;
        this.verbose = verbose;
        this.partitioned = partitioned;
        this.memoizationEnabled = memoizationEnabled;
        this.cacheMaxEntries = cacheMaxEntries;
        this.cacheTTLms = cacheTTLms;
    }

    public static CepBenchmarkConfig getDefault() {
        return new CepBenchmarkConfig(
                1, // 5 minutes default
                "rules/wikimedia_content_moderation_join_heavy.drl",
                "https://stream.wikimedia.org/v2/stream/recentchange",
                true,
                false,
                false,
                2000,
                -1);
    }

    public long getDurationMinutes() {
        return durationMinutes;
    }

    public String getRulesPath() {
        return rulesPath;
    }

    public String getWikimediaUrl() {
        return wikimediaUrl;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public boolean isPartitioned() {
        return partitioned;
    }

    public boolean isMemoizationEnabled() {
        return memoizationEnabled;
    }

    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    public long getCacheTTLms() {
        return cacheTTLms;
    }
}
