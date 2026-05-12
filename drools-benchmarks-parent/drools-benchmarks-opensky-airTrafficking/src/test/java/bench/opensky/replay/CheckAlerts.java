package bench.opensky.replay;

import bench.opensky.model.Alert;
import bench.opensky.model.OpenSkyStateVector;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Standalone runner that replays the OpenSky dataset through the Drools engine
 * and writes all Alert objects still alive in working memory at the end to
 * alerts.csv (these are alerts active within the last 120 simulated seconds of
 * the recording).
 *
 * No rule logic is touched. No listeners are needed.
 * Run via: mvn test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=bench.opensky.replay.CheckAlerts
 */
public class CheckAlerts {
    public static void main(String[] args) throws Exception {
        System.out.println("Loading data...");
        OpenSkyJsonlLoader loader = new OpenSkyJsonlLoader();
        List<OpenSkyStateVector> events = loader.loadFlat("data/opensky_flat_20260217_160412.jsonl");
        System.out.println("Loaded " + events.size() + " events.");

        OpenSkyReplayEngine engine = new OpenSkyReplayEngine();
        engine.init();
        engine.enableCsvLogging(null, "rule_firings.csv");

        int totalFired = 0;
        long start = System.currentTimeMillis();
        for (OpenSkyStateVector ev : events) {
            totalFired += engine.ingestEvent(ev);
        }
        long ms = System.currentTimeMillis() - start;

        System.out.println("Done processing in " + ms + " ms.");
        engine.dispose();
    }
}
