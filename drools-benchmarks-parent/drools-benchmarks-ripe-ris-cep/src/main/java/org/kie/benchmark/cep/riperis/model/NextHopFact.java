package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class NextHopFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private String rawNextHop;
    private String primaryNextHop;
    private String family;
    private boolean multiValue;

    public NextHopFact() {
    }

    public NextHopFact(String messageId, String prefix, String rawNextHop, String primaryNextHop, String family, boolean multiValue) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.rawNextHop = rawNextHop;
        this.primaryNextHop = primaryNextHop;
        this.family = family;
        this.multiValue = multiValue;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getRawNextHop() {
        return rawNextHop;
    }

    public void setRawNextHop(String rawNextHop) {
        this.rawNextHop = rawNextHop;
    }

    public String getPrimaryNextHop() {
        return primaryNextHop;
    }

    public void setPrimaryNextHop(String primaryNextHop) {
        this.primaryNextHop = primaryNextHop;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public boolean isMultiValue() {
        return multiValue;
    }

    public void setMultiValue(boolean multiValue) {
        this.multiValue = multiValue;
    }

}