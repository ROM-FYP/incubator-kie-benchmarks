package org.kie.benchmark.cep.wikimedia.memoization;

import org.kie.api.definition.type.Expires;
import org.kie.api.definition.type.Role;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the cacheable output of a cluster-level computation.
 * This abstraction captures the semantic result that is relevant to correlation
 * and final alert outcomes.
 */
@Role(Role.Type.EVENT)
@Expires("90s")
public class ClusterOutput implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int clusterId;
    private String kind;              // e.g., "BOT_DETECTED", "CONTENT_FLAGGED", "MINOR_TRACKED"
    private String entityId;          // e.g., username, article title
    private long logicalTimestamp;
    private Map<String, Object> attributes;
    
    public ClusterOutput() {
        this.attributes = new HashMap<>();
    }
    
    public ClusterOutput(int clusterId, String kind, String entityId, long logicalTimestamp) {
        this.clusterId = clusterId;
        this.kind = kind;
        this.entityId = entityId;
        this.logicalTimestamp = logicalTimestamp;
        this.attributes = new HashMap<>();
    }
    
    public ClusterOutput(int clusterId, String kind, String entityId, long logicalTimestamp, Map<String, Object> attributes) {
        this.clusterId = clusterId;
        this.kind = kind;
        this.entityId = entityId;
        this.logicalTimestamp = logicalTimestamp;
        this.attributes = new HashMap<>(attributes);
    }
    
    // Getters and setters
    public int getClusterId() { return clusterId; }
    public void setClusterId(int clusterId) { this.clusterId = clusterId; }
    
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    
    public long getLogicalTimestamp() { return logicalTimestamp; }
    public void setLogicalTimestamp(long logicalTimestamp) { this.logicalTimestamp = logicalTimestamp; }
    
    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }
    
    @Override
    public String toString() {
        return "ClusterOutput{" +
                "clusterId=" + clusterId +
                ", kind='" + kind + '\'' +
                ", entityId='" + entityId + '\'' +
                ", timestamp=" + logicalTimestamp +
                ", attributes=" + attributes +
                '}';
    }
}
