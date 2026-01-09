package org.kie.benchmark.cep.reproducible.wikimedia.config;

import java.nio.file.Path;

public final class ReplayConfig {

    private final Path ndjsonFile;

    // replay controls
    private final ReplayMode mode;
    private final double speedFactor;        // used in TIMESTAMP mode
    private final double eventsPerSecond;    // used in FIXED_RATE mode

    // limits
    private final long maxEvents;

    public ReplayConfig(Path ndjsonFile,
                        ReplayMode mode,
                        double speedFactor,
                        double eventsPerSecond,
                        long maxEvents) {

        this.ndjsonFile = ndjsonFile;
        this.mode = mode;
        this.speedFactor = speedFactor;
        this.eventsPerSecond = eventsPerSecond;
        this.maxEvents = maxEvents;
    }

    public Path getNdjsonFile() {
        return ndjsonFile;
    }

    public ReplayMode getMode() {
        return mode;
    }

    public double getSpeedFactor() {
        return speedFactor;
    }

    public double getEventsPerSecond() {
        return eventsPerSecond;
    }

    public long getMaxEvents() {
        return maxEvents;
    }

    public static ReplayConfig timestamp(Path ndjsonFile, double speedFactor, long maxEvents) {
        return new ReplayConfig(ndjsonFile, ReplayMode.TIMESTAMP, speedFactor, 0.0, maxEvents);
    }

    public static ReplayConfig fixedRate(Path ndjsonFile, double eventsPerSecond, long maxEvents) {
        return new ReplayConfig(ndjsonFile, ReplayMode.FIXED_RATE, 1.0, eventsPerSecond, maxEvents);
    }
}
