package uk.gov.moj.cpp.courtscheduler.api.converter;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse;

/**
 * Base converter class for judiciary availability rule responses.
 * Contains common response conversion logic for converting domain objects to JSON.
 */
public abstract class BaseJudiciaryAvailabilityRuleResponseConverter {

    protected static final String START_DATE = "startDate";
    protected static final String END_DATE = "endDate";
    protected static final String REASON = "reason";

    /**
     * Converts list of repeat days (AvailabilityDayOfWeek enum) to JSON array of strings.
     */
    protected JsonArray convertRepeatDaysToJson(List<AvailabilityDayOfWeek> repeatDays) {
        JsonArrayBuilder repeatDaysArrayBuilder = Json.createArrayBuilder();
        if (repeatDays != null) {
            for (AvailabilityDayOfWeek repeatDay : repeatDays) {
                repeatDaysArrayBuilder.add(repeatDay.name());
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

    /**
     * Converts a single JudiciaryAvailabilityRuleResponse to JSON object.
     * This method is shared by both Find and Get converters.
     */
    protected JsonObject convertRule(final JudiciaryAvailabilityRuleResponse rule) {
        JsonObjectBuilder ruleBuilder = Json.createObjectBuilder()
                .add("id", rule.getId())
                .add("judiciaryId", rule.getJudiciaryId())
                .add("courtHouseId", rule.getCourtHouseId())
                .add(START_DATE, rule.getStartDate().toString())
                .add(END_DATE, rule.getEndDate().toString())
                .add("repeatDays", convertRepeatDaysToJson(rule.getRepeatDays()))
                .add("unavailabilities", convertUnavailabilitiesToJson(rule.getUnavailabilities()));

        addOptionalEnumField(ruleBuilder, "sessionType", rule.getSessionType());

        return ruleBuilder.build();
    }
}
