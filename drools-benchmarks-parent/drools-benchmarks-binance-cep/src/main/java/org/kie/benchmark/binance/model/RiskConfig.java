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

/**
 * Risk configuration fact matching the RiskConfig declaration in taxonomy.drl.
 * Contains per-symbol thresholds for all risk rules.
 */
public class RiskConfig {

    private String symbol;

    // Clock
    private long maxFutureSkewMs;
    private long maxPastSkewMs;

    // Health
    private long hbTimeoutMs;
    private long bookStaleMs;
    private long tradeStaleMs;
    private long markStaleMs;
    private long indexStaleMs;
    private int maxOoBurst;

    // Book validity
    private double maxJumpBpsNoTrade;

    // Spread tiers (bps)
    private double spreadMedBps;
    private double spreadHighBps;
    private double spreadCritBps;

    // Depth tiers
    private double depthHigh;
    private double depthMed;
    private double depthLow;
    private double depthCritLow;

    // Trade rate tiers (trades/sec)
    private double trMed;
    private double trHigh;
    private double trCrit;

    // Outlier band for trades vs mid (bps)
    private double tradeOutlierBps;

    // Vol tier thresholds
    private double volMed;
    private double volHigh;
    private double volCrit;

    // Dislocation thresholds (bps)
    private double dislocMedBps;
    private double dislocHighBps;
    private double dislocCritBps;
    private long dislocPersistMs;

    // Liquidation tiers (count per 10s)
    private int liqMed;
    private int liqHigh;
    private int liqCrit;
    private int liqJump;

    // Mode control
    private long cooldownMs;
    private int safeSignalsNeed;

    public RiskConfig() {
    }

    public RiskConfig(String symbol) {
        this.symbol = symbol;
        // Set default thresholds
        this.maxFutureSkewMs = 5000;
        this.maxPastSkewMs = 60000;
        this.hbTimeoutMs = 10000;
        this.bookStaleMs = 5000;
        this.tradeStaleMs = 10000;
        this.markStaleMs = 5000;
        this.indexStaleMs = 5000;
        this.maxOoBurst = 10;
        this.maxJumpBpsNoTrade = 50;
        this.spreadMedBps = 5;
        this.spreadHighBps = 20;
        this.spreadCritBps = 100;
        // Depth thresholds calibrated to DERIVE_DepthState_Update proxy.
        // depthProxy = 10000/spreadBps. Typical BTCUSDT: spreadBps~1 => depth~10000.
        // LOW tier means spread is wide (thin book).
        this.depthHigh    = 5000;  // spreadBps <= 2  => healthy book
        this.depthMed     = 1000;  // spreadBps <= 10 => moderate
        this.depthLow     =  500;  // spreadBps <= 20 => thin
        this.depthCritLow =  100;  // spreadBps >= 100 bps => critically thin
        this.trMed = 10;
        this.trHigh = 50;
        this.trCrit = 200;
        this.tradeOutlierBps = 100;
        // Vol thresholds calibrated to UPD_VolSpreadProxy output.
        // vol10s = |spreadBps_change| per DEPTH event. Typical BTCUSDT spread
        // moves 0.2-1.0 bps between ticks. HIGH = notable jump, CRIT = extreme.
        this.volMed  = 0.5;   // 0.5 bps spread change => elevated vol
        this.volHigh = 2.0;   // 2.0 bps change       => high vol
        this.volCrit = 5.0;   // 5.0 bps change       => critical vol
        // Mark-mid dislocation: on liquid Binance perpetuals mark price is
        // typically within 1-5 bps of mid. Thresholds calibrated to dataset.
        this.dislocMedBps  = 1.0;   // 1 bps mark-mid gap  => moderate disloc
        this.dislocHighBps = 3.0;   // 3 bps mark-mid gap  => high dislocation
        this.dislocCritBps = 10.0;  // 10 bps mark-mid gap => critical disloc
        this.dislocPersistMs = 100; // 100ms persistence check (event cadence)
        this.liqMed = 5;
        this.liqHigh = 20;
        this.liqCrit = 100;
        this.liqJump = 10;
        this.cooldownMs = 30000;
        this.safeSignalsNeed = 3;
    }

    // Getters and Setters (abbreviated for brevity)
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getMaxFutureSkewMs() {
        return maxFutureSkewMs;
    }

    public void setMaxFutureSkewMs(long maxFutureSkewMs) {
        this.maxFutureSkewMs = maxFutureSkewMs;
    }

    public long getMaxPastSkewMs() {
        return maxPastSkewMs;
    }

    public void setMaxPastSkewMs(long maxPastSkewMs) {
        this.maxPastSkewMs = maxPastSkewMs;
    }

    public long getHbTimeoutMs() {
        return hbTimeoutMs;
    }

    public void setHbTimeoutMs(long hbTimeoutMs) {
        this.hbTimeoutMs = hbTimeoutMs;
    }

    public long getBookStaleMs() {
        return bookStaleMs;
    }

