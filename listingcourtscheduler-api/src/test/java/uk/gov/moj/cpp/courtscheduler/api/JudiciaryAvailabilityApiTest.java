package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.converter.AddJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.DeleteJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciaryAvailabilityRuleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException;
import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityApiTest {

    @Mock
    private Enveloper enveloper;

    @Mock
    private Requester requester;

    @Mock
    private AddJudiciaryAvailabilityRuleConverter addJudiciaryAvailabilityRuleConverter;

    @Mock
    private DeleteJudiciaryAvailabilityRuleConverter deleteJudiciaryAvailabilityRuleConverter;

    @Mock
    private UpdateJudiciaryAvailabilityRuleConverter updateJudiciaryAvailabilityRuleConverter;

    @Mock
    private FindJudiciaryAvailabilityConverter findJudiciaryAvailabilityConverter;

    @Mock
    private FindJudiciaryAvailabilityRuleConverter findJudiciaryAvailabilityRuleConverter;

    @Mock
    private JudiciaryAvailabilityService judiciaryAvailabilityService;

    @Mock
    private JudiciaryAvailabilityRuleApiValidator judiciaryAvailabilityRuleApiValidator;

    @Mock
    private FindJudiciaryAvailabilityRuleResponseConverter findJudiciaryAvailabilityRuleResponseConverter;

    @Mock
    private GetJudiciaryAvailabilityRuleConverter getJudiciaryAvailabilityRuleConverter;

    @Mock
    private GetJudiciaryAvailabilityRuleResponseConverter getJudiciaryAvailabilityRuleResponseConverter;

    @Mock
    private Function<Object, JsonEnvelope> function;

    @InjectMocks
    private JudiciaryAvailabilityApi judiciaryAvailabilityApi;

    @Test
    void shouldDeleteJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule";
        final JsonEnvelope deleteEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        when(this.enveloper.withMetadataFrom(deleteEnvelope, requestName)).thenReturn(function);
        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        judiciaryAvailabilityApi.deleteJudiciaryAvailabilityRule(deleteEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(deleteEnvelope, requestName);
        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).deleteJudiciaryAvailabilityRule(any(DeleteJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenDeleteValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule";
        final JsonEnvelope deleteEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        // This test verifies that validation exceptions are still thrown when validator returns an error
        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Request cannot be null")
                .build();

        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                judiciaryAvailabilityApi.deleteJudiciaryAvailabilityRule(deleteEnvelope));

        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService, never()).deleteJudiciaryAvailabilityRule(any());
    }

    @Test
    void shouldAddJudiciaryAvailabilityRule() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("sessionType", "AM")
                .add("repeatDays", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("day", "Monday")
                                .build())
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule";
        final JsonEnvelope addEnvelope = createEnvelope(requestName, jsonPayloadObject);

        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(addEnvelope, requestName)).thenReturn(function);
        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        judiciaryAvailabilityApi.addJudiciaryAvailabilityRule(addEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(addEnvelope, requestName);
        verify(addJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).addJudiciaryAvailabilityRule(any(AddJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenAddJudiciaryAvailabilityRuleHasInvalidData() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule";
        final JsonEnvelope addEnvelope = createEnvelope(requestName, jsonPayloadObject);

        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        // courtHouseId not set to trigger validation error

        final JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "courtHouseId cannot be null")
                .build();

        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () -> judiciaryAvailabilityApi.addJudiciaryAvailabilityRule(addEnvelope));

        verify(addJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService, never()).addJudiciaryAvailabilityRule(any());
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("sessionType", "AM")
                .add("repeatDays", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("day", "Monday")
                                .build())
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(updateEnvelope, requestName)).thenReturn(function);
        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        judiciaryAvailabilityApi.updateJudiciaryAvailabilityRule(updateEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(updateEnvelope, requestName);
        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).updateJudiciaryAvailabilityRule(any(UpdateJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithRuleIdFromRequestParameter() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(updateEnvelope, requestName)).thenReturn(function);
        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        judiciaryAvailabilityApi.updateJudiciaryAvailabilityRule(updateEnvelope);

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).updateJudiciaryAvailabilityRule(any(UpdateJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenUpdateValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "ruleId cannot be blank")
                .build();

        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                judiciaryAvailabilityApi.updateJudiciaryAvailabilityRule(updateEnvelope));

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService, never()).updateJudiciaryAvailabilityRule(any());
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithUnAvailabilities() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .add("unavailabilities", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("startDate", "2026-02-10")
                                .add("endDate", "2026-02-12")
                                .add("reason", "ANNUAL_LEAVE")
                                .build())
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(updateEnvelope, requestName)).thenReturn(function);
        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        judiciaryAvailabilityApi.updateJudiciaryAvailabilityRule(updateEnvelope);

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).updateJudiciaryAvailabilityRule(any(UpdateJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRules() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 10)
                .add("pageNumber", 1)
                .add("withJudiciary", true)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability.rule";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(true);

        final List<JudiciaryAvailabilityRuleResponse> rules = new ArrayList<>();
        final JudiciaryAvailabilityRuleResponse ruleResponse = new JudiciaryAvailabilityRuleResponse();
        ruleResponse.setId(randomUUID().toString());
        ruleResponse.setJudiciaryId(judiciaryId);
        ruleResponse.setCourtHouseId(courtHouseId);
        rules.add(ruleResponse);

        final FindJudiciaryAvailabilityRuleResponse serviceResponse = new FindJudiciaryAvailabilityRuleResponse(rules, 1, 1, 10);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rules", createArrayBuilder().build())
                .add("totalCount", 1)
                .add("pageNumber", 1)
                .add("pageSize", 10)
                .add("judiciaries", createArrayBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(findJudiciaryAvailabilityRuleResponseConverter.convert(any(FindJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        judiciaryAvailabilityApi.findJudiciaryAvailabilityRules(findEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(findJudiciaryAvailabilityRuleResponseConverter).convert(any(FindJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaryId() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 20)
                .add("pageNumber", 1)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability.rule";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(20);
        request.setPageNumber(1);

        final FindJudiciaryAvailabilityRuleResponse serviceResponse = new FindJudiciaryAvailabilityRuleResponse(Collections.emptyList(), 0, 1, 20);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rules", createArrayBuilder().build())
                .add("totalCount", 0)
                .add("pageNumber", 1)
                .add("pageSize", 20)
                .add("judiciaries", createArrayBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(findJudiciaryAvailabilityRuleResponseConverter.convert(any(FindJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        judiciaryAvailabilityApi.findJudiciaryAvailabilityRules(findEnvelope);

        verify(findJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(findJudiciaryAvailabilityRuleResponseConverter).convert(any(FindJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPagination() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 5)
                .add("pageNumber", 2)
                .add("withJudiciary", false)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability.rule";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(5);
        request.setPageNumber(2);
        request.setWithJudiciary(false);

        final FindJudiciaryAvailabilityRuleResponse serviceResponse = new FindJudiciaryAvailabilityRuleResponse(Collections.emptyList(), 0, 2, 5);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rules", createArrayBuilder().build())
                .add("totalCount", 0)
                .add("pageNumber", 2)
                .add("pageSize", 5)
                .add("judiciaries", createArrayBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(findJudiciaryAvailabilityRuleResponseConverter.convert(any(FindJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        judiciaryAvailabilityApi.findJudiciaryAvailabilityRules(findEnvelope);

        verify(findJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        final ArgumentCaptor<FindJudiciaryAvailabilityRuleRequest> requestCaptor = ArgumentCaptor.forClass(FindJudiciaryAvailabilityRuleRequest.class);
        verify(judiciaryAvailabilityService).findJudiciaryAvailabilityRules(requestCaptor.capture(), eq(requester));
        assertEquals(5, requestCaptor.getValue().getPageSize(), "pageSize should be 5");
        assertEquals(2, requestCaptor.getValue().getPageNumber(), "pageNumber should be 2");
        assertEquals(false, requestCaptor.getValue().getWithJudiciary(), "withJudiciary should be false");
        verify(findJudiciaryAvailabilityRuleResponseConverter).convert(any(FindJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldFindJudiciaryAvailability() {
        final String judiciaryId1 = randomUUID().toString();
        final String judiciaryId2 = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);

        final List<String> availableJudiciaries = new ArrayList<>();
        availableJudiciaries.add(judiciaryId1);
        availableJudiciaries.add(judiciaryId2);
        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse(availableJudiciaries);

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = judiciaryAvailabilityApi.findJudiciaryAvailability(findEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityWithEmptyList() {
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);

        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse(Collections.emptyList());
        final JsonEnvelope expectedResult = createEnvelope(requestName, createObjectBuilder().add("availableJudiciaries", Json.createArrayBuilder().build()).build());

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(function.apply(any())).thenReturn(expectedResult);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = judiciaryAvailabilityApi.findJudiciaryAvailability(findEnvelope);

        assertNotNull(result, "Result should not be null");
        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityWithJudiciaryId() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);

        final List<String> availableJudiciaries = new ArrayList<>();
        availableJudiciaries.add(judiciaryId);
        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse(availableJudiciaries);

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = judiciaryAvailabilityApi.findJudiciaryAvailability(findEnvelope);

        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        final ArgumentCaptor<FindJudiciaryAvailabilityRequest> requestCaptor = ArgumentCaptor.forClass(FindJudiciaryAvailabilityRequest.class);
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(requestCaptor.capture());
        assertEquals(judiciaryId, requestCaptor.getValue().getJudiciaryId(), "judiciaryId should be set");
        assertEquals(courtHouseId, requestCaptor.getValue().getCourtHouseId(), "courtHouseId should be set");
    }

    @Test
    void shouldFindJudiciaryAvailabilityWithNullResponse() {
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);

        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse();
        serviceResponse.setAvailableJudiciaries(null);
        final JsonEnvelope expectedResult = createEnvelope(requestName, createObjectBuilder().add("availableJudiciaries", Json.createArrayBuilder().build()).build());

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(function.apply(any())).thenReturn(expectedResult);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = judiciaryAvailabilityApi.findJudiciaryAvailability(findEnvelope);

        assertNotNull(result, "Result should not be null");
        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        // ruleId is a path parameter, may be included in payload by framework
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("withJudiciary", true)
                .build();
        final String requestName = "courtscheduler.judiciary.get.availability.rule";
        final JsonEnvelope getEnvelope = createEnvelope(requestName, jsonPayloadObject);

        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(true);

        final JudiciaryAvailabilityRuleResponse ruleResponse = new JudiciaryAvailabilityRuleResponse();
        ruleResponse.setId(ruleId);
        ruleResponse.setJudiciaryId(judiciaryId);

        final uk.gov.moj.cpp.courtscheduler.domain.Judiciary judiciary = new uk.gov.moj.cpp.courtscheduler.domain.Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");

        final GetJudiciaryAvailabilityRuleResponse serviceResponse = new GetJudiciaryAvailabilityRuleResponse(ruleResponse, judiciary);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rule", createObjectBuilder().build())
                .add("judiciary", createObjectBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(getEnvelope, requestName)).thenReturn(function);
        when(getJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(getJudiciaryAvailabilityRuleResponseConverter.convert(any(GetJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        judiciaryAvailabilityApi.getJudiciaryAvailabilityRule(getEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(getEnvelope, requestName);
        verify(getJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(getJudiciaryAvailabilityRuleResponseConverter).convert(any(GetJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithoutJudiciary() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        // ruleId is a path parameter, may be included in payload by framework
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("withJudiciary", false)
                .build();
        final String requestName = "courtscheduler.judiciary.get.availability.rule";
        final JsonEnvelope getEnvelope = createEnvelope(requestName, jsonPayloadObject);

        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRuleResponse ruleResponse = new JudiciaryAvailabilityRuleResponse();
        ruleResponse.setId(ruleId);
        ruleResponse.setJudiciaryId(judiciaryId);

        final GetJudiciaryAvailabilityRuleResponse serviceResponse = new GetJudiciaryAvailabilityRuleResponse(ruleResponse, null);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rule", createObjectBuilder().build())
                .addNull("judiciary")
                .build();

        when(this.enveloper.withMetadataFrom(getEnvelope, requestName)).thenReturn(function);
        when(getJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(getJudiciaryAvailabilityRuleResponseConverter.convert(any(GetJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        judiciaryAvailabilityApi.getJudiciaryAvailabilityRule(getEnvelope);

        verify(getJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(getJudiciaryAvailabilityRuleResponseConverter).convert(any(GetJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldReturnSuccessWhenAddJudiciaryAvailabilityValidationPasses() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest request =
                new uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(EMPTY_JSON_OBJECT);
        when(enveloper.withMetadataFrom(validationEnvelope, requestName)).thenReturn(function);
        when(function.apply(any(JsonObject.class))).thenReturn(validationEnvelope);

        judiciaryAvailabilityApi.validateAddJudiciaryAvailabilityRule(validationEnvelope);

        verify(addJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(), any());
        verify(enveloper).withMetadataFrom(validationEnvelope, requestName);
    }

    @Test
    void shouldReturnFailureWhenAddJudiciaryAvailabilityValidationFails() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest request =
                new uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        final JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Date range cannot exceed 3 years")
                .build();

        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                judiciaryAvailabilityApi.validateAddJudiciaryAvailabilityRule(validationEnvelope));
    }

    @Test
    void shouldReturnSuccessWhenUpdateJudiciaryAvailabilityValidationPasses() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(EMPTY_JSON_OBJECT);
        when(enveloper.withMetadataFrom(validationEnvelope, requestName)).thenReturn(function);
        when(function.apply(any(JsonObject.class))).thenReturn(validationEnvelope);

        judiciaryAvailabilityApi.validateUpdateJudiciaryAvailabilityRule(validationEnvelope);

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(), any());
        verify(enveloper).withMetadataFrom(validationEnvelope, requestName);
    }

    @Test
    void shouldReturnFailureWhenUpdateJudiciaryAvailabilityValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        final JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "If start date is changed, it must be in the future")
                .build();

        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                judiciaryAvailabilityApi.validateUpdateJudiciaryAvailabilityRule(validationEnvelope));
    }

    @Test
    void shouldValidateDeleteJudiciaryAvailabilityRuleSuccessfully() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);
        when(enveloper.withMetadataFrom(validationEnvelope, requestName)).thenReturn(function);
        when(function.apply(any(JsonObject.class))).thenReturn(validationEnvelope);

        judiciaryAvailabilityApi.validateDeleteJudiciaryAvailabilityRule(validationEnvelope);

        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
    }

    @Test
    void shouldThrowUnprocessableEntityExceptionWhenDeleteValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Cannot delete availability rule. Rule is already applied to session session-123 on 2026-01-15 (AM)")
                .build();

        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                judiciaryAvailabilityApi.validateDeleteJudiciaryAvailabilityRule(validationEnvelope));

        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
    }

    private JsonEnvelope createEnvelope(final String name, final JsonValue payload) {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();

        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(name)
                .withId(uuid)
                .withUserId(userId.toString())
                .build();
        return new DefaultJsonEnvelopeProvider().envelopeFrom(metadata, payload);
    }
}
