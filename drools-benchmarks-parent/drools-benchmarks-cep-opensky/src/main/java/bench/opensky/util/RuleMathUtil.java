package bench.opensky.util;

/**
 * Static math helpers used by DRL rules via {@code import static bench.opensky.util.RuleMathUtil.*}.
 * Moved out of inline DRL functions so that POJOs-only model works with standard Java imports.
 */
public final class RuleMathUtil {

    private static final double EARTH_RADIUS_M = 6_371_000.0;
    private static final double METERS_PER_NM = 1_852.0;

    private RuleMathUtil() { }

    /** Convert meters to nautical miles. */
    public static double nm(double meters) {
        return meters / METERS_PER_NM;
    }

    /** Convert nautical miles to meters. */
    public static double metersFromNm(double nmVal) {
        return nmVal * METERS_PER_NM;
    }

    /**
     * Haversine great-circle distance in meters.
     */
    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_M * c;
    }

    /**
     * Simplified time-to-CPA (flat-earth approximation for short horizons).
     * Returns seconds until closest point of approach; POSITIVE_INFINITY if essentially stationary.
     */
    public static double timeToCpaSec(double dx, double dy, double dvx, double dvy) {
        double dv2 = dvx * dvx + dvy * dvy;
        if (dv2 < 1e-6) return Double.POSITIVE_INFINITY;
        return -(dx * dvx + dy * dvy) / dv2;
    }
}
