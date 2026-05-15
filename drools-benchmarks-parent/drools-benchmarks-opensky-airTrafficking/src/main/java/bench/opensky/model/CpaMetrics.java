package bench.opensky.model;

/**
 * CPA (Closest Point of Approach) computation results.
 */
// Event role + expiration declared in airTraffick_rules.drl
public class CpaMetrics {

    private String a;
    private String b;
    private Double computedAtSec;
    private Double tCpaSec;
    private Double dCpaM;
    private Double vertAtCpaM;
    private Boolean closing;

    public CpaMetrics() { }

    public CpaMetrics(String a, String b, Double computedAtSec, Double tCpaSec,
                      Double dCpaM, Double vertAtCpaM, Boolean closing) {
        this.a = a;
        this.b = b;
        this.computedAtSec = computedAtSec;
        this.tCpaSec = tCpaSec;
        this.dCpaM = dCpaM;
        this.vertAtCpaM = vertAtCpaM;
        this.closing = closing;
    }

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }
    public String getB() { return b; }
    public void setB(String b) { this.b = b; }
    public Double getComputedAtSec() { return computedAtSec; }
    public void setComputedAtSec(Double computedAtSec) { this.computedAtSec = computedAtSec; }
    public Double getTCpaSec() { return tCpaSec; }
    public void setTCpaSec(Double tCpaSec) { this.tCpaSec = tCpaSec; }
    public Double getDCpaM() { return dCpaM; }
    public void setDCpaM(Double dCpaM) { this.dCpaM = dCpaM; }
    public Double getVertAtCpaM() { return vertAtCpaM; }
    public void setVertAtCpaM(Double vertAtCpaM) { this.vertAtCpaM = vertAtCpaM; }
    public Boolean getClosing() { return closing; }
    public void setClosing(Boolean closing) { this.closing = closing; }

    @Override
    public String toString() {
        return "CpaMetrics{a=" + a + ", b=" + b + ", tCPA=" + tCpaSec + ", dCPA=" + dCpaM + "}";
    }
}
