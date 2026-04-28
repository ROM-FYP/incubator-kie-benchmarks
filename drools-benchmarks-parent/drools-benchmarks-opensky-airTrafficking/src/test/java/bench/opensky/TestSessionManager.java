package bench.opensky;

import bench.opensky.router.SessionManager;

public class TestSessionManager {
    public static void main(String[] args) throws Exception {
        System.setProperty("opensky.cluster.two.session", "true");
        SessionManager sm = new SessionManager();
        sm.init("airTraffick_rules.drl", "src/main/resources/rule_graph_new.net.ftree");
        System.out.println("Active clusters: " + sm.getActiveClusterIds());
        System.exit(0);
    }
}
