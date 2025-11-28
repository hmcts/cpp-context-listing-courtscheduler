package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;

import javax.json.Json;
import javax.json.JsonObject;

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
                .add("group", "Available")
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getCourtHouseId(), is(courtHouseId));
        assertThat(result.getGroup(), is("Available"));
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is("Monday"));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(nullValue()));
        assertThat(result.getRepeatDays().get(1).getDayOfWeek(), is("Tuesday"));
        assertThat(result.getRepeatDays().get(1).getIndex(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithIndexedRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-07-31")
                .add("recurringType", "Monthly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("day", "Tuesday")
                                .add("index", 2))
                        .add(Json.createObjectBuilder()
                                .add("day", "Wednesday")
                                .add("index", 3)))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getRecurringType(), is("Monthly"));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is("Tuesday"));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(2));
        assertThat(result.getRepeatDays().get(1).getDayOfWeek(), is("Wednesday"));
        assertThat(result.getRepeatDays().get(1).getIndex(), is(3));
    }

    @Test
    void shouldConvertJsonObjectWithMixedRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Unavailable")
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("reason", "Holiday")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add(Json.createObjectBuilder()
                                .add("day", "Wednesday")
                                .add("index", 1)))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getGroup(), is("Unavailable"));
        assertThat(result.getReason(), is("Holiday"));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is("Monday"));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(nullValue()));
        assertThat(result.getRepeatDays().get(1).getDayOfWeek(), is("Wednesday"));
        assertThat(result.getRepeatDays().get(1).getIndex(), is(1));
    }

    @Test
    void shouldConvertJsonObjectWithOptionalFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder().add("Monday"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRecurringType(), is(nullValue()));
        assertThat(result.getReason(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithIndexedDayWithoutIndex() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("day", "Friday")))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(1));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is("Friday"));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(nullValue()));
    }
}

