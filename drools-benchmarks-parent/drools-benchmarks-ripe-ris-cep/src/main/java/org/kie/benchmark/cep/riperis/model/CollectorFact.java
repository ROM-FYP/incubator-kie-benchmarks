package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class CollectorFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String host;

    public CollectorFact() {
    }

    public CollectorFact(String messageId, String host) {
        this.messageId = messageId;
        this.host = host;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

}