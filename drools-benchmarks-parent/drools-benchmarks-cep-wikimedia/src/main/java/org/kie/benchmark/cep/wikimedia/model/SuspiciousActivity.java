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
 * Tracks suspicious activity detected in the event stream.
 * Used in beta joins to correlate with quality grades and user context.
 */
public class SuspiciousActivity {
    private String user;
    private String title;
    private String reason;
    private String status;  // "DETECTED", "INVESTIGATING", "RESOLVED"
    private int riskLevel;

    public SuspiciousActivity() {
    }

    public SuspiciousActivity(String user, String title, String reason, String status, int riskLevel) {
        this.user = user;
        this.title = title;
        this.reason = reason;
        this.status = status;
        this.riskLevel = riskLevel;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(int riskLevel) {
        this.riskLevel = riskLevel;
    }

    @Override
    public String toString() {
        return "SuspiciousActivity{" +
                "user='" + user + '\'' +
                ", title='" + title + '\'' +
                ", reason='" + reason + '\'' +
                ", status='" + status + '\'' +
                ", riskLevel=" + riskLevel +
                '}';
    }
}
