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
 * Represents an edit that has been verified as coming from a high-reputation
 * user.
 * Derived fact used in the hot path of edit processing.
 */
public class VerifiedEdit {
    private String user;
    private String title;
    private int diffBytes;
    private long timestamp;

    public VerifiedEdit() {
    }

    public VerifiedEdit(String user, String title, int diffBytes, long timestamp) {
        this.user = user;
        this.title = title;
        this.diffBytes = diffBytes;
        this.timestamp = timestamp;
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

    public int getDiffBytes() {
        return diffBytes;
    }

    public void setDiffBytes(int diffBytes) {
        this.diffBytes = diffBytes;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "VerifiedEdit{" +
                "user='" + user + '\'' +
                ", title='" + title + '\'' +
                ", diffBytes=" + diffBytes +
                ", timestamp=" + timestamp +
                '}';
    }
}
