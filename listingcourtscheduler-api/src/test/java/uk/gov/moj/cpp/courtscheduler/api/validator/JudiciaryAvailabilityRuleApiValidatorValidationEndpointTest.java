package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.util.UUID.randomUUID;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;

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
        addRequest.setRepeatDays(Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Monday));

        updateRequest = new UpdateJudiciaryAvailabilityRuleRequest();
        updateRequest.setRuleId(randomUUID().toString());
        updateRequest.setJudiciaryId(randomUUID().toString());
        updateRequest.setCourtHouseId(randomUUID().toString());
        updateRequest.setStartDate(LocalDate.now().plusDays(1));
        updateRequest.setEndDate(LocalDate.now().plusDays(31));
        updateRequest.setRepeatDays(Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Monday));

        deleteRequest = new DeleteJudiciaryAvailabilityRuleRequest();
        deleteRequest.setRuleId(randomUUID().toString());
        deleteRequest.setJudiciaryId(randomUUID().toString());
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidAddRequest() {
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(null);

        JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertThat(result, is(EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenAddRequestIsNull() {
        JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(null, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenAddRequestHasBusinessRuleViolations() {
        String businessError = "Date range cannot exceed 3 years";
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(businessError);

        JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("3 years"));
    }

    @Test
    void shouldReturnErrorWhenAddRequestHasMultipleBusinessRuleViolations() {
        // Since we now return only the first error, this test should check for the first error only
        String businessError = "Date range cannot exceed 3 years";
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(businessError);

        JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertFalse(result.isEmpty());
        String errorMessage = result.getString("errorMessage");
        assertTrue(errorMessage.contains("3 years"));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidUpdateRequest() {
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(null);

        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertThat(result, is(EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestIsNull() {
        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(null, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestRuleIdIsBlank() {
        updateRequest.setRuleId("");

        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("ruleId"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestHasBusinessRuleViolations() {
        String businessError = "If start date is changed, it must be in the future";
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(businessError);

        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("future"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestHasMultipleBusinessRuleViolations() {
        // Since we now return only the first error, this test should check for the first error only
        String businessError = "Date range cannot exceed 3 years";
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(businessError);

        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        String errorMessage = result.getString("errorMessage");
        assertTrue(errorMessage.contains("3 years"));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidDeleteRequest() {
        when(service.validateDeleteJudiciaryAvailabilityRule(deleteRequest)).thenReturn(null);

        JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        assertThat(result, is(EMPTY_JSON_OBJECT));
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestIsNull() {
        JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(null, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Request"));
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestRuleIdIsBlank() {
        deleteRequest.setRuleId("");

        JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("ruleId"));
    }

    @Test
    void shouldNotReturnErrorWhenDeleteRequestJudiciaryIdIsBlank() {
        deleteRequest.setJudiciaryId("");

        JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        // judiciaryId is optional for delete operations, so no error should be returned
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenDeleteRequestHasBusinessRuleViolations() {
        String businessError = "Cannot delete availability rule. Rule is already applied to session session-123 on 2026-01-15 (AM)";
        when(service.validateDeleteJudiciaryAvailabilityRule(deleteRequest)).thenReturn(businessError);

        JsonObject result = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(deleteRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("already applied"));
    }
}


