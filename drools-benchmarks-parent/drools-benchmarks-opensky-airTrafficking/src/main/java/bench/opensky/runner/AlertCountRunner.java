package bench.opensky.runner;

import bench.opensky.model.Alert;
import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import bench.opensky.router.Router;
import bench.opensky.router.SessionManager;
import org.kie.api.runtime.KieSession;

import java.util.List;

/**
 * Standalone runner that feeds the ENTIRE dataset through the engine exactly once
 * and prints the final count of Alerts remaining in the working memory
 * for both Baseline and Routed logic.
 *
 * Usage:
 *   java -cp target/benchmarks.jar bench.opensky.runner.AlertCountRunner
 */
public class AlertCountRunner {

    public static void main(String[] args) throws Exception {
        String dataset = args.length > 0 ? args[0] : "data/opensky_flat_20260217_160412.jsonl";
        String treeRulesFile = "src/main/resources/tree_rules.txt";
        String ftreeFile = "src/main/resources/rule_graph_full.ftree";

        System.out.printf("[AlertCountRunner] Loading dataset: %s%n", dataset);
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat(dataset);
        System.out.printf("[AlertCountRunner] Loaded %,d events%n", events.size());

        System.out.println("==================================================");
        System.out.println("1) Running BASELINE (Single Session)");
        System.out.println("==================================================");
        long baselineAlerts = runBaseline(events);

        System.out.println("\n==================================================");
        System.out.println("2) Running ROUTED (Multi-Session)");
        System.out.println("==================================================");
        long routedAlerts = runRouted(events, treeRulesFile, ftreeFile);

        System.out.println("\n==================================================");
        System.out.println("FINAL ALERT COUNT COMPARISON:");
        System.out.println("  Baseline : " + baselineAlerts);
        System.out.println("  Routed   : " + routedAlerts);
        
        if (baselineAlerts == routedAlerts) {
            System.out.println("  STATUS   : MATCH (SUCCESS)");
        } else {
            System.out.println("  STATUS   : MISMATCH (ROUTER IS DROPPING OR OVER-GENERATING ALERTS)");
        }
        System.out.println("==================================================");
    }

    private static long runBaseline(List<OpenSkyStateVector> events) {
        OpenSkyReplayEngine engine = new OpenSkyReplayEngine();
        engine.init();

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < events.size(); i++) {
            engine.ingestEvent(events.get(i));
            if ((i + 1) % 10000 == 0) {
                System.out.printf("  [Baseline] %,d / %,d events ingested...%n", i + 1, events.size());
            }
        }
        long elapsed = System.currentTimeMillis() - t0;
        long alertCount = engine.getAlertCount();

        engine.dispose();
        System.out.printf("  -> Baseline finished. %,d alerts in WM. Time: %.1f s%n", alertCount, elapsed / 1000.0);
        return alertCount;
    }

    private static long runRouted(List<OpenSkyStateVector> events, String treeRulesFile, String ftreeFile) throws Exception {
        Router router = new Router(treeRulesFile);
        SessionManager sessionManager = new SessionManager();
        sessionManager.init("airTraffick_rules.drl", ftreeFile);

        OpenSkyReplayEngine engine = new OpenSkyReplayEngine();
        // Skip init() for routed, it uses the SessionManager

        long t0 = System.currentTimeMillis();
        int emptyRoutes = 0;
        int totalRouted = 0;
        for (int i = 0; i < events.size(); i++) {
            OpenSkyStateVector sv = events.get(i);
            java.util.Set<String> clusters = router.route(sv);
            if (clusters.isEmpty()) {
                emptyRoutes++;
            } else {
                totalRouted++;
            }
            engine.ingestEventRouted(sv, router, sessionManager);
            if ((i + 1) % 10000 == 0) {
                System.out.printf("  [Routed] %,d / %,d events ingested... (Empty routes: %d, Routed: %d)%n", 
                                  i + 1, events.size(), emptyRoutes, totalRouted);
            }
        }
        long elapsed = System.currentTimeMillis() - t0;

        long alertCount = 0;
        for (String clusterId : sessionManager.getActiveClusterIds()) {
            KieSession clusterSession = sessionManager.getSession(clusterId);
            if (clusterSession != null) {
                alertCount += clusterSession.getObjects(new org.kie.api.runtime.ClassObjectFilter(Alert.class)).size();
            }
        }

        sessionManager.disposeAll();
        engine.dispose();
        
        System.out.printf("  -> Routed finished. %,d total alerts across clusters. Time: %.1f s%n", alertCount, elapsed / 1000.0);
        return alertCount;
    }
}
