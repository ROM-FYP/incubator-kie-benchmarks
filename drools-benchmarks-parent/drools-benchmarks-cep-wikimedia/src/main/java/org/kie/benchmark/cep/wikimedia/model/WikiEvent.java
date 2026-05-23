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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Timestamp;

import java.util.Map;

/**
 * Represents a Wikipedia edit event from the Wikimedia stream.
 * This is the primary event type fed into the Drools CEP engine.
 *
 * <p>The raw Wikimedia Recent Changes stream has article sizes in a nested object:
 * <pre>{"length": {"old": 24354, "new": 24420}}</pre>
 * This class computes {@code sizeDelta = length.new - length.old} during
 * deserialization so rules can use {@code sizeDelta} directly.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} silently drops
 * extra fields present in the live Wikimedia dataset (e.g. {@code $schema},
 * {@code meta}, {@code type}, {@code namespace}) so only the CEP-relevant
 * fields are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Role(Role.Type.EVENT)
@Expires("90s")
@Timestamp("timestamp")
public class WikiEvent {
    private String title;
    private String user;
    private String comment;
    private boolean bot;
    private boolean minor;
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

    /**
     * Jackson setter for the nested "length" object.
     * Computes sizeDelta = new - old.  If either field is missing, sizeDelta stays 0.
     */
    @JsonSetter("length")
    public void setLength(Map<String, Integer> length) {
        if (length != null) {
            Integer oldLen = length.get("old");
            Integer newLen = length.get("new");
            if (oldLen != null && newLen != null) {
                this.sizeDelta = newLen - oldLen;
            }
        }
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

    public boolean isMinor() {
        return minor;
    }

    public void setMinor(boolean minor) {
        this.minor = minor;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        // The JSON data has timestamp in seconds, but the DRL and pseudo-clock expect milliseconds
        this.timestamp = timestamp * 1000L;
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
                ", minor=" + minor +
                ", timestamp=" + timestamp +
                ", sizeDelta=" + sizeDelta +
                '}';
    }
}

