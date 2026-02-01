package org.kie.benchmark.cep.wikimedia;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * Factory for creating partitioned KieSessions.
 */
public class CepPartitionedSessionFactory {
    private static final Logger logger = LoggerFactory.getLogger(CepPartitionedSessionFactory.class);

    private final Map<ClusterId, KieBase> clusterKieBases = new EnumMap<>(ClusterId.class);
    private final KieBase commonKieBase; // Fallback or shared if needed

    public CepPartitionedSessionFactory() {
        this.commonKieBase = null; // Not using a shared base for now, fully isolated
        initializeClusterBases();
    }

    private void initializeClusterBases() {
        // C1: Minor
        clusterKieBases.put(ClusterId.C1_MINOR, createKieBase("rules/clusters/c1_minor.drl"));
        
        // C2: Content
        clusterKieBases.put(ClusterId.C2_CONTENT, createKieBase("rules/clusters/c2_content.drl"));
        
        // C3: Bot
        clusterKieBases.put(ClusterId.C3_BOT, createKieBase("rules/clusters/c3_bot.drl"));
        
        // C4: Vandalism
        clusterKieBases.put(ClusterId.C4_VANDALISM, createKieBase("rules/clusters/c4_vandalism.drl"));
    }

    private KieBase createKieBase(String clusterDrlPath) {
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kfs = kieServices.newKieFileSystem();

            // Load Common DRL (Declarations)
            loadResource(kfs, "rules/clusters/common.drl");
            
            // Load Cluster Specific DRL
            loadResource(kfs, clusterDrlPath);

            KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
            kieBuilder.buildAll();

            if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                throw new RuntimeException("Build errors for " + clusterDrlPath + ": " + kieBuilder.getResults().toString());
            }

            // Create KieBase with STREAM mode for CEP
            org.kie.api.KieBaseConfiguration kieBaseConfig = kieServices.newKieBaseConfiguration();
            kieBaseConfig.setOption(EventProcessingOption.STREAM);
            
            return kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId())
                    .newKieBase(kieBaseConfig);

        } catch (Exception e) {
            logger.error("Failed to create KieBase for " + clusterDrlPath, e);
            throw new RuntimeException("Failed to create KieBase", e);
        }
    }

    private void loadResource(KieFileSystem kfs, String path) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new RuntimeException("Cannot find rules file: " + path);
        }
        kfs.write("src/main/resources/" + path, ResourceFactory.newInputStreamResource(stream));
    }

    public Map<ClusterId, KieSession> createSessions(boolean pseudoClock) {
        Map<ClusterId, KieSession> sessions = new EnumMap<>(ClusterId.class);
        KieServices kieServices = KieServices.Factory.get();
        org.kie.api.runtime.KieSessionConfiguration sessionConfig = kieServices.newKieSessionConfiguration();
        
        if (pseudoClock) {
            sessionConfig.setOption(org.kie.api.runtime.conf.ClockTypeOption.get("pseudo"));
        }

        for (Map.Entry<ClusterId, KieBase> entry : clusterKieBases.entrySet()) {
            sessions.put(entry.getKey(), entry.getValue().newKieSession(sessionConfig, null));
        }
        return sessions;
    }
    
    // Default compatibility
    public Map<ClusterId, KieSession> createSessions() {
        return createSessions(false);
    }
}
