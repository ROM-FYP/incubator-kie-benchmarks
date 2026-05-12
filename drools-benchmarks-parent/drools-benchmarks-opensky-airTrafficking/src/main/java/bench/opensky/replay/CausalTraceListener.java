package bench.opensky.replay;

import org.kie.api.event.rule.*;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.Match;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dual listener ({@link AgendaEventListener} + {@link RuleRuntimeEventListener}) that
 * emits structured JSON-lines traces suitable for constructing a rule-interaction graph.
 *
 * <h3>Event types emitted</h3>
 * <ul>
 *   <li>FACT_INSERT / FACT_UPDATE / FACT_DELETE — with {@code fact_id}, {@code fact_type},
 *       {@code producer_rule}
 *   <li>ACTIVATION_CREATED — with {@code rule}, {@code activation_id},
 *       {@code supporting_fact_ids} (the facts that matched the LHS)
 *   <li>ACTIVATION_FIRED — with {@code rule}, {@code activation_id}
 * </ul>
 *
 * <h3>Causal edge reconstruction</h3>
 * From these events you can derive:
 * <pre>
 *   Ri → Rj  iff  some fact_id F appears in
 *               ACTIVATION_CREATED.supporting_fact_ids of Rj
 *             AND
 *               FACT_INSERT/UPDATE.producer_rule = Ri for that F
 * </pre>
 */
public class CausalTraceListener {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final FactRegistry registry = new FactRegistry();
    private final BufferedWriter writer;

    /** Sequence counter — guarantees strict ordering even within the same ms timestamp. */
    private final AtomicLong seq = new AtomicLong(0);

    /**
     * Activation-ID map: Drools does not provide a stable activation ID, so we
     * generate one on matchCreated and look it up on afterMatchFired.
     * Key: the Match object's identity hash. Value: our generated UUID string.
     */
    private final Map<Integer, String> activationIds = new ConcurrentHashMap<>();

    /**
     * The rule that is currently firing (null when no rule is executing).
     * Used to attribute INSERT / UPDATE events to the correct producer.
     */
    private final ThreadLocal<String> currentFiringRule = new ThreadLocal<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    public CausalTraceListener(String outputFile) {
        try {
            this.writer = new BufferedWriter(new FileWriter(outputFile, /* append = */ false));
        } catch (IOException e) {
            throw new RuntimeException("Cannot open causal trace output file: " + outputFile, e);
        }
    }

    // -------------------------------------------------------------------------
    // Public accessors
    // -------------------------------------------------------------------------

    public AgendaEventListener agendaListener() {
        return new DefaultAgendaEventListener() {

            @Override
            public void matchCreated(MatchCreatedEvent event) {
                Match match = event.getMatch();
                String activationId = UUID.randomUUID().toString();
                activationIds.put(System.identityHashCode(match), activationId);

                // Collect the IDs of all facts bound in the LHS pattern match
                List<String> factIds = extractFactIds(match);

                emit(buildActivationCreated(
                        match.getRule().getName(), activationId, factIds));
            }

            @Override
            public void beforeMatchFired(BeforeMatchFiredEvent event) {
                currentFiringRule.set(event.getMatch().getRule().getName());
            }

            @Override
            public void afterMatchFired(AfterMatchFiredEvent event) {
                Match match = event.getMatch();
                String activationId = activationIds.remove(System.identityHashCode(match));
                if (activationId == null) activationId = "UNKNOWN";

                emit(buildActivationFired(match.getRule().getName(), activationId));
                currentFiringRule.remove();
            }

            @Override
            public void matchCancelled(MatchCancelledEvent event) {
                // Clean up orphaned activation IDs to avoid memory leak
                activationIds.remove(System.identityHashCode(event.getMatch()));
            }
        };
    }

    public RuleRuntimeEventListener runtimeListener() {
        return new DefaultRuleRuntimeEventListener() {

            @Override
            public void objectInserted(ObjectInsertedEvent event) {
                Object fact = event.getObject();
                String producer = currentProducer();
                registry.setProducer(fact, producer);
                emit(buildFactEvent("FACT_INSERT", fact, producer));
            }

            @Override
            public void objectUpdated(ObjectUpdatedEvent event) {
                Object fact = event.getObject();
                String producer = currentProducer();
                registry.setProducer(fact, producer);
                emit(buildFactEvent("FACT_UPDATE", fact, producer));
            }

            @Override
            public void objectDeleted(ObjectDeletedEvent event) {
                Object fact = event.getOldObject();
                String producer = registry.getProducer(fact);
                emit(buildFactEvent("FACT_DELETE", fact, producer));
                registry.remove(fact);
            }
        };
    }

    public void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reset() {
        registry.clear();
        activationIds.clear();
    }

    // -------------------------------------------------------------------------
    // JSON builders (hand-written — no external library required)
    // -------------------------------------------------------------------------

    private String buildActivationCreated(String rule, String activationId, List<String> factIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"ACTIVATION_CREATED\"");
        sb.append(",\"seq\":").append(seq.incrementAndGet());
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"tid\":").append(Thread.currentThread().getId());
        sb.append(",\"rule\":\"").append(escapeJson(rule)).append("\"");
        sb.append(",\"activation_id\":\"").append(activationId).append("\"");
        sb.append(",\"supporting_facts\":[");
        for (int i = 0; i < factIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(factIds.get(i)).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildActivationFired(String rule, String activationId) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"ACTIVATION_FIRED\"");
        sb.append(",\"seq\":").append(seq.incrementAndGet());
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"tid\":").append(Thread.currentThread().getId());
        sb.append(",\"rule\":\"").append(escapeJson(rule)).append("\"");
        sb.append(",\"activation_id\":\"").append(activationId).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private String buildFactEvent(String type, Object fact, String producer) {
        String factId = registry.getOrAssign(fact);
        String factType = fact.getClass().getSimpleName();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"").append(type).append("\"");
        sb.append(",\"seq\":").append(seq.incrementAndGet());
        sb.append(",\"ts\":").append(System.currentTimeMillis());
        sb.append(",\"tid\":").append(Thread.currentThread().getId());
        sb.append(",\"fact_id\":\"").append(factId).append("\"");
        sb.append(",\"fact_type\":\"").append(escapeJson(factType)).append("\"");
        sb.append(",\"producer\":\"").append(escapeJson(producer)).append("\"");
        
        try {
            String factData = MAPPER.writeValueAsString(fact);
            sb.append(",\"fact_data\":").append(factData);
        } catch (Exception e) {
            sb.append(",\"fact_data\":{}");
        }
        
        sb.append("}");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Who is currently firing? EXTERNAL if called from outside a rule action. */
    private String currentProducer() {
        String rule = currentFiringRule.get();
        return (rule != null) ? rule : "EXTERNAL";
    }

    /**
     * Extract fact IDs for all fact handles bound by the match.
     * We iterate {@link Match#getFactHandles()} which returns the full LHS bindings.
     */
    private List<String> extractFactIds(Match match) {
        Collection<? extends FactHandle> handles = match.getFactHandles();
        List<String> ids = new ArrayList<>(handles.size());
        for (FactHandle fh : handles) {
            // InternalFactHandle.getObject() is available at runtime via the Drools API
            Object obj = ((org.drools.core.common.InternalFactHandle) fh).getObject();
            if (obj != null) {
                ids.add(registry.getOrAssign(obj));
            }
        }
        return ids;
    }

    private void emit(String json) {
        try {
            writer.write(json);
            writer.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Minimal JSON string escaping (handles the most common problem characters). */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
