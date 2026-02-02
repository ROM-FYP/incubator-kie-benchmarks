package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

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
                    if (errCount <= 5) {
                        System.err.println("Error at line " + lineNum + ": " + e.getMessage());
                        System.err.println("Line content: " + (line.length() > 100 ? line.substring(0, 100) + "..." : line));
                    }
                }
            }
        }

        if (errCount > 0) {
            System.err.println("Total errors encountered: " + errCount);
        }

        int bots = 0;
        int vandalismCandidates = 0;
        int contentCandidates = 0;
        int contentMajor = 0;
        int minorCandidates = 0;
        int ignored = 0;

        for (WikiEvent e : events) {
            if (e.isBot()) {
                bots++;
            } else {
                int size = e.getSizeDelta();
                if (size < -100) {
                    vandalismCandidates++;
                } else if (size > 200) {
                    contentCandidates++;
                    if (size > 1000) contentMajor++;
                } else if (size >= -50 && size <= 50) {
                    minorCandidates++;
                } else {
                    ignored++;
                }
            }
        }

        System.out.println("Ground Truth Analysis for: " + args[0]);
        System.out.println("Total Successfully Parsed Events: " + events.size());
        System.out.println("----------------------------------------");
        System.out.println("Bots: " + bots + " (Expected Alerts: " + bots + ")");
        System.out.println("Minors: " + minorCandidates + " (Expected Alerts: " + minorCandidates + ")");
        System.out.println("Content (>200): " + contentCandidates);
        System.out.println("  - Major (>1000): " + contentMajor + " (Expected Alerts: " + (contentMajor * 3) + ")");
        System.out.println("Vandalism (<-100): " + vandalismCandidates + " (Expected Alerts: Potential Log)");
        System.out.println("Ignored/Noise: " + ignored);
        System.out.println("----------------------------------------");
        
        int minExpected = bots + minorCandidates + (contentMajor * 3);
        System.out.println("Calculated Theoretical Alerts: " + minExpected + " (Bot + Minor + 3x ContentMajor)");
    }
}
