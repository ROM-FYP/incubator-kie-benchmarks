package bench.opensky.router;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an Infomap {@code .ftree} file to build a mapping of
 * cluster IDs to the rule names belonging to each cluster.
 */
public class ClusterRuleMapping {

    private static final Pattern NODE_LINE = Pattern.compile(
            "^(\\d+):\\d+\\s+[\\d.eE+\\-]+\\s+\"([^\"]+)\"\\s+\\d+$");

    /**
     * Parse the ftree file and return cluster_id → list of rule names.
     *
     * @param ftreeFile path to the .ftree file
     * @return map, e.g. { "cluster_1" → ["R056_...", "R067b_..."], "cluster_2" → [...] }
     */
    public Map<String, List<String>> parse(String ftreeFile) throws IOException {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        boolean inNodes = false;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(ftreeFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("*Nodes")) {
                    inNodes = true;
                    continue;
                }
                if (line.startsWith("*Links") || line.startsWith("*Modules")) {
                    if (inNodes) break; // done with nodes section
                    continue;
                }
                if (!inNodes || line.startsWith("#") || line.isEmpty()) {
                    continue;
                }

                Matcher m = NODE_LINE.matcher(line);
                if (m.find()) {
                    String clusterId = "cluster_" + m.group(1);
                    String ruleName = m.group(2);
                    mapping.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(ruleName);
                }
            }
        }

        return mapping;
    }
}
