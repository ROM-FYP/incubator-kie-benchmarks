package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class RouteValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private boolean valid;
    private String validationType;
    private String description;

    public RouteValidationResult() {
    }

    public RouteValidationResult(String messageId, String prefix, boolean valid, String validationType, String description) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.valid = valid;
        this.validationType = validationType;
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

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public String getValidationType() {
        return validationType;
    }

    public void setValidationType(String validationType) {
        this.validationType = validationType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}