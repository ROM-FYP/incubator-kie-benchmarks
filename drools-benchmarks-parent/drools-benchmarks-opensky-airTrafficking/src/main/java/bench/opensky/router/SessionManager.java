package bench.opensky.router;

import bench.opensky.model.Params;
import org.drools.compiler.kie.builder.impl.DrlProject;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.time.SessionPseudoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages multiple KieSessions — one per Infomap cluster.
 *
 * <p>For each cluster, the full DRL is split into a cluster-specific DRL
 * containing only the shared header (package, imports, globals, declares)
 * plus the {@code rule "..." ... end} blocks whose names belong to that cluster.</p>
 */
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    private static final Set<String> MANDATORY_CORE_RULES = Set.of(
            "R000_LoadDefaultParams",
            "R041_AssignGridCell",
            "R042_PairWithinCell",
            "R056_BuildConflictCandidateBasic",
            "R068_ClearOldConflictCandidates",
            "R068b_ComputeCpaMetrics",
            "R073_InitPairRiskState",
            "R074a_UpdateStreaks_ALERT",
            "R074b_UpdateStreaks_WARN",
            "R074c_UpdateStreaks_INFO",
            "R075_RaiseAlertsOnlyAfterPersistence",
            "R081_RaiseTrafficAdvisoryOnWARN",
            "R082_RaiseSafetyAlertOnALERT",
            "R092_DoNotSpamNuisanceAlerts",
            "R094_ClearOldAdvisories"
    );

    /**
     * Regex that matches a complete rule block:
     * {@code rule "RuleName" ... end}
     * Uses DOTALL so the body can span multiple lines.
     */
    private static final Pattern RULE_BLOCK = Pattern.compile(
            "(rule\\s+\"([^\"]+)\".*?end)", Pattern.DOTALL);

    private final Map<String, KieSession> sessions = new LinkedHashMap<>();
    private final Map<String, SessionPseudoClock> clocks = new LinkedHashMap<>();

    /**
     * Initialize cluster sessions.
     *
     * @param drlResourceName classpath resource name for the DRL (e.g. "airTraffick_rules.drl")
     * @param ftreeFile       path to the .ftree file for cluster→rule mapping
     */
    public void init(String drlResourceName, String ftreeFile) throws IOException {
        // 1. Load full DRL
        String fullDrl = loadClasspathResource(drlResourceName);

        // 2. Parse cluster→rule mapping
        ClusterRuleMapping mapper = new ClusterRuleMapping();
        Map<String, List<String>> clusterRules = mapper.parse(ftreeFile);
        LOG.info("SessionManager: {} clusters from ftree", clusterRules.size());

        // 3. Extract the shared DRL header (everything before the first rule block)
        Matcher firstRule = RULE_BLOCK.matcher(fullDrl);
        String header;
        if (firstRule.find()) {
            header = fullDrl.substring(0, firstRule.start()).trim();
        } else {
            throw new RuntimeException("No rule blocks found in DRL");
        }

        // 4. Index all rule blocks by name
        Map<String, String> ruleBlocks = new LinkedHashMap<>();
        Matcher allRules = RULE_BLOCK.matcher(fullDrl);
        while (allRules.find()) {
            String ruleBody = allRules.group(1);
            String ruleName = allRules.group(2);
            ruleBlocks.put(ruleName, ruleBody);
        }
        LOG.info("SessionManager: indexed {} rule blocks from DRL", ruleBlocks.size());

        // 5. Build per-cluster sessions
        KieServices ks = KieServices.Factory.get();

        for (Map.Entry<String, List<String>> entry : clusterRules.entrySet()) {
            String clusterId = entry.getKey();
            List<String> ruleNames = entry.getValue();

            // Assemble cluster-specific DRL
            StringBuilder clusterDrl = new StringBuilder(header);
            clusterDrl.append("\n\n// ---- Core Mandatory Rules ----\n\n");
            
            for (String mandatoryRule : MANDATORY_CORE_RULES) {
                String block = ruleBlocks.get(mandatoryRule);
                if (block != null) {
                    clusterDrl.append(block).append("\n\n");
                }
            }

            clusterDrl.append("\n\n// ---- Cluster: ").append(clusterId).append(" ----\n\n");

            int included = 0;
            for (String ruleName : ruleNames) {
                if (MANDATORY_CORE_RULES.contains(ruleName)) continue; // Avoid duplicate
                String block = ruleBlocks.get(ruleName);
                if (block != null) {
                    clusterDrl.append(block).append("\n\n");
                    included++;
                } else {
                    LOG.warn("Rule '{}' (cluster {}) not found in DRL — skipping", ruleName, clusterId);
                }
            }

            if (included == 0) {
                LOG.warn("Cluster {} has no valid rules — skipping session creation", clusterId);
                continue;
            }

            // Build KieBase from cluster DRL
            try {
                KieFileSystem kfs = ks.newKieFileSystem();
                kfs.write("src/main/resources/rules/" + clusterId + ".drl", clusterDrl.toString());

                KieBuilder kb = ks.newKieBuilder(kfs).buildAll(DrlProject.class);
                Results results = kb.getResults();
                if (results.hasMessages(Message.Level.ERROR)) {
                    for (Message msg : results.getMessages(Message.Level.ERROR)) {
                        LOG.error("[{}] DRL build error: {}", clusterId, msg);
                    }
                    LOG.error("Skipping cluster {} due to DRL errors", clusterId);
                    continue;
                }

                KieModule km = kb.getKieModule();
                KieContainer kc = ks.newKieContainer(km.getReleaseId());

                org.kie.api.KieBaseConfiguration kbConfig = ks.newKieBaseConfiguration();
                kbConfig.setOption(EventProcessingOption.STREAM);
                KieBase kieBase = kc.newKieBase(kbConfig);

                KieSessionConfiguration sessionConfig = ks.newKieSessionConfiguration();
                sessionConfig.setOption(ClockTypeOption.PSEUDO);
                KieSession session = kieBase.newKieSession(sessionConfig, null);

                SessionPseudoClock clock = session.getSessionClock();
                session.setGlobal("clock", clock);

                sessions.put(clusterId, session);
                clocks.put(clusterId, clock);

                LOG.info("Cluster {} session created with {} rules", clusterId, included);
            } catch (Exception e) {
                LOG.error("Failed to build session for cluster {}: {}", clusterId, e.getMessage());
            }
        }

        LOG.info("SessionManager initialized: {} active cluster sessions", sessions.size());
    }

    public KieSession getSession(String clusterId) {
        return sessions.get(clusterId);
    }

    public SessionPseudoClock getPseudoClock(String clusterId) {
        return clocks.get(clusterId);
    }

    /**
     * Advance all cluster pseudo-clocks to the given target time.
     */
    public void advanceAllClocks(long targetMs) {
        for (Map.Entry<String, SessionPseudoClock> entry : clocks.entrySet()) {
            SessionPseudoClock clock = entry.getValue();
            long nowMs = clock.getCurrentTime();
            if (targetMs > nowMs) {
                clock.advanceTime(targetMs - nowMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    /** Get all active cluster IDs. */
    public Set<String> getActiveClusterIds() {
        return Collections.unmodifiableSet(sessions.keySet());
    }

    /** Dispose all sessions. */
    public void disposeAll() {
        for (KieSession session : sessions.values()) {
            try {
                session.dispose();
            } catch (Exception e) {
                LOG.warn("Error disposing session: {}", e.getMessage());
            }
        }
        sessions.clear();
        clocks.clear();
    }

    private String loadClasspathResource(String resourceName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + resourceName);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
