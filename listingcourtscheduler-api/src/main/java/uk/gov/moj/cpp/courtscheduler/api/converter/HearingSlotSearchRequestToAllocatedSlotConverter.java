package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;

public class HearingSlotSearchRequestToAllocatedSlotConverter {

    private HearingSlotSearchRequestToAllocatedSlotConverter() {
    }
    public static AllocatedSlot convert(final HearingSlotSearchRequest hearingSlotSearchRequest) {
        AllocatedSlot allocatedSlot = new AllocatedSlot();
        allocatedSlot.setHearingId(hearingSlotSearchRequest.hearingId());
        allocatedSlot.setOuCode(hearingSlotSearchRequest.ouCode());
        allocatedSlot.setCourtRoomUUId(hearingSlotSearchRequest.courtRoomId());
        allocatedSlot.setSessionDate(hearingSlotSearchRequest.hearingSessionDate());
        allocatedSlot.setHearingStartTime(hearingSlotSearchRequest.sessionStartTime());
        allocatedSlot.setDuration(hearingSlotSearchRequest.durationInMinutes());
        allocatedSlot.setHearingSessionDateSearchCutOff(hearingSlotSearchRequest.hearingSessionDateSearchCutOff());

        return allocatedSlot;
    }
}
