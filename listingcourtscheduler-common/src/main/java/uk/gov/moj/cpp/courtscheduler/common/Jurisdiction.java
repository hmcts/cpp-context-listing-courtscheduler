package uk.gov.moj.cpp.courtscheduler.common;

public enum Jurisdiction {
    MAGISTRATES("MAGISTRATES"),
    CROWN("CROWN");

    private final String jurisdictionType;

    Jurisdiction(String jurisdictionType) {
        this.jurisdictionType = jurisdictionType;
    }

    public String getJurisdiction() {
        return this.jurisdictionType;
    }

    public static Jurisdiction fromString(String jurisdictionType) {
        return Jurisdiction.valueOf(jurisdictionType.toUpperCase());
    }

    public boolean equalsIgnoreCase(String other) {
        return this.jurisdictionType.equalsIgnoreCase(other);
    }
}