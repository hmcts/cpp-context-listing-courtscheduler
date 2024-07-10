package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import javax.json.JsonObject;

public class UpdateCourtScheduleConverter implements Converter<JsonObject, UpdateCourtSchedule> {
    @Override
    public UpdateCourtSchedule convert(final JsonObject jsonObject) {

        UpdateCourtSchedule.UpdateCourtScheduleBuilder courtScheduleBuilder = new UpdateCourtSchedule.UpdateCourtScheduleBuilder();
        courtScheduleBuilder
                .withCourtScheduleId(jsonObject.getString("courtScheduleId"))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .withBusinessType(jsonObject.getString("businessType"))
                .withSessionType(jsonObject.getString("courtSession"))
                .withPanel(jsonObject.getString("panel"));

        if (jsonObject.containsKey("maxSlots")) {
            courtScheduleBuilder.withMaxSlots(jsonObject.getInt("maxSlots"));
        }

        if (jsonObject.containsKey("maxDuration")) {
            courtScheduleBuilder.withMaxDuration(jsonObject.getInt("maxDuration"));
        }

        return courtScheduleBuilder.build();
    }
}
