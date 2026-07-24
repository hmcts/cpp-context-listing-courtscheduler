package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

@Service
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

}