    public void setBookStaleMs(long bookStaleMs) {
        this.bookStaleMs = bookStaleMs;
    }

    public long getTradeStaleMs() {
        return tradeStaleMs;
    }

    public void setTradeStaleMs(long tradeStaleMs) {
        this.tradeStaleMs = tradeStaleMs;
    }

    public long getMarkStaleMs() {
        return markStaleMs;
    }

    public void setMarkStaleMs(long markStaleMs) {
        this.markStaleMs = markStaleMs;
    }

    public long getIndexStaleMs() {
        return indexStaleMs;
    }

    public void setIndexStaleMs(long indexStaleMs) {
        this.indexStaleMs = indexStaleMs;
    }

    public int getMaxOoBurst() {
        return maxOoBurst;
    }

    public void setMaxOoBurst(int maxOoBurst) {
        this.maxOoBurst = maxOoBurst;
    }

    public double getMaxJumpBpsNoTrade() {
        return maxJumpBpsNoTrade;
    }

    public void setMaxJumpBpsNoTrade(double maxJumpBpsNoTrade) {
        this.maxJumpBpsNoTrade = maxJumpBpsNoTrade;
    }

    public double getSpreadMedBps() {
        return spreadMedBps;
    }

    public void setSpreadMedBps(double spreadMedBps) {
        this.spreadMedBps = spreadMedBps;
    }

    public double getSpreadHighBps() {
        return spreadHighBps;
    }

    public void setSpreadHighBps(double spreadHighBps) {
        this.spreadHighBps = spreadHighBps;
    }

    public double getSpreadCritBps() {
        return spreadCritBps;
    }

    public void setSpreadCritBps(double spreadCritBps) {
        this.spreadCritBps = spreadCritBps;
    }

    public double getDepthHigh() {
        return depthHigh;
    }

    public void setDepthHigh(double depthHigh) {
        this.depthHigh = depthHigh;
    }

    public double getDepthMed() {
        return depthMed;
    }

    public void setDepthMed(double depthMed) {
        this.depthMed = depthMed;
    }

    public double getDepthLow() {
        return depthLow;
    }

    public void setDepthLow(double depthLow) {
        this.depthLow = depthLow;
    }

    public double getDepthCritLow() {
        return depthCritLow;
    }

    public void setDepthCritLow(double depthCritLow) {
        this.depthCritLow = depthCritLow;
    }

    public double getTrMed() {
        return trMed;
    }

    public void setTrMed(double trMed) {
        this.trMed = trMed;
    }

    public double getTrHigh() {
        return trHigh;
    }

    public void setTrHigh(double trHigh) {
        this.trHigh = trHigh;
    }

    public double getTrCrit() {
        return trCrit;
    }

    public void setTrCrit(double trCrit) {
        this.trCrit = trCrit;
    }

    public double getTradeOutlierBps() {
        return tradeOutlierBps;
    }

    public void setTradeOutlierBps(double tradeOutlierBps) {
        this.tradeOutlierBps = tradeOutlierBps;
    }

    public double getVolMed() {
        return volMed;
    }

    public void setVolMed(double volMed) {
        this.volMed = volMed;
    }

    public double getVolHigh() {
        return volHigh;
    }

    public void setVolHigh(double volHigh) {
        this.volHigh = volHigh;
    }

    public double getVolCrit() {
        return volCrit;
    }

    public void setVolCrit(double volCrit) {
        this.volCrit = volCrit;
    }

    public double getDislocMedBps() {
        return dislocMedBps;
    }

    public void setDislocMedBps(double dislocMedBps) {
        this.dislocMedBps = dislocMedBps;
    }

    public double getDislocHighBps() {
        return dislocHighBps;
    }

    public void setDislocHighBps(double dislocHighBps) {
        this.dislocHighBps = dislocHighBps;
    }

    public double getDislocCritBps() {
        return dislocCritBps;
    }

    public void setDislocCritBps(double dislocCritBps) {
        this.dislocCritBps = dislocCritBps;
    }

    public long getDislocPersistMs() {
        return dislocPersistMs;
    }

    public void setDislocPersistMs(long dislocPersistMs) {
        this.dislocPersistMs = dislocPersistMs;
    }

    public int getLiqMed() {
        return liqMed;
    }

    public void setLiqMed(int liqMed) {
        this.liqMed = liqMed;
    }

    public int getLiqHigh() {
        return liqHigh;
    }

    public void setLiqHigh(int liqHigh) {
        this.liqHigh = liqHigh;
    }

    public int getLiqCrit() {
        return liqCrit;
    }

    public void setLiqCrit(int liqCrit) {
        this.liqCrit = liqCrit;
    }

    public int getLiqJump() {
        return liqJump;
    }

    public void setLiqJump(int liqJump) {
        this.liqJump = liqJump;
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = cooldownMs;
    }

    public int getSafeSignalsNeed() {
        return safeSignalsNeed;
    }

    public void setSafeSignalsNeed(int safeSignalsNeed) {
        this.safeSignalsNeed = safeSignalsNeed;
    }
}
