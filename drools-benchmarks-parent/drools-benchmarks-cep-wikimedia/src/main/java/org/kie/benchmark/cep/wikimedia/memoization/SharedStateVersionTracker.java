package org.kie.benchmark.cep.wikimedia.memoization;

import org.drools.core.event.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks shared state changes in the correlation session to invalidate cache entries.
 * Increments a monotonic version counter whenever shared state facts are modified.
 */
public class SharedStateVersionTracker extends DefaultRuleRuntimeEventListener {
    private static final Logger logger = LoggerFactory.getLogger(SharedStateVersionTracker.class);
    
    private final AtomicLong version = new AtomicLong(0);
    private final Set<String> sharedStateTypes;
    
    public SharedStateVersionTracker() {
        this.sharedStateTypes = new HashSet<>();
        // Define which fact types invalidate the cache when modified
        sharedStateTypes.add("UserActivity");
        sharedStateTypes.add("ArticleQuality");
        sharedStateTypes.add("EditPattern");
    }
    
    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        String typeName = event.getObject().getClass().getSimpleName();
        if (sharedStateTypes.contains(typeName)) {
            long newVersion = version.incrementAndGet();
            logger.debug("Shared state inserted: {} -> version {}", typeName, newVersion);
        }
    }
    
    @Override
    public void objectUpdated(ObjectUpdatedEvent event) {
        String typeName = event.getObject().getClass().getSimpleName();
        if (sharedStateTypes.contains(typeName)) {
            long newVersion = version.incrementAndGet();
            logger.debug("Shared state updated: {} -> version {}", typeName, newVersion);
        }
    }
    
    public long getCurrentVersion() {
        return version.get();
    }
    
    public void reset() {
        version.set(0);
        logger.info("Shared state version reset to 0");
    }
}
