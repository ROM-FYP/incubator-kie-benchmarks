package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class PeerSessionFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String peer;
    private String peerAsn;
    private String host;
    private double timestamp;

    public PeerSessionFact() {
    }

    public PeerSessionFact(String messageId, String peer, String peerAsn, String host, double timestamp) {
        this.messageId = messageId;
        this.peer = peer;
        this.peerAsn = peerAsn;
        this.host = host;
        this.timestamp = timestamp;
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

}