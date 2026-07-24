package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;

public class AllocatedListingEachBooked {

    private String courtScheduleId;
    private Integer duration;
    private Date hearingStartTime;

    public AllocatedListingEachBooked(final String courtScheduleId, final Integer duration, final Date hearingStartTime) {
        this.courtScheduleId = courtScheduleId;
        this.duration = duration;
        this.hearingStartTime = hearingStartTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public Date getHearingStartTime() {
        return hearingStartTime;
    }
}
