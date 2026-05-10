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
 * Feed health tracking fact matching the FeedHealth declaration in
 * taxonomy.drl.
 * Tracks last-seen timestamps and health status for each symbol.
 */
public class FeedHealth {

    private String symbol;
    private String status; // OK | DEGRADED | STALE_BOOK | STALE_TRADE | STALE_MARK | STALE_INDEX |
                           // CORRUPT_BOOK | BOOK_UNTRUSTED | FEED_OVERLOAD
    private long lastBookTsMs;
    private long lastTradeTsMs;
    private long lastMarkTsMs;
    private long lastIndexTsMs;
    private long lastHbTsMs;
    private int ooCount; // out-of-order count in window
    private int gapCount; // book gap count (if you track update IDs)

    public FeedHealth() {
    }

    public FeedHealth(String symbol, String status, long lastBookTsMs, long lastTradeTsMs,
            long lastMarkTsMs, long lastIndexTsMs, long lastHbTsMs, int ooCount, int gapCount) {
        this.symbol = symbol;
        this.status = status;
        this.lastBookTsMs = lastBookTsMs;
        this.lastTradeTsMs = lastTradeTsMs;
        this.lastMarkTsMs = lastMarkTsMs;
        this.lastIndexTsMs = lastIndexTsMs;
        this.lastHbTsMs = lastHbTsMs;
        this.ooCount = ooCount;
        this.gapCount = gapCount;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getLastBookTsMs() {
        return lastBookTsMs;
    }

    public void setLastBookTsMs(long lastBookTsMs) {
        this.lastBookTsMs = lastBookTsMs;
    }

    public long getLastTradeTsMs() {
        return lastTradeTsMs;
    }

    public void setLastTradeTsMs(long lastTradeTsMs) {
        this.lastTradeTsMs = lastTradeTsMs;
    }

    public long getLastMarkTsMs() {
        return lastMarkTsMs;
    }

    public void setLastMarkTsMs(long lastMarkTsMs) {
        this.lastMarkTsMs = lastMarkTsMs;
    }

    public long getLastIndexTsMs() {
        return lastIndexTsMs;
    }

    public void setLastIndexTsMs(long lastIndexTsMs) {
        this.lastIndexTsMs = lastIndexTsMs;
    }

    public long getLastHbTsMs() {
        return lastHbTsMs;
    }

    public void setLastHbTsMs(long lastHbTsMs) {
        this.lastHbTsMs = lastHbTsMs;
    }

    public int getOoCount() {
        return ooCount;
    }

    public void setOoCount(int ooCount) {
        this.ooCount = ooCount;
    }

    public int getGapCount() {
        return gapCount;
    }

    public void setGapCount(int gapCount) {
        this.gapCount = gapCount;
    }
}
