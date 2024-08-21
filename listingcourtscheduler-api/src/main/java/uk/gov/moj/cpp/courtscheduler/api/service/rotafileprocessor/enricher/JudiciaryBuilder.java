package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.LEFT_WINGER;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.POSITION;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.RIGHT_WINGER;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JudiciaryBuilder {

    public CourtScheduleJudiciary build(final Map<String, String> schedule, final String courtScheduleId) {
        final String position = schedule.get(POSITION);
        final boolean isBenchChairman = isBenchChair(position);

        return judiciary()
                .withCourtScheduleId(courtScheduleId)
                .withCourtListingProfileId(schedule.get(COURT_LISTING_PROFILE_ID))
                .withJudiciaryId(schedule.get(JUDICIARY_ID))
                .withRotaJudiciaryId(schedule.get(ROTA_JUDICIARY_ID))
                .withTitle(schedule.get(TITLE))
                .withForenames(schedule.get(FORENAMES))
                .withSurname(schedule.get(SURNAME))
                .withEmailAddress(schedule.get(EMAIL_ADDRESS))
                .withJudiciaryType(schedule.get(JUDICIARY_TYPE))
                .withPosition(schedule.get(POSITION))
                .withIsBenchChairman(isBenchChairman)
                .withIsDeputy(!isBenchChairman)
                .withCreatedOn(Calendar.getInstance().getTime())
                .withUpdatedOn(Calendar.getInstance().getTime())
                .build();
    }

    private boolean isBenchChair(final String postion) {
        return !(LEFT_WINGER.equals(postion) || RIGHT_WINGER.equals(postion));
    }
}
