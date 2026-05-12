package bench.opensky.model;

/**
 * Alert inhibition configuration.
 */
public class AlertInhibit {

    private String kind;   // "AIRSPACE", "FLIGHTPAIR", "FLIGHT"
    private String key;    // cellId, icao24, or "a|b"
    private Boolean enabled;

    public AlertInhibit() { }

    public AlertInhibit(String kind, String key, Boolean enabled) {
        this.kind = kind;
        this.key = key;
        this.enabled = enabled;
    }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    @Override
    public String toString() {
        return "AlertInhibit{kind=" + kind + ", key=" + key + ", enabled=" + enabled + "}";
    }
}
