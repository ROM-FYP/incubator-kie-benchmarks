package bench.opensky.replay;

import bench.opensky.model.OpenSkyFlatRecord;
import bench.opensky.model.OpenSkyStateVector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads OpenSky flat JSONL files into lists of {@link OpenSkyStateVector}.
 *
 * <p>Two loading styles are provided:
 * <ul>
 *   <li>{@link #loadFlat(String)} — returns all events as a single flat, time-ordered
 *       {@code List<OpenSkyStateVector>}. This is the preferred method for the CEP
 *       stream benchmark where one event is ingested per {@code fireAllRules()} call.</li>
 *   <li>{@link #load(String, Mode)} — legacy method kept for compatibility; returns
 *       events grouped by snapshot time.</li>
 * </ul>
 * </p>
 */
public class OpenSkyJsonlLoader {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSkyJsonlLoader.class);

    /** Legacy grouping mode (used by the old snapshot benchmark). */
    public enum Mode {
        /** Group records by snapshot_time, return ordered list of snapshots. */
        GROUP_BY_SNAPSHOT,
        /** Return one record per snapshot (one-element lists). */
        STREAM
    }

    private final ObjectMapper mapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // CEP stream loader (primary)
    // -------------------------------------------------------------------------

    /**
     * Load all events from a JSONL classpath resource into a flat, time-ordered list.
     * Each element is one {@link OpenSkyStateVector}. The list preserves file order,
     * which is assumed to be chronological (ascending {@code snapshot_time}).
     *
     * @param resourcePath classpath resource (e.g. {@code "data/opensky_flat_20260217_160412.jsonl"})
     * @return flat list of state vectors in file order
     * @throws IOException if the resource cannot be read
     */
    public List<OpenSkyStateVector> loadFlat(String resourcePath) throws IOException {
        LOG.info("Loading flat CEP stream from classpath: {}", resourcePath);
        List<OpenSkyFlatRecord> records = parseRecords(resourcePath);
        List<OpenSkyStateVector> events = records.stream()
                .map(OpenSkyStateVector::fromFlatRecord)
                .collect(Collectors.toList());
        LOG.info("Loaded {} events", events.size());
        return events;
    }

    // -------------------------------------------------------------------------
    // Legacy snapshot loader
    // -------------------------------------------------------------------------

    /**
     * Load and return an ordered list of snapshots (each snapshot is a list of state vectors).
     *
     * @param resourcePath classpath resource (e.g. {@code "data/opensky_flat_20260217_160412.jsonl"})
     * @param mode         grouping mode
     * @return ordered list of snapshots
     * @deprecated Prefer {@link #loadFlat(String)} for the CEP stream benchmark.
     */
    @Deprecated
    public List<List<OpenSkyStateVector>> load(String resourcePath, Mode mode) throws IOException {
        LOG.info("Loading JSONL (legacy mode={}) from classpath: {}", mode, resourcePath);
        List<OpenSkyFlatRecord> records = parseRecords(resourcePath);

        if (mode == Mode.STREAM) {
            return records.stream()
                    .map(r -> List.of(OpenSkyStateVector.fromFlatRecord(r)))
                    .collect(Collectors.toList());
        }

        // GROUP_BY_SNAPSHOT: group by snapshotTime, preserve order
        Map<Long, List<OpenSkyStateVector>> grouped = new LinkedHashMap<>();
        int outOfOrder = 0;
        long prevTime = Long.MIN_VALUE;

        for (OpenSkyFlatRecord rec : records) {
            long st = rec.getSnapshotTime();
            if (st < prevTime) outOfOrder++;
            prevTime = st;
            grouped.computeIfAbsent(st, k -> new ArrayList<>())
                   .add(OpenSkyStateVector.fromFlatRecord(rec));
        }

        if (outOfOrder > 0) {
            LOG.warn("{} records found out of chronological order", outOfOrder);
        }

        List<List<OpenSkyStateVector>> snapshots = new ArrayList<>(grouped.values());
        LOG.info("Grouped into {} snapshots ({} unique snapshot times)",
                snapshots.size(), grouped.size());
        return snapshots;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private List<OpenSkyFlatRecord> parseRecords(String resourcePath) throws IOException {
        List<OpenSkyFlatRecord> records = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + resourcePath);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                int lineNum = 0, errors = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                        records.add(mapper.readValue(line, OpenSkyFlatRecord.class));
                    } catch (Exception e) {
                        errors++;
                        if (errors <= 5) {
                            LOG.warn("Parse error at line {}: {}", lineNum, e.getMessage());
                        }
                    }
                }
                LOG.info("Parsed {} records from {} lines ({} errors)", records.size(), lineNum, errors);
            }
        }
        return records;
    }
}
