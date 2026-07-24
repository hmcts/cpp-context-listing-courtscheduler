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

    private final FindJudiciaryAvailabilityConverter converter = new FindJudiciaryAvailabilityConverter();

    @Test
    void shouldConvertJsonObjectWithAllParameters() {
        final String courtCentreId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtCentreId", courtCentreId)
                .add("judiciaryId", judiciaryId)
                .build();

        FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getCourtHouseId(), is(courtCentreId));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyRequiredParameters() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .build();

        FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithOnlyCourtCentreId() {
        final String courtCentreId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtCentreId", courtCentreId)
                .build();

        FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

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

        FindJudiciaryAvailabilityRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getJudiciaryId(), is(judiciaryId));
    }
}

