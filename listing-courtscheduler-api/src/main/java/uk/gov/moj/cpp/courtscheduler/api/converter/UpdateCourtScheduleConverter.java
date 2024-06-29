package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import java.time.LocalDate;

import javax.json.JsonObject;

public class UpdateCourtScheduleConverter implements Converter<JsonObject, UpdateCourtSchedule> {
    @Override
    public UpdateCourtSchedule convert(final JsonObject jsonObject) {

        UpdateCourtSchedule.UpdateCourtScheduleBuilder courtScheduleBuilder = new UpdateCourtSchedule.UpdateCourtScheduleBuilder();
        courtScheduleBuilder
                .withCourtScheduleId(jsonObject.getString("courtScheduleId"))
                .withCourtHouseId(jsonObject.getString("courtHouseId"))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .withBusinessType(jsonObject.getString("businessType"))
                .withSessionType(jsonObject.getString("sessionType"))
                .withPanel(jsonObject.getString("panel"))
                .withSessionDate(LocalDate.parse(jsonObject.getString("sessionDate")));

        if (jsonObject.containsKey("availableSlots")) {
            courtScheduleBuilder.withAvailableSlots(jsonObject.getInt("availableSlots"));
        }

        if (jsonObject.containsKey("availableDuration")) {
            courtScheduleBuilder.withAvailableSlots(jsonObject.getInt("availableDuration"));
        }

        return courtScheduleBuilder.build();
    }
}
