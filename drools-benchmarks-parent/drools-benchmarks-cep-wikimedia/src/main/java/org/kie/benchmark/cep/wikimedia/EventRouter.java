package org.kie.benchmark.cep.wikimedia;

import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.util.EnumSet;

/**
 * Routes events to appropriate clusters.
 */
public class EventRouter {

    public EnumSet<ClusterId> route(WikiEvent event) {
        // C3: Bot
        if (event.isBot()) {
            return EnumSet.of(ClusterId.C3_BOT);
        }
        
        // C5: Discussion - NO DEDICATED CLUSTER (Fallback to Baseline/None in Caller)
        if (event.getTitle() != null && event.getTitle().startsWith("Talk:")) {
            return EnumSet.noneOf(ClusterId.class); 
        }

        // C4: Vandalism (sizeDelta < -100)
        if (event.getSizeDelta() < -100) {
            return EnumSet.of(ClusterId.C4_VANDALISM);
        }

        // C2: Content Growth (sizeDelta > 200, not bot)
        // Note: bot check already handled above, so here bot is false
        if (event.getSizeDelta() > 200) {
            return EnumSet.of(ClusterId.C2_CONTENT);
        }

        // C1: Minor Edits (-50 <= sizeDelta <= 50, not bot)
        // Note: bot check already handled above
        if (event.getSizeDelta() >= -50 && event.getSizeDelta() <= 50) {
            return EnumSet.of(ClusterId.C1_MINOR);
        }

        // Fallback for others
        return EnumSet.noneOf(ClusterId.class);
    }
}
