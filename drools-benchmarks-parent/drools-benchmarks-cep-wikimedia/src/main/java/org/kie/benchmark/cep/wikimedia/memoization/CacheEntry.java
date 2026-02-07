package org.kie.benchmark.cep.wikimedia.memoization;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for cached cluster outputs with metadata.
 */
public class CacheEntry {
    private final List<ClusterOutput> outputs;
    private final long createdAtMs;
    private long hitCount;
    
    public CacheEntry(List<ClusterOutput> outputs) {
        this.outputs = new ArrayList<>(outputs);
        this.createdAtMs = System.currentTimeMillis();
        this.hitCount = 0;
    }
    
    public List<ClusterOutput> getOutputs() {
        return new ArrayList<>(outputs);
    }
    
    public long getCreatedAtMs() {
        return createdAtMs;
    }
    
    public long getHitCount() {
        return hitCount;
    }
    
    public void incrementHitCount() {
        this.hitCount++;
    }
    
    public boolean isExpired(long ttlMs) {
        if (ttlMs <= 0) return false;
        return System.currentTimeMillis() - createdAtMs > ttlMs;
    }
}
