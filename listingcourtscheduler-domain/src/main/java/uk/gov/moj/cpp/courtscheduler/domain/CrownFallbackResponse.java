package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record CrownFallbackResponse(String hearingId,
                                    String courtScheduleId,
                                    String courtRoomId,
                                    String sessionDate,
                                    String sessionStartTime,
                                    String sessionEndTime,
                                    Integer durationInMinutes,
                                    Boolean isDraft,
                                    String businessType,
                                    String source,
                                    Boolean overbooked) {

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CrownFallbackResponse that = (CrownFallbackResponse) o;
        return Objects.equals(hearingId, that.hearingId)
                && Objects.equals(courtScheduleId, that.courtScheduleId)
                && Objects.equals(courtRoomId, that.courtRoomId)
                && Objects.equals(sessionDate, that.sessionDate)
                && Objects.equals(sessionStartTime, that.sessionStartTime)
                && Objects.equals(sessionEndTime, that.sessionEndTime)
                && Objects.equals(durationInMinutes, that.durationInMinutes)
                && Objects.equals(isDraft, that.isDraft)
                && Objects.equals(businessType, that.businessType)
                && Objects.equals(source, that.source)
                && Objects.equals(overbooked, that.overbooked);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId, courtScheduleId, courtRoomId, sessionDate, sessionStartTime,
                sessionEndTime, durationInMinutes, isDraft, businessType, source, overbooked);
    }
}
