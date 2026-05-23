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

/** Derived fact: Trade statistics */
public class TradeStats {
    private String symbol;
    private double lastTradePx;
    private double lastTradeQty;
    private double tradeRate1s;
    private String tradeRateTier; // LOW | MED | HIGH | CRIT
    private long tsMs;

    public TradeStats() {
    }

    public TradeStats(String symbol, double lastTradePx, double lastTradeQty,
            double tradeRate1s, String tradeRateTier, long tsMs) {
        this.symbol = symbol;
        this.lastTradePx = lastTradePx;
        this.lastTradeQty = lastTradeQty;
        this.tradeRate1s = tradeRate1s;
        this.tradeRateTier = tradeRateTier;
        this.tsMs = tsMs;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getLastTradePx() {
        return lastTradePx;
    }

    public void setLastTradePx(double lastTradePx) {
        this.lastTradePx = lastTradePx;
    }

    public double getLastTradeQty() {
        return lastTradeQty;
    }

    public void setLastTradeQty(double lastTradeQty) {
        this.lastTradeQty = lastTradeQty;
    }

    public double getTradeRate1s() {
        return tradeRate1s;
    }

    public void setTradeRate1s(double tradeRate1s) {
        this.tradeRate1s = tradeRate1s;
    }

    public String getTradeRateTier() {
        return tradeRateTier;
    }

    public void setTradeRateTier(String tradeRateTier) {
        this.tradeRateTier = tradeRateTier;
    }

    public long getTsMs() {
        return tsMs;
    }

    public void setTsMs(long tsMs) {
        this.tsMs = tsMs;
    }
}
