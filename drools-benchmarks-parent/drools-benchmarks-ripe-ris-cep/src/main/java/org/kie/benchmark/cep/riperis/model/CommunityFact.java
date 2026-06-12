package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class CommunityFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String kind;
    private int count;

    public CommunityFact() {
    }

    public CommunityFact(String messageId, String kind, int count) {
        this.messageId = messageId;
        this.kind = kind;
        this.count = count;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}