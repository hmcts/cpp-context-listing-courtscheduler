package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_AFTERNOON;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_MORNING;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import jakarta.json.JsonObject;

@Service
public class UpdateCourtScheduleConverter implements Converter<JsonObject, UpdateCourtSchedule> {
    private static final String CROWN = "CROWN";
    private static final String ADULT = "ADULT";

    @Override
    public UpdateCourtSchedule convert(final JsonObject jsonObject) {

        UpdateCourtSchedule.UpdateCourtScheduleBuilder courtScheduleBuilder = new UpdateCourtSchedule.UpdateCourtScheduleBuilder();
        String jurisdiction = jsonObject.getString("jurisdiction");
        
        courtScheduleBuilder
                .withCourtScheduleId(jsonObject.getString("courtScheduleId"))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .withBusinessType(jsonObject.getString("businessType"))
                .withSessionType(jsonObject.getString("courtSession"))
                .withJurisdiction(jurisdiction);

        // Handle panel based on jurisdiction
        // For CROWN: panel is optional, but if supplied must be ADULT
        // For MAGISTRATES: panel is mandatory (validation happens in validator)
        if (CROWN.equalsIgnoreCase(jurisdiction)) {
            if (jsonObject.containsKey("panel") && !jsonObject.isNull("panel")) {
                String panel = jsonObject.getString("panel");
                if (panel != null && !panel.trim().isEmpty() && !ADULT.equalsIgnoreCase(panel)) {
                    throw new ConverterException("For CROWN jurisdiction, panel must be ADULT if supplied");
                }
                // Only set panel if it's ADULT (optional for CROWN, so null/empty is fine)
                if (panel != null && !panel.trim().isEmpty() && ADULT.equalsIgnoreCase(panel)) {
                    courtScheduleBuilder.withPanel(panel);
                }
            }
            // If panel key doesn't exist or is null, don't set it (optional for CROWN)
        } else {
            // For MAGISTRATES, set panel as before (validation happens in validator)
            if (jsonObject.containsKey("panel") && !jsonObject.isNull("panel")) {
                courtScheduleBuilder.withPanel(jsonObject.getString("panel"));
            }
        }

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

        if (jsonObject.containsKey("isDraft")) {
            courtScheduleBuilder.withIsDraft(jsonObject.getBoolean("isDraft"));
        }

        return courtScheduleBuilder.build();
    }
}
