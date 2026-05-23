package bench.opensky.model;

/**
 * System availability status.
 */
public class Status {

    private Boolean stcaAvailable;
    private Double at;

    public Status() { }

    public Status(Boolean stcaAvailable, Double at) {
        this.stcaAvailable = stcaAvailable;
        this.at = at;
    }

    public Boolean getStcaAvailable() { return stcaAvailable; }
    public void setStcaAvailable(Boolean stcaAvailable) { this.stcaAvailable = stcaAvailable; }
    public Double getAt() { return at; }
    public void setAt(Double at) { this.at = at; }

    @Override
    public String toString() {
        return "Status{stcaAvailable=" + stcaAvailable + ", at=" + at + "}";
    }
}
