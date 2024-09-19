package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;

public class CourtScheduleMatcherInfo {

    private String courtScheduleId;
    private String ouCode;
    private Date createdOn;

    public CourtScheduleMatcherInfo(final String courtScheduleId, final String ouCode, final Date createdOn) {
        this.createdOn = createdOn;
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

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final Date createdOn) {
        this.createdOn = createdOn;
    }
}
