/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kie.benchmark.binance;

import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceEventProvider;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;
import org.kie.benchmark.binance.util.EventReplayController;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Rule Coverage Analysis — determines which of the 106 rules in taxonomy.drl
 * actually fire at least once during a full replay of the collected dataset.
 *
 * Output:
 *  - Per-rule fire count (sorted descending)
 *  - Rules that NEVER fired
 *  - Coverage % = rules fired at least once / total rules
 *
 * Run via: mvn exec:java -Dexec.mainClass=org.kie.benchmark.binance.RuleCoverageAnalysis
 */
public class RuleCoverageAnalysis {

    /** AgendaEventListener that tracks per-rule fire counts. */
    static class CoverageTracker implements AgendaEventListener {
        private final Map<String, AtomicInteger> fireCounts = new LinkedHashMap<>();

        @Override
        public void afterMatchFired(AfterMatchFiredEvent event) {
            String ruleName = event.getMatch().getRule().getName();
            fireCounts.computeIfAbsent(ruleName, k -> new AtomicInteger(0)).incrementAndGet();
        }

        // --- unused but required by interface ---
        @Override public void matchCreated(MatchCreatedEvent event) {}
        @Override public void matchCancelled(MatchCancelledEvent event) {}
        @Override public void beforeMatchFired(BeforeMatchFiredEvent event) {}
        @Override public void agendaGroupPopped(org.kie.api.event.rule.AgendaGroupPoppedEvent event) {}
        @Override public void agendaGroupPushed(org.kie.api.event.rule.AgendaGroupPushedEvent event) {}
        @Override public void beforeRuleFlowGroupActivated(org.kie.api.event.rule.RuleFlowGroupActivatedEvent event) {}
        @Override public void afterRuleFlowGroupActivated(org.kie.api.event.rule.RuleFlowGroupActivatedEvent event) {}
        @Override public void beforeRuleFlowGroupDeactivated(org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent event) {}
        @Override public void afterRuleFlowGroupDeactivated(org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent event) {}

        public Map<String, AtomicInteger> getFireCounts() { return fireCounts; }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Binance CEP Rule Coverage Analysis ===");
        System.out.println("Loading rules and dataset...\n");

        // --- Setup ---
        BinanceRulesProvider rulesProvider = new BinanceRulesProvider();
        BinanceEventProvider eventProvider = new BinanceEventProvider();
        List<MarketEvent> allEvents = eventProvider.getEvents();

        Set<String> symbols = allEvents.stream()
                .map(MarketEvent::getSymbol)
                .collect(Collectors.toSet());

        System.out.println("Dataset:    " + eventProvider.getDatasetId());
        System.out.println("Events:     " + allEvents.size());
        System.out.println("Symbols:    " + symbols.size() + " — " + new TreeSet<>(symbols));
        System.out.println();

        // Count event type distribution
        Map<String, Long> eventTypeDist = allEvents.stream()
                .collect(Collectors.groupingBy(MarketEvent::getEventType, Collectors.counting()));
        System.out.println("Event type distribution:");
        eventTypeDist.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> System.out.printf("  %-12s %,d  (%.1f%%)%n",
                        e.getKey(), e.getValue(),
                        100.0 * e.getValue() / allEvents.size()));
        System.out.println();

        // --- Create session with coverage tracker ---
        KieSession session = rulesProvider.createSession();
        CoverageTracker tracker = new CoverageTracker();
        session.addEventListener(tracker);

        // Sink for alert channel
        session.registerChannel("alerts", obj -> { /* no-op */ });

        // Bootstrap all symbols
        for (String sym : symbols) {
            session.insert(new RiskConfig(sym));
            session.insert(new ModeState(sym, "NORMAL", false, 0L, ""));
            session.insert(new FeedHealth(sym, "OK", 0L, 0L, 0L, 0L, 0L, 0, 0));
        }
        session.fireAllRules();

        // --- Replay all events ---
        System.out.println("Replaying " + allEvents.size() + " events...");
        long t0 = System.currentTimeMillis();
        EventReplayController controller = new EventReplayController(session);
        int totalFired = controller.replayEvents(allEvents);
        long duration = System.currentTimeMillis() - t0;
        System.out.printf("Replay complete: %,d rule activations in %,d ms%n%n", totalFired, duration);

        // --- Working memory size at end ---
        long wmSize = session.getFactCount();
        System.out.println("Working memory facts at end: " + wmSize);
        System.out.println();

        session.dispose();
        rulesProvider.dispose();

        // --- Coverage Report ---
        Map<String, AtomicInteger> counts = tracker.getFireCounts();

        // All rules that fired (sorted descending by count)
        List<Map.Entry<String, AtomicInteger>> fired = counts.entrySet().stream()
                .filter(e -> e.getValue().get() > 0)
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .collect(Collectors.toList());

        System.out.println("=== Rules That Fired (sorted by activation count) ===");
        System.out.printf("%-55s %10s%n", "Rule Name", "Activations");
        System.out.println("-".repeat(67));
        fired.forEach(e -> System.out.printf("%-55s %,10d%n", e.getKey(), e.getValue().get()));
        System.out.println();

        // Rules that never fired — we need the full rule list from the DRL.
        // Since we can't introspect all rule names without KieBase.getKiePackage(),
        // we report rules that fired vs the expected total from the DRL header.
        System.out.println("=== Rules That NEVER Fired ===");
        // Get all rule names from KieBase via the package
        // Note: session is disposed, so we create a fresh lightweight session to extract names
        BinanceRulesProvider probe = new BinanceRulesProvider();
        KieSession probeSession = probe.createSession();
        Set<String> allRuleNames = probeSession.getKieBase()
                .getKiePackages().stream()
                .flatMap(pkg -> pkg.getRules().stream())
                .map(org.kie.api.definition.rule.Rule::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        probeSession.dispose();
        probe.dispose();

        Set<String> firedNames = counts.keySet();
        List<String> neverFired = allRuleNames.stream()
                .filter(r -> !firedNames.contains(r))
                .sorted()
                .collect(Collectors.toList());

        if (neverFired.isEmpty()) {
            System.out.println("  (none — all rules fired at least once!)");
        } else {
            neverFired.forEach(r -> System.out.println("  ❌ " + r));
        }
        System.out.println();

        // --- Summary ---
        int totalRules = allRuleNames.size();
        int coveredRules = (int) allRuleNames.stream().filter(firedNames::contains).count();
        double coverage = 100.0 * coveredRules / totalRules;

        System.out.println("=== Coverage Summary ===");
        System.out.printf("Total rules in DRL:       %d%n", totalRules);
        System.out.printf("Rules fired >= 1 time:    %d%n", coveredRules);
        System.out.printf("Rules never fired:        %d%n", totalRules - coveredRules);
        System.out.printf("Rule coverage:            %.1f%%%n", coverage);
        System.out.printf("Total activations:        %,d%n", totalFired);
        System.out.printf("Avg activations/rule:     %.1f%n",
                coveredRules > 0 ? (double) totalFired / coveredRules : 0);
        System.out.printf("WM facts at end:          %d%n", wmSize);
        System.out.printf("Events in dataset:        %,d%n", allEvents.size());
        System.out.printf("Activations/event:        %.2f%n",
                allEvents.isEmpty() ? 0 : (double) totalFired / allEvents.size());
    }
}
