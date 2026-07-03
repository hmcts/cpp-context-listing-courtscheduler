package uk.gov.moj.cpp.courtscheduler.api.converter;



import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;


public class AllocatedSlotToHearingSlotSearchResponseConverter {

    private AllocatedSlotToHearingSlotSearchResponseConverter() {
    }

    public static HearingSlotSearchAndBookResponse convert(final AllocatedSlot allocatedSlot, String hearingId) {
        return new HearingSlotSearchAndBookResponse(hearingId, allocatedSlot.getCourtScheduleId(), allocatedSlot.getCourtRoomUUId(),
                DateUtils.toResponseDateStringWithoutMillis(allocatedSlot.getHearingStartTime()), allocatedSlot.getDuration(), allocatedSlot.getJudiciaries());
    }
}
