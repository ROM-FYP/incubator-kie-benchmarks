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
 * Intermediate fact marking crypto-related content for deep inspection.
 * Used in the cold path for cryptocurrency scam detection.
 */
public class DeepCryptoFlag {
    private String title;
    private String user;
    private int diffBytes;
    private double riskScore;

    public DeepCryptoFlag() {
    }

    public DeepCryptoFlag(String title, String user, int diffBytes, double riskScore) {
        this.title = title;
        this.user = user;
        this.diffBytes = diffBytes;
        this.riskScore = riskScore;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getDiffBytes() {
        return diffBytes;
    }

    public void setDiffBytes(int diffBytes) {
        this.diffBytes = diffBytes;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = riskScore;
    }

    @Override
    public String toString() {
        return "DeepCryptoFlag{" +
                "title='" + title + '\'' +
                ", user='" + user + '\'' +
                ", diffBytes=" + diffBytes +
                ", riskScore=" + riskScore +
                '}';
    }
}
