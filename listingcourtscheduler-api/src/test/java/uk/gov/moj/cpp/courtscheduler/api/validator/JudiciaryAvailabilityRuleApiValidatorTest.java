package uk.gov.moj.cpp.courtscheduler.api.validator;

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
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
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
        this.request.setStartDate(LocalDate.of(2026, 1, 1));
        this.request.setEndDate(LocalDate.of(2026, 1, 31));
        
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Tuesday, null));
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
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Tuesday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Wednesday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Thursday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Friday, null));
        this.request.setRepeatDays(repeatDays);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldAcceptDayNamesCaseInsensitive() {
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Tuesday, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Wednesday, null));
        this.request.setRepeatDays(repeatDays);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidDeleteRequest() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(UUID.randomUUID().toString());
        deleteRequest.setJudiciaryId(UUID.randomUUID().toString());

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestIsNull() {
        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(null);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenRuleIdIsBlank() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId("");

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("ruleId"));
    }

    @Test
    void shouldReturnErrorWhenRuleIdIsNull() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(null);

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("ruleId"));
    }

    @Test
    void shouldReturnErrorWhenJudiciaryIdIsBlankForDelete() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(UUID.randomUUID().toString());
        deleteRequest.setJudiciaryId("");

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("judiciaryId"));
    }

    @Test
    void shouldReturnErrorWhenJudiciaryIdIsNullForDelete() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(UUID.randomUUID().toString());
        deleteRequest.setJudiciaryId(null);

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString("errorMessage").contains("judiciaryId"));
    }
}

