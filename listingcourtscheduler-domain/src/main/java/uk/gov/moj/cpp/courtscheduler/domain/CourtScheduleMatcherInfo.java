package uk.gov.moj.cpp.courtscheduler.domain;

public class CourtScheduleMatcherInfo {

    private String courtScheduleId;
    private String ouCode;

    public CourtScheduleMatcherInfo(final String courtScheduleId, final String ouCode) {
        this.ouCode = ouCode;
        this.courtScheduleId = courtScheduleId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getOuCode() {
        return ouCode;
    }

    public void setOuCode(final String ouCode) {
        this.ouCode = ouCode;
    }
}
