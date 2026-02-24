package bench.opensky.model;

import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;

/**
 * Audit trail entry.
 */
@Role(Role.Type.EVENT)
@Expires("30s")
public class AuditEvent {

    private String kind;
    private String key;
    private double at;
    private String details;

    public AuditEvent() { }

    public AuditEvent(String kind, String key, double at, String details) {
        this.kind = kind;
        this.key = key;
        this.at = at;
        this.details = details;
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public double getAt() { return at; }
    public void setAt(double at) { this.at = at; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    @Override
    public String toString() {
        return "AuditEvent{kind=" + kind + ", key=" + key + ", details=" + details + "}";
    }
}
