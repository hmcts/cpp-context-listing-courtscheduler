package uk.gov.moj.cpp.courtscheduler.domain;

/**
 * Request for {@code courtscheduler.reserve-unconfirmed-hearing}
 * ({@code PUT /sessions/{sessionId}/hearings/{unconfirmedHearingId}}). {@code sessionId} and
 * {@code unconfirmedHearingId} come from the path, not the body.
 */
public class ReserveUnconfirmedHearingRequest {

    private String hearingStartTime;
    private boolean isSlotBased;
    private int duration;

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public ReserveUnconfirmedHearingRequest setHearingStartTime(final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
        return this;
    }

    public boolean isSlotBased() {
        return isSlotBased;
    }

    public ReserveUnconfirmedHearingRequest setSlotBased(final boolean slotBased) {
        isSlotBased = slotBased;
        return this;
    }

    public int getDuration() {
        return duration;
    }

    public ReserveUnconfirmedHearingRequest setDuration(final int duration) {
        this.duration = duration;
        return this;
    }
}
