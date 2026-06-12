package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class OriginFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String origin;
    private int rank;

    public OriginFact() {
    }

    public OriginFact(String messageId, String origin, int rank) {
        this.messageId = messageId;
        this.origin = origin;
        this.rank = rank;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

}