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
 * Aggregates facts from multiple clusters for editorial decision-making.
 * Primary target for beta joins - combines UserContext, ContentAnalysis, and QualityGrade.
 */
public class EditorialPackage {
    private String title;
    private String user;
    private String qualityGrade;
    private double contentScore;
    private int userReputation;
    private String recommendation;  // "APPROVE", "REVIEW", "REJECT"

    public EditorialPackage() {
    }

    public EditorialPackage(String title, String user, String qualityGrade, double contentScore, 
                           int userReputation, String recommendation) {
        this.title = title;
        this.user = user;
        this.qualityGrade = qualityGrade;
        this.contentScore = contentScore;
        this.userReputation = userReputation;
        this.recommendation = recommendation;
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

    public String getQualityGrade() {
        return qualityGrade;
    }

    public void setQualityGrade(String qualityGrade) {
        this.qualityGrade = qualityGrade;
    }

    public double getContentScore() {
        return contentScore;
    }

    public void setContentScore(double contentScore) {
        this.contentScore = contentScore;
    }

    public int getUserReputation() {
        return userReputation;
    }

    public void setUserReputation(int userReputation) {
        this.userReputation = userReputation;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    @Override
    public String toString() {
        return "EditorialPackage{" +
                "title='" + title + '\'' +
                ", user='" + user + '\'' +
                ", qualityGrade='" + qualityGrade + '\'' +
                ", contentScore=" + contentScore +
                ", userReputation=" + userReputation +
                ", recommendation='" + recommendation + '\'' +
                '}';
    }
}
