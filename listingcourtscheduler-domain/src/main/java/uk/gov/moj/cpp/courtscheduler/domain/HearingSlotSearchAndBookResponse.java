package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public record HearingSlotSearchAndBookResponse(String hearingId,
                                               String courtScheduleId,
                                               String courtRoomId,
                                               String hearingStartTime,
                                               Integer duration,
                                               List<CourtScheduleJudiciary> judiciaries) {
    public HearingSlotSearchAndBookResponse() {
        this(null, null, null, null, null, null);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final HearingSlotSearchAndBookResponse that = (HearingSlotSearchAndBookResponse) o;
        return Objects.equals(hearingId(), that.hearingId()) && Objects.equals(courtScheduleId(),
                that.courtScheduleId()) && Objects.equals(courtRoomId(),
                that.courtRoomId()) && Objects.equals(hearingStartTime(),
                that.hearingStartTime()) && Objects.equals(duration(),
                that.duration()) && Objects.equals(judiciaries(), that.judiciaries());
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId(), courtScheduleId(), courtRoomId(), hearingStartTime(),
                duration(), judiciaries());
    }
}
