package uk.gov.moj.cpp.courtscheduler.api.converter;

import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;

import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse;

/**
 * Base converter class for judiciary availability rule responses.
 * Contains common response conversion logic for converting domain objects to JSON.
 */
public abstract class BaseJudiciaryAvailabilityRuleResponseConverter {

    protected static final String DAY = "day";
    protected static final String INDEX = "index";
    protected static final String START_DATE = "startDate";
    protected static final String END_DATE = "endDate";
    protected static final String REASON = "reason";

    /**
     * Converts list of repeat days to JSON array.
     * Supports both string format (when index is null) and object format (when index is present).
     */
    protected JsonArray convertRepeatDaysToJson(List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        JsonArrayBuilder repeatDaysArrayBuilder = Json.createArrayBuilder();
        if (repeatDays != null) {
            for (JudiciaryAvailabilityRuleRepeatDay repeatDay : repeatDays) {
                if (repeatDay.getIndex() != null) {
                    // Object format with index
                    repeatDaysArrayBuilder.add(Json.createObjectBuilder()
                            .add(DAY, repeatDay.getDayOfWeek().name())
                            .add(INDEX, repeatDay.getIndex())
                            .build());
                } else {
                    // Simple string format
                    repeatDaysArrayBuilder.add(repeatDay.getDayOfWeek().name());
                }
            }
        }
        return repeatDaysArrayBuilder.build();
    }

    /**
     * Converts list of unavailabilities to JSON array.
     */
    protected JsonArray convertUnavailabilitiesToJson(List<JudiciaryUnavailabilityResponse> unavailabilities) {
        JsonArrayBuilder unavailabilitiesArrayBuilder = Json.createArrayBuilder();
        if (unavailabilities != null) {
            for (JudiciaryUnavailabilityResponse unavailability : unavailabilities) {
                JsonObjectBuilder unavailabilityBuilder = Json.createObjectBuilder()
                        .add(START_DATE, unavailability.getStartDate().toString())
                        .add(END_DATE, unavailability.getEndDate().toString());
                
                if (unavailability.getReason() != null) {
                    unavailabilityBuilder.add(REASON, unavailability.getReason().name());
                }
                
                unavailabilitiesArrayBuilder.add(unavailabilityBuilder.build());
            }
        }
        return unavailabilitiesArrayBuilder.build();
    }

    /**
     * Adds optional enum field to JSON object builder if value is not null.
     */
    protected void addOptionalEnumField(JsonObjectBuilder builder, String fieldName, Enum<?> value) {
        if (value != null) {
            builder.add(fieldName, value.name());
        }
    }
}
