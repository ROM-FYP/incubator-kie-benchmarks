package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;

/**
 * Replays recorded events using a Pseudo Clock for deterministic execution.
 */
public class EventReplayer {
    private static final Logger logger = LoggerFactory.getLogger(EventReplayer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String inputPath;
    private final List<WikiEvent> events = new ArrayList<>();

    public EventReplayer(String inputPath) {
        this.inputPath = inputPath;
    }

    public void loadEvents() {
        logger.info("Loading events for replay from: {}", inputPath);
        try (BufferedReader reader = new BufferedReader(new FileReader(inputPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    events.add(objectMapper.readValue(line, WikiEvent.class));
                }
            }
            logger.info("Loaded {} events.", events.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load events", e);
        }
    }

    /**
     * Replays events.
     * @param timeAdvancer Consumer to advance time (in milliseconds).
     * @param eventInserter Consumer to insert the event into the session(s).
     * @param ruleFirer   Runnable/Consumer to fire rules.
     */
    public void replay(LongConsumer timeAdvancer, BiConsumer<WikiEvent, Long> eventProcessor, Runnable ruleFirer) {
        if (events.isEmpty()) {
            logger.warn("No events to replay!");
            return;
        }

        logger.info("Starting deterministic replay...");
        long startWallTime = System.nanoTime();

        // Assuming recorded order is correct stream order.

        long firstEventTime = events.get(0).getTimestamp();
        long currentClockTime = firstEventTime;
        
        // Align clock to first event
        timeAdvancer.accept(firstEventTime); // Advance to start time

        for (WikiEvent event : events) {
            long eventTime = event.getTimestamp();
            long timeDelta = eventTime - currentClockTime;

            if (timeDelta > 0) {
                timeAdvancer.accept(timeDelta);
                currentClockTime = eventTime;
            }

            // Process event (Route & Insert)
            eventProcessor.accept(event, eventTime);

            // Fire Rules (Micro-batching per event for strict determinism)
            ruleFirer.run();
        }

        long endWallTime = System.nanoTime();
        double durationSeconds = (endWallTime - startWallTime) / 1_000_000_000.0;
        
        logger.info("Replay completed.");
        logger.info("Replay Wall Duration: {} sec", String.format("%.4f", durationSeconds));
        // Avoid division by zero
        double throughput = durationSeconds > 0 ? events.size() / durationSeconds : 0;
        logger.info("Replay Throughput: {} events/sec", String.format("%.2f", throughput));
    }
    
    public int getEventCount() {
        return events.size();
    }
}
