package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class FindJudiciaryAvailabilityRuleResponseConverter extends BaseJudiciaryAvailabilityRuleResponseConverter
        implements Converter<FindJudiciaryAvailabilityRuleResponse, JsonObject> {

    private final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.Judiciary> judiciaryConverter;

    public FindJudiciaryAvailabilityRuleResponseConverter() {
        this.judiciaryConverter = new ListToJsonArrayConverter<>();
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

        return Json.createObjectBuilder()
                .add("rules", rulesArrayBuilder.build())
                .add("totalCount", response.getTotalCount())
                .add("pageNumber", response.getPageNumber())
                .add("pageSize", response.getPageSize())
                .add("judiciaries", judiciariesArray)
                .build();
    }

    private JsonObject convertRule(final JudiciaryAvailabilityRuleResponse rule) {
        JsonObjectBuilder ruleBuilder = Json.createObjectBuilder()
                .add("id", rule.getId())
                .add("judiciaryId", rule.getJudiciaryId())
                .add("courtHouseId", rule.getCourtHouseId())
                .add("startDate", rule.getStartDate().toString())
                .add("endDate", rule.getEndDate().toString())
                .add("repeatDays", convertRepeatDaysToJson(rule.getRepeatDays()))
                .add("unavailabilities", convertUnavailabilitiesToJson(rule.getUnavailabilities()));

        addOptionalEnumField(ruleBuilder, "recurringType", rule.getRecurringType());
        addOptionalEnumField(ruleBuilder, "sessionType", rule.getSessionType());

        return ruleBuilder.build();
    }
}

