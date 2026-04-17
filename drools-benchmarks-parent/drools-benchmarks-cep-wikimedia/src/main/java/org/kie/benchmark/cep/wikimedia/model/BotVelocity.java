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

package org.kie.benchmark.cep.wikimedia.model;

/**
 * Aggregated bot velocity metrics derived from BotAction accumulation.
 * Indicates the rate of bot activity and whether a burst is detected.
 */
public class BotVelocity {
    private String user;
    private double actionsPerMinute;
    private boolean burstDetected;

    public BotVelocity() {
    }

    public BotVelocity(String user, double actionsPerMinute, boolean burstDetected) {
        this.user = user;
        this.actionsPerMinute = actionsPerMinute;
        this.burstDetected = burstDetected;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public double getActionsPerMinute() {
        return actionsPerMinute;
    }

    public void setActionsPerMinute(double actionsPerMinute) {
        this.actionsPerMinute = actionsPerMinute;
    }

    public boolean isBurstDetected() {
        return burstDetected;
    }

    public void setBurstDetected(boolean burstDetected) {
        this.burstDetected = burstDetected;
    }

    @Override
    public String toString() {
        return "BotVelocity{" +
                "user='" + user + '\'' +
                ", actionsPerMinute=" + actionsPerMinute +
                ", burstDetected=" + burstDetected +
                '}';
    }
}
