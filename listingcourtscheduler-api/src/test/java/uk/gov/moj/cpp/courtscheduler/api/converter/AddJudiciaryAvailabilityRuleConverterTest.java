package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddJudiciaryAvailabilityRuleConverterTest {

    private final AddJudiciaryAvailabilityRuleConverter converter = new AddJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldConvertJsonObjectWithSimpleRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Monday.name())
                        .add(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Tuesday.name()))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getCourtHouseId(), is(courtHouseId));
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Monday));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.Tuesday));
    }

    @Test
    void shouldConvertJsonObjectWithMultipleRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-07-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Tuesday")
                        .add("Wednesday"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Tuesday));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.Wednesday));
    }

    @Test
    void shouldConvertJsonObjectWithOptionalFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder().add("Monday"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(1));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Monday));
    }
}

