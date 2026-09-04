package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record ReserveUnconfirmedHearingResponse(String courtScheduleId,
                                                 String hearingId,
                                                 String expiresAt,
                                                 boolean isSlotBased,
                                                 int duration,
                                                 String hearingStartTime,
                                                 String source) {

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ReserveUnconfirmedHearingResponse that = (ReserveUnconfirmedHearingResponse) o;
        return isSlotBased == that.isSlotBased
                && duration == that.duration
                && Objects.equals(courtScheduleId, that.courtScheduleId)
                && Objects.equals(hearingId, that.hearingId)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(hearingStartTime, that.hearingStartTime)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId, hearingId, expiresAt, isSlotBased, duration, hearingStartTime, source);
    }
}
