package bench.opensky.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jackson deserialization target for one line of the flat JSONL file.
 * Maps directly to the nested JSON structure: { snapshot_time, collected_at_utc, request_index, bbox, state }.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenSkyFlatRecord {

    @JsonProperty("snapshot_time")
    private long snapshotTime;

    @JsonProperty("collected_at_utc")
    private String collectedAtUtc;

    @JsonProperty("request_index")
    private int requestIndex;

    @JsonProperty("bbox")
    private BBox bbox;

    @JsonProperty("state")
    private State state;

    public OpenSkyFlatRecord() { }

    // ---- getters / setters ----

    public long getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(long snapshotTime) { this.snapshotTime = snapshotTime; }

    public String getCollectedAtUtc() { return collectedAtUtc; }
    public void setCollectedAtUtc(String collectedAtUtc) { this.collectedAtUtc = collectedAtUtc; }

    public int getRequestIndex() { return requestIndex; }
    public void setRequestIndex(int requestIndex) { this.requestIndex = requestIndex; }

    public BBox getBbox() { return bbox; }
    public void setBbox(BBox bbox) { this.bbox = bbox; }

    public State getState() { return state; }
    public void setState(State state) { this.state = state; }

    @Override
    public String toString() {
        return "OpenSkyFlatRecord{snapshotTime=" + snapshotTime + ", icao24=" +
                (state != null ? state.getIcao24() : "null") + "}";
    }

    // ---- nested types ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BBox {
        private double lamin;
        private double lomin;
        private double lamax;
        private double lomax;

        public BBox() { }

        public double getLamin() { return lamin; }
        public void setLamin(double lamin) { this.lamin = lamin; }
        public double getLomin() { return lomin; }
        public void setLomin(double lomin) { this.lomin = lomin; }
        public double getLamax() { return lamax; }
        public void setLamax(double lamax) { this.lamax = lamax; }
        public double getLomax() { return lomax; }
        public void setLomax(double lomax) { this.lomax = lomax; }

        @Override public String toString() {
            return "BBox{" + lamin + "," + lomin + "," + lamax + "," + lomax + "}";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class State {
        private String icao24;
        private String callsign;

        @JsonProperty("origin_country")
        private String originCountry;

        @JsonProperty("time_position")
        private Long timePosition;

        @JsonProperty("last_contact")
        private Long lastContact;

        private Double lon;
        private Double lat;

        @JsonProperty("baro_altitude_m")
        private Double baroAltitudeM;

        @JsonProperty("on_ground")
        private Boolean onGround;

        @JsonProperty("velocity_mps")
        private Double velocityMps;

        @JsonProperty("true_track_deg")
        private Double trueTrackDeg;

        @JsonProperty("vertical_rate_mps")
        private Double verticalRateMps;

        @JsonProperty("geo_altitude_m")
        private Double geoAltitudeM;

        private String squawk;
        private Boolean spi;

        @JsonProperty("position_source")
        private Integer positionSource;

        private Integer category;

        public State() { }

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

        @Override public String toString() {
            return "State{icao24=" + icao24 + ", callsign=" + callsign + "}";
        }
    }
}
