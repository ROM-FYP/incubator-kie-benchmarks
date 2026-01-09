package org.kie.benchmark.cep.reproducible.wikimedia.model;

public class PageEditor {
    private final String pageTitle;
    private final String user;

    public PageEditor(String pageTitle, String user) {
        this.pageTitle = pageTitle;
        this.user = user;
    }

    public String getPageTitle() { return pageTitle; }
    public String getUser() { return user; }
}
