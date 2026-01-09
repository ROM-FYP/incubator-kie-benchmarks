package org.kie.benchmark.cep.reproducible.wikimedia.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.reproducible.wikimedia.config.ReplayConfig;
import org.kie.benchmark.cep.reproducible.wikimedia.config.ReplayMode;
import org.kie.benchmark.cep.reproducible.wikimedia.model.WikiEvent;
import org.kie.benchmark.cep.reproducible.wikimedia.preprocess.WikiEventParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NdjsonFileEventSource implements AutoCloseable {

    private final ReplayConfig config;
    private final BufferedReader reader;

    // Parse each NDJSON line
    private final ObjectMapper mapper = new ObjectMapper();
    private final WikiEventParser parser = new WikiEventParser();

    // replay state
    private long emitted = 0;
    private boolean first = true;

    // timing state
    private long lastArrivalMs = -1;
    private long fixedRateSleepMs = 0;

    public NdjsonFileEventSource(ReplayConfig config) throws IOException {
        this.config = config;
        Path f = config.getNdjsonFile();
        this.reader = Files.newBufferedReader(f, StandardCharsets.UTF_8);

        if (config.getMode() == ReplayMode.FIXED_RATE) {
            double eps = config.getEventsPerSecond();
            if (eps <= 0.0) {
                throw new IllegalArgumentException("eventsPerSecond must be > 0 for FIXED_RATE mode");
            }
            this.fixedRateSleepMs = Math.max(0, (long) Math.floor(1000.0 / eps));
        }
    }

    /**
     * Returns next parsed WikiEvent (filtered) or null when file ends / maxEvents reached.
     */
    public WikiEvent next() throws IOException, InterruptedException {
        if (emitted >= config.getMaxEvents()) {
            return null;
        }

        String line;
        while ((line = reader.readLine()) != null) {

            // skip empty lines
            if (line.isEmpty()) {
                continue;
            }

            JsonNode root;
            try {
                root = mapper.readTree(line);
            } catch (Exception e) {
                // bad line: skip (or throw if you want strict mode later)
                continue;
            }

            // TIMING CONTROL (based on mode)
            if (config.getMode() == ReplayMode.TIMESTAMP) {
                maybeSleepTimestampMode(root);
            } else {
                maybeSleepFixedRateMode();
            }

            // IMPORTANT:
            // WikiEventParser expects the "raw Wikimedia JSON schema" (type/namespace/title/user/...)
            // But our NDJSON file is already "preprocessed" (title/user/comment/bot/timestamp/sizeDelta).
            //
            // Therefore in Phase A we do one of two things:
            //  (A) Update WikiEventParser to accept the preprocessed format, OR
            //  (B) Add a separate parser for preprocessed NDJSON lines.
            //
            // We will do (B) to avoid breaking existing logic: parse preprocessed directly here.

            WikiEvent evt = parsePreprocessedLine(root);
            if (evt == null) {
                continue;
            }

            emitted++;
            return evt;
        }

        return null;
    }

    private void maybeSleepTimestampMode(JsonNode root) throws InterruptedException {
        // Prefer arrivalTimestamp (ms) if present, else fall back to "timestamp" (seconds or ms depending on producer)
        long arrival = root.path("arrivalTimestamp").asLong(-1);

        if (arrival <= 0) {
            // fallback: if timestamp looks like seconds, convert to ms
            long ts = root.path("timestamp").asLong(-1);
            if (ts > 0 && ts < 10_000_000_000L) { // heuristic: seconds epoch
                arrival = ts * 1000L;
            } else {
                arrival = ts;
            }
        }

        if (arrival <= 0) {
            return; // no timing info, emit immediately
        }

        if (first) {
            first = false;
            lastArrivalMs = arrival;
            return; // no sleep before first event
        }

        long delta = arrival - lastArrivalMs;
        lastArrivalMs = arrival;

        if (delta <= 0) {
            return;
        }

        double speed = config.getSpeedFactor();
        if (speed <= 0.0) {
            speed = 1.0;
        }

        long scaled = (long) Math.floor(delta / speed);
        if (scaled > 0) {
            Thread.sleep(scaled);
        }
    }

    private void maybeSleepFixedRateMode() throws InterruptedException {
        if (fixedRateSleepMs > 0) {
            Thread.sleep(fixedRateSleepMs);
        }
    }

    /**
     * Parse our preprocessed NDJSON format:
     * { "title", "user", "comment", "bot", "timestamp", "sizeDelta" }
     *
     * timestamp here is seconds (per your sample output), and we keep it in seconds to match WikiEventParser semantics.
     */
    private WikiEvent parsePreprocessedLine(JsonNode root) {
        String title = root.path("title").asText(null);
        String user = root.path("user").asText(null);
        String comment = root.path("comment").asText("");
        boolean bot = root.path("bot").asBoolean(false);

        long timestamp = root.path("timestamp").asLong(-1); // seconds in your file
        int sizeDelta = root.path("sizeDelta").asInt(0);

        if (title == null || user == null || timestamp <= 0) {
            return null;
        }

        // Keep timestamp in seconds to match the Java parseEvent() you showed earlier.
        return new WikiEvent(title, user, comment, bot, timestamp, sizeDelta);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
