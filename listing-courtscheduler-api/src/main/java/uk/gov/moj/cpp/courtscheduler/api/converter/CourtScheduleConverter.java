package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import javax.json.JsonObject;
import java.time.LocalDate;

public class CourtScheduleConverter implements Converter<JsonObject, CourtSchedule> {
    @Override
    public CourtSchedule convert(final JsonObject jsonObject) {

        CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new CourtSchedule.CourtScheduleBuilder();
        return courtScheduleBuilder
                .withPanel(jsonObject.getString("panel"))
                .withSessionDate(LocalDate.parse(jsonObject.getString("sessionDate")))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .withCourtHouseId(jsonObject.getString("courtHouseId"))
                .withBusinessType(jsonObject.getString("businessType"))
                .build();
    }
}
