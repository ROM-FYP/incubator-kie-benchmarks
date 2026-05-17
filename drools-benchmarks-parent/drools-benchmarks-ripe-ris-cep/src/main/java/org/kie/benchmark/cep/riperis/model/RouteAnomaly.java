package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class RouteAnomaly implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private String code;
    private String description;

    public RouteAnomaly() {
    }

    public RouteAnomaly(String messageId, String prefix, String code, String description) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.code = code;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}