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
 * Mode state fact matching the ModeState declaration in taxonomy.drl.
 * Tracks the current operational mode: NORMAL → THROTTLED → SAFE → HALTED
 */
public class ModeState {

    private String symbol;
    private String mode; // NORMAL | THROTTLED | SAFE | HALTED
    private boolean latched;
    private long lastChangeMs;
    private String reason;

    public ModeState() {
    }

    public ModeState(String symbol, String mode, boolean latched, long lastChangeMs, String reason) {
        this.symbol = symbol;
        this.mode = mode;
        this.latched = latched;
        this.lastChangeMs = lastChangeMs;
        this.reason = reason;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isLatched() {
        return latched;
    }

    public void setLatched(boolean latched) {
        this.latched = latched;
    }

    public long getLastChangeMs() {
        return lastChangeMs;
    }

    public void setLastChangeMs(long lastChangeMs) {
        this.lastChangeMs = lastChangeMs;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
