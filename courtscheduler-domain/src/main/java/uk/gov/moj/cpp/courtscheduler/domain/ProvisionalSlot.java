package uk.gov.moj.cpp.courtscheduler.domain;

public class ProvisionalSlot {

    private String courtScheduleId;

    private String hearingStartTime;

    public ProvisionalSlot() {
    }

    public ProvisionalSlot(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public ProvisionalSlot(final String courtScheduleId, final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
        this.courtScheduleId = courtScheduleId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public String getHearingStartTime() { return hearingStartTime; }
}

