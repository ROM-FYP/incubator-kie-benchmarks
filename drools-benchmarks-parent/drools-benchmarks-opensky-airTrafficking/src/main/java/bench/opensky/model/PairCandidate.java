package bench.opensky.model;

import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;

/**
 * Candidate pair of aircraft from the same grid cell.
 */
@Role(Role.Type.EVENT)
@Expires("10s")
public class PairCandidate {

    private String a;
    private String b;
    private String cellId;

    public PairCandidate() { }

    public PairCandidate(String a, String b, String cellId) {
        this.a = a;
        this.b = b;
        this.cellId = cellId;
    }

    public String getA() { return a; }
    public void setA(String a) { this.a = a; }
    public String getB() { return b; }
    public void setB(String b) { this.b = b; }
    public String getCellId() { return cellId; }
    public void setCellId(String cellId) { this.cellId = cellId; }

    @Override
    public String toString() {
        return "PairCandidate{a=" + a + ", b=" + b + ", cell=" + cellId + "}";
    }
}
