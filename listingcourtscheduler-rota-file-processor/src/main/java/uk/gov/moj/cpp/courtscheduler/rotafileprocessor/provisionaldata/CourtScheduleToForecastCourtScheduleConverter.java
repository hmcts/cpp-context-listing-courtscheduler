package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class CourtScheduleToForecastCourtScheduleConverter {

    // Generated models have no copy-constructor/builder — a full field-for-field copy via
    // Jackson (same type in, same type out) replaces the old CourtScheduleBuilder#withCourtSchedule
    // convenience, then the 4 fields below are overridden same as before.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public CourtSchedule convertToProvisionalCourtSchedule(final CourtSchedule courtSchedule,
                                                           final LocalDate sessionDate,
                                                           final String courtScheduleId) {
        final CourtSchedule copy = OBJECT_MAPPER.convertValue(courtSchedule, CourtSchedule.class);
        copy.setListingProfileId(null);
        copy.setCourtScheduleId(courtScheduleId);
        copy.setSessionDate(sessionDate);
        copy.setJudiciaries(null);
        return copy;
    }
}
