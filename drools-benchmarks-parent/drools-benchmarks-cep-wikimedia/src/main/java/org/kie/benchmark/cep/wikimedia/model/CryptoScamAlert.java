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
 * Intermediate alert specifically for cryptocurrency scam detection.
 * Aggregates evidence before final Alert generation.
 */
public class CryptoScamAlert {
    private String title;
    private String user;
    private String evidence;
    private int severity;

    public CryptoScamAlert() {
    }

    public CryptoScamAlert(String title, String user, String evidence, int severity) {
        this.title = title;
        this.user = user;
        this.evidence = evidence;
        this.severity = severity;
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

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    @Override
    public String toString() {
        return "CryptoScamAlert{" +
                "title='" + title + '\'' +
                ", user='" + user + '\'' +
                ", evidence='" + evidence + '\'' +
                ", severity=" + severity +
                '}';
    }
}
