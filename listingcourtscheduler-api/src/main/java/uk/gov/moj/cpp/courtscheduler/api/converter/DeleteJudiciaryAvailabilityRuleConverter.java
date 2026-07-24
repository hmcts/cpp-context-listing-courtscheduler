package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import jakarta.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;

@Service
public class DeleteJudiciaryAvailabilityRuleConverter implements Converter<JsonObject, DeleteJudiciaryAvailabilityRuleRequest> {

    private static final String RULE_ID = "ruleId";
    private static final String JUDICIARY_ID = "judiciaryId";

    @Override
    public DeleteJudiciaryAvailabilityRuleRequest convert(final JsonObject jsonObject) {
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        if (jsonObject.containsKey(RULE_ID) && !jsonObject.isNull(RULE_ID)) {
            request.setRuleId(jsonObject.getString(RULE_ID));
        }
        if (jsonObject.containsKey(JUDICIARY_ID) && !jsonObject.isNull(JUDICIARY_ID)) {
            request.setJudiciaryId(jsonObject.getString(JUDICIARY_ID));
        }
        return request;
    }
}




