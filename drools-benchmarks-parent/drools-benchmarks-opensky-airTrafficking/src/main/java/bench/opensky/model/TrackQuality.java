package bench.opensky.model;

/**
 * Track quality flags per aircraft (icao24).
 */
// Event role + expiration declared in airTraffick_rules.drl
public class TrackQuality {

    private String icao24;
    private Boolean stalePos;
    private Boolean staleAny;
    private Boolean missingPos;
    private Boolean badAlt;
    private Boolean badVel;
    private String reason;

    public TrackQuality() { }

    public TrackQuality(String icao24, Boolean stalePos, Boolean staleAny,
                        Boolean missingPos, Boolean badAlt, Boolean badVel, String reason) {
        this.icao24 = icao24;
        this.stalePos = stalePos;
        this.staleAny = staleAny;
        this.missingPos = missingPos;
        this.badAlt = badAlt;
        this.badVel = badVel;
        this.reason = reason;
    }

    public String getIcao24() { return icao24; }
    public void setIcao24(String icao24) { this.icao24 = icao24; }
    public Boolean getStalePos() { return stalePos; }
    public void setStalePos(Boolean stalePos) { this.stalePos = stalePos; }
    public Boolean getStaleAny() { return staleAny; }
    public void setStaleAny(Boolean staleAny) { this.staleAny = staleAny; }
    public Boolean getMissingPos() { return missingPos; }
    public void setMissingPos(Boolean missingPos) { this.missingPos = missingPos; }
    public Boolean getBadAlt() { return badAlt; }
    public void setBadAlt(Boolean badAlt) { this.badAlt = badAlt; }
    public Boolean getBadVel() { return badVel; }
    public void setBadVel(Boolean badVel) { this.badVel = badVel; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    @Override
    public String toString() {
        return "TrackQuality{icao24=" + icao24 + ", reason=" + reason + "}";
    }
}
