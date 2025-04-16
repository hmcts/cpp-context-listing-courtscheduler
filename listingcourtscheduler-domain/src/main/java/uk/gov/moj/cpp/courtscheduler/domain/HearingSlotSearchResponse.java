package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record HearingSlotSearchResponse(String hearingId,
                                        String courtScheduleId,
                                        String courtRoomId,
                                        String sessionStartTime,
                                        Integer duration) {
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final HearingSlotSearchResponse that = (HearingSlotSearchResponse) o;
        return Objects.equals(hearingId(), that.hearingId()) && Objects.equals(courtScheduleId(),
                that.courtScheduleId()) && Objects.equals(courtRoomId(),
                that.courtRoomId()) && Objects.equals(sessionStartTime(),
                that.sessionStartTime()) && Objects.equals(duration(),
                that.duration());
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId(), courtScheduleId(), courtRoomId(), sessionStartTime(),
                duration());
    }
}
