package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;
import java.util.List;

public class RouteAnnouncement implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String peer;
    private String peerAsn;
    private String host;
    private double timestamp;
    private String prefix;
    private String nextHop;
    private List<?> path;
    private String origin;
    private Integer med;
    private Object aggregator;
    private List<?> community;

    public RouteAnnouncement() {
    }

    public RouteAnnouncement(String messageId, String peer, String peerAsn, String host, double timestamp, String prefix, String nextHop, List<?> path, String origin, Integer med, Object aggregator, List<?> community) {
        this.messageId = messageId;
        this.peer = peer;
        this.peerAsn = peerAsn;
        this.host = host;
        this.timestamp = timestamp;
        this.prefix = prefix;
        this.nextHop = nextHop;
        this.path = path;
        this.origin = origin;
        this.med = med;
        this.aggregator = aggregator;
        this.community = community;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
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

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getNextHop() {
        return nextHop;
    }

    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public List<?> getPath() {
        return path;
    }

    public void setPath(List<?> path) {
        this.path = path;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public Integer getMed() {
        return med;
    }

    public void setMed(Integer med) {
        this.med = med;
    }

    public Object getAggregator() {
        return aggregator;
    }

    public void setAggregator(Object aggregator) {
        this.aggregator = aggregator;
    }

    public List<?> getCommunity() {
        return community;
    }

    public void setCommunity(List<?> community) {
        this.community = community;
    }

}