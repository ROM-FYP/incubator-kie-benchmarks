package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class TimeBucketFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private double bucket;

    public TimeBucketFact() {
    }

    public TimeBucketFact(String messageId, double bucket) {
        this.messageId = messageId;
        this.bucket = bucket;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public double getBucket() {
        return bucket;
    }

    public void setBucket(double bucket) {
        this.bucket = bucket;
    }

}