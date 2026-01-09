package org.kie.benchmark.cep.reproducible.wikimedia.model;

public class ViralTopicAlert {
    private final String pageTitle;
    private final String type;
    private final double score;

    public ViralTopicAlert(String pageTitle, String type, double score) {
        this.pageTitle = pageTitle;
        this.type = type;
        this.score = score;
    }

    public String getPageTitle() { return pageTitle; }
    public String getType() { return type; }
    public double getScore() { return score; }
}
