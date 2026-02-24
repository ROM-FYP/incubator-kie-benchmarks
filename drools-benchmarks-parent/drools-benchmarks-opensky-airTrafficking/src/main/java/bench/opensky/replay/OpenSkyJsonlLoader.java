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
 * Loads OpenSky flat JSONL files into grouped snapshots of {@link OpenSkyStateVector}.
 */
public class OpenSkyJsonlLoader {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSkyJsonlLoader.class);

    public enum Mode {
        /** Group records by snapshot_time, return ordered list of snapshots. */
        GROUP_BY_SNAPSHOT,
        /** Return one record per snapshot (one-element lists). */
        STREAM
    }

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Load and return an ordered list of snapshots (each snapshot is a list of state vectors).
     *
     * @param resourcePath classpath resource (e.g. "data/opensky_flat_20260217_160412.jsonl")
     * @param mode         grouping mode
     * @return ordered list of snapshots
     */
    public List<List<OpenSkyStateVector>> load(String resourcePath, Mode mode) throws IOException {
        LOG.info("Loading JSONL from classpath: {}", resourcePath);

        List<OpenSkyFlatRecord> records = new ArrayList<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                int lineNum = 0;
                int errors = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                        OpenSkyFlatRecord rec = mapper.readValue(line, OpenSkyFlatRecord.class);
                        records.add(rec);
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
            if (st < prevTime) {
                outOfOrder++;
            }
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
}
