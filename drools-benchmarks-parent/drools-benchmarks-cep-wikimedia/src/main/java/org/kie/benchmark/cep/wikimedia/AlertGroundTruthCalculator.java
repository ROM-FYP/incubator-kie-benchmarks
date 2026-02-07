package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

/**
 * Calculates expected alert counts based on refined definitions:
 * 1. Bots: Only high-volume bots (>10 edits/min) trigger an alert.
 * 2. Content Major: Each major edit (>1000 sizeDelta) triggers exactly 1 alert (_Log).
 * 3. Minors/Others: Tracked but no alerts generated.
 */
public class AlertGroundTruthCalculator {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java AlertGroundTruthCalculator <path_to_json>");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<WikiEvent> events = new ArrayList<>();
        File file = new File(args[0]);

        int lineNum = 0;
        int errCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                try {
                    WikiEvent e = mapper.readValue(line, WikiEvent.class);
                    events.add(e);
                } catch (Exception e) {
                    errCount++;
                }
            }
        }

        // --- REFINED LOGIC ---
        
        // 1. Bot High Volume Logic (Activity Rate > 10 edits/min)
        Map<String, List<Long>> botHistory = new HashMap<>();
        int botAlerts = 0;
        int contentMajor = 0;

        for (WikiEvent e : events) {
            if (e.isBot()) {
                String user = e.getUser();
                long ts = e.getTimestamp();
                botHistory.putIfAbsent(user, new ArrayList<>());
                List<Long> history = botHistory.get(user);
                history.add(ts);
                
                // Count edits in last 60s
                long count = history.stream().filter(t -> t > (ts - 60)).count();
                if (count > 10) {
                    botAlerts++;
                }
            } else {
                int size = e.getSizeDelta();
                if (size > 1000) {
                    contentMajor++;
                }
            }
        }

        int expectedAlerts = botAlerts + contentMajor;

        System.out.println("Ground Truth Analysis (Refined) for: " + args[0]);
        System.out.println("----------------------------------------");
        System.out.println("Refined Bot Alerts (>10/min): " + botAlerts);
        System.out.println("Refined Content Major Alerts: " + contentMajor);
        System.out.println("Refined Total Expected Alerts: " + expectedAlerts);
        System.out.println("----------------------------------------");

        // Write to file for benchmark verification
        try (FileWriter writer = new FileWriter("ground_truth_summary.txt")) {
            writer.write("Ground Truth Summary\n");
            writer.write("--------------------\n");
            writer.write("Expected Alerts: " + expectedAlerts + "\n");
            writer.write("Refined Bots: " + botAlerts + "\n");
            writer.write("Refined Content Major: " + contentMajor + "\n");
        }
    }
}
