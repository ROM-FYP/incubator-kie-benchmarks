package bench.opensky.replay;

import java.io.*;
import java.util.*;

public class AnalyzeRuleFirings {
    public static void main(String[] args) throws Exception {
        String file = args.length > 0 ? args[0] : "rule_firings.csv";
        Map<String, Long> counts = new LinkedHashMap<>();
        long total = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(file), 1 << 20)) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                int end = line.indexOf(',', line.indexOf(',') + 1);
                String rule = (end == -1) ? line : line.substring(line.indexOf(',') + 1, end);
                counts.merge(rule, 1L, Long::sum);
                total++;
            }
        }
        // Sort by count descending
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        System.out.println("Total rows: " + total);
        System.out.println("Unique rules: " + sorted.size());
        System.out.println();
        System.out.printf("%-55s %s%n", "Rule", "Count");
        System.out.println("-".repeat(75));
        for (Map.Entry<String, Long> e : sorted) {
            System.out.printf("%-55s %,d%n", e.getKey(), e.getValue());
        }
    }
}
