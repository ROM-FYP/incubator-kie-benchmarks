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
 * Represents a detected global pattern across multiple wikis or events.
 * Used for cross-wiki correlation and coordinated activity detection.
 */
public class GlobalPattern {
    private String pattern;
    private String sourceWiki;
    private String targetWiki;
    private double correlation;

    public GlobalPattern() {
    }

    public GlobalPattern(String pattern, String sourceWiki, String targetWiki, double correlation) {
        this.pattern = pattern;
        this.sourceWiki = sourceWiki;
        this.targetWiki = targetWiki;
        this.correlation = correlation;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getSourceWiki() {
        return sourceWiki;
    }

    public void setSourceWiki(String sourceWiki) {
        this.sourceWiki = sourceWiki;
    }

    public String getTargetWiki() {
        return targetWiki;
    }

    public void setTargetWiki(String targetWiki) {
        this.targetWiki = targetWiki;
    }

    public double getCorrelation() {
        return correlation;
    }

    public void setCorrelation(double correlation) {
        this.correlation = correlation;
    }

    @Override
    public String toString() {
        return "GlobalPattern{" +
                "pattern='" + pattern + '\'' +
                ", sourceWiki='" + sourceWiki + '\'' +
                ", targetWiki='" + targetWiki + '\'' +
                ", correlation=" + correlation +
                '}';
    }
}
