package uk.gov.moj.cpp.courtscheduler.common;

public enum Jurisdiction {
    MAGISTRATES("MAGISTRATES"),
    CROWN("CROWN");

    private final String jurisdictionType;

    Jurisdiction(final String jurisdictionType) {
        this.jurisdictionType = jurisdictionType;
    }

    public String getJurisdiction() {
        return this.jurisdictionType;
    }

    public static Jurisdiction fromString(final String jurisdictionType) {
        return Jurisdiction.valueOf(jurisdictionType.toUpperCase());
    }

    public boolean equalsIgnoreCase(final String other) {
        return this.jurisdictionType.equalsIgnoreCase(other);
    }
}