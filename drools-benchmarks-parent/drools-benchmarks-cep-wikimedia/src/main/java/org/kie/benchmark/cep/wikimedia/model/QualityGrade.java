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
 * Quality assessment grade for an edit.
 * Used in beta joins to correlate quality with user activity and suspicions.
 */
public class QualityGrade {
    private String title;
    private String user;
    private String grade;  // "HIGH", "MEDIUM", "LOW"
    private int score;

    public QualityGrade() {
    }

    public QualityGrade(String title, String user, String grade, int score) {
        this.title = title;
        this.user = user;
        this.grade = grade;
        this.score = score;
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

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "QualityGrade{" +
                "title='" + title + '\'' +
                ", user='" + user + '\'' +
                ", grade='" + grade + '\'' +
                ", score=" + score +
                '}';
    }
}
