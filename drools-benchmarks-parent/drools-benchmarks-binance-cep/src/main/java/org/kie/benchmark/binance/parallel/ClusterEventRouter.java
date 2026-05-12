package org.kie.benchmark.binance.parallel;

/**
 * Pre-computed routing table for 4-cluster parallel execution.
 * Route incoming MarketEvents to cluster sessions by eventType.
 *
 * Clusters:
 *   C1 = Feed Health & Mode Transitions
 *   C2 = Market Microstructure (Depth/Spread/Vol/TradeAlpha)
 *   C3 = Liquidation Monitoring
 *   C4 = Trade Rate
 */
public class ClusterEventRouter {

    // Bit flags for cluster targets
    public static final int C1 = 0b0001;
    public static final int C2 = 0b0010;
    public static final int C3 = 0b0100;
    public static final int C4 = 0b1000;

    /**
     * Returns a bitmask of which clusters this event should be routed to.
     * 0 = drop (no cluster needs it).
     */
    public static int route(String eventType) {
        if (eventType == null) return 0;
        switch (eventType) {
            case "DEPTH":     return C1 | C2;       // Feed health + Microstructure
            case "TRADE":     return C1 | C2 | C4;  // Feed health + Microstructure + Trade rate
            case "MARK":      return C1 | C2;       // Feed health + Microstructure
            case "INDEX":     return C1 | C2;       // Feed health + Microstructure
            case "HEARTBEAT": return C1;             // Feed health only
            case "LIQ":       return C3;             // Liquidation only
            default:          return 0;              // DECODE_ERROR, RECONNECT, unknown → drop
        }
    }

    /** Convenience: does this event target the given cluster? */
    public static boolean targetsC1(int mask) { return (mask & C1) != 0; }
    public static boolean targetsC2(int mask) { return (mask & C2) != 0; }
    public static boolean targetsC3(int mask) { return (mask & C3) != 0; }
    public static boolean targetsC4(int mask) { return (mask & C4) != 0; }
}
