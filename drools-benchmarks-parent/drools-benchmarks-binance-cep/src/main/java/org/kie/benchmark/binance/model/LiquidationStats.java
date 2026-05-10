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

/** Derived fact: Liquidation statistics */
public class LiquidationStats {
    private String symbol;
    private int liqCount10s;
    private double liqNot10s;
    private String tier; // LOW | MED | HIGH | CRIT
    private long tsMs;

    public LiquidationStats() {
    }

    public LiquidationStats(String symbol, int liqCount10s, double liqNot10s, String tier, long tsMs) {
        this.symbol = symbol;
        this.liqCount10s = liqCount10s;
        this.liqNot10s = liqNot10s;
        this.tier = tier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public int getLiqCount10s() {
        return liqCount10s;
    }

    public void setLiqCount10s(int liqCount10s) {
        this.liqCount10s = liqCount10s;
    }

    public double getLiqNot10s() {
        return liqNot10s;
    }

    public void setLiqNot10s(double liqNot10s) {
        this.liqNot10s = liqNot10s;
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
