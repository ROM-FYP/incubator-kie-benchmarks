package bench.opensky.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Routes facts to clusters by evaluating per-cluster decision trees.
 *
 * <p>Usage:
 * <pre>
 *   Router router = new Router("path/to/tree_rules.txt");
 *   Set&lt;String&gt; clusters = router.route(stateVector);
 * </pre>
 */
public class Router {

    private static final Logger LOG = LoggerFactory.getLogger(Router.class);

    private final Map<String, TreeNode> trees;
    private final FeatureExtractor extractor = new FeatureExtractor();

    /**
     * Construct a router by parsing the tree rules file.
     *
     * @param treeRulesFile path to tree_rules.txt
     */
    public Router(String treeRulesFile) throws IOException {
        TreeParser parser = new TreeParser();
        this.trees = parser.parse(treeRulesFile);
        LOG.info("Router loaded {} cluster trees from {}", trees.size(), treeRulesFile);
    }

    /**
     * Evaluate all cluster trees against the given fact.
     *
     * @param fact the fact object (e.g. OpenSkyStateVector)
     * @return set of cluster names where prediction = 1 (multi-label)
     */
    public Set<String> route(Object fact) {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, TreeNode> entry : trees.entrySet()) {
            int prediction = evaluate(entry.getValue(), fact);
            if (prediction == 1) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * Walk the decision tree and return the leaf prediction.
     */
    private int evaluate(TreeNode node, Object fact) {
        if (node.isLeaf()) {
            return node.getPrediction();
        }

        double value = extractor.extract(fact, node.getFeature());

        if (value <= node.getThreshold()) {
            return evaluate(node.getLeft(), fact);
        } else {
            return evaluate(node.getRight(), fact);
        }
    }

    /** Get loaded cluster names (useful for SessionManager initialization). */
    public Set<String> getClusterNames() {
        return Collections.unmodifiableSet(trees.keySet());
    }
}
