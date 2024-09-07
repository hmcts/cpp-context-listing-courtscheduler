package uk.gov.moj.cpp.courtscheduler.domain;

public class SlotStartTime {
    private String hearingStartTime;
    private long count;

    public SlotStartTime() {
    }

    public SlotStartTime(final String hearingStartTime, final long count) {
        this.hearingStartTime = hearingStartTime;
        this.count = count;
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
