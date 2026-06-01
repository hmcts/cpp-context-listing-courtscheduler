package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindJudiciaryAvailabilityRuleConverterTest {

    private final FindJudiciaryAvailabilityRuleConverter converter = new FindJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldConvertJsonObjectWithAllParameters() {
        final String courtCentreId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtCentreId", courtCentreId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 10)
                .add("pageNumber", 2)
                .add("withJudiciary", true)
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getCourtHouseId(), is(courtCentreId));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getPageSize(), is(10));
        assertThat(result.getPageNumber(), is(2));
        assertThat(result.getWithJudiciary(), is(true));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyRequiredParameters() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(nullValue()));
        assertThat(result.getPageSize(), is(20)); // Default value
        assertThat(result.getPageNumber(), is(1)); // Default value
        assertThat(result.getWithJudiciary(), is(true)); // Default value
    }

    @Test
    void shouldUseDefaultPaginationWhenNotProvided() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getPageSize(), is(20));
        assertThat(result.getPageNumber(), is(1));
    }

    @Test
    void shouldUseDefaultWithJudiciaryWhenNotProvided() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getWithJudiciary(), is(true));
    }

    @Test
    void shouldHandleNullWithJudiciary() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .addNull("withJudiciary")
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getWithJudiciary(), is(true));
    }

    @Test
    void shouldHandleNullPageSize() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .addNull("pageSize")
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getPageSize(), is(20));
    }

    @Test
    void shouldHandleNullPageNumber() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .addNull("pageNumber")
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getPageNumber(), is(1));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyCourtCentreId() {
        final String courtCentreId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtCentreId", courtCentreId)
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getCourtHouseId(), is(courtCentreId));
        assertThat(result.getJudiciaryId(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyJudiciaryId() {
        final String judiciaryId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("judiciaryId", judiciaryId)
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
    }

    @Test
    void shouldConvertJsonObjectWithWithJudiciaryFalse() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("withJudiciary", false)
                .build();

        FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getWithJudiciary(), is(false));
    }

}

