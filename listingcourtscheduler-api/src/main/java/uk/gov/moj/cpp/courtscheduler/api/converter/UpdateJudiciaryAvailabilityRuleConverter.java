package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import jakarta.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;

@Service
public class UpdateJudiciaryAvailabilityRuleConverter extends BaseJudiciaryAvailabilityRuleConverter 
        implements Converter<JsonObject, UpdateJudiciaryAvailabilityRuleRequest> {

    private static final String RULE_ID = "ruleId";
    private static final String ID = "id";

    @Override
    public UpdateJudiciaryAvailabilityRuleRequest convert(JsonObject jsonObject) {
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();

        if (hasField(jsonObject, RULE_ID)) {
            request.setRuleId(jsonObject.getString(RULE_ID));
        } else if (hasField(jsonObject, ID)) {
            request.setRuleId(jsonObject.getString(ID));
        }

        populateBaseFields(jsonObject, request);
        populateDetailFields(jsonObject, request);

        return request;
    }
}

