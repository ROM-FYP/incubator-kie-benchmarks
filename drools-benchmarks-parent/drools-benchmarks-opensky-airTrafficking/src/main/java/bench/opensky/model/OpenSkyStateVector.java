package bench.opensky.model;

/**
 * Flattened state vector fact inserted into KieSession.
 * Field names follow Java bean conventions (camelCase).
 * This is the primary fact type that DRL rules operate on (replaces the DRL-declared StateVector).
 */
// Event role + expiration declared in airTraffick_rules.drl
public class OpenSkyStateVector {

    private String icao24;
    private String callsign;
    private String originCountry;
    private Long timePosition;
    private Long lastContact;
    private Double lon;
    private Double lat;
    private Double baroAltitudeM;
    private Boolean onGround;
    private Double velocityMps;
    private Double trueTrackDeg;
    private Double verticalRateMps;
    private Double geoAltitudeM;
    private String squawk;
    private Boolean spi;
    private Integer positionSource;
    private Integer category;
    private long snapshotTime;

    public OpenSkyStateVector() { }

    /** Factory: create from parsed JSONL record. */
    public static OpenSkyStateVector fromFlatRecord(OpenSkyFlatRecord rec) {
        OpenSkyStateVector sv = new OpenSkyStateVector();
        sv.snapshotTime = rec.getSnapshotTime();
        OpenSkyFlatRecord.State s = rec.getState();
        if (s != null) {
            sv.icao24 = s.getIcao24();
            sv.callsign = s.getCallsign();
            sv.originCountry = s.getOriginCountry();
            sv.timePosition = s.getTimePosition();
            sv.lastContact = s.getLastContact();
            sv.lon = s.getLon();
            sv.lat = s.getLat();
            sv.baroAltitudeM = s.getBaroAltitudeM();
            sv.onGround = s.getOnGround();
            sv.velocityMps = s.getVelocityMps();
            sv.trueTrackDeg = s.getTrueTrackDeg();
            sv.verticalRateMps = s.getVerticalRateMps();
            sv.geoAltitudeM = s.getGeoAltitudeM();
            sv.squawk = s.getSquawk();
            sv.spi = s.getSpi();
            sv.positionSource = s.getPositionSource();
            sv.category = s.getCategory();
        }
        return sv;
    }

    // ---- getters / setters ----

    public String getIcao24() { return icao24; }
    public void setIcao24(String icao24) { this.icao24 = icao24; }

    public String getCallsign() { return callsign; }
    public void setCallsign(String callsign) { this.callsign = callsign; }

    public String getOriginCountry() { return originCountry; }
    public void setOriginCountry(String originCountry) { this.originCountry = originCountry; }

    public Long getTimePosition() { return timePosition; }
    public void setTimePosition(Long timePosition) { this.timePosition = timePosition; }

    public Long getLastContact() { return lastContact; }
    public void setLastContact(Long lastContact) { this.lastContact = lastContact; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getBaroAltitudeM() { return baroAltitudeM; }
    public void setBaroAltitudeM(Double baroAltitudeM) { this.baroAltitudeM = baroAltitudeM; }

    public Boolean getOnGround() { return onGround; }
    public void setOnGround(Boolean onGround) { this.onGround = onGround; }

    public Double getVelocityMps() { return velocityMps; }
    public void setVelocityMps(Double velocityMps) { this.velocityMps = velocityMps; }

    public Double getTrueTrackDeg() { return trueTrackDeg; }
    public void setTrueTrackDeg(Double trueTrackDeg) { this.trueTrackDeg = trueTrackDeg; }

    public Double getVerticalRateMps() { return verticalRateMps; }
    public void setVerticalRateMps(Double verticalRateMps) { this.verticalRateMps = verticalRateMps; }

    public Double getGeoAltitudeM() { return geoAltitudeM; }
    public void setGeoAltitudeM(Double geoAltitudeM) { this.geoAltitudeM = geoAltitudeM; }

    public String getSquawk() { return squawk; }
    public void setSquawk(String squawk) { this.squawk = squawk; }

    public Boolean getSpi() { return spi; }
    public void setSpi(Boolean spi) { this.spi = spi; }

    public Integer getPositionSource() { return positionSource; }
    public void setPositionSource(Integer positionSource) { this.positionSource = positionSource; }

    public Integer getCategory() { return category; }
    public void setCategory(Integer category) { this.category = category; }

    public long getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(long snapshotTime) { this.snapshotTime = snapshotTime; }

    @Override
    public String toString() {
        return "OpenSkyStateVector{icao24=" + icao24 + ", callsign=" + callsign +
               ", lat=" + lat + ", lon=" + lon + ", alt=" + baroAltitudeM + "}";
    }
}
