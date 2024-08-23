package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;

public class CourtScheduleMatcherInfo {

    private String courtScheduleId;
    private Date createdOn;

    public CourtScheduleMatcherInfo(final String courtScheduleId, final Date createdOn) {
        this.createdOn = createdOn;
        this.courtScheduleId = courtScheduleId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final Date createdOn) {
        this.createdOn = createdOn;
    }
}
