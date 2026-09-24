package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.Instant;

public class AllocatedListingEachBooked {

    private final String courtScheduleId;
    private final Integer duration;
    private final Instant hearingStartTime;

    public AllocatedListingEachBooked(final String courtScheduleId, final Integer duration, final Instant hearingStartTime) {
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

    public Instant getHearingStartTime() {
        return hearingStartTime;
    }
}
