package uk.gov.moj.cpp.courtscheduler.common;

import java.util.Locale;

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
        return Jurisdiction.valueOf(jurisdictionType.toUpperCase(Locale.ROOT));
    }

    public boolean equalsIgnoreCase(final String other) {
        return this.jurisdictionType.equalsIgnoreCase(other);
    }
}