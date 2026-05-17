package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;

public class AggregationHint implements Serializable {
    private static final long serialVersionUID = 1L;

    private String leftMessageId;
    private String rightMessageId;
    private String kind;
    private String description;

    public AggregationHint() {
    }

    public AggregationHint(String leftMessageId, String rightMessageId, String kind, String description) {
        this.leftMessageId = leftMessageId;
        this.rightMessageId = rightMessageId;
        this.kind = kind;
        this.description = description;
    }

    public String getLeftMessageId() {
        return leftMessageId;
    }

    public void setLeftMessageId(String leftMessageId) {
        this.leftMessageId = leftMessageId;
    }

    public String getRightMessageId() {
        return rightMessageId;
    }

    public void setRightMessageId(String rightMessageId) {
        this.rightMessageId = rightMessageId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}