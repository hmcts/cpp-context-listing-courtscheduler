package uk.gov.moj.cpp.courtscheduler.api.converter;

import javax.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;

public class AddJudiciaryAvailabilityRuleConverter extends BaseJudiciaryAvailabilityRuleConverter
        implements Converter<JsonObject, AddJudiciaryAvailabilityRuleRequest> {

    @Override
    public AddJudiciaryAvailabilityRuleRequest convert(JsonObject jsonObject) {
        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();

        populateBaseFields(jsonObject, request);
        populateDetailFields(jsonObject, request);

        return request;
    }
}

