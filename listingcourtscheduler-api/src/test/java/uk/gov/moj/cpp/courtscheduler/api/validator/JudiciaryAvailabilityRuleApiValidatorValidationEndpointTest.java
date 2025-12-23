package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.util.UUID.randomUUID;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.json.JsonObject;

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

    @BeforeEach
    void setUp() {
        addRequest = new AddJudiciaryAvailabilityRuleRequest();
        addRequest.setJudiciaryId(randomUUID().toString());
        addRequest.setCourtHouseId(randomUUID().toString());
        addRequest.setStartDate(LocalDate.now().plusDays(1));
        addRequest.setEndDate(LocalDate.now().plusDays(31));
        addRequest.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null)
        ));

        updateRequest = new UpdateJudiciaryAvailabilityRuleRequest();
        updateRequest.setRuleId(randomUUID().toString());
        updateRequest.setJudiciaryId(randomUUID().toString());
        updateRequest.setCourtHouseId(randomUUID().toString());
        updateRequest.setStartDate(LocalDate.now().plusDays(1));
        updateRequest.setEndDate(LocalDate.now().plusDays(31));
        updateRequest.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null)
        ));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidAddRequest() {
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(new ArrayList<>());

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
        List<String> businessErrors = Arrays.asList("Date range cannot exceed 3 years");
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(businessErrors);

        JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("3 years"));
    }

    @Test
    void shouldReturnErrorWhenAddRequestHasMultipleBusinessRuleViolations() {
        List<String> businessErrors = Arrays.asList(
                "Date range cannot exceed 3 years",
                "Start date must be in the future during creation"
        );
        when(service.validateAddJudiciaryAvailabilityRule(addRequest)).thenReturn(businessErrors);

        JsonObject result = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(addRequest, service);

        assertFalse(result.isEmpty());
        String errorMessage = result.getString("errorMessage");
        assertTrue(errorMessage.contains("3 years"));
        assertTrue(errorMessage.contains("future"));
    }

    @Test
    void shouldReturnEmptyJsonObjectForValidUpdateRequest() {
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(new ArrayList<>());

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
        List<String> businessErrors = Arrays.asList("If start date is changed, it must be in the future");
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(businessErrors);

        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("future"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRequestHasMultipleBusinessRuleViolations() {
        List<String> businessErrors = Arrays.asList(
                "Date range cannot exceed 3 years",
                "Unavailabilities cannot overlap"
        );
        when(service.validateUpdateJudiciaryAvailabilityRule(updateRequest)).thenReturn(businessErrors);

        JsonObject result = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(updateRequest, service);

        assertFalse(result.isEmpty());
        String errorMessage = result.getString("errorMessage");
        assertTrue(errorMessage.contains("3 years"));
        assertTrue(errorMessage.contains("overlap"));
    }
}


