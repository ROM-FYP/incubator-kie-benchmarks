package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class PrefixFact implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String prefix;
    private String family;
    private int prefixLength;
    private String kind;

    public PrefixFact() {
    }

    public PrefixFact(String messageId, String prefix, String family, int prefixLength, String kind) {
        this.messageId = messageId;
        this.prefix = prefix;
        this.family = family;
        this.prefixLength = prefixLength;
        this.kind = kind;
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

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public void setPrefixLength(int prefixLength) {
        this.prefixLength = prefixLength;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

}