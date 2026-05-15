package bench.opensky.model;

/**
 * Alert acknowledgement.
 */
// Event role + expiration declared in airTraffick_rules.drl
public class AlertAck {

    private String key;   // "a|b|type"
    private Double at;

    public AlertAck() { }

    public AlertAck(String key, Double at) {
        this.key = key;
        this.at = at;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Double getAt() { return at; }
    public void setAt(Double at) { this.at = at; }

    @Override
    public String toString() {
        return "AlertAck{key=" + key + ", at=" + at + "}";
    }
}
