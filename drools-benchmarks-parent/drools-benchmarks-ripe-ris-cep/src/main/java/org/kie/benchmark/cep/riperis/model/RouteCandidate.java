package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;
import java.util.List;

public class RouteCandidate implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private String peer;
    private String peerAsn;
    private String host;
    private double timestamp;
    private List<?> path;
    private int pathLength;
    private String firstAsn;
    private String originAsn;
    private String origin;
    private int originRank;
    private Integer med;
    private String nextHop;
    private boolean feasible;

    public RouteCandidate() {
    }

    public RouteCandidate(String messageId, String prefix, String peer, String peerAsn, String host, double timestamp, List<?> path, int pathLength, String firstAsn, String originAsn, String origin, int originRank, Integer med, String nextHop, boolean feasible) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.peer = peer;
        this.peerAsn = peerAsn;
        this.host = host;
        this.timestamp = timestamp;
        this.path = path;
        this.pathLength = pathLength;
        this.firstAsn = firstAsn;
        this.originAsn = originAsn;
        this.origin = origin;
        this.originRank = originRank;
        this.med = med;
        this.nextHop = nextHop;
        this.feasible = feasible;
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

    public String getPeerAsn() {
        return peerAsn;
    }

    public void setPeerAsn(String peerAsn) {
        this.peerAsn = peerAsn;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public List<?> getPath() {
        return path;
    }

    public void setPath(List<?> path) {
        this.path = path;
    }

    public int getPathLength() {
        return pathLength;
    }

    public void setPathLength(int pathLength) {
        this.pathLength = pathLength;
    }

    public String getFirstAsn() {
        return firstAsn;
    }

    public void setFirstAsn(String firstAsn) {
        this.firstAsn = firstAsn;
    }

    public String getOriginAsn() {
        return originAsn;
    }

    public void setOriginAsn(String originAsn) {
        this.originAsn = originAsn;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public int getOriginRank() {
        return originRank;
    }

    public void setOriginRank(int originRank) {
        this.originRank = originRank;
    }

    public Integer getMed() {
        return med;
    }

    public void setMed(Integer med) {
        this.med = med;
    }

    public String getNextHop() {
        return nextHop;
    }

    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public void setFeasible(boolean feasible) {
        this.feasible = feasible;
    }

}