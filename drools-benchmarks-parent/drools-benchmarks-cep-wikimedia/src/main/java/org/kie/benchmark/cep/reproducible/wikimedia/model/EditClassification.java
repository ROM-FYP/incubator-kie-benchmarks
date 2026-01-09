package org.kie.benchmark.cep.reproducible.wikimedia.model;

public class EditClassification {
    private final String pageTitle;
    private final String type;
    private final int weight;

    public EditClassification(String pageTitle, String type, int weight) {
        this.pageTitle = pageTitle;
        this.type = type;
        this.weight = weight;
    }

    public String getPageTitle() { return pageTitle; }
    public String getType() { return type; }
    public int getWeight() { return weight; }
}
