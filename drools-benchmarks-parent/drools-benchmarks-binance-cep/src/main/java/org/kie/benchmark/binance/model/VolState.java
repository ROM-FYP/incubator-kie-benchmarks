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

/** Derived fact: Volatility state */
public class VolState {
    private String symbol;
    private double vol1s;
    private double vol10s;
    private double vol1m;
    private String tier; // LOW | MED | HIGH | CRIT
    private long tsMs;

    public VolState() {
    }

    public VolState(String symbol, double vol1s, double vol10s, double vol1m, String tier, long tsMs) {
        this.symbol = symbol;
        this.vol1s = vol1s;
        this.vol10s = vol10s;
        this.vol1m = vol1m;
        this.tier = tier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getVol1s() {
        return vol1s;
    }

    public void setVol1s(double vol1s) {
        this.vol1s = vol1s;
    }

    public double getVol10s() {
        return vol10s;
    }

    public void setVol10s(double vol10s) {
        this.vol10s = vol10s;
    }

    public double getVol1m() {
        return vol1m;
    }

    public void setVol1m(double vol1m) {
        this.vol1m = vol1m;
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
