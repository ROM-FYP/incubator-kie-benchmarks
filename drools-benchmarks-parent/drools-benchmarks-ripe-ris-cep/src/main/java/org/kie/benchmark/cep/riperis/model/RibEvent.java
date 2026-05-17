package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class RibEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private String rib;
    private String description;

    public RibEvent() {
    }

    public RibEvent(String messageId, String prefix, String rib, String description) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.rib = rib;
        this.description = description;
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

    public String getRib() {
        return rib;
    }

    public void setRib(String rib) {
        this.rib = rib;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}