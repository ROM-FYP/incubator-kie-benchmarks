package org.kie.benchmark.cep.reproducible.wikimedia.metrics;

public final class ReplayMetrics {

    private long events;
    private long startNs;
    private long endNs;

    public void start() {
        startNs = System.nanoTime();
    }

    public void recordEvent() {
        events++;
    }

    public void stop() {
        endNs = System.nanoTime();
    }

    public long getEvents() {
        return events;
    }

    public double durationSeconds() {
        return (endNs - startNs) / 1_000_000_000.0;
    }

    public double throughput() {
        return events / durationSeconds();
    }
}
