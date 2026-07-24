package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseJudiciaryAvailabilityRuleConverterTest {

    private final AddJudiciaryAvailabilityRuleConverter converter = new AddJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldPopulateBaseFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getCourtHouseId(), is(courtHouseId));
        assertThat(result.getStartDate().toString(), is("2026-01-01"));
        assertThat(result.getEndDate().toString(), is("2026-01-31"));
    }

    @Test
    void shouldHandleNullBaseFields() {
        JsonObject jsonObject = Json.createObjectBuilder().build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(nullValue()));
        assertThat(result.getCourtHouseId(), is(nullValue()));
        assertThat(result.getStartDate(), is(nullValue()));
        assertThat(result.getEndDate(), is(nullValue()));
    }

    @Test
    void shouldPopulateDetailFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("sessionType", "AM")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday"))
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-10")
                                .add("endDate", "2026-01-12")
                                .add("reason", "ANNUAL_LEAVE")))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getSessionType(), is(SessionType.AM));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Monday));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.Tuesday));
        assertThat(result.getUnavailabilities().size(), is(1));
        assertThat(result.getUnavailabilities().get(0).getStartDate().toString(), is("2026-01-10"));
        assertThat(result.getUnavailabilities().get(0).getEndDate().toString(), is("2026-01-12"));
        assertThat(result.getUnavailabilities().get(0).getReason(), is(UnavailabilityReason.ANNUAL_LEAVE));
    }

    @Test
    void shouldHandleNullDetailFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .addNull("sessionType")
                .addNull("repeatDays")
                .addNull("unavailabilities")
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getSessionType(), is(nullValue()));
        assertThat(result.getRepeatDays(), is(nullValue()));
        assertThat(result.getUnavailabilities(), is(nullValue()));
    }

    @Test
    void shouldConvertRepeatDaysWithCaseVariations() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("monday")
                        .add("TUESDAY")
                        .add("Wednesday")
                        .add("thursday")
                        .add("FRIDAY"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(5));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Monday));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.Tuesday));
        assertThat(result.getRepeatDays().get(2), is(AvailabilityDayOfWeek.Wednesday));
        assertThat(result.getRepeatDays().get(3), is(AvailabilityDayOfWeek.Thursday));
        assertThat(result.getRepeatDays().get(4), is(AvailabilityDayOfWeek.Friday));
    }

    @Test
    void shouldSkipInvalidRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("InvalidDay")
                        .add("Tuesday"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Monday));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.Tuesday));
    }

    @Test
    void shouldSkipNonStringRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add(123)
                        .add(true)
                        .add(Json.createObjectBuilder().add("day", "Tuesday")))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(1));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.Monday));
    }

    @Test
    void shouldConvertEmptyRepeatDaysArray() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder())
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(0));
    }

    @Test
    void shouldConvertUnavailabilitiesWithAllFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-10")
                                .add("endDate", "2026-01-12")
                                .add("reason", "TRAINING"))
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-20")
                                .add("endDate", "2026-01-22")
                                .add("reason", "SICK_LEAVE")))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(2));
        assertThat(result.getUnavailabilities().get(0).getReason(), is(UnavailabilityReason.TRAINING));
        assertThat(result.getUnavailabilities().get(1).getReason(), is(UnavailabilityReason.SICK_LEAVE));
    }

    @Test
    void shouldConvertUnavailabilitiesWithoutReason() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-10")
                                .add("endDate", "2026-01-12")))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(1));
        assertThat(result.getUnavailabilities().get(0).getReason(), is(nullValue()));
    }

    @Test
    void shouldSkipNonObjectUnavailabilities() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-10")
                                .add("endDate", "2026-01-12"))
                        .add("not an object")
                        .add(123)
                        .add(true))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(1));
    }

    @Test
    void shouldConvertEmptyUnavailabilitiesArray() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("unavailabilities", Json.createArrayBuilder())
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(0));
    }

    @Test
    void shouldHandleAllUnavailabilityReasons() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-10")
                                .add("endDate", "2026-01-12")
                                .add("reason", "TRAINING"))
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-13")
                                .add("endDate", "2026-01-15")
                                .add("reason", "ANNUAL_LEAVE"))
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-16")
                                .add("endDate", "2026-01-18")
                                .add("reason", "OFFICIAL_BUSINESS"))
                        .add(Json.createObjectBuilder()
                                .add("startDate", "2026-01-19")
                                .add("endDate", "2026-01-21")
                                .add("reason", "SICK_LEAVE")))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(4));
        assertThat(result.getUnavailabilities().get(0).getReason(), is(UnavailabilityReason.TRAINING));
        assertThat(result.getUnavailabilities().get(1).getReason(), is(UnavailabilityReason.ANNUAL_LEAVE));
        assertThat(result.getUnavailabilities().get(2).getReason(), is(UnavailabilityReason.OFFICIAL_BUSINESS));
        assertThat(result.getUnavailabilities().get(3).getReason(), is(UnavailabilityReason.SICK_LEAVE));
    }

    @Test
    void shouldHandleAllSessionTypes() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        for (SessionType sessionType : SessionType.values()) {
            JsonObject jsonObject = Json.createObjectBuilder()
                    .add("judiciaryId", judiciaryId)
                    .add("courtHouseId", courtHouseId)
                    .add("startDate", "2026-01-01")
                    .add("endDate", "2026-01-31")
                    .add("sessionType", sessionType.name())
                    .build();

            AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

            assertNotNull(result);
            assertThat(result.getSessionType(), is(sessionType));
        }
    }

    @Test
    void shouldHandleEmptyStringRepeatDay() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("")
                        .add("Tuesday"))
                .build();

        AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        // Empty string should be skipped (converted to empty after titleCase, then fails valueOf)
        assertTrue(result.getRepeatDays().size() <= 2);
    }
}
