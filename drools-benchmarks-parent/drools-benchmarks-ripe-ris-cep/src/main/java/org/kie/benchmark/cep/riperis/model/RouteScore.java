package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class RouteScore implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private String peer;
    private int pathLength;
    private int originRank;
    private int med;
    private int score;

    public RouteScore() {
    }

    public RouteScore(String messageId, String prefix, String peer, int pathLength, int originRank, int med, int score) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.peer = peer;
        this.pathLength = pathLength;
        this.originRank = originRank;
        this.med = med;
        this.score = score;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getPeer() {
        return peer;
    }

    public void setPeer(String peer) {
        this.peer = peer;
    }

    public int getPathLength() {
        return pathLength;
    }

    public void setPathLength(int pathLength) {
        this.pathLength = pathLength;
    }

    public int getOriginRank() {
        return originRank;
    }

    public void setOriginRank(int originRank) {
        this.originRank = originRank;
    }

    public int getMed() {
        return med;
    }

    public void setMed(int med) {
        this.med = med;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

}