package bench.opensky.model;

import org.kie.api.definition.type.Role;
import org.kie.api.definition.type.Expires;

/**
 * Kinematic delta between two consecutive state vectors for the same aircraft.
 */
@Role(Role.Type.EVENT)
@Expires("10s")
public class KinematicDelta {

    private String icao24;
    private Double dVel;
    private Double dTrack;
    private Double dVrt;
    private Double dtSec;

    public KinematicDelta() { }

    public KinematicDelta(String icao24, Double dVel, Double dTrack, Double dVrt, Double dtSec) {
        this.icao24 = icao24;
        this.dVel = dVel;
        this.dTrack = dTrack;
        this.dVrt = dVrt;
        this.dtSec = dtSec;
    }

    public String getIcao24() { return icao24; }
    public void setIcao24(String icao24) { this.icao24 = icao24; }
    public Double getDVel() { return dVel; }
    public void setDVel(Double dVel) { this.dVel = dVel; }
    public Double getDTrack() { return dTrack; }
    public void setDTrack(Double dTrack) { this.dTrack = dTrack; }
    public Double getDVrt() { return dVrt; }
    public void setDVrt(Double dVrt) { this.dVrt = dVrt; }
    public Double getDtSec() { return dtSec; }
    public void setDtSec(Double dtSec) { this.dtSec = dtSec; }

    @Override
    public String toString() {
        return "KinematicDelta{icao24=" + icao24 + ", dVel=" + dVel + ", dt=" + dtSec + "}";
    }
}
