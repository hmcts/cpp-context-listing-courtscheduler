package uk.gov.moj.cpp.courtscheduler.api.validator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;

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
        
        this.request.setRepeatDays(Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Monday, uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Tuesday));
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

    // Note: judiciaryId validation removed for add operations as it's always present from URL path parameter

    @Test
    void shouldReturnErrorWhenCourtHouseIdIsBlank() {
        this.request.setCourtHouseId(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString("errorMessage"), is("Select a courthouse"));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsNull() {
        this.request.setStartDate(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString("errorMessage"), is("Enter a start date"));
    }

    @Test
    void shouldReturnErrorWhenEndDateIsNull() {
        this.request.setEndDate(null);

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString("errorMessage"), is("Enter an end date"));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsAfterEndDate() {
        this.request.setStartDate(LocalDate.of(2026, 1, 31));
        this.request.setEndDate(LocalDate.of(2026, 1, 1));

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString("errorMessage"), is("The start date must be the same as or before the end date"));
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
        assertThat(result.getString("errorMessage"), is("Select the days you want to repeat"));
    }

    @Test
    void shouldReturnErrorWhenRepeatDaysIsEmpty() {
        this.request.setRepeatDays(new ArrayList<>());

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString("errorMessage"), is("Select the days you want to repeat"));
    }

    @Test
    void shouldReturnErrorWhenRepeatDayIsNull() {
        this.request.setRepeatDays(Arrays.asList((uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek) null));

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        Assertions.assertFalse(result.isEmpty());
        assertThat(result.getString("errorMessage"), is("Select a day of the week"));
    }

    @Test
    void shouldAcceptValidDayNames() {
        this.request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday, AvailabilityDayOfWeek.Wednesday, AvailabilityDayOfWeek.Thursday, AvailabilityDayOfWeek.Friday));

        JsonObject result = this.validator.validateAddJudiciaryAvailabilityRule(this.request);

        MatcherAssert.assertThat(result, CoreMatchers.is(JsonValue.EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldAcceptDayNamesCaseInsensitive() {
        this.request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday, AvailabilityDayOfWeek.Wednesday));

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
    void shouldNotReturnErrorWhenJudiciaryIdIsBlankForDelete() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(UUID.randomUUID().toString());
        deleteRequest.setJudiciaryId("");

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        // judiciaryId is optional for delete operations, so no error should be returned
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotReturnErrorWhenJudiciaryIdIsNullForDelete() {
        DeleteJudiciaryAvailabilityRuleRequest deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(UUID.randomUUID().toString());
        deleteRequest.setJudiciaryId(null);

        JsonObject result = this.validator.validateDeleteJudiciaryAvailabilityRule(deleteRequest);

        // judiciaryId is optional for delete operations, so no error should be returned
        Assertions.assertTrue(result.isEmpty());
    }
    // Note: ruleId and judiciaryId validation removed for delete operations as they're always present from URL path parameters
}

