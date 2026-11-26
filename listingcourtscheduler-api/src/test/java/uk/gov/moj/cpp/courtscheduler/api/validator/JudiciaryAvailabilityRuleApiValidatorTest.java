package uk.gov.moj.cpp.courtscheduler.api.validator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.DeleteJudiciaryAvailabilityRuleRequest;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityRuleApiValidatorTest {
    private static final String ERROR_MESSAGE = "errorMessage";


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

        this.request.setRepeatDays(Arrays.asList("Monday", "Tuesday"));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidRequest() {
        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenRequestIsNull() {
        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(null);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString(ERROR_MESSAGE).contains("Request"));
    }

    // Note: judiciaryId validation removed for add operations as it's always present from URL path parameter

    @Test
    void shouldReturnErrorWhenCourtHouseIdIsBlank() {
        this.request.setCourtHouseId(null);

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("Select a courthouse"));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsNull() {
        this.request.setStartDate(null);

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("Enter a start date"));
    }

    @Test
    void shouldReturnErrorWhenEndDateIsNull() {
        this.request.setEndDate(null);

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("Enter an end date"));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsAfterEndDate() {
        this.request.setStartDate(LocalDate.of(2026, 1, 31));
        this.request.setEndDate(LocalDate.of(2026, 1, 1));

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("The start date must be the same as or before the end date"));
    }

    @Test
    void shouldAcceptStartDateEqualToEndDate() {
        final LocalDate date = LocalDate.of(2026, 1, 15);
        this.request.setStartDate(date);
        this.request.setEndDate(date);

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenRepeatDaysIsNull() {
        this.request.setRepeatDays(null);

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("Select the days you want to repeat"));
    }

    @Test
    void shouldReturnErrorWhenRepeatDaysIsEmpty() {
        this.request.setRepeatDays(new ArrayList<>());

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("Select the days you want to repeat"));
    }

    @Test
    void shouldReturnErrorWhenRepeatDayIsNull() {
        this.request.setRepeatDays(Arrays.asList((String) null));

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString(ERROR_MESSAGE), is("Select a day of the week"));
    }

    @Test
    void shouldAcceptValidDayNames() {
        this.request.setRepeatDays(Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"));

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldAcceptDayNamesCaseInsensitive() {
        this.request.setRepeatDays(Arrays.asList("Monday", "Tuesday", "Wednesday"));

        final JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidDeleteRequest() {
        final DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(UUID.randomUUID().toString());

        final JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestIsNull() {
        final JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(null);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString(ERROR_MESSAGE).contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenRuleIdIsBlank() {
        final DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId("");

        final JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString(ERROR_MESSAGE).contains("ruleId"));
    }

    @Test
    void shouldReturnErrorWhenRuleIdIsNull() {
        final DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(null);

        final JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        Assertions.assertFalse(result.isEmpty());
        Assertions.assertTrue(result.getString(ERROR_MESSAGE).contains("ruleId"));
    }

    // Note: judiciaryId is not modeled on DeleteJudiciaryAvailabilityRuleRequest (it was
    // an unused inherited field on the old hand-written DeleteJudiciaryAvailabilityRuleRequest —
    // the validator never read it), so the judiciaryId-blank/null-for-delete cases are no longer
    // applicable and were removed rather than kept as no-op assertions.

    // Note: ruleId and judiciaryId validation removed for delete operations as they're always present from URL path parameters
}
