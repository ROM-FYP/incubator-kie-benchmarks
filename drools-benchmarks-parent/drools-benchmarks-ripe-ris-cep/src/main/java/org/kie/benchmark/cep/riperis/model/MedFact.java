package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class MedFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private Integer med;
    private String neighborAsn;

    public MedFact() {
    }

    public MedFact(String messageId, Integer med, String neighborAsn) {
        this.messageId = messageId;
        this.med = med;
        this.neighborAsn = neighborAsn;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Integer getMed() {
        return med;
    }

    public void setMed(Integer med) {
        this.med = med;
    }

    public String getNeighborAsn() {
        return neighborAsn;
    }

    public void setNeighborAsn(String neighborAsn) {
        this.neighborAsn = neighborAsn;
    }

}