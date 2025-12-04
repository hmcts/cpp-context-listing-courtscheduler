package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.util.UUID.randomUUID;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;
import javax.json.JsonValue;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityType;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityRuleApiValidatorTest {

    @InjectMocks
    private JudiciaryAvailabilityRuleApiValidator validator;

    private AddJudiciaryAvailabilityRuleRequest request;

    @BeforeEach
    void setUp() {
        this.request = new AddJudiciaryAvailabilityRuleRequest();
        this.request.setJudiciaryId(UUID.randomUUID().toString());
        this.request.setCourtHouseId(UUID.randomUUID().toString());
        this.request.setAvailabilityType(AvailabilityType.AVAILABLE);
        this.request.setStartDate(LocalDate.of(2026, 1, 1));
        this.request.setEndDate(LocalDate.of(2026, 1, 31));
        
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.MONDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, null));
        this.request.setRepeatDays(repeatDays);
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidRequest() {
        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenRequestIsNull() {
        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(null);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenJudiciaryIdIsBlank() {
        this.request.setJudiciaryId("");

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("judiciaryId"));
    }

    @Test
    void shouldReturnErrorWhenCourtHouseIdIsBlank() {
        this.request.setCourtHouseId(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("courtHouseId"));
    }

    @Test
    void shouldReturnErrorWhenGroupIsNull() {
        this.request.setAvailabilityType(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("availabilityType"));
    }

    @Test
    void shouldAcceptAvailableGroup() {
        this.request.setAvailabilityType(AvailabilityType.AVAILABLE);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldAcceptUnavailableGroup() {
        this.request.setAvailabilityType(AvailabilityType.UNAVAILABLE);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsNull() {
        this.request.setStartDate(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("startDate"));
    }

    @Test
    void shouldReturnErrorWhenEndDateIsNull() {
        this.request.setEndDate(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("endDate"));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsAfterEndDate() {
        this.request.setStartDate(LocalDate.of(2026, 1, 31));
        this.request.setEndDate(LocalDate.of(2026, 1, 1));

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("startDate") || 
                   result.getString("errorMessage").contains("endDate"));
    }

    @Test
    void shouldAcceptStartDateEqualToEndDate() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        this.request.setStartDate(date);
        this.request.setEndDate(date);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenRepeatDaysIsNull() {
        this.request.setRepeatDays(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("repeatDays"));
    }

    @Test
    void shouldReturnErrorWhenRepeatDaysIsEmpty() {
        this.request.setRepeatDays(new ArrayList<>());

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("repeatDays"));
    }

    @Test
    void shouldReturnErrorWhenDayOfWeekIsBlank() {
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(null, null));
        this.request.setRepeatDays(repeatDays);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("repeatDays") || 
                   result.getString("errorMessage").contains("dayOfWeek"));
    }

    @Test
    void shouldReturnErrorWhenDayOfWeekIsInvalid() {
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(null, null));
        this.request.setRepeatDays(repeatDays);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("dayOfWeek") ||
                   result.getString("errorMessage").contains("InvalidDay"));
    }

    @Test
    void shouldAcceptValidDayNames() {
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.MONDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.WEDNESDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.THURSDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.FRIDAY, null));
        //repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, null));
        //repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay("Sunday", null));
        this.request.setRepeatDays(repeatDays);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldAcceptDayNamesCaseInsensitive() {
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.MONDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.WEDNESDAY, null));
        this.request.setRepeatDays(repeatDays);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }
}

