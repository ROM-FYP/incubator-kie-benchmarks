package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class RouteWithdrawal implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String peer;
    private String peerAsn;
    private String host;
    private double timestamp;
    private String prefix;

    public RouteWithdrawal() {
    }

    public RouteWithdrawal(String messageId, String peer, String peerAsn, String host, double timestamp, String prefix) {
        this.messageId = messageId;
        this.peer = peer;
        this.peerAsn = peerAsn;
        this.host = host;
        this.timestamp = timestamp;
        this.prefix = prefix;
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

}