package uk.gov.moj.cpp.courtscheduler.domain;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize"})
public class IneligibleSession {

    private String courtScheduleId;
    private String reason;

    public IneligibleSession() {
    }

    public IneligibleSession(final String courtScheduleId, final String reason) {
        this.courtScheduleId = courtScheduleId;
        this.reason = reason;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(final String reason) {
        this.reason = reason;
    }
}


