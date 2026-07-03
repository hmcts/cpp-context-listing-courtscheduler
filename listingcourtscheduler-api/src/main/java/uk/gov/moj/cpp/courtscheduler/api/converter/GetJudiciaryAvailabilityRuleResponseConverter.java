package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

@Service
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
