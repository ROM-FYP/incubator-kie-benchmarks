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

/** Derived fact: Order book depth state */
public class DepthState {
    private String symbol;
    private double bidDepth;
    private double askDepth;
    private String tier; // HIGH | MED | LOW | CRIT_LOW
    private long tsMs;

    public DepthState() {
    }

    public DepthState(String symbol, double bidDepth, double askDepth, String tier, long tsMs) {
        this.symbol = symbol;
        this.bidDepth = bidDepth;
        this.askDepth = askDepth;
        this.tier = tier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getBidDepth() {
        return bidDepth;
    }

    public void setBidDepth(double bidDepth) {
        this.bidDepth = bidDepth;
    }

    public double getAskDepth() {
        return askDepth;
    }

    public void setAskDepth(double askDepth) {
        this.askDepth = askDepth;
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
