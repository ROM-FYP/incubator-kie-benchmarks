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
 * Represents a Wikipedia edit event from the Wikimedia stream.
 * This is the primary event type fed into the Drools CEP engine.
 */
public class WikiEvent {
    private String title;
    private String user;
    private String comment;
    private boolean bot;
    private long timestamp;
    private int sizeDelta;

    public WikiEvent() {
    }

    public WikiEvent(String title, String user, String comment, boolean bot, long timestamp, int sizeDelta) {
        this.title = title;
        this.user = user;
        this.comment = comment;
        this.bot = bot;
        this.timestamp = timestamp;
        this.sizeDelta = sizeDelta;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public boolean isBot() {
        return bot;
    }

    public void setBot(boolean bot) {
        this.bot = bot;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getSizeDelta() {
        return sizeDelta;
    }

    public void setSizeDelta(int sizeDelta) {
        this.sizeDelta = sizeDelta;
    }

    @Override
    public String toString() {
        return "WikiEvent{" +
                "title='" + title + '\'' +
                ", user='" + user + '\'' +
                ", bot=" + bot +
                ", timestamp=" + timestamp +
                ", sizeDelta=" + sizeDelta +
                '}';
    }
}
