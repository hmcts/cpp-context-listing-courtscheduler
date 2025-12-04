package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityType;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;

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
                .add("availabilityType", AvailabilityType.AVAILABLE.name())
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.MONDAY.name())
                        .add(AvailabilityDayOfWeek.TUESDAY.name()))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getCourtHouseId(), is(courtHouseId));
        assertThat(result.getAvailabilityType(), is(AvailabilityType.AVAILABLE));
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.MONDAY));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(nullValue()));
        assertThat(result.getRepeatDays().get(1).getDayOfWeek(), is(AvailabilityDayOfWeek.TUESDAY));
        assertThat(result.getRepeatDays().get(1).getIndex(), is(nullValue()));
    }

    @Test
    void shouldConvertJsonObjectWithIndexedRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("availabilityType", AvailabilityType.AVAILABLE.name())
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-07-31")
                .add("recurringType", RecurringType.MONTHLY.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("day", AvailabilityDayOfWeek.TUESDAY.name())
                                .add("index", 2))
                        .add(Json.createObjectBuilder()
                                .add("day", AvailabilityDayOfWeek.WEDNESDAY.name())
                                .add("index", 3)))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getRecurringType(), is(RecurringType.MONTHLY));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.TUESDAY));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(2));
        assertThat(result.getRepeatDays().get(1).getDayOfWeek(), is(AvailabilityDayOfWeek.WEDNESDAY));
        assertThat(result.getRepeatDays().get(1).getIndex(), is(3));
    }

    @Test
    void shouldConvertJsonObjectWithMixedRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("availabilityType", AvailabilityType.UNAVAILABLE.name())
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("reason", "Holiday")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add(Json.createObjectBuilder()
                                .add("day", AvailabilityDayOfWeek.WEDNESDAY.name())
                                .add("index", 1)))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getAvailabilityType(), is(AvailabilityType.UNAVAILABLE));
        assertThat(result.getReason(), is("Holiday"));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.MONDAY));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(nullValue()));
        assertThat(result.getRepeatDays().get(1).getDayOfWeek(), is(AvailabilityDayOfWeek.WEDNESDAY));
        assertThat(result.getRepeatDays().get(1).getIndex(), is(1));
    }

    @Test
    void shouldConvertJsonObjectWithOptionalFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("availabilityType", AvailabilityType.AVAILABLE.name())
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
                .add("availabilityType", AvailabilityType.AVAILABLE.name())
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("day", AvailabilityDayOfWeek.FRIDAY.name())))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(1));
        assertThat(result.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.FRIDAY));
        assertThat(result.getRepeatDays().get(0).getIndex(), is(nullValue()));
    }
}

