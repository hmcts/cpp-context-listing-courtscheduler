package uk.gov.moj.cpp.courtscheduler.api.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_AFTERNOON;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_MORNING;

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

        if (jsonObject.containsKey("allDaySplit")) {
            courtScheduleBuilder.withAllDaySplit(jsonObject.getBoolean("allDaySplit"));
        }

        if (jsonObject.containsKey(MAX_DURATION_FOR_MORNING.getLabel())) {
            courtScheduleBuilder.withMaxDurationForMorning(jsonObject.getInt(MAX_DURATION_FOR_MORNING.getLabel(), -1));
        }

        if (jsonObject.containsKey(MAX_DURATION_FOR_AFTERNOON.getLabel())) {
            courtScheduleBuilder.withMaxDurationForAfternoon(jsonObject.getInt(MAX_DURATION_FOR_AFTERNOON.getLabel(), -1));
        }

        if (jsonObject.containsKey("sessionStartTime")) {
            courtScheduleBuilder.withSessionStartTime(jsonObject.getString("sessionStartTime"));
        }

        if (jsonObject.containsKey("sessionEndTime")) {
            courtScheduleBuilder.withSessionEndTime(jsonObject.getString("sessionEndTime"));
        }

        if (jsonObject.containsKey("isOverbookingAllowed")) {
            courtScheduleBuilder.withIsOverbookingAllowed(jsonObject.getBoolean("isOverbookingAllowed"));
        }

        return courtScheduleBuilder.build();
    }
}
