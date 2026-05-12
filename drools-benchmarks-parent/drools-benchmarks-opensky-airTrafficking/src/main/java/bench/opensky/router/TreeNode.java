package bench.opensky.router;

/**
 * Recursive binary decision tree node.
 * <ul>
 *   <li><b>Decision node</b>: splits on {@code feature <= threshold} (left) vs {@code > threshold} (right).</li>
 *   <li><b>Leaf node</b>: holds a {@code prediction} (0 or 1).</li>
 * </ul>
 */
public class TreeNode {

    // Decision node fields
    private String feature;
    private double threshold;
    private TreeNode left;
    private TreeNode right;

    // Leaf node field
    private int prediction = -1; // -1 means "not a leaf"

    /** Create a decision (split) node. */
    public static TreeNode decision(String feature, double threshold, TreeNode left, TreeNode right) {
        TreeNode node = new TreeNode();
        node.feature = feature;
        node.threshold = threshold;
        node.left = left;
        node.right = right;
        return node;
    }

    /** Create a leaf node. */
    public static TreeNode leaf(int prediction) {
        TreeNode node = new TreeNode();
        node.prediction = prediction;
        return node;
    }

    public boolean isLeaf() {
        return prediction >= 0;
    }

    public String getFeature()    { return feature; }
    public double getThreshold()  { return threshold; }
    public TreeNode getLeft()     { return left; }
    public TreeNode getRight()    { return right; }
    public int getPrediction()    { return prediction; }

    @Override
    public String toString() {
        if (isLeaf()) return "Leaf(" + prediction + ")";
        return "Split(" + feature + " <= " + threshold + ")";
    }
}
