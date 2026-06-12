package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class RouteDecision implements Serializable {
    private static final long serialVersionUID = 1L;

    private String prefix;
    private String winnerMessageId;
    private String loserMessageId;
    private String reason;

    public RouteDecision() {
    }

    public RouteDecision(String prefix, String winnerMessageId, String loserMessageId, String reason) {
        this.prefix = prefix;
        this.winnerMessageId = winnerMessageId;
        this.loserMessageId = loserMessageId;
        this.reason = reason;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getWinnerMessageId() {
        return winnerMessageId;
    }

    public void setWinnerMessageId(String winnerMessageId) {
        this.winnerMessageId = winnerMessageId;
    }

    public String getLoserMessageId() {
        return loserMessageId;
    }

    public void setLoserMessageId(String loserMessageId) {
        this.loserMessageId = loserMessageId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

}