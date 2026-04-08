package org.kie.benchmark.binance.parallel;

/**
 * Pre-computed routing table for 2-cluster parallel execution (V3).
 * 
 * Clusters:
 *   CA = Feed Health + Liquidation + Trade Rate (merged C1+C3+C4)
 *   CB = Market Microstructure (C2)
 */
public class ClusterEventRouterV3 {

    public static final int CA = 0b01;  // Cluster A
    public static final int CB = 0b10;  // Cluster B

    /**
     * Returns a bitmask of which clusters this event should be routed to.
     * 0 = drop (no cluster needs it).
     */
    public static int route(String eventType) {
        if (eventType == null) return 0;
        switch (eventType) {
            case "DEPTH":     return CA | CB;  // Both clusters
            case "TRADE":     return CA | CB;  // Both clusters
            case "MARK":      return CA | CB;  // Both clusters
            case "INDEX":     return CA | CB;  // Both clusters
            case "HEARTBEAT": return CA;       // CA only (Feed Health)
            case "LIQ":       return CA;       // CA only (Liquidation)
            default:          return 0;        // DECODE_ERROR, RECONNECT, unknown → drop
        }
    }

    public static boolean targetsCA(int mask) { return (mask & CA) != 0; }
    public static boolean targetsCB(int mask) { return (mask & CB) != 0; }
}
