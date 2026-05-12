/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.benchmark.binance.parallel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an Infomap .ftree file and extracts a mapping from rule name
 * to the top-level module (cluster) ID.
 *
 * <p>Expected line format:
 * {@code <path> <flow> "<ruleName>" <nodeId>}
 * where path is like "1:2:3" and the top-level module is the first number.</p>
 */
public class FtreeParser {

    // Matches lines like: 1:2:3 0.123456 "RuleName" 42
    private static final Pattern NODE_LINE_PATTERN =
            Pattern.compile("^(\\d+(?::\\d+)*)\\s+[\\d.eE+-]+\\s+\"([^\"]+)\"\\s+\\d+");

    private FtreeParser() {}

    /**
     * Parses an .ftree file from an InputStream.
     *
     * @param inputStream the input stream of the .ftree file
     * @return map of rule name → top-level cluster ID
     * @throws IOException if the stream cannot be read
     */
    public static Map<String, Integer> parse(InputStream inputStream) throws IOException {
        Map<String, Integer> clusterMap = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip comments, empty lines, and *Links sections
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("*")) {
                    continue;
                }
                Matcher matcher = NODE_LINE_PATTERN.matcher(line);
                if (matcher.find()) {
                    String path = matcher.group(1);
                    String ruleName = matcher.group(2);

                    // Extract top-level module: first number before ':'
                    int topLevelCluster;
                    int colonIdx = path.indexOf(':');
                    if (colonIdx > 0) {
                        topLevelCluster = Integer.parseInt(path.substring(0, colonIdx));
                    } else {
                        topLevelCluster = Integer.parseInt(path);
                    }
                    clusterMap.put(ruleName, topLevelCluster);
                }
            }
        }
        return clusterMap;
    }

    /**
     * Parses an .ftree file from a file path.
     *
     * @param filePath path to the .ftree file
     * @return map of rule name → top-level cluster ID
     * @throws IOException if the file cannot be read
     */
    public static Map<String, Integer> parse(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            return parse(fis);
        }
    }
}
