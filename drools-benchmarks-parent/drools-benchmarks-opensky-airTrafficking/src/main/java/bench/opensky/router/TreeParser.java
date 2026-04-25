package bench.opensky.router;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an sklearn {@code export_text} formatted tree file into per-cluster {@link TreeNode} trees.
 *
 * <p>Expected format:
 * <pre>
 * --- cluster_1 ---
 * |--- attr_velocity <= 9.38
 * |   |--- class: 0
 * |--- attr_velocity >  9.38
 * |   |--- class: 1
 *
 * --- cluster_2 ---
 * |--- class: 0
 * </pre>
 */
public class TreeParser {

    private static final Pattern CLUSTER_HEADER = Pattern.compile("---\\s*(cluster_\\d+)\\s*---");
    private static final Pattern SPLIT_LINE     = Pattern.compile("\\|---\\s+(\\S+)\\s*(<=|>)\\s+([\\d.eE+\\-]+)");
    private static final Pattern LEAF_LINE      = Pattern.compile("\\|---\\s+class:\\s*(\\d+)");

    /**
     * Parse the tree rules file and return one tree per cluster.
     *
     * @param filePath path to tree_rules.txt
     * @return map of cluster name (e.g. "cluster_1") → root TreeNode
     */
    public Map<String, TreeNode> parse(String filePath) throws IOException {
        List<String> lines;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        Map<String, TreeNode> trees = new LinkedHashMap<>();
        String currentCluster = null;
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            Matcher headerMatch = CLUSTER_HEADER.matcher(line);
            if (headerMatch.find()) {
                // Flush previous cluster block
                if (currentCluster != null && !currentBlock.isEmpty()) {
                    trees.put(currentCluster, buildTree(currentBlock));
                }
                currentCluster = headerMatch.group(1);
                currentBlock = new ArrayList<>();
            } else if (currentCluster != null && line.contains("|---")) {
                currentBlock.add(line);
            }
        }
        // Flush last cluster
        if (currentCluster != null && !currentBlock.isEmpty()) {
            trees.put(currentCluster, buildTree(currentBlock));
        }

        return trees;
    }

    // -----------------------------------------------------------------------
    // Recursive tree builder
    // -----------------------------------------------------------------------

    private TreeNode buildTree(List<String> lines) {
        int[] pos = {0};
        return buildNode(lines, pos, 0);
    }

    private TreeNode buildNode(List<String> lines, int[] pos, int expectedDepth) {
        if (pos[0] >= lines.size()) return TreeNode.leaf(0);

        String line = lines.get(pos[0]);
        int depth = countDepth(line);

        if (depth != expectedDepth) return TreeNode.leaf(0);

        // Check for leaf
        Matcher leafMatch = LEAF_LINE.matcher(line);
        if (leafMatch.find()) {
            pos[0]++;
            return TreeNode.leaf(Integer.parseInt(leafMatch.group(1)));
        }

        // Must be a split
        Matcher splitMatch = SPLIT_LINE.matcher(line);
        if (!splitMatch.find()) {
            pos[0]++;
            return TreeNode.leaf(0);
        }

        String feature = splitMatch.group(1);
        String operator = splitMatch.group(2);
        double threshold = Double.parseDouble(splitMatch.group(3));

        pos[0]++;

        if ("<=".equals(operator)) {
            // This is the left branch; parse left child, then expect a `>` sibling
            TreeNode leftChild = buildNode(lines, pos, expectedDepth + 1);

            // Now read the `>` sibling at the same depth
            TreeNode rightChild = TreeNode.leaf(0);
            if (pos[0] < lines.size()) {
                int siblingDepth = countDepth(lines.get(pos[0]));
                if (siblingDepth == expectedDepth) {
                    pos[0]++; // skip the `> threshold` line
                    rightChild = buildNode(lines, pos, expectedDepth + 1);
                }
            }

            return TreeNode.decision(feature, threshold, leftChild, rightChild);
        } else {
            // `>` encountered first (shouldn't happen in well-formed sklearn output, treat as right-only)
            TreeNode rightChild = buildNode(lines, pos, expectedDepth + 1);
            return TreeNode.decision(feature, threshold, TreeNode.leaf(0), rightChild);
        }
    }

    /**
     * Count tree depth by counting the number of "|   " indentation segments.
     */
    private int countDepth(String line) {
        int depth = 0;
        int i = 0;
        while (i < line.length()) {
            if (line.charAt(i) == '|') {
                // Check if this is part of "|   " (indent) vs "|---" (node marker)
                if (i + 3 < line.length() && line.charAt(i + 1) == '-' && line.charAt(i + 2) == '-' && line.charAt(i + 3) == '-') {
                    break; // this is the node marker at current depth
                }
                depth++;
                i += 4; // skip "|   "
            } else if (line.charAt(i) == ' ') {
                i++;
            } else {
                break;
            }
        }
        return depth;
    }
}
