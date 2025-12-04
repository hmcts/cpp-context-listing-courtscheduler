package uk.gov.moj.cpp.courtscheduler.api.converter;

import javax.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;

public class DeleteJudiciaryAvailabilityRuleConverter implements Converter<JsonObject, DeleteJudiciaryAvailabilityRuleRequest> {

    @Override
    public DeleteJudiciaryAvailabilityRuleRequest convert(final JsonObject jsonObject) {
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(jsonObject.getString("ruleId"));
        return request;
    }
}



