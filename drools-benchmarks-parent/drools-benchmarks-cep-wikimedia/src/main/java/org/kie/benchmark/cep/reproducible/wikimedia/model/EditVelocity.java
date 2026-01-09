package org.kie.benchmark.cep.reproducible.wikimedia.model;

public class EditVelocity {

    private String pageTitle;
    private double rate;

    public EditVelocity() { }

    public EditVelocity(String pageTitle, double rate) {
        this.pageTitle = pageTitle;
        this.rate = rate;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }
}
