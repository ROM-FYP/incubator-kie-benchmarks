package org.kie.benchmark.cep.wikimedia;

import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

/**
 * Routes events to appropriate clusters.
 */
public class EventRouter {

    /**
     * Determines which cluster should handle this event.
     * Returns ClusterId.S_CORRELATION is NOT returned here as it's a sink for forwarded facts.
     */
    public static ClusterId route(WikiEvent event) {
        // C3: Bot
        if (event.isBot()) {
            return ClusterId.C3_BOT;
        }
        
        // C5: Discussion - NO DEDICATED CLUSTER (Handled by Pipeline Fallback)
        if (event.getTitle() != null && event.getTitle().startsWith("Talk:")) {
            return ClusterId.C1_MINOR; // Fallback to Minor for noise
        }

        // C4: Vandalism (sizeDelta < -100)
        if (event.getSizeDelta() < -100) {
            return ClusterId.C4_VANDALISM;
        }

        // C2: Content Growth (sizeDelta > 200, not bot)
        if (event.getSizeDelta() > 200) {
            return ClusterId.C2_CONTENT;
        }

        // C1: Minor Edits (-50 <= sizeDelta <= 50, not bot)
        if (event.getSizeDelta() >= -50 && event.getSizeDelta() <= 50) {
            return ClusterId.C1_MINOR;
        }

        // Fallback
        return ClusterId.C1_MINOR;
    }
}
