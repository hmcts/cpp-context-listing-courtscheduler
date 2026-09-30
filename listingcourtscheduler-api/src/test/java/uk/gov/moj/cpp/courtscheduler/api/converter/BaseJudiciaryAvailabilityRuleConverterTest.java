package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseJudiciaryAvailabilityRuleConverterTest {
    private static final String DATE_2026_01_01 = "2026-01-01";
    private static final String DATE_2026_01_10 = "2026-01-10";
    private static final String DATE_2026_01_12 = "2026-01-12";
    private static final String DATE_2026_01_31 = "2026-01-31";
    private static final String MONDAY_2 = "Monday";
    private static final String TUESDAY_2 = "Tuesday";
    private static final String COURT_HOUSE_ID = "courtHouseId";
    private static final String END_DATE = "endDate";
    private static final String JUDICIARY_ID = "judiciaryId";
    private static final String REASON = "reason";
    private static final String REPEAT_DAYS = "repeatDays";
    private static final String START_DATE = "startDate";
    private static final String UNAVAILABILITIES = "unavailabilities";


    private final AddJudiciaryAvailabilityRuleConverter converter = new AddJudiciaryAvailabilityRuleConverter();

    @Test
    void shouldPopulateBaseFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getCourtHouseId(), is(courtHouseId));
        assertThat(result.getStartDate().toString(), is(DATE_2026_01_01));
        assertThat(result.getEndDate().toString(), is(DATE_2026_01_31));
    }

    @Test
    void shouldHandleNullBaseFields() {
        final JsonObject jsonObject = Json.createObjectBuilder().build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

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
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add("sessionType", "AM")
                .add(REPEAT_DAYS, Json.createArrayBuilder()
                        .add(MONDAY_2)
                        .add(TUESDAY_2))
                .add(UNAVAILABILITIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, DATE_2026_01_10)
                                .add(END_DATE, DATE_2026_01_12)
                                .add(REASON, "ANNUAL_LEAVE")))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getSessionType(), is(SessionType.AM));
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.MONDAY));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.TUESDAY));
        assertThat(result.getUnavailabilities().size(), is(1));
        assertThat(result.getUnavailabilities().getFirst().getStartDate().toString(), is(DATE_2026_01_10));
        assertThat(result.getUnavailabilities().getFirst().getEndDate().toString(), is(DATE_2026_01_12));
        assertThat(result.getUnavailabilities().getFirst().getReason(), is(UnavailabilityReason.ANNUAL_LEAVE));
    }

    @Test
    void shouldHandleNullDetailFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .addNull("sessionType")
                .addNull(REPEAT_DAYS)
                .addNull(UNAVAILABILITIES)
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getSessionType(), is(nullValue()));
        assertThat(result.getRepeatDays(), is(empty()));
        assertThat(result.getUnavailabilities(), is(empty()));
    }

    @Test
    void shouldConvertRepeatDaysWithCaseVariations() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(REPEAT_DAYS, Json.createArrayBuilder()
                        .add("monday")
                        .add("TUESDAY")
                        .add("Wednesday")
                        .add("thursday")
                        .add("FRIDAY"))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(5));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.MONDAY));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.TUESDAY));
        assertThat(result.getRepeatDays().get(2), is(AvailabilityDayOfWeek.WEDNESDAY));
        assertThat(result.getRepeatDays().get(3), is(AvailabilityDayOfWeek.THURSDAY));
        assertThat(result.getRepeatDays().get(4), is(AvailabilityDayOfWeek.FRIDAY));
    }

    @Test
    void shouldSkipInvalidRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(REPEAT_DAYS, Json.createArrayBuilder()
                        .add(MONDAY_2)
                        .add("InvalidDay")
                        .add(TUESDAY_2))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(2));
        assertThat(result.getRepeatDays().get(0), is(AvailabilityDayOfWeek.MONDAY));
        assertThat(result.getRepeatDays().get(1), is(AvailabilityDayOfWeek.TUESDAY));
    }

    @Test
    void shouldSkipNonStringRepeatDays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(REPEAT_DAYS, Json.createArrayBuilder()
                        .add(MONDAY_2)
                        .add(123)
                        .add(true)
                        .add(Json.createObjectBuilder().add("day", TUESDAY_2)))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(1));
        assertThat(result.getRepeatDays().getFirst(), is(AvailabilityDayOfWeek.MONDAY));
    }

    @Test
    void shouldConvertEmptyRepeatDaysArray() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(REPEAT_DAYS, Json.createArrayBuilder())
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getRepeatDays().size(), is(0));
    }

    @Test
    void shouldConvertUnavailabilitiesWithAllFields() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(UNAVAILABILITIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, DATE_2026_01_10)
                                .add(END_DATE, DATE_2026_01_12)
                                .add(REASON, "TRAINING"))
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, "2026-01-20")
                                .add(END_DATE, "2026-01-22")
                                .add(REASON, "SICK_LEAVE")))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(2));
        assertThat(result.getUnavailabilities().get(0).getReason(), is(UnavailabilityReason.TRAINING));
        assertThat(result.getUnavailabilities().get(1).getReason(), is(UnavailabilityReason.SICK_LEAVE));
    }

    @Test
    void shouldConvertUnavailabilitiesWithoutReason() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(UNAVAILABILITIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, DATE_2026_01_10)
                                .add(END_DATE, DATE_2026_01_12)))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(1));
        assertThat(result.getUnavailabilities().getFirst().getReason(), is(nullValue()));
    }

    @Test
    void shouldSkipNonObjectUnavailabilities() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(UNAVAILABILITIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, DATE_2026_01_10)
                                .add(END_DATE, DATE_2026_01_12))
                        .add("not an object")
                        .add(123)
                        .add(true))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(1));
    }

    @Test
    void shouldConvertEmptyUnavailabilitiesArray() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(UNAVAILABILITIES, Json.createArrayBuilder())
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        assertThat(result.getUnavailabilities().size(), is(0));
    }

    @Test
    void shouldHandleAllUnavailabilityReasons() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(UNAVAILABILITIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, DATE_2026_01_10)
                                .add(END_DATE, DATE_2026_01_12)
                                .add(REASON, "TRAINING"))
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, "2026-01-13")
                                .add(END_DATE, "2026-01-15")
                                .add(REASON, "ANNUAL_LEAVE"))
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, "2026-01-16")
                                .add(END_DATE, "2026-01-18")
                                .add(REASON, "OFFICIAL_BUSINESS"))
                        .add(Json.createObjectBuilder()
                                .add(START_DATE, "2026-01-19")
                                .add(END_DATE, "2026-01-21")
                                .add(REASON, "SICK_LEAVE")))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

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
        
        for (final SessionType sessionType : SessionType.values()) {
            final JsonObject jsonObject = Json.createObjectBuilder()
                    .add(JUDICIARY_ID, judiciaryId)
                    .add(COURT_HOUSE_ID, courtHouseId)
                    .add(START_DATE, DATE_2026_01_01)
                    .add(END_DATE, DATE_2026_01_31)
                    .add("sessionType", sessionType.name())
                    .build();

            final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

            assertNotNull(result);
            assertThat(result.getSessionType(), is(sessionType));
        }
    }

    @Test
    void shouldHandleEmptyStringRepeatDay() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(COURT_HOUSE_ID, courtHouseId)
                .add(START_DATE, DATE_2026_01_01)
                .add(END_DATE, DATE_2026_01_31)
                .add(REPEAT_DAYS, Json.createArrayBuilder()
                        .add(MONDAY_2)
                        .add("")
                        .add(TUESDAY_2))
                .build();

        final AddJudiciaryAvailabilityRuleRequest result = converter.convert(jsonObject);

        assertNotNull(result);
        // Empty string should be skipped (converted to empty after titleCase, then fails valueOf)
        assertTrue(result.getRepeatDays().size() <= 2);
    }
}
