package org.kie.benchmark.cep.riperis.model;

import java.io.Serializable;
import java.util.List;

public class BgpPath implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String peerAsn;
    private List<?> path;
    private int pathLength;
    private String firstAsn;
    private String originAsn;
    private boolean hasAsSet;

    public BgpPath() {
    }

    public BgpPath(String messageId, String peerAsn, List<?> path, int pathLength, String firstAsn, String originAsn, boolean hasAsSet) {
        this.messageId = messageId;
        this.peerAsn = peerAsn;
        this.path = path;
        this.pathLength = pathLength;
        this.firstAsn = firstAsn;
        this.originAsn = originAsn;
        this.hasAsSet = hasAsSet;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getPeerAsn() {
        return peerAsn;
    }

    public void setPeerAsn(String peerAsn) {
        this.peerAsn = peerAsn;
    }

    public List<?> getPath() {
        return path;
    }

    public void setPath(List<?> path) {
        this.path = path;
    }

    public int getPathLength() {
        return pathLength;
    }

    public void setPathLength(int pathLength) {
        this.pathLength = pathLength;
    }

    public String getFirstAsn() {
        return firstAsn;
    }

    public void setFirstAsn(String firstAsn) {
        this.firstAsn = firstAsn;
    }

    public String getOriginAsn() {
        return originAsn;
    }

    public void setOriginAsn(String originAsn) {
        this.originAsn = originAsn;
    }

    public boolean isHasAsSet() {
        return hasAsSet;
    }

    public void setHasAsSet(boolean hasAsSet) {
        this.hasAsSet = hasAsSet;
    }

}