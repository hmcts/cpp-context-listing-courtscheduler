package uk.gov.moj.cpp.courtscheduler.domain;

public class SlotStartTime {
    private String sessionStartTime;
    private String sessionEndTime;
    private String hearingStartTime;
    private long count;

    public SlotStartTime() {
    }

    public SlotStartTime(final String sessionStartTime, final String sessionEndTime, final String hearingStartTime, final long count) {
        this.sessionStartTime = sessionStartTime;
        this.sessionEndTime = sessionEndTime;
        this.hearingStartTime = hearingStartTime;
        this.count = count;
    }

    public String getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(final String sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public String getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(final String sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    public long getCount() {
        return count;
    }

    public SlotStartTime setCount(final long count) {
        this.count = count;
        return this;
    }
}
