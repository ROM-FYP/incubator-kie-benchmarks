package bench.opensky.benchmark;

import bench.opensky.replay.OpenSkyJsonlLoader;
import bench.opensky.replay.OpenSkyReplayEngine;
import bench.opensky.model.OpenSkyStateVector;
import bench.opensky.model.Alert;
import bench.opensky.model.AuditEvent;
import bench.opensky.router.SessionManager;
import bench.opensky.router.AlphaRouter;
import org.kie.api.runtime.KieSession;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.Collection;

public class MetricsMain {
    public static void main(String[] args) throws Exception {
        System.out.println("Loading Dataset...");
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat("data/opensky_flat_20260217_160412.jsonl");
        if (events.size() > 10000) {
            events = events.subList(0, 10000);
        }
        System.out.println("Total Events: " + events.size());

        // --- BASELINE ---
        System.out.println("\nRunning Baseline...");
        OpenSkyReplayEngine baselineEngine = new OpenSkyReplayEngine();
        baselineEngine.init();
        int baseFired = 0;
        for (OpenSkyStateVector sv : events) {
            baseFired += baselineEngine.ingestEvent(sv);
        }
        
        KieSession baseKs = baselineEngine.getSession();
        long baseAlerts = baseKs.getObjects(o -> o instanceof Alert).size();
        long baseAudits = baseKs.getObjects(o -> o instanceof AuditEvent).size();
        baselineEngine.dispose();

        // --- PARALLEL 89/13 SPLIT ---
        System.out.println("\nRunning 89/13 Alpha Routed Split...");
        System.setProperty("opensky.empirical.override", "true");
        SessionManager sessionManager = new SessionManager();
        sessionManager.init("airTraffick_rules.drl", "src/main/resources/rule_graph_full.ftree");
        AlphaRouter alphaRouter = new AlphaRouter();

        int routedToMonolith = 0;
        int routedToIndependent = 0;
        long firedMonolith = 0;
        long firedIndependent = 0;
        long lastEventTimeMs = 0;

        for (OpenSkyStateVector sv : events) {
            long targetMs = sv.getSnapshotTime() * 1000L;
            if (targetMs > lastEventTimeMs) {
                sessionManager.advanceAllClocks(targetMs);
                lastEventTimeMs = targetMs;
            }

            Set<String> clusters = alphaRouter.route(sv);
            boolean toMonolith = clusters.contains("monolith");
            boolean toIndie = clusters.contains("independent");

            if (toMonolith) routedToMonolith++;
            if (toIndie) routedToIndependent++;

            if (toMonolith) {
                KieSession ks = sessionManager.getSession("monolith");
                if (ks != null) {
                    ks.insert(sv);
                    firedMonolith += ks.fireAllRules(5000);
                }
            }

            if (toIndie) {
                KieSession ks = sessionManager.getSession("independent");
                if (ks != null) {
                    ks.insert(sv);
                    firedIndependent += ks.fireAllRules(5000);
                }
            }
        }

        KieSession mKs = sessionManager.getSession("monolith");
        long mAlerts = mKs != null ? mKs.getObjects(o -> o instanceof Alert).size() : 0;
        long mAudits = mKs != null ? mKs.getObjects(o -> o instanceof AuditEvent).size() : 0;

        KieSession iKs = sessionManager.getSession("independent");
        long iAlerts = iKs != null ? iKs.getObjects(o -> o instanceof Alert).size() : 0;
        long iAudits = iKs != null ? iKs.getObjects(o -> o instanceof AuditEvent).size() : 0;

        sessionManager.disposeAll();

        System.out.println("\n---- CORRECTNESS & METRICS REPORT ----");
        System.out.println("Dataset Size: " + events.size());
        System.out.println("\n[1] BASELINE APPROACH");
        System.out.println("  Total Rules Fired: " + baseFired);
        System.out.println("  Output Alerts: " + baseAlerts);
        System.out.println("  Output Audits: " + baseAudits);
        
        System.out.println("\n[2] CLUSTERED APPROACH (Alpha Router, 89/13 Split)");
        System.out.println("  Events Routed to Monolith (89 rules): " + routedToMonolith);
        System.out.println("  Events Routed to Independent (13 rules): " + routedToIndependent);
        System.out.println("  Rules Fired in Monolith: " + firedMonolith);
        System.out.println("  Rules Fired in Independent: " + firedIndependent);
        System.out.println("  Total Rules Fired: " + (firedMonolith + firedIndependent));
        System.out.println("  Output Alerts (Combined): " + (mAlerts + iAlerts));
        System.out.println("  Output Audits (Combined): " + (mAudits + iAudits));
    }
}
