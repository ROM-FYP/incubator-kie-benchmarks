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
 * Base market event matching the MarketEvent declaration in taxonomy.drl.
 * Represents a single event from Binance WebSocket streams.
 * 
 * @role(event) in DRL
 */
public class MarketEvent {

    private String symbol;
    private long tsMs; // event time in milliseconds
    private String eventType; // "DEPTH" | "TRADE" | "MARK" | "INDEX" | "LIQ" | "HEARTBEAT"

    // Generic payload fields for single-file simplicity
    private double p1; // price / bid / mark / index / ...
    private double p2; // ask / qty / ...
    private double p3; // optional
    private String s1; // optional

    public MarketEvent() {
    }

    public MarketEvent(String symbol, long tsMs, String eventType, double p1, double p2, double p3, String s1) {
        this.symbol = symbol;
        this.tsMs = tsMs;
        this.eventType = eventType;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.s1 = s1;
    }

    // Getters and Setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public long getTsMs() {
        return tsMs;
    }

    public void setTsMs(long tsMs) {
        this.tsMs = tsMs;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public double getP1() {
        return p1;
    }

    public void setP1(double p1) {
        this.p1 = p1;
    }

    public double getP2() {
        return p2;
    }

    public void setP2(double p2) {
        this.p2 = p2;
    }

    public double getP3() {
        return p3;
    }

    public void setP3(double p3) {
        this.p3 = p3;
    }

    public String getS1() {
        return s1;
    }

    public void setS1(String s1) {
        this.s1 = s1;
    }

    @Override
    public String toString() {
        return "MarketEvent{" +
                "symbol='" + symbol + '\'' +
                ", tsMs=" + tsMs +
                ", eventType='" + eventType + '\'' +
                ", p1=" + p1 +
                ", p2=" + p2 +
                '}';
    }
}
