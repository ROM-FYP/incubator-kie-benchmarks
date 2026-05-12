package bench.opensky.router;

import bench.opensky.model.OpenSkyStateVector;
import java.util.HashSet;
import java.util.Set;

/**
 * Event Router based strictly on the Alpha Node constraints of the rules mapped to each 
 * cluster in the empirical 89/13 dependency resolution split.
 * 
 * If a state vector satisfies ANY of the alpha constraints of ANY rule in a cluster, 
 * it must be routed to that cluster.
 */
public class AlphaRouter {

    public Set<String> route(OpenSkyStateVector sv) {
        Set<String> targetClusters = new HashSet<>();

        // 1. Monolith (89 rules)
        // Contains C1, C2, C3, C4, C6, C8
        // R041 requires (lat != null, lon != null, onGround == false, velocity > 10)
        // BUT C3 auditing rules like R006 require (onGround == true)
        // R007 requires (callsign == null)
        // Because the monolith contains rules that validate every edge case (in-air, on-ground, missing data),
        // the union of its alpha constraints is effectively true for all events.
        targetClusters.add("monolith");

        // 2. Independent Cluster (13 rules)
        // Combines original C5, C7, C9
        // R001: lat == null || lon == null
        // R002: timePosition != null (almost all events have timePosition)
        // R004: baroAltitudeM < -200 || geoAltitudeM < -200
        // R005: velocityMps < 0
        // R003: lastContact == null || (clock > lastContact + delay) (requires clock, passes through alpha)
        // R021: velocityMps != null
        if (sv.getTimePosition() != null || 
            sv.getLat() == null || sv.getLon() == null || 
            (sv.getBaroAltitudeM() != null && sv.getBaroAltitudeM() < -200) ||
            (sv.getGeoAltitudeM() != null && sv.getGeoAltitudeM() < -200) ||
            (sv.getVelocityMps() != null)) {
            targetClusters.add("independent");
        }

        return targetClusters;
    }
}
