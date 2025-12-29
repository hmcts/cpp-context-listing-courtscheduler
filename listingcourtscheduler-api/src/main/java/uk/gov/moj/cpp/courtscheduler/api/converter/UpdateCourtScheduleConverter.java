package uk.gov.moj.cpp.courtscheduler.api.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_AFTERNOON;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_MORNING;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import javax.json.JsonObject;

public class UpdateCourtScheduleConverter implements Converter<JsonObject, UpdateCourtSchedule> {
    private static final String CROWN = "CROWN";
    private static final String ADULT = "ADULT";
    private static final String PANEL = "panel";

    @Override
    public UpdateCourtSchedule convert(final JsonObject jsonObject) {
        UpdateCourtSchedule.UpdateCourtScheduleBuilder courtScheduleBuilder = new UpdateCourtSchedule.UpdateCourtScheduleBuilder();
        String jurisdiction = jsonObject.getString("jurisdiction");
        
        setRequiredFields(courtScheduleBuilder, jsonObject, jurisdiction);
        setPanelField(courtScheduleBuilder, jsonObject, jurisdiction);
        setOptionalFields(courtScheduleBuilder, jsonObject);

        return courtScheduleBuilder.build();
    }

    private void setRequiredFields(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject, String jurisdiction) {
        builder
                .withCourtScheduleId(jsonObject.getString("courtScheduleId"))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .withBusinessType(jsonObject.getString("businessType"))
                .withSessionType(jsonObject.getString("courtSession"))
                .withJurisdiction(jurisdiction);
    }

    private void setPanelField(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject, String jurisdiction) {
        if (!hasPanel(jsonObject)) {
            return;
        }

        if (CROWN.equalsIgnoreCase(jurisdiction)) {
            setPanelForCrown(builder, jsonObject);
        } else {
            setPanelForMagistrates(builder, jsonObject);
        }
    }

    private boolean hasPanel(JsonObject jsonObject) {
        return jsonObject.containsKey(PANEL) && !jsonObject.isNull(PANEL);
    }

    private void setPanelForCrown(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject) {
        String panel = jsonObject.getString(PANEL);
        if (isInvalidCrownPanel(panel)) {
            throw new ConverterException("For CROWN jurisdiction, panel must be ADULT if supplied");
        }
        if (isValidAdultPanel(panel)) {
            builder.withPanel(panel);
        }
    }

    private boolean isInvalidCrownPanel(String panel) {
        return panel != null && !panel.trim().isEmpty() && !ADULT.equalsIgnoreCase(panel);
    }

    private boolean isValidAdultPanel(String panel) {
        return panel != null && !panel.trim().isEmpty() && ADULT.equalsIgnoreCase(panel);
    }

    private void setPanelForMagistrates(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject) {
        builder.withPanel(jsonObject.getString(PANEL));
    }

    private void setOptionalFields(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject) {
        setIntFieldIfPresent(builder, jsonObject, "maxSlots", builder::withMaxSlots);
        setIntFieldIfPresent(builder, jsonObject, "maxDuration", builder::withMaxDuration);
        setBooleanFieldIfPresent(builder, jsonObject, "allDaySplit", builder::withAllDaySplit);
        setDurationFieldIfPresent(builder, jsonObject, MAX_DURATION_FOR_MORNING.getLabel(), builder::withMaxDurationForMorning);
        setDurationFieldIfPresent(builder, jsonObject, MAX_DURATION_FOR_AFTERNOON.getLabel(), builder::withMaxDurationForAfternoon);
        setStringFieldIfPresent(builder, jsonObject, "sessionStartTime", builder::withSessionStartTime);
        setStringFieldIfPresent(builder, jsonObject, "sessionEndTime", builder::withSessionEndTime);
        setBooleanFieldIfPresent(builder, jsonObject, "isOverbookingAllowed", builder::withIsOverbookingAllowed);
        setBooleanFieldIfPresent(builder, jsonObject, "isDraft", builder::withIsDraft);
    }

    private void setIntFieldIfPresent(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject, 
                                     String key, java.util.function.IntConsumer setter) {
        if (jsonObject.containsKey(key)) {
            setter.accept(jsonObject.getInt(key));
        }
    }

    private void setDurationFieldIfPresent(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject, 
                                          String key, java.util.function.IntConsumer setter) {
        if (jsonObject.containsKey(key)) {
            setter.accept(jsonObject.getInt(key, -1));
        }
    }

    private void setStringFieldIfPresent(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject, 
                                        String key, java.util.function.Consumer<String> setter) {
        if (jsonObject.containsKey(key)) {
            setter.accept(jsonObject.getString(key));
        }
    }

    private void setBooleanFieldIfPresent(UpdateCourtSchedule.UpdateCourtScheduleBuilder builder, JsonObject jsonObject, 
                                         String key, java.util.function.Consumer<Boolean> setter) {
        if (jsonObject.containsKey(key)) {
            setter.accept(jsonObject.getBoolean(key));
        }
    }
}
