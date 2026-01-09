package org.kie.benchmark.cep.reproducible.wikimedia;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.cep.reproducible.wikimedia.config.ReplayConfig;
import org.kie.benchmark.cep.reproducible.wikimedia.drools.DroolsSessionFactory;
import org.kie.benchmark.cep.reproducible.wikimedia.metrics.ReplayMetrics;
import org.kie.benchmark.cep.reproducible.wikimedia.model.WikiEvent;
import org.kie.benchmark.cep.reproducible.wikimedia.source.NdjsonFileEventSource;
import org.kie.benchmark.cep.reproducible.wikimedia.util.ResourceFiles;

import java.text.DecimalFormat;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ReproducibleWikimediaRunner {
    private static String normalizeResource(String p) {
        if (p == null || p.isBlank()) return p;
        return p.startsWith("/") ? p : "/" + p;
    }

    public static void main(String[] args) throws Exception {

        // Defaults: use bundled resources if user doesn't provide explicit paths.
        String ndjsonResource = "reproducible/wikimedia/data/wikimedia_60min.ndjson";
        String drlResource    = "reproducible/wikimedia/rules/advanced_viral_rules.drl";

        String ndjsonPathArg = null;
        String drlPathArg = null;

        // Simple arg parsing (no extra deps)
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ndjson":
                    ndjsonPathArg = args[++i];
                    break;
                case "--drl":
                    drlPathArg = args[++i];
                    break;
                case "--ndjsonResource":
                    ndjsonResource = args[++i];
                    break;
                case "--drlResource":
                    drlResource = args[++i];
                    break;
                default:
                    // ignore unknowns (or throw)
                    break;
            }
        }

        // Resolve NDJSON Path
        Path ndjson;
        if (ndjsonPathArg != null) {
            ndjson = Paths.get(ndjsonPathArg);
        } else {
            // Copy classpath resource into target/ for easy inspection
            ndjsonResource = normalizeResource(ndjsonResource);
            ndjson = ResourceFiles.copyToTarget(ndjsonResource, "reproducible/wikimedia/data/wikimedia_60min.ndjson");
        }

        // Resolve DRL Path
        Path drl;
        if (drlPathArg != null) {
            drl = Paths.get(drlPathArg);
        } else {
            drlResource = normalizeResource(drlResource);
            drl = ResourceFiles.copyToTarget(drlResource, "reproducible/wikimedia/rules/advanced_viral_rules.drl");
        }

        System.out.println("NDJSON: " + ndjson.toAbsolutePath());
        System.out.println("DRL   : " + drl.toAbsolutePath());

        ReplayConfig config = ReplayConfig.timestamp(
                ndjson,
                1.0,          // speedFactor
                Long.MAX_VALUE // maxEvents
        );

        KieSession session = DroolsSessionFactory.fromDrl(drl);

        ReplayMetrics metrics = new ReplayMetrics();
        metrics.start();

        try (NdjsonFileEventSource source = new NdjsonFileEventSource(config)) {
            WikiEvent evt;
            while ((evt = source.next()) != null) {
                session.insert(evt);
                session.fireAllRules();
                metrics.recordEvent();
            }
        }

        metrics.stop();
        session.dispose();

        DecimalFormat df = new DecimalFormat("#.###");

        System.out.println("\n\n======= Replay Complete =======\n");
        System.out.println("Events     : " + metrics.getEvents());
        System.out.println("Duration   : " + df.format(metrics.durationSeconds()/60) + " min");
        System.out.println("Throughput : " + df.format(metrics.throughput()) + " events/sec\n\n");
    }
}
