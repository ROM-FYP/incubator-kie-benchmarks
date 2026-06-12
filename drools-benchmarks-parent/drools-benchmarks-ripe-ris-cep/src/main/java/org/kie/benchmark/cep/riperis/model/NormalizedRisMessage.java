package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;
import java.util.List;

public class NormalizedRisMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private double timestamp;
    private String peer;
    private String peerAsn;
    private String host;
    private String bgpType;
    private List<?> path;
    private List<?> community;
    private String origin;
    private Integer med;
    private Object aggregator;
    private List<?> announcements;
    private List<?> withdrawals;

    public NormalizedRisMessage() {
    }

    public NormalizedRisMessage(String id, double timestamp, String peer, String peerAsn, String host, String bgpType, List<?> path, List<?> community, String origin, Integer med, Object aggregator, List<?> announcements, List<?> withdrawals) {
        this.id = id;
        this.timestamp = timestamp;
        this.peer = peer;
        this.peerAsn = peerAsn;
        this.host = host;
        this.bgpType = bgpType;
        this.path = path;
        this.community = community;
        this.origin = origin;
        this.med = med;
        this.aggregator = aggregator;
        this.announcements = announcements;
        this.withdrawals = withdrawals;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
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

    public String getBgpType() {
        return bgpType;
    }

    public void setBgpType(String bgpType) {
        this.bgpType = bgpType;
    }

    public List<?> getPath() {
        return path;
    }

    public void setPath(List<?> path) {
        this.path = path;
    }

    public List<?> getCommunity() {
        return community;
    }

    public void setCommunity(List<?> community) {
        this.community = community;
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

    public List<?> getAnnouncements() {
        return announcements;
    }

    public void setAnnouncements(List<?> announcements) {
        this.announcements = announcements;
    }

    public List<?> getWithdrawals() {
        return withdrawals;
    }

    public void setWithdrawals(List<?> withdrawals) {
        this.withdrawals = withdrawals;
    }

}