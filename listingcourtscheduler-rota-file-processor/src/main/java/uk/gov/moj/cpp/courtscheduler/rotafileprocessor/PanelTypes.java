package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

public enum PanelTypes {

    ADULT("ADULT"),
    YOUTH("YOUTH");

    private final String name;

    PanelTypes(final String name) {
        this.name = name;
    }
}
