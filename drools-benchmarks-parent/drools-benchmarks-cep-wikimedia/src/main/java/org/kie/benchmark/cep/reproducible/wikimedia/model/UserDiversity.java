package org.kie.benchmark.cep.reproducible.wikimedia.model;

public class UserDiversity {
    private final String pageTitle;
    private int editors;

    public UserDiversity(String pageTitle, int editors) {
        this.pageTitle = pageTitle;
        this.editors = editors;
    }

    public String getPageTitle() { return pageTitle; }
    public int getEditors() { return editors; }
    public void setEditors(int editors) { this.editors = editors; }
}
