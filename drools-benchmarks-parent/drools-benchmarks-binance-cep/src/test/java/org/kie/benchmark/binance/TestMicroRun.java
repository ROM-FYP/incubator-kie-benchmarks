package org.kie.benchmark.binance;

import org.kie.api.runtime.KieSession;
import org.kie.benchmark.binance.model.*;
import org.kie.benchmark.binance.provider.BinanceRulesProvider;

public class TestMicroRun {
    public static void main(String[] args) {
        BinanceRulesProvider provider = new BinanceRulesProvider();
        KieSession session = provider.createSession();
        
        System.out.println("Loaded rules, injecting test events...");
        
        String sym = "BTCUSDT";
        session.insert(new RiskConfig(sym));
        
        // Let Bootstrap rules run first
        session.fireAllRules();
        
        // Micro-Volatility (DEPTH) Trigger:
        // First depth initializes BestBidAsk at 50,000 / 50,001
        session.insert(new MarketEvent(sym, 1000L, "DEPTH", 50000.0, 50001.0, 0, ""));
        session.fireAllRules();
        
        // Second depth shifts to 50,500 / 50,501 (creates SpreadVelocityState positive impact)
        // Also fires K section rules
        session.insert(new MarketEvent(sym, 1010L, "DEPTH", 50500.0, 50501.0, 0, ""));
        session.fireAllRules();
        
        // Mark Dislocation (MARK) Trigger:
        // Mark swings to 51,000 against mid of 50,500
        session.insert(new MarketEvent(sym, 1020L, "MARK", 51000.0, 0, 0, ""));
        session.fireAllRules();
        
        System.out.println("=== Risk Signals Generated ===");
        session.getObjects().stream()
               .filter(o -> o instanceof RiskSignal)
               .forEach(System.out::println);
               
        provider.dispose();
    }
}
