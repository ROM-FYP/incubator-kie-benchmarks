package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class AggregatorFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private Object aggregator;

    public AggregatorFact() {
    }

    public AggregatorFact(String messageId, Object aggregator) {
        this.messageId = messageId;
        this.aggregator = aggregator;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Object getAggregator() {
        return aggregator;
    }

    public void setAggregator(Object aggregator) {
        this.aggregator = aggregator;
    }

}