package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class FindJudiciaryAvailabilityRuleResponseConverter implements Converter<FindJudiciaryAvailabilityRuleResponse, JsonObject> {

    private final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.Judiciary> judiciaryConverter;
    private final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism> specialismsConverter;

    public FindJudiciaryAvailabilityRuleResponseConverter() {
        this.judiciaryConverter = new ListToJsonArrayConverter<>();
        this.specialismsConverter = new ListToJsonArrayConverter<>();
    }

    @Override
    public JsonObject convert(final FindJudiciaryAvailabilityRuleResponse response) {
        final JsonArrayBuilder rulesArrayBuilder = Json.createArrayBuilder();
        if (response.getRules() != null) {
            for (JudiciaryAvailabilityRuleResponse rule : response.getRules()) {
                rulesArrayBuilder.add(convertRule(rule));
            }
        }

        // Always include judiciaries node (empty array if not requested or no results)
        final JsonArray judiciariesArray = response.getJudiciaries() != null 
                ? judiciaryConverter.convert(response.getJudiciaries())
                : Json.createArrayBuilder().build();

        // Always include specialisms node (empty array if not requested or no results)
        // The specialisms are already grouped by judiciaryId from the reference data service
        final JsonArray specialismsArray = response.getSpecialisms() != null 
                ? specialismsConverter.convert(response.getSpecialisms())
                : Json.createArrayBuilder().build();

        return Json.createObjectBuilder()
                .add("rules", rulesArrayBuilder.build())
                .add("totalCount", response.getTotalCount())
                .add("pageNumber", response.getPageNumber())
                .add("pageSize", response.getPageSize())
                .add("judiciaries", judiciariesArray)
                .add("specialisms", specialismsArray)
                .build();
    }

    private JsonObject convertRule(final JudiciaryAvailabilityRuleResponse rule) {
        final JsonObjectBuilder ruleBuilder = Json.createObjectBuilder()
                .add("id", rule.getId())
                .add("judiciaryId", rule.getJudiciaryId())
                .add("courtHouseId", rule.getCourtHouseId())
                .add("startDate", rule.getStartDate().toString())
                .add("endDate", rule.getEndDate().toString())
                .add("repeatDays", convertRepeatDays(rule.getRepeatDays()))
                .add("unavailabilities", convertUnavailabilities(rule.getUnavailabilities()));

        if (rule.getRecurringType() != null) {
            ruleBuilder.add("recurringType", rule.getRecurringType().name());
        }
        if (rule.getSessionType() != null) {
            ruleBuilder.add("sessionType", rule.getSessionType().name());
        }

        return ruleBuilder.build();
    }

    private JsonArray convertRepeatDays(final java.util.List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        final JsonArrayBuilder repeatDaysArrayBuilder = Json.createArrayBuilder();
        if (repeatDays != null) {
            for (JudiciaryAvailabilityRuleRepeatDay repeatDay : repeatDays) {
                if (repeatDay.getIndex() != null) {
                    // Object format with index
                    repeatDaysArrayBuilder.add(Json.createObjectBuilder()
                            .add("day", repeatDay.getDayOfWeek().name())
                            .add("index", repeatDay.getIndex())
                            .build());
                } else {
                    // Simple string format
                    repeatDaysArrayBuilder.add(repeatDay.getDayOfWeek().name());
                }
            }
        }
        return repeatDaysArrayBuilder.build();
    }

    private JsonArray convertUnavailabilities(final java.util.List<JudiciaryUnavailabilityResponse> unavailabilities) {
        final JsonArrayBuilder unavailabilitiesArrayBuilder = Json.createArrayBuilder();
        if (unavailabilities != null) {
            for (JudiciaryUnavailabilityResponse unavailability : unavailabilities) {
                final JsonObjectBuilder unavailabilityBuilder = Json.createObjectBuilder()
                        .add("startDate", unavailability.getStartDate().toString())
                        .add("endDate", unavailability.getEndDate().toString());
                if (unavailability.getReason() != null) {
                    unavailabilityBuilder.add("reason", unavailability.getReason().name());
                }
                unavailabilitiesArrayBuilder.add(unavailabilityBuilder.build());
            }
        }
        return unavailabilitiesArrayBuilder.build();
    }
}

