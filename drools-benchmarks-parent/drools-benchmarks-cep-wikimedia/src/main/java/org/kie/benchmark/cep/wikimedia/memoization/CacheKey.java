package org.kie.benchmark.cep.wikimedia.memoization;

import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.util.Objects;

/**
 * Deterministic cache key for cluster-level memoization.
 * Uses coarse bucketing to maximize cache hit rates while preserving correctness.
 */
public class CacheKey {
    private final int clusterId;
    private final String eventTypeBucket;
    private final boolean botFlag;
    private final int sizeDeltaBucket;      // Rounded to nearest 500
    private final String titlePrefixBucket;  // First 3 chars
    private final long timeBucket;           // floor(ts / 10000) for 10s windows
    private final long sharedStateVersion;
    
    private CacheKey(int clusterId, String eventTypeBucket, boolean botFlag, 
                     int sizeDeltaBucket, String titlePrefixBucket,
                     long timeBucket, long sharedStateVersion) {
        this.clusterId = clusterId;
        this.eventTypeBucket = eventTypeBucket;
        this.botFlag = botFlag;
        this.sizeDeltaBucket = sizeDeltaBucket;
        this.titlePrefixBucket = titlePrefixBucket;
        this.timeBucket = timeBucket;
        this.sharedStateVersion = sharedStateVersion;
    }
    
    /**
     * Creates a cache key from a WikiEvent with coarse bucketing.
     */
    public static CacheKey from(int clusterId, WikiEvent event, long sharedStateVersion) {
        // Event type bucketing
        String eventTypeBucket = determineEventType(event);
        
        // Size delta bucketing (round to nearest 500)
        int sizeDeltaBucket = (event.getSizeDelta() / 500) * 500;
        
        // Title prefix bucketing (first 3 chars)
        String titlePrefixBucket = event.getTitle() != null && event.getTitle().length() >= 3
                ? event.getTitle().substring(0, 3)
                : (event.getTitle() != null ? event.getTitle() : "");
        
        // Time bucketing (10 second windows)
        long timeBucket = event.getTimestamp() / 10000;
        
        return new CacheKey(
            clusterId,
            eventTypeBucket,
            event.isBot(),
            sizeDeltaBucket,
            titlePrefixBucket,
            timeBucket,
            sharedStateVersion
        );
    }
    
    private static String determineEventType(WikiEvent event) {
        if (event.isBot()) return "bot";
        if (event.getSizeDelta() > 1000) return "major";
        if (event.getSizeDelta() < -100) return "vandal";
        if (Math.abs(event.getSizeDelta()) <= 50) return "minor";
        return "standard";
    }
    
    public int getClusterId() {
        return clusterId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return clusterId == cacheKey.clusterId &&
                botFlag == cacheKey.botFlag &&
                sizeDeltaBucket == cacheKey.sizeDeltaBucket &&
                timeBucket == cacheKey.timeBucket &&
                sharedStateVersion == cacheKey.sharedStateVersion &&
                Objects.equals(eventTypeBucket, cacheKey.eventTypeBucket) &&
                Objects.equals(titlePrefixBucket, cacheKey.titlePrefixBucket);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(clusterId, eventTypeBucket, botFlag, sizeDeltaBucket, 
                           titlePrefixBucket, timeBucket, sharedStateVersion);
    }
    
    @Override
    public String toString() {
        return String.format("CacheKey[C%d,%s,bot=%b,size=%d,title=%s,t=%d,v=%d]",
                clusterId, eventTypeBucket, botFlag, sizeDeltaBucket,
                titlePrefixBucket, timeBucket, sharedStateVersion);
    }
}
