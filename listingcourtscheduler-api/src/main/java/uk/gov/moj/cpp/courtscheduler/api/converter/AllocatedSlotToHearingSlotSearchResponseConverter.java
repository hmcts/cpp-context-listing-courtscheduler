package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchResponse;

public class AllocatedSlotToHearingSlotSearchResponseConverter {

    private AllocatedSlotToHearingSlotSearchResponseConverter() {
    }
    public static HearingSlotSearchResponse convert(final AllocatedSlot allocatedSlot, String hearingId) {
        return new HearingSlotSearchResponse(hearingId, allocatedSlot.getCourtScheduleId(), allocatedSlot.getCourtRoomUUId(),
                allocatedSlot.getHearingStartTime(), allocatedSlot.getDuration());
    }
}
