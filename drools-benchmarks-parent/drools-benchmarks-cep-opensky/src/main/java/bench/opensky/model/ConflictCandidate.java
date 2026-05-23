package bench.opensky.model;

import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;

/**
 * Conflict detection result between a pair of aircraft.
 */
@Role(Role.Type.EVENT)
@Expires("10s")
public class ConflictCandidate {

    private String a;
    private String b;
    private Double distM;
    private Double vertSepM;
    private Boolean closing;
    private Double ttcSec;
    private String severity;   // INFO, WARN, ALERT

    public ConflictCandidate() { }

    public ConflictCandidate(String a, String b, Double distM, Double vertSepM,
                             Boolean closing, Double ttcSec, String severity) {
        this.a = a;
        this.b = b;
        this.distM = distM;
        this.vertSepM = vertSepM;
        this.closing = closing;
        this.ttcSec = ttcSec;
        this.severity = severity;
    }

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }
    public String getB() { return b; }
    public void setB(String b) { this.b = b; }
    public Double getDistM() { return distM; }
    public void setDistM(Double distM) { this.distM = distM; }
    public Double getVertSepM() { return vertSepM; }
    public void setVertSepM(Double vertSepM) { this.vertSepM = vertSepM; }
    public Boolean getClosing() { return closing; }
    public void setClosing(Boolean closing) { this.closing = closing; }
    public Double getTtcSec() { return ttcSec; }
    public void setTtcSec(Double ttcSec) { this.ttcSec = ttcSec; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    @Override
    public String toString() {
        return "ConflictCandidate{a=" + a + ", b=" + b + ", distM=" + distM + ", sev=" + severity + "}";
    }
}
