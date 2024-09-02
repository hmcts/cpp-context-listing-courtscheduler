package uk.gov.moj.cpp.courtscheduler.domain.rota;

public enum PanelTypes {

    ADULT("ADULT"),
    YOUTH("YOUTH");

    private final String name;

    PanelTypes(String name) {
        this.name = name;
    }
}
