package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.util.UUID.randomUUID;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateJudiciaryAvailabilityRuleRequest;

import java.time.LocalDate;
import java.util.Arrays;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityRuleApiValidatorValidationEndpointTest {
    private static final String ERROR_MESSAGE = "errorMessage";


    @Mock
    private JudiciaryAvailabilityService service;

    @InjectMocks
    private JudiciaryAvailabilityRuleApiValidator validator;

    private AddJudiciaryAvailabilityRuleRequest addRequest;
    private UpdateJudiciaryAvailabilityRuleRequest updateRequest;
    private DeleteJudiciaryAvailabilityRuleRequest deleteRequest;

    @BeforeEach
    void setUp() {
        addRequest = new AddJudiciaryAvailabilityRuleRequest();
        addRequest.setJudiciaryId(randomUUID().toString());
        addRequest.setCourtHouseId(randomUUID().toString());
        addRequest.setStartDate(LocalDate.now().plusDays(1));
        addRequest.setEndDate(LocalDate.now().plusDays(31));
        addRequest.setRepeatDays(Arrays.asList("Monday"));

        updateRequest = new UpdateJudiciaryAvailabilityRuleRequest();
        updateRequest.setRuleId(randomUUID().toString());
        updateRequest.setJudiciaryId(randomUUID().toString());
        updateRequest.setCourtHouseId(randomUUID().toString());
        updateRequest.setStartDate(LocalDate.now().plusDays(1));
        updateRequest.setEndDate(LocalDate.now().plusDays(31));
        updateRequest.setRepeatDays(Arrays.asList("Monday"));

        deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(randomUUID().toString());
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidAddRequest() {
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(null);

        final JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertThat(result, is(EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenAddRequestIsNull() {
        final JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(null, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenAddRequestHasBusinessRuleViolations() {
        final String businessError = "Date range cannot exceed 3 years";
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(businessError);

        final JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("3 years"));
    }

    @Test
    void shouldReturnErrorWhenAddRequestHasMultipleBusinessRuleViolations() {
        // Since we now return only the first error, this test should check for the first error only
        final String businessError = "Date range cannot exceed 3 years";
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(businessError);

        final JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertFalse(result.isEmpty());
        final String errorMessage = result.getString(ERROR_MESSAGE);
        assertTrue(errorMessage.contains("3 years"));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidUpdateRequest() {
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(null);

        final JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertThat(result, is(EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestIsNull() {
        final JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(null, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestRuleIdIsBlank() {
        updateRequest.setRuleId("");

        final JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("ruleId"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestHasBusinessRuleViolations() {
        final String businessError = "If start date is changed, it must be in the future";
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(businessError);

        final JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("future"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestHasMultipleBusinessRuleViolations() {
        // Since we now return only the first error, this test should check for the first error only
        final String businessError = "Date range cannot exceed 3 years";
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(businessError);

        final JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        final String errorMessage = result.getString(ERROR_MESSAGE);
        assertTrue(errorMessage.contains("3 years"));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidDeleteRequest() {
        when(service.validateDeleteJudiciaryAvailabilityRule(deleteRequest)).thenReturn(null);

        final JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        assertThat(result, is(EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestIsNull() {
        final JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(null, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestRuleIdIsBlank() {
        deleteRequest.setRuleId("");

        final JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("ruleId"));
    }

    // Note: judiciaryId is not modeled on DeleteJudiciaryAvailabilityRuleRequest (it was
    // an unused inherited field on the old hand-written request — never read by the validator),
    // so shouldNotReturnErrorWhenDeleteRequestJudiciaryIdIsBlank is no longer applicable.

    @Test
    void shouldReturnErrorWhenDeleteRequestHasBusinessRuleViolations() {
        final String businessError = "Cannot delete availability rule. Rule is already applied to session session-123 on 2026-01-15 (AM)";
        when(service.validateDeleteJudiciaryAvailabilityRule(deleteRequest)).thenReturn(businessError);

        final JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("already applied"));
    }
}
