package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public class GetJudiciaryAvailabilityRuleResponseConverter extends BaseJudiciaryAvailabilityRuleResponseConverter
        implements Converter<GetJudiciaryAvailabilityRuleResponse, JsonObject> {

    private final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.Judiciary> judiciaryConverter;

    public GetJudiciaryAvailabilityRuleResponseConverter() {
        this.judiciaryConverter = new ListToJsonArrayConverter<>();
    }

    @Override
    public JsonObject convert(final GetJudiciaryAvailabilityRuleResponse response) {
        final JudiciaryAvailabilityRuleResponse rule = response.getRule();
        
        final JsonObject ruleObject = convertRule(rule);

        // Build response with rule and optional judiciary
        final JsonObjectBuilder responseBuilder = Json.createObjectBuilder()
                .add("rule", ruleObject);

        // Include judiciary node if present (null if not requested or not found)
        if (response.getJudiciary() != null) {
            final JsonObject judiciaryObject = judiciaryConverter.mapObjectToJsonObject(response.getJudiciary());
            responseBuilder.add("judiciary", judiciaryObject);
        } else {
            responseBuilder.addNull("judiciary");
        }

        return responseBuilder.build();
    }
}
