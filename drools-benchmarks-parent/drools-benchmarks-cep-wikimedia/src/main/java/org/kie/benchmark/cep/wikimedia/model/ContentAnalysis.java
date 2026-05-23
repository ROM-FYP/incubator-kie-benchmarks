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

import java.util.List;

/**
 * Results of content analysis including sentiment and complexity metrics.
 * Represents expensive text processing operations.
 */
public class ContentAnalysis {
    private String title;
    private String content;
    private double sentiment;
    private int complexity;
    private List<String> flags;

    public ContentAnalysis() {
    }

    public ContentAnalysis(String title, String content, double sentiment, int complexity, List<String> flags) {
        this.title = title;
        this.content = content;
        this.sentiment = sentiment;
        this.complexity = complexity;
        this.flags = flags;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getSentiment() {
        return sentiment;
    }

    public void setSentiment(double sentiment) {
        this.sentiment = sentiment;
    }

    public int getComplexity() {
        return complexity;
    }

    public void setComplexity(int complexity) {
        this.complexity = complexity;
    }

    public List<String> getFlags() {
        return flags;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "ContentAnalysis{" +
                "title='" + title + '\'' +
                ", sentiment=" + sentiment +
                ", complexity=" + complexity +
                ", flags=" + flags +
                '}';
    }
}
