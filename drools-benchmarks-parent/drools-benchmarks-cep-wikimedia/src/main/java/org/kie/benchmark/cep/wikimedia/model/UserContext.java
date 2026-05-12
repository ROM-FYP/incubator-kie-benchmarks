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
 * Enriched user context information derived from WikiEvent processing.
 * Used to track user reputation, activity patterns, and history.
 */
public class UserContext {
    private String user;
    private int reputation;
    private int editCount;
    private int accountAge;
    private int warnings;

    public UserContext() {
    }

    public UserContext(String user, int reputation, int editCount, int accountAge, int warnings) {
        this.user = user;
        this.reputation = reputation;
        this.editCount = editCount;
        this.accountAge = accountAge;
        this.warnings = warnings;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getReputation() {
        return reputation;
    }

    public void setReputation(int reputation) {
        this.reputation = reputation;
    }

    public int getEditCount() {
        return editCount;
    }

    public void setEditCount(int editCount) {
        this.editCount = editCount;
    }

    public int getAccountAge() {
        return accountAge;
    }

    public void setAccountAge(int accountAge) {
        this.accountAge = accountAge;
    }

    public int getWarnings() {
        return warnings;
    }

    public void setWarnings(int warnings) {
        this.warnings = warnings;
    }

    @Override
    public String toString() {
        return "UserContext{" +
                "user='" + user + '\'' +
                ", reputation=" + reputation +
                ", editCount=" + editCount +
                ", accountAge=" + accountAge +
                ", warnings=" + warnings +
                '}';
    }
}
