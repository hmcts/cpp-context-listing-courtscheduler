package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record HearingSlotSearchRequest(String hearingId,
                                       String courtCentreId,
                                       String hearingSessionDate,
                                       String courtRoomId,
                                       String hearingSessionDateSearchCutOff,
                                       String sessionStartTime,
                                       Integer durationInMinutes,
                                       Boolean isPolice,
                                       String businessType) {
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final HearingSlotSearchRequest that = (HearingSlotSearchRequest) o;
        return Objects.equals(hearingId(), that.hearingId()) && Objects.equals(courtCentreId(),
                that.courtCentreId()) && Objects.equals(hearingSessionDate(),
                that.hearingSessionDate()) && Objects.equals(courtRoomId(),
                that.courtRoomId()) && Objects.equals(hearingSessionDateSearchCutOff(),
                that.hearingSessionDateSearchCutOff()) && Objects.equals(sessionStartTime(),
                that.sessionStartTime()) && Objects.equals(durationInMinutes(),
                that.durationInMinutes()) && Objects.equals(isPolice(), that.isPolice())
                && Objects.equals(businessType(), that.businessType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId(), courtCentreId(), hearingSessionDate(), courtRoomId(),
                hearingSessionDateSearchCutOff(), sessionStartTime(), durationInMinutes(), isPolice(),
                businessType());
    }
}
