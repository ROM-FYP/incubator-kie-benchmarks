package org.kie.benchmark.cep.wikimedia;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kie.benchmark.cep.wikimedia.model.WikiEvent;

import java.io.*;
import java.util.*;

/**
 * Utility to merge multiple event JSON files and sort by timestamp.
 */
public class EventMerger {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java EventMerger <output_file> <input_file1> <input_file2> ...");
            return;
        }

        String outputFile = args[0];
        List<WikiEvent> allEvents = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        // Read all input files
        for (int i = 1; i < args.length; i++) {
            String inputFile = args[i];
            System.out.println("Reading: " + inputFile);
            
            try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        WikiEvent event = mapper.readValue(line, WikiEvent.class);
                        allEvents.add(event);
                        lineCount++;
                    } catch (Exception e) {
                        System.err.println("Error parsing line in " + inputFile + ": " + e.getMessage());
                    }
                }
                System.out.println("  Loaded " + lineCount + " events");
            }
        }

        // Sort by timestamp
        System.out.println("Sorting " + allEvents.size() + " events by timestamp...");
        allEvents.sort(Comparator.comparingLong(WikiEvent::getTimestamp));

        // Write merged output
        System.out.println("Writing to: " + outputFile);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (WikiEvent event : allEvents) {
                writer.write(mapper.writeValueAsString(event));
                writer.newLine();
            }
        }

        System.out.println("Merge complete! Total events: " + allEvents.size());
    }
}
