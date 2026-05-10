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

/** Derived fact: Mark/Index price state */
public class MarkIndexState {
    private String symbol;
    private double markPx;
    private double indexPx;
    private double midPx;
    private double markIndexBps;
    private double markMidBps;
    private String tier; // LOW | MED | HIGH | CRIT
    private long tsMs;

    public MarkIndexState() {
    }

    public MarkIndexState(String symbol, double markPx, double indexPx, double midPx,
            double markIndexBps, double markMidBps, String tier, long tsMs) {
        this.symbol = symbol;
        this.markPx = markPx;
        this.indexPx = indexPx;
        this.midPx = midPx;
        this.markIndexBps = markIndexBps;
        this.markMidBps = markMidBps;
        this.tier = tier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getMarkPx() {
        return markPx;
    }

    public void setMarkPx(double markPx) {
        this.markPx = markPx;
    }

    public double getIndexPx() {
        return indexPx;
    }

    public void setIndexPx(double indexPx) {
        this.indexPx = indexPx;
    }

    public double getMidPx() {
        return midPx;
    }

    public void setMidPx(double midPx) {
        this.midPx = midPx;
    }

    public double getMarkIndexBps() {
        return markIndexBps;
    }

    public void setMarkIndexBps(double markIndexBps) {
        this.markIndexBps = markIndexBps;
    }

    public double getMarkMidBps() {
        return markMidBps;
    }

    public void setMarkMidBps(double markMidBps) {
        this.markMidBps = markMidBps;
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
