package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LEFT_WINGER;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.POSITION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.RIGHT_WINGER;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.TITLE;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.util.Calendar;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class JudiciaryBuilder {

    public CourtScheduleJudiciary build(final Map<String, String> schedule, final String courtScheduleId) {
        final String position = schedule.get(POSITION);
        final boolean isBenchChairman = isBenchChair(position);

        return new CourtScheduleJudiciary()
                .courtScheduleId(courtScheduleId)
                .courtListingProfileId(schedule.get(COURT_LISTING_PROFILE_ID))
                .judiciaryId(schedule.get(JUDICIARY_ID))
                .rotaJudiciaryId(schedule.get(ROTA_JUDICIARY_ID))
                .title(schedule.get(TITLE))
                .forenames(schedule.get(FORENAMES))
                .surname(schedule.get(SURNAME))
                .emailAddress(schedule.get(EMAIL_ADDRESS))
                .judiciaryType(schedule.get(JUDICIARY_TYPE))
                .position(schedule.get(POSITION))
                .benchChairman(isBenchChairman)
                .deputy(!isBenchChairman)
                .createdOn(DateUtils.toOffsetDateTime(Calendar.getInstance().getTime()))
                .updatedOn(DateUtils.toOffsetDateTime(Calendar.getInstance().getTime()))
                .active(true);
    }

    private boolean isBenchChair(final String postion) {
        return !(LEFT_WINGER.equals(postion) || RIGHT_WINGER.equals(postion));
    }
}
