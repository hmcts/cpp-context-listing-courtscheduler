package uk.gov.moj.cpp.courtscheduler.converter;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import javax.json.JsonObject;
import java.time.LocalDate;

public class UpdateCourtScheduleConverter implements Converter<JsonObject, UpdateCourtSchedule> {
    @Override
    public UpdateCourtSchedule convert(final JsonObject jsonObject) {

        UpdateCourtSchedule.UpdateCourtScheduleBuilder courtScheduleBuilder = new UpdateCourtSchedule.UpdateCourtScheduleBuilder();
        return courtScheduleBuilder
                .withCourtScheduleId(jsonObject.getString("courtScheduleId"))
                .withCourtHouseId(jsonObject.getString("courtHouseId"))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .withBusinessType(jsonObject.getString("businessType"))
                .withSessionType(jsonObject.getString("sessionType"))
                .withPanel(jsonObject.getString("panel"))
                .withSessionDate(LocalDate.parse(jsonObject.getString("sessionDate")))
                .build();
    }
}
