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

    private final GetJudiciaryAvailabilityRuleConverter converter = new GetJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldConvertJsonObjectWithRuleIdAndWithJudiciary() {
        final String ruleId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("withJudiciary", true)
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
        assertThat(result.getWithJudiciary(), is(true));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyRuleId() {
        final String ruleId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
        assertThat(result.getWithJudiciary(), is(true)); // Default value
    }

    @Test
    void shouldUseDefaultWithJudiciaryWhenNotProvided() {
        final String ruleId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getWithJudiciary(), is(true));
    }

    @Test
    void shouldHandleNullWithJudiciary() {
        final String ruleId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .addNull("withJudiciary")
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getWithJudiciary(), is(true)); // Default value
    }

    @Test
    void shouldConvertJsonObjectWithWithJudiciaryFalse() {
        final String ruleId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("withJudiciary", false)
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
        assertThat(result.getWithJudiciary(), is(false));
    }

    @Test
    void shouldHandleNullRuleId() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .addNull("ruleId")
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(nullValue()));
    }

    @Test
    void shouldHandleMissingRuleId() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("withJudiciary", true)
                .build();

        GetJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(nullValue()));
    }
}
