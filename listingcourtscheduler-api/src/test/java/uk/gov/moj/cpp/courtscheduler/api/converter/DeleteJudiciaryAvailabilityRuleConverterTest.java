package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteJudiciaryAvailabilityRuleConverterTest {

    private final DeleteJudiciaryAvailabilityRuleConverter converter = new DeleteJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldConvertJsonObjectWithRuleId() {
        final String ruleId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .build();

        DeleteJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
    }

    @Test
    void shouldConvertJsonObjectWithValidUuid() {
        final String ruleId = "123e4567-e89b-12d3-a456-426614174000";
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .build();

        DeleteJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRuleId(), is(ruleId));
    }
}



