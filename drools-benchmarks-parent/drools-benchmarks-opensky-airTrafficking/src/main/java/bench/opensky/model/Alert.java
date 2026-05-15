package bench.opensky.model;

/**
 * Raised alert.
 */
// Event role + expiration declared in airTraffick_rules.drl
public class Alert {

    private String a;
    private String b;
    private String type;       // STCA_LIKE, TRAFFIC_ADVISORY, SAFETY_ALERT
    private String severity;
    private Double createdAt;
    private String reason;

    public Alert() { }

    public Alert(String a, String b, String type, String severity, Double createdAt, String reason) {
        this.a = a;
        this.b = b;
        this.type = type;
        this.severity = severity;
        this.createdAt = createdAt;
        this.reason = reason;
    }

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }
    public String getB() { return b; }
    public void setB(String b) { this.b = b; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public Double getCreatedAt() { return createdAt; }
    public void setCreatedAt(Double createdAt) { this.createdAt = createdAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return "Alert{a=" + a + ", b=" + b + ", type=" + type + ", sev=" + severity + "}";
    }
}
