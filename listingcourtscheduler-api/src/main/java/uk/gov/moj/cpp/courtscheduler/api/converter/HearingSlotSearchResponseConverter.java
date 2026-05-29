package uk.gov.moj.cpp.courtscheduler.api.converter;



import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;


public class HearingSlotSearchResponseConverter {

    private HearingSlotSearchResponseConverter() {
    }

    public static HearingSlotSearchAndBookResponse convert(final CourtSchedule courtSchedule, final String hearingId) {
        return new HearingSlotSearchAndBookResponse(hearingId, courtSchedule.getCourtScheduleId(),
                courtSchedule.getCourtRoomId(), toIsoString(courtSchedule.getSessionStartTime()),
                courtSchedule.getAvailableDuration(), courtSchedule.getJudiciaries());
    }
}
