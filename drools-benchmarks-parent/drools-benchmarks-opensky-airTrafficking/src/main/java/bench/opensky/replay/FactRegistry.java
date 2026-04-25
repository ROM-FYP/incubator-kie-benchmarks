package bench.opensky.replay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Assigns stable, unique IDs to fact objects and tracks which rule last produced
 * (inserted or updated) each fact.
 *
 * <p>Two maps are maintained:
 * <ul>
 *   <li>{@code factIds}      — Object identity → fact_id string ("F12345")
 *   <li>{@code provenance}   — fact_id → name of the rule that last wrote the fact,
 *                              or "EXTERNAL" for externally inserted facts.
 * </ul>
 */
public class FactRegistry {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    /** Object identity → fact ID */
    private final Map<Integer, String> factIds = new ConcurrentHashMap<>();

    /** fact_id → last producer rule name (or "EXTERNAL") */
    private final Map<String, String> provenance = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Fact ID management
    // -------------------------------------------------------------------------

    /**
     * Return the existing fact_id for the object, assigning a new one if not seen before.
     * Uses {@link System#identityHashCode} as the key — stable for the object lifetime.
     */
    public String getOrAssign(Object fact) {
        return factIds.computeIfAbsent(
                System.identityHashCode(fact),
                k -> "F" + COUNTER.incrementAndGet());
    }

    /** Return an already-assigned fact_id, or {@code null} if unknown. */
    public String get(Object fact) {
        return factIds.get(System.identityHashCode(fact));
    }

    /** Remove all records for a fact (called on DELETE). */
    public void remove(Object fact) {
        String id = factIds.remove(System.identityHashCode(fact));
        if (id != null) {
            provenance.remove(id);
        }
    }

    // -------------------------------------------------------------------------
    // Provenance tracking
    // -------------------------------------------------------------------------

    /** Record that {@code producerRule} is responsible for this fact's current state. */
    public void setProducer(Object fact, String producerRule) {
        String id = getOrAssign(fact);
        provenance.put(id, producerRule);
    }

    /**
     * Return the rule name that last produced the fact, or {@code "EXTERNAL"} if
     * the fact was inserted from outside the rule engine.
     */
    public String getProducer(Object fact) {
        String id = factIds.get(System.identityHashCode(fact));
        if (id == null) return "EXTERNAL";
        return provenance.getOrDefault(id, "EXTERNAL");
    }

    public void clear() {
        factIds.clear();
        provenance.clear();
    }
}
