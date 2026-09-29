package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindJudiciaryAvailabilityConverterTest {
    private static final String DATE_2026_01_01 = "2026-01-01";
    private static final String DATE_2026_01_31 = "2026-01-31";
    private static final String END_DATE = "endDate";
    private static final String START_DATE = "startDate";


    private final FindJudiciaryAvailabilityConverter converter = new FindJudiciaryAvailabilityConverter();

    @Test
    void shouldConvertJsonObjectWithAllParameters() {
        final String courtCentreId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("courtCentreId", courtCentreId)
                .add("judiciaryId", judiciaryId)
                .build();

        final FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is(DATE_2026_01_01));
        assertThat(result.getEndDate().toString(), is(DATE_2026_01_31));
        assertThat(result.getCourtHouseId(), is(courtCentreId));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyRequiredParameters() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .build();

        final FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is(DATE_2026_01_01));
        assertThat(result.getEndDate().toString(), is(DATE_2026_01_31));
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyCourtCentreId() {
        final String courtCentreId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("courtCentreId", courtCentreId)
                .build();

        final FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

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

        final FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
    }
}

