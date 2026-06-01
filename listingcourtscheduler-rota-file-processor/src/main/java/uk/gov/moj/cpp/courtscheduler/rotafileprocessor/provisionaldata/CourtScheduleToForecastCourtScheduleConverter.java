package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class CourtScheduleToForecastCourtScheduleConverter {

    public CourtSchedule convertToProvisionalCourtSchedule(final CourtSchedule courtSchedule,
                                                           final LocalDate sessionDate,
                                                           final String courtScheduleId) {
        final CourtSchedule.CourtScheduleBuilder builder = new CourtSchedule.CourtScheduleBuilder();
        builder.withCourtSchedule(courtSchedule)
                .withListingProfileId(null)
                .withCourtScheduleId(courtScheduleId)
                .withSessionDate(sessionDate)
                .withJudiciaries(null);
        return builder.build();

    }
}
