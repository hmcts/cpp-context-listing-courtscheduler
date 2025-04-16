package uk.gov.moj.cpp.courtscheduler.api.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toIsoString;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HearingSlotSearchResponseConverter {

    private HearingSlotSearchResponseConverter() {
    }
    public static HearingSlotSearchResponse convert(final CourtSchedule courtSchedule, final String hearingId) {
        return new HearingSlotSearchResponse(hearingId, courtSchedule.getCourtScheduleId(),
                courtSchedule.getCourtRoomId(), toIsoString(courtSchedule.getSessionStartTime()), courtSchedule.getAvailableDuration());
    }
}
