package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Records incoming events to a file.
 */
public class EventRecorder {
    private static final Logger logger = LoggerFactory.getLogger(EventRecorder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String outputPath;
    private BufferedWriter writer;
    private final AtomicInteger count = new AtomicInteger(0);

    public EventRecorder(String outputPath) {
        this.outputPath = outputPath;
    }

    public void start() {
        try {
            writer = new BufferedWriter(new FileWriter(outputPath));
            logger.info("Recording events to: {}", outputPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open recording file", e);
        }
    }

    public void record(WikiEvent event) {
        if (writer != null) {
            try {
                String json = objectMapper.writeValueAsString(event);
                writer.write(json);
                writer.newLine();
                count.incrementAndGet();
            } catch (IOException e) {
                logger.error("Failed to write event", e);
            }
        }
    }

    public void stop() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
                logger.info("Recording finished. Total events recorded: {}", count.get());
            } catch (IOException e) {
                logger.error("Error closing recorder", e);
            }
        }
    }
}
