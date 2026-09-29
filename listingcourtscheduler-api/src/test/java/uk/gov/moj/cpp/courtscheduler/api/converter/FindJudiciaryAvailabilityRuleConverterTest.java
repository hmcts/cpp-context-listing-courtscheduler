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
    private static final String DATE_2026_01_01 = "2026-01-01";
    private static final String DATE_2026_01_31 = "2026-01-31";
    private static final String END_DATE = "endDate";
    private static final String START_DATE = "startDate";


    private final FindJudiciaryAvailabilityRuleConverter converter = new FindJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldConvertJsonObjectWithAllParameters() {
        final String courtCentreId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("courtCentreId", courtCentreId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 10)
                .add("pageNumber", 2)
                .add("withJudiciary", true)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is(DATE_2026_01_01));
        assertThat(result.getEndDate().toString(), is(DATE_2026_01_31));
        assertThat(result.getCourtHouseId(), is(courtCentreId));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getPageSize(), is(10));
        assertThat(result.getPageNumber(), is(2));
        assertThat(result.isWithJudiciary(), is(true));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyRequiredParameters() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is(DATE_2026_01_01));
        assertThat(result.getEndDate().toString(), is(DATE_2026_01_31));
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(nullValue()));
        assertThat(result.getPageSize(), is(20)); // Default value
        assertThat(result.getPageNumber(), is(1)); // Default value
        assertThat(result.isWithJudiciary(), is(true)); // Default value
    }

    @Test
    void shouldUseDefaultPaginationWhenNotProvided() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getPageSize(), is(20));
        assertThat(result.getPageNumber(), is(1));
    }

    @Test
    void shouldUseDefaultWithJudiciaryWhenNotProvided() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.isWithJudiciary(), is(true));
    }

    @Test
    void shouldHandleNullWithJudiciary() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .addNull("withJudiciary")
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.isWithJudiciary(), is(true));
    }

    @Test
    void shouldHandleNullPageSize() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .addNull("pageSize")
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getPageSize(), is(20));
    }

    @Test
    void shouldHandleNullPageNumber() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .addNull("pageNumber")
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getPageNumber(), is(1));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyCourtCentreId() {
        final String courtCentreId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("courtCentreId", courtCentreId)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getCourtHouseId(), is(courtCentreId));
        assertThat(result.getJudiciaryId(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyJudiciaryId() {
        final String judiciaryId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("judiciaryId", judiciaryId)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
    }

    @Test
    void shouldConvertJsonObjectWithWithJudiciaryFalse() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("withJudiciary", false)
                .build();

        final FindJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.isWithJudiciary(), is(false));
    }

}

