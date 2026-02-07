package org.kie.benchmark.cep.wikimedia;

import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Listener that forwards specific facts from pipeline sessions to the correlation session.
 */
public class FactForwardingListener extends DefaultRuleRuntimeEventListener {
    private static final Logger logger = LoggerFactory.getLogger(FactForwardingListener.class);

    private final KieSession correlationSession;
    private final Set<String> forwardedTypes;
    private boolean forwardedThisCycle = false;

    public FactForwardingListener(KieSession correlationSession) {
        this.correlationSession = correlationSession;
        this.forwardedTypes = new HashSet<>();
        
        // Whitelist from requirements
        forwardedTypes.add("VandalismCandidate");
        forwardedTypes.add("VandalismAnalysis");
        forwardedTypes.add("VandalismFlagged");
        forwardedTypes.add("BotProfile");
        forwardedTypes.add("BotHealthCheck");
        forwardedTypes.add("ContentAddition");
        forwardedTypes.add("ContentReview");
        forwardedTypes.add("MinorEdit");
        forwardedTypes.add("MinorClassified");
        forwardedTypes.add("MinorValidated");
        
        // Memoization support: forward ClusterOutput to correlation
        forwardedTypes.add("ClusterOutput");
    }

    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        Object fact = event.getObject();
        String typeName = fact.getClass().getSimpleName();

        if (forwardedTypes.contains(typeName)) {
            forwardToCorrelation(fact, typeName);
        }
    }

    private void forwardToCorrelation(Object fact, String typeName) {
        // 1. Determine logical event time if possible
        Long timestamp = tryGetTimestamp(fact);
        
        // 2. Lazily advance correlation clock
        if (timestamp != null) {
            SessionPseudoClock clock = correlationSession.getSessionClock();
            long currentTime = clock.getCurrentTime();
            if (timestamp > currentTime) {
                clock.advanceTime(timestamp - currentTime, TimeUnit.MILLISECONDS);
            }
        }

        // 3. Insert into correlation session
        correlationSession.insert(fact);
        this.forwardedThisCycle = true;
        // We don't fireAllRules here; it's handled in the main benchmark loop
        // to avoid recursive firing if not needed, or as per the specific execution flow.
        // The requirement says: "If at least one fact was forwarded: Fire S_CORRELATION once."
        // We will track if something was forwarded in the benchmark loop.
    }

    private Long tryGetTimestamp(Object fact) {
        try {
            // Priority 1: getTimestamp() (WikiEvent, VandalismCandidate)
            Method getTimestamp = fact.getClass().getMethod("getTimestamp");
            return (Long) getTimestamp.invoke(fact);
        } catch (Exception e) {
            // Priority 2: In DRL declared types, fields are accessible via getters
            try {
                String methodName = "getTimestamp";
                Method m = fact.getClass().getMethod(methodName);
                return (Long) m.invoke(fact);
            } catch (Exception e2) {
                // Fallback: use current clock of the source session if we could access it?
                // For now, return null to skip clock advancement for this fact
                return null;
            }
        }
    }

    public boolean hasForwarded() {
        return forwardedThisCycle;
    }

    public void reset() {
        this.forwardedThisCycle = false;
    }
}
