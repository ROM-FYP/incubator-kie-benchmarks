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

package org.kie.benchmark.binance.model;

/** Derived fact: Spread state with tier classification */
public class SpreadState {
    private String symbol;
    private double spreadBps;
    private String tier; // LOW | MED | HIGH | CRIT
    private long tsMs;

    public SpreadState() {
    }

    public SpreadState(String symbol, double spreadBps, String tier, long tsMs) {
        this.symbol = symbol;
        this.spreadBps = spreadBps;
        this.tier = tier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getSpreadBps() {
        return spreadBps;
    }

    public void setSpreadBps(double spreadBps) {
        this.spreadBps = spreadBps;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public long getTsMs() {
        return tsMs;
    }

    public void setTsMs(long tsMs) {
        this.tsMs = tsMs;
    }
}
