package uk.gov.moj.cpp.courtscheduler.domain;

public enum SessionTimeEnum {
    AM(10),
    PM(14),
    AD(10);

    private final int defaultStartTime;

    SessionTimeEnum(int defaultStartTime) {
        this.defaultStartTime = defaultStartTime;
    }

    public int getDefaultStartTime() {
        return defaultStartTime;
    }

    public static SessionTimeEnum fromName(final String name) {
        return SessionTimeEnum.valueOf(name);
    }
}

