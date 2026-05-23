package bench.opensky.model;

import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;

/**
 * Alert acknowledgement.
 */
@Role(Role.Type.EVENT)
@Expires("10m")
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
