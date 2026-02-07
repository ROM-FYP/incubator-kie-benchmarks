package org.kie.benchmark.cep.wikimedia.memoization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * LRU cache for cluster-level memoization with TTL and metrics tracking.
 */
public class ClusterMemoizationCache {
    private static final Logger logger = LoggerFactory.getLogger(ClusterMemoizationCache.class);
    
    private final LinkedHashMap<CacheKey, CacheEntry> cache;
    private final int maxEntries;
    private final long ttlMs;
    
    // Metrics
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();
    private final AtomicLong insertions = new AtomicLong();
    private final Map<Integer, AtomicLong> clusterHits = new HashMap<>();
    private final Map<Integer, AtomicLong> clusterMisses = new HashMap<>();
    
    public ClusterMemoizationCache(int maxEntries, long ttlMs) {
        this.maxEntries = maxEntries;
        this.ttlMs = ttlMs;
        
        // LRU eviction with access-order
        this.cache = new LinkedHashMap<CacheKey, CacheEntry>(maxEntries + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheEntry> eldest) {
                boolean shouldRemove = size() > maxEntries;
                if (shouldRemove) {
                    evictions.incrementAndGet();
                    logger.debug("Evicting cache entry: {}", eldest.getKey());
                }
                return shouldRemove;
            }
        };
        
        // Initialize per-cluster metrics
        for (int i = 0; i < 5; i++) {
            clusterHits.put(i, new AtomicLong());
            clusterMisses.put(i, new AtomicLong());
        }
    }
    
    /**
     * Retrieves cached outputs for the given key.
     * Returns empty if not found or expired.
     */
    public synchronized Optional<List<ClusterOutput>> get(CacheKey key) {
        CacheEntry entry = cache.get(key);
        
        if (entry == null) {
            misses.incrementAndGet();
            clusterMisses.get(key.getClusterId()).incrementAndGet();
            return Optional.empty();
        }
        
        if (entry.isExpired(ttlMs)) {
            cache.remove(key);
            misses.incrementAndGet();
            clusterMisses.get(key.getClusterId()).incrementAndGet();
            logger.debug("Cache entry expired: {}", key);
            return Optional.empty();
        }
        
        entry.incrementHitCount();
        hits.incrementAndGet();
        clusterHits.get(key.getClusterId()).incrementAndGet();
        logger.debug("Cache HIT: {} (hits: {})", key, entry.getHitCount());
        
        return Optional.of(entry.getOutputs());
    }
    
    /**
     * Stores cluster outputs in the cache.
     */
    public synchronized void put(CacheKey key, List<ClusterOutput> outputs) {
        cache.put(key, new CacheEntry(outputs));
        insertions.incrementAndGet();
        logger.debug("Cache PUT: {} ({} outputs)", key, outputs.size());
    }
    
    /**
     * Invalidates cache entries matching the predicate.
     */
    public synchronized void invalidate(Predicate<CacheKey> filter) {
        int removed = 0;
        Iterator<Map.Entry<CacheKey, CacheEntry>> iter = cache.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> entry = iter.next();
            if (filter.test(entry.getKey())) {
                iter.remove();
                removed++;
            }
        }
        logger.debug("Invalidated {} cache entries", removed);
    }
    
    /**
     * Clears the entire cache.
     */
    public synchronized void clear() {
        cache.clear();
        logger.info("Cache cleared");
    }
    
    // Metrics accessors
    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getEvictions() { return evictions.get(); }
    public long getInsertions() { return insertions.get(); }
    public int getSize() { return cache.size(); }
    
    public double getHitRate() {
        long total = hits.get() + misses.get();
        return total == 0 ? 0.0 : (double) hits.get() / total;
    }
    
    public Map<Integer, Long> getClusterHits() {
        Map<Integer, Long> result = new HashMap<>();
        for (Map.Entry<Integer, AtomicLong> entry : clusterHits.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
    
    public Map<Integer, Long> getClusterMisses() {
        Map<Integer, Long> result = new HashMap<>();
        for (Map.Entry<Integer, AtomicLong> entry : clusterMisses.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }
    
    public void printMetrics() {
        logger.info("=== Memoization Cache Metrics ===");
        logger.info("Total Hits: {}", hits.get());
        logger.info("Total Misses: {}", misses.get());
        logger.info("Hit Rate: {:.2f}%", getHitRate() * 100);
        logger.info("Cache Size: {}/{}", cache.size(), maxEntries);
        logger.info("Evictions: {}", evictions.get());
        logger.info("Insertions: {}", insertions.get());
        
        logger.info("Per-Cluster Hit Rates:");
        for (int i = 0; i < 5; i++) {
            long h = clusterHits.get(i).get();
            long m = clusterMisses.get(i).get();
            double rate = (h + m) == 0 ? 0.0 : (double) h / (h + m);
            logger.info("  Cluster {}: {:.2f}% ({} hits / {} total)", i, rate * 100, h, h + m);
        }
    }
}
