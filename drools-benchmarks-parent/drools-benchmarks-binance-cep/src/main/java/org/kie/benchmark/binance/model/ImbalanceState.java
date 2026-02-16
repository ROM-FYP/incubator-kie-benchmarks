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

/** Derived fact: Order book imbalance state */
public class ImbalanceState {
    private String symbol;
    private double imbalance; // (bidDepth-askDepth)/(bidDepth+askDepth) in [-1..1]
    private String tier; // BAL | BIASED | EXTREME
    private long tsMs;

    public ImbalanceState() {
    }

    public ImbalanceState(String symbol, double imbalance, String tier, long tsMs) {
        this.symbol = symbol;
        this.imbalance = imbalance;
        this.tier = tier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getImbalance() {
        return imbalance;
    }

    public void setImbalance(double imbalance) {
        this.imbalance = imbalance;
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
