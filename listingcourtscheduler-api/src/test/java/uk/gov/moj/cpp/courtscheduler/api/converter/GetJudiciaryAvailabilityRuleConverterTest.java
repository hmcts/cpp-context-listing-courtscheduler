package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetJudiciaryAvailabilityRuleConverterTest {
    private static final String RULE_ID = "ruleId";
    private static final String WITH_JUDICIARY = "withJudiciary";


    private final GetJudiciaryAvailabilityRuleConverter converter = new GetJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldConvertJsonObjectWithRuleIdAndWithJudiciary() {
        final String ruleId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(RULE_ID, ruleId)
                .add(WITH_JUDICIARY, true)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
        assertThat(result.isWithJudiciary(), is(true));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyRuleId() {
        final String ruleId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(RULE_ID, ruleId)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
        assertThat(result.isWithJudiciary(), is(true)); // Default value
    }

    @Test
    void shouldUseDefaultWithJudiciaryWhenNotProvided() {
        final String ruleId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(RULE_ID, ruleId)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.isWithJudiciary(), is(true));
    }

    @Test
    void shouldHandleNullWithJudiciary() {
        final String ruleId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(RULE_ID, ruleId)
                .addNull(WITH_JUDICIARY)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.isWithJudiciary(), is(true)); // Default value
    }

    @Test
    void shouldConvertJsonObjectWithWithJudiciaryFalse() {
        final String ruleId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(RULE_ID, ruleId)
                .add(WITH_JUDICIARY, false)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
        assertThat(result.isWithJudiciary(), is(false));
    }

    @Test
    void shouldHandleNullRuleId() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .addNull(RULE_ID)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(nullValue()));
    }

    @Test
    void shouldHandleMissingRuleId() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(WITH_JUDICIARY, true)
                .build();

        final GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(nullValue()));
    }
}
