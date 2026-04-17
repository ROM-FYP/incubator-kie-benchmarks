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
 * Final alert fact representing a detected viral topic.
 * Generated when rules detect sufficient activity and diversity for a page.
 */
public class ViralTopicAlert {
    private String pageTitle;
    private String category;
    private double score;
    private long timestamp;

    public ViralTopicAlert() {
    }

    public ViralTopicAlert(String pageTitle, String category, double score) {
        this.pageTitle = pageTitle;
        this.category = category;
        this.score = score;
        this.timestamp = System.currentTimeMillis();
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ViralTopicAlert{" +
                "pageTitle='" + pageTitle + '\'' +
                ", category='" + category + '\'' +
                ", score=" + score +
                ", timestamp=" + timestamp +
                '}';
    }
}
