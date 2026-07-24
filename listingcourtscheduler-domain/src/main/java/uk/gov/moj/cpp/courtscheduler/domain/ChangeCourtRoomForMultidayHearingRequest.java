package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

/**
 * Request for {@code courtscheduler.change-court-room-for-multiday-hearing}.
 *
 * <p>Re-allocates ONLY the submitted days of an existing multi-day hearing onto the given
 * sessions; days not submitted are left untouched. Validate-all-first: any invalid day fails the
 * whole request (422) with zero mutations — not even the valid days are released or booked.</p>
 */
public class ChangeCourtRoomForMultidayHearingRequest {

    private String hearingId;
    private List<RequestedDay> days;

    public String getHearingId() {
        return hearingId;
    }

    public ChangeCourtRoomForMultidayHearingRequest setHearingId(final String hearingId) {
        this.hearingId = hearingId;
        return this;
    }

    public List<RequestedDay> getDays() {
        return days;
    }

    public ChangeCourtRoomForMultidayHearingRequest setDays(final List<RequestedDay> days) {
        this.days = days;
        return this;
    }
}
