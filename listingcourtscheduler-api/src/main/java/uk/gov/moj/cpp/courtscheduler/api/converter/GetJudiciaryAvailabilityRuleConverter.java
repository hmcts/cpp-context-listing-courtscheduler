package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import jakarta.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;

@Service
public class GetJudiciaryAvailabilityRuleConverter implements Converter<JsonObject, GetJudiciaryAvailabilityRuleRequest> {

    private static final boolean DEFAULT_WITH_JUDICIARY = true;
    public static final String RULE_ID = "ruleId";
    public static final String WITH_JUDICIARY = "withJudiciary";

    @Override
    public GetJudiciaryAvailabilityRuleRequest convert(final JsonObject jsonObject) {
        final GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();

        if (jsonObject.containsKey(RULE_ID) && !jsonObject.isNull(RULE_ID)) {
            request.setRuleId(jsonObject.getString(RULE_ID));
        }

        if (jsonObject.containsKey(WITH_JUDICIARY) && !jsonObject.isNull(WITH_JUDICIARY)) {
            request.setWithJudiciary(jsonObject.getBoolean(WITH_JUDICIARY));
        } else {
            request.setWithJudiciary(DEFAULT_WITH_JUDICIARY);
        }

        return request;
    }
}
