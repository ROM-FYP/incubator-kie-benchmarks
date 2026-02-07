package org.kie.benchmark.cep.wikimedia.memoization;

import org.drools.core.event.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures ClusterOutput facts produced by a cluster session during rule execution.
 * This listener is attached to each cluster session to collect outputs for caching.
 */
public class ClusterOutputCaptureListener extends DefaultRuleRuntimeEventListener {
    private static final Logger logger = LoggerFactory.getLogger(ClusterOutputCaptureListener.class);
    
    private final int clusterId;
    private final List<ClusterOutput> capturedOutputs;
    
    public ClusterOutputCaptureListener(int clusterId) {
        this.clusterId = clusterId;
        this.capturedOutputs = new ArrayList<>();
    }
    
    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        Object obj = event.getObject();
        if (obj instanceof ClusterOutput) {
            ClusterOutput output = (ClusterOutput) obj;
            capturedOutputs.add(output);
            logger.debug("Cluster {} captured output: {}", clusterId, output);
        }
    }
    
    /**
     * Returns captured outputs and clears the internal list.
     */
    public List<ClusterOutput> getAndClearOutputs() {
        List<ClusterOutput> result = new ArrayList<>(capturedOutputs);
        capturedOutputs.clear();
        return result;
    }
    
    /**
     * Clears captured outputs without returning them.
     */
    public void clear() {
        capturedOutputs.clear();
    }
    
    /**
     * Returns the number of captured outputs without clearing.
     */
    public int getCapturedCount() {
        return capturedOutputs.size();
    }
}
