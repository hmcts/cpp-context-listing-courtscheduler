package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import jakarta.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;

@Service
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

