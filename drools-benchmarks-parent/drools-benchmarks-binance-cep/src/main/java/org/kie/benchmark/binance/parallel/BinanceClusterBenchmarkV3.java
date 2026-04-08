package org.kie.benchmark.binance.parallel;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Correctness test harness for the V3 2-cluster parallel execution.
 * CA = Feed Health + Liquidation + Trade Rate (merged C1+C3+C4)
 * CB = Market Microstructure (C2)
 */
public class BinanceClusterBenchmarkV3 {

    public static void main(String[] args) {
        try {
            System.out.println("╔══════════════════════════════════════════════╗");
            System.out.println("║  Cluster-Level Parallel Benchmark V3 — Test  ║");
            System.out.println("║  2 threads: CA(Feed+Liq+Trade) + CB(Micro)   ║");
            System.out.println("╚══════════════════════════════════════════════╝\n");

            BinanceEventProvider eventProvider = new BinanceEventProvider();
            List<MarketEvent> allLoadedEvents = eventProvider.getEvents();

            int maxEvents = 50000;
            if (args.length > 0) {
                try { maxEvents = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
            }
            List<MarketEvent> events = (maxEvents > 0 && maxEvents < allLoadedEvents.size())
                    ? allLoadedEvents.subList(0, maxEvents) : allLoadedEvents;

            Set<String> symbols = events.stream()
                    .map(MarketEvent::getSymbol)
                    .collect(Collectors.toSet());

            System.out.println("Events: " + events.size() + " | Symbols: " + symbols.size());

            String drlContent;
            try (InputStream is = BinanceClusterBenchmarkV3.class.getResourceAsStream("/rules/taxonomy.drl")) {
                drlContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // ── Baseline ──
            System.out.println("\n── Single-Session Baseline ──────────────────");
            BinanceRulesProvider rulesProvider = new BinanceRulesProvider();
            KieSession baselineSession = rulesProvider.createSession();
            EventReplayController controller = new EventReplayController(baselineSession);

            Map<String, Integer> baselineSignals = new HashMap<>();
            baselineSession.registerChannel("alerts", new org.kie.api.runtime.Channel() {
                @Override
                public void send(Object object) {
                    if (object instanceof RiskSignal) {
                        RiskSignal s = (RiskSignal) object;
                        String key = s.getSymbol() + "-" + s.getKind() + "-" + s.getSeverity();
                        baselineSignals.put(key, baselineSignals.getOrDefault(key, 0) + 1);
                    }
                }
            });

            for (String sym : symbols) {
                baselineSession.insert(new RiskConfig(sym));
                baselineSession.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
                baselineSession.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
            }
            baselineSession.fireAllRules();

            long baselineStart = System.currentTimeMillis();
            int baselineFired = controller.replayEvents(events);
            long baselineDuration = System.currentTimeMillis() - baselineStart;

            baselineSession.dispose();
            rulesProvider.dispose();

            System.out.println("Rules fired:  " + baselineFired);
            System.out.println("Duration:     " + baselineDuration + " ms");
            System.out.println("Throughput:   " + String.format("%.2f",
                    events.size() * 1000.0 / baselineDuration) + " events/sec");

            // ── 2-Cluster Parallel ──
            System.out.println("\n── Cluster-Parallel Execution V3 (2T) ──────");

            ClusterParallelOrchestratorV3 orchestrator = new ClusterParallelOrchestratorV3(drlContent, symbols);

            long clusterStart = System.currentTimeMillis();
            int clusterFired = orchestrator.replayEvents(events);
            long clusterDuration = System.currentTimeMillis() - clusterStart;

            Map<Integer, Integer> perSessionFired = orchestrator.getPerSessionFired();
            Map<Integer, Integer> perSessionEvents = orchestrator.getPerSessionEventsReceived();

            orchestrator.dispose();

            System.out.println("Rules fired:  " + clusterFired);
            System.out.println("Duration:     " + clusterDuration + " ms");
            System.out.println("Throughput:   " + String.format("%.2f",
                    events.size() * 1000.0 / clusterDuration) + " events/sec");
            System.out.println("Pool size:    2");

            // ── Comparison ──
            System.out.println("\n── Comparison ──────────────────────────────");
            System.out.println(String.format("%-25s %15s %15s", "", "Single", "Cluster V3"));
            System.out.println(String.format("%-25s %,15d %,15d", "Rules fired",
                    baselineFired, clusterFired));
            System.out.println(String.format("%-25s %,15d ms %,12d ms", "Duration",
                    baselineDuration, clusterDuration));
            System.out.println(String.format("%-25s %15.2f %15.2f", "Events/sec",
                    events.size() * 1000.0 / baselineDuration,
                    events.size() * 1000.0 / clusterDuration));

            double speedup = (double) baselineDuration / clusterDuration;
            System.out.println(String.format("%-25s %15s %14.2fx", "Speedup", "1.00x", speedup));

            // ── Correctness ──
            System.out.println("\n── Experimental Correctness ────────────────");
            Map<String, Integer> clusterSignals = orchestrator.getEmittedSignals();

            int baselineTotalSignals = baselineSignals.values().stream().mapToInt(Integer::intValue).sum();
            int clusterTotalSignals = clusterSignals.values().stream().mapToInt(Integer::intValue).sum();

            System.out.println("Baseline Emitted Signals   : " + baselineTotalSignals);
            System.out.println("Cluster Emitted Signals    : " + clusterTotalSignals);

            Set<String> allKeys = new HashSet<>();
            allKeys.addAll(baselineSignals.keySet());
            allKeys.addAll(clusterSignals.keySet());

            int overlap = 0;
            int misses = 0;
            long redundant = 0;

            for (String key : allKeys) {
                int baseCt = baselineSignals.getOrDefault(key, 0);
                int clustCt = clusterSignals.getOrDefault(key, 0);
                if (baseCt > 0 && clustCt > 0) overlap++;
                if (baseCt > 0 && clustCt == 0) misses++;
                if (clustCt > baseCt) redundant += (clustCt - baseCt);
            }

            System.out.println("Overlapping Signal Types   : " + overlap);
            System.out.println("Signals Only in Baseline   : " + misses);
            System.out.println("Extra Redundant Signals    : " + redundant);

            int maxClusterFired = perSessionFired.values().stream()
                    .mapToInt(Integer::intValue).max().orElse(0);
            boolean noLoop = maxClusterFired < events.size() * 50;
            System.out.println("\nCorrectness checks:");
            System.out.println("- No infinity loops: " + noLoop + " (max " + maxClusterFired + ")");
            System.out.println("Status: " + (noLoop ? "✅ PASS" : "❌ WARNING"));

            // ── Per-Session ──
            String[] names = { "", "CA (Feed+Liq+Trade)", "CB (Microstructure)" };
            System.out.println("\n── Per-Session Breakdown ───────────────────");
            for (Map.Entry<Integer, Integer> entry : perSessionFired.entrySet()) {
                int cid = entry.getKey();
                int fired = entry.getValue();
                int eventsRecv = perSessionEvents.getOrDefault(cid, 0);
                System.out.println(String.format("  %-25s Events: %,8d  Fired: %,8d",
                        names[cid], eventsRecv, fired));
            }

            // ── Signal dump ──
            Path outPath = Paths.get("output", "v3_signal_diff.txt");
            Files.createDirectories(outPath.getParent());
            try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(outPath, StandardCharsets.UTF_8))) {
                out.println("Key | Baseline | Cluster");
                for (String key : allKeys) {
                    out.println(key + " | " + baselineSignals.getOrDefault(key, 0) + " | "
                            + clusterSignals.getOrDefault(key, 0));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
