package uk.gov.moj.cpp.courtscheduler.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.converter.AssignCourtroomRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignJudiciariesRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignJudiciaryToSessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CreateSessionsRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ValidateSessionAvailabilityRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.ChangeJudiciaryForHearingsHelper;
import uk.gov.moj.cpp.courtscheduler.api.validator.AssignJudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciaryToSessionsRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CourtSchedulerApiTest {

    // shared
    @Mock private ObjectMapper objectMapper;
    @Mock private HttpServletRequest request;

    // court schedule CRUD
    @Mock private SessionsService sessionsService;
    @Mock private SessionsApiValidator sessionsApiValidator;
    @Mock private CourtScheduleApiValidator courtScheduleApiValidator;
    @Mock private CreateSessionsRequestParamConverter createSessionsRequestParamConverter;
    @Mock private UpdateCourtScheduleConverter updateCourtScheduleConverter;
    @Mock private SessionsConverter sessionsConverter;
    @Mock private AssignCourtroomRequestConverter assignCourtroomRequestConverter;
    @Mock private ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter;

    // judiciary assign / unassign
    @Mock private JudiciaryAssignmentService judiciaryAssignmentService;
    @Mock private JudiciaryUnassignmentService judiciaryUnassignmentService;
    @Mock private ChangeJudiciaryForHearingsHelper changeJudiciaryForHearingsHelper;
    @Mock private AssignJudiciariesApiValidator assignJudiciariesApiValidator;
    @Mock private JudiciariesApiValidator judiciariesApiValidator;
    @Mock private AssignJudiciariesRequestConverter assignJudiciariesRequestConverter;
    @Mock private AssignJudiciaryToSessionsConverter assignJudiciaryToSessionsConverter;

    // hearings
    @Mock private SlotsUpdateService slotsUpdateService;
    @Mock private SlotsRemoveService slotsRemoveService;
    @Mock private HearingSlotsApiValidator hearingSlotsApiValidator;
    @Mock private ListHearingSlotConverter listHearingSlotConverter;

    // MI
    @Mock private MiService miService;
    @Mock private MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter;

    // provisional booking
    @Mock private ProvisionalBookingService provisionalBookingService;
    @Mock private ProvisionalBookingApiValidator provisionalBookingApiValidator;
    @Mock private ProvisionalSlotConverter provisionalSlotConverter;

    @InjectMocks
    private CourtSchedulerApi courtSchedulerApi;

    private static final String ASSIGN_CONTENT_TYPE =
            "application/vnd.courtscheduler.assign-judiciary+json";
    private static final String UNASSIGN_CONTENT_TYPE =
            "application/vnd.courtscheduler.unassign.judiciary+json";
    private static final String JUDICIARIES = "judiciaries";
    private static final String JUDGE_ID_1 = "judge-1";

    // -----------------------------------------------------------------------
    // postCourtschedulerSessionJudiciary — dispatch
    // -----------------------------------------------------------------------

    @Test
    void shouldThrowUnsupportedMediaTypeWhenContentTypeIsUnrecognised() {
        when(request.getContentType()).thenReturn("application/json");

        assertThrows(ResponseStatusException.class,
                () -> courtSchedulerApi.postCourtschedulerSessionJudiciary(new HashMap<>()));
    }

    @Test
    void shouldThrowUnsupportedMediaTypeWhenContentTypeIsNull() {
        when(request.getContentType()).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> courtSchedulerApi.postCourtschedulerSessionJudiciary(new HashMap<>()));
    }

    // -----------------------------------------------------------------------
    // assignJudiciary — happy path
    // -----------------------------------------------------------------------

    @Test
    void assignJudiciary_shouldReturn202AndInvokeHelperWhenValidationPasses() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        final AssignJudiciariesRequest dto = AssignJudiciariesRequest.builder().build();
        final List<String> changedIds = List.of("schedule-1");
        final List<JsonObject> payloads = List.of(Json.createObjectBuilder().build());

        when(request.getContentType()).thenReturn(ASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(assignJudiciariesRequestConverter.convert(any())).thenReturn(dto);
        when(assignJudiciariesApiValidator.validate(dto)).thenReturn(Json.createObjectBuilder().build());
        when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), anyString()))
                .thenReturn(changedIds);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedIds))
                .thenReturn(payloads);

        final ResponseEntity<Void> response =
                courtSchedulerApi.postCourtschedulerSessionJudiciary(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(changedIds);
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(payloads);
    }

    @Test
    void assignJudiciary_shouldPassEmptyChangedIdsListToHelperWhenServiceReturnsNone() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        final AssignJudiciariesRequest dto = AssignJudiciariesRequest.builder().build();

        when(request.getContentType()).thenReturn(ASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(assignJudiciariesRequestConverter.convert(any())).thenReturn(dto);
        when(assignJudiciariesApiValidator.validate(dto)).thenReturn(Json.createObjectBuilder().build());
        when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), anyString()))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(List.of()))
                .thenReturn(List.of());

        final ResponseEntity<Void> response =
                courtSchedulerApi.postCourtschedulerSessionJudiciary(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(List.of());
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(List.of());
    }

    // -----------------------------------------------------------------------
    // assignJudiciary — validation failure
    // -----------------------------------------------------------------------

    @Test
    void assignJudiciary_shouldThrowValidationExceptionWhenValidationFails() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        final AssignJudiciariesRequest dto = AssignJudiciariesRequest.builder().build();
        final JsonObject errors = Json.createObjectBuilder().add("error", "missing field").build();

        when(request.getContentType()).thenReturn(ASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(assignJudiciariesRequestConverter.convert(any())).thenReturn(dto);
        when(assignJudiciariesApiValidator.validate(dto)).thenReturn(errors);

        assertThrows(ValidationException.class,
                () -> courtSchedulerApi.postCourtschedulerSessionJudiciary(body));

        verify(judiciaryAssignmentService, never()).assignJudiciaries(any(), anyString());
        verify(changeJudiciaryForHearingsHelper, never()).createChangeJudiciaryForHearingsPayloads(anyList());
    }

    // -----------------------------------------------------------------------
    // unassignJudiciary — happy path
    // -----------------------------------------------------------------------

    @Test
    void unassignJudiciary_shouldReturn202AndInvokeHelperWhenValidationPasses() throws Exception {
        final List<String> changedIds = List.of("schedule-1");
        final List<JsonObject> payloads = List.of(Json.createObjectBuilder().build());

        final Map<String, Object> judiciary = new HashMap<>();
        judiciary.put("judiciaryId", JUDGE_ID_1);
        judiciary.put("sessionIds", List.of("session-1"));

        final Map<String, Object> body = new HashMap<>();
        body.put(JUDICIARIES, List.of(judiciary));

        when(request.getContentType()).thenReturn(UNASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(judiciariesApiValidator.validateUnassignJudiciaryRequest(any()))
                .thenReturn(Json.createObjectBuilder().build());
        when(judiciaryUnassignmentService.unassignJudiciary(any(), anyString(), any(Boolean.class)))
                .thenReturn(changedIds);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedIds))
                .thenReturn(payloads);

        final ResponseEntity<Void> response =
                courtSchedulerApi.postCourtschedulerSessionJudiciary(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(changedIds);
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(payloads);
    }

    @Test
    void unassignJudiciary_shouldPassSkipValidationsTrueWhenBodyContainsFlag() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put(JUDICIARIES, List.of());
        body.put("skipValidations", true);

        when(request.getContentType()).thenReturn(UNASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(judiciariesApiValidator.validateUnassignJudiciaryRequest(any()))
                .thenReturn(Json.createObjectBuilder().build());
        when(judiciaryUnassignmentService.unassignJudiciary(any(), anyString(), any(Boolean.class)))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(anyList()))
                .thenReturn(List.of());

        courtSchedulerApi.postCourtschedulerSessionJudiciary(body);

        verify(judiciaryUnassignmentService).unassignJudiciary(any(), anyString(), org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void unassignJudiciary_shouldPassSkipValidationsFalseWhenFlagAbsentFromBody() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put(JUDICIARIES, List.of());

        when(request.getContentType()).thenReturn(UNASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(judiciariesApiValidator.validateUnassignJudiciaryRequest(any()))
                .thenReturn(Json.createObjectBuilder().build());
        when(judiciaryUnassignmentService.unassignJudiciary(any(), anyString(), any(Boolean.class)))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(anyList()))
                .thenReturn(List.of());

        courtSchedulerApi.postCourtschedulerSessionJudiciary(body);

        verify(judiciaryUnassignmentService).unassignJudiciary(any(), anyString(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void unassignJudiciary_shouldMapMultipleJudiciariedAndSessionIdsIntoServiceCall() throws Exception {
        final List<Map<String, Object>> judiciaries = new ArrayList<>();
        final Map<String, Object> j1 = new HashMap<>();
        j1.put("judiciaryId", JUDGE_ID_1);
        j1.put("sessionIds", List.of("session-a", "session-b"));
        final Map<String, Object> j2 = new HashMap<>();
        j2.put("judiciaryId", "judge-2");
        j2.put("sessionIds", List.of("session-c"));
        judiciaries.add(j1);
        judiciaries.add(j2);

        final Map<String, Object> body = new HashMap<>();
        body.put(JUDICIARIES, judiciaries);

        when(request.getContentType()).thenReturn(UNASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(judiciariesApiValidator.validateUnassignJudiciaryRequest(any()))
                .thenReturn(Json.createObjectBuilder().build());
        when(judiciaryUnassignmentService.unassignJudiciary(any(), anyString(), any(Boolean.class)))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(anyList()))
                .thenReturn(List.of());

        final ResponseEntity<Void> response =
                courtSchedulerApi.postCourtschedulerSessionJudiciary(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(judiciaryUnassignmentService).unassignJudiciary(
                org.mockito.ArgumentMatchers.argThat(map ->
                        map.containsKey(JUDGE_ID_1) && map.get(JUDGE_ID_1).size() == 2
                                && map.containsKey("judge-2") && map.get("judge-2").size() == 1),
                anyString(),
                org.mockito.ArgumentMatchers.eq(false));
    }

    // -----------------------------------------------------------------------
    // unassignJudiciary — validation failure
    // -----------------------------------------------------------------------

    @Test
    void unassignJudiciary_shouldThrowValidationExceptionWhenValidationFails() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put(JUDICIARIES, List.of());
        final JsonObject errors = Json.createObjectBuilder().add("error", "bad request").build();

        when(request.getContentType()).thenReturn(UNASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(judiciariesApiValidator.validateUnassignJudiciaryRequest(any())).thenReturn(errors);

        assertThrows(ValidationException.class,
                () -> courtSchedulerApi.postCourtschedulerSessionJudiciary(body));

        verify(judiciaryUnassignmentService, never()).unassignJudiciary(any(), anyString(), any(Boolean.class));
        verify(changeJudiciaryForHearingsHelper, never()).createChangeJudiciaryForHearingsPayloads(anyList());
    }

    // -----------------------------------------------------------------------
    // unassignJudiciary — IllegalStateException → BAD_REQUEST
    // -----------------------------------------------------------------------

    @Test
    void unassignJudiciary_shouldThrowBadRequestWhenServiceThrowsIllegalStateException() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        body.put(JUDICIARIES, List.of());

        when(request.getContentType()).thenReturn(UNASSIGN_CONTENT_TYPE);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(judiciariesApiValidator.validateUnassignJudiciaryRequest(any()))
                .thenReturn(Json.createObjectBuilder().build());
        when(judiciaryUnassignmentService.unassignJudiciary(any(), anyString(), any(Boolean.class)))
                .thenThrow(new IllegalStateException("constraint violation"));

        final ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> courtSchedulerApi.postCourtschedulerSessionJudiciary(body));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(changeJudiciaryForHearingsHelper, never()).createChangeJudiciaryForHearingsPayloads(anyList());
    }

    // -----------------------------------------------------------------------
    // postBulkAssignJudiciaries
    // -----------------------------------------------------------------------

    @Test
    void bulkAssignJudiciaries_shouldReturn202AndInvokeHelperWhenServiceSucceeds() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        final AssignJudiciaryToSessionsRequest dto = AssignJudiciaryToSessionsRequest.builder()
                .withCourtScheduleIds(List.of("cs-1"))
                .build();
        final List<String> changedIds = List.of("cs-1");
        final List<JsonObject> payloads = List.of(Json.createObjectBuilder().build());

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(assignJudiciaryToSessionsConverter.convert(any())).thenReturn(dto);
        when(judiciaryAssignmentService.assignJudiciaryToSessions(any(AssignJudiciaryToSessionsRequest.class), anyString()))
                .thenReturn(changedIds);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedIds))
                .thenReturn(payloads);

        final ResponseEntity<Void> response = courtSchedulerApi.postBulkAssignJudiciaries(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(changedIds);
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(payloads);
    }

    @Test
    void bulkAssignJudiciaries_shouldThrowValidationExceptionWhenServiceThrowsIllegalArgumentException()
            throws Exception {
        final Map<String, Object> body = new HashMap<>();
        final AssignJudiciaryToSessionsRequest dto = AssignJudiciaryToSessionsRequest.builder().build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(assignJudiciaryToSessionsConverter.convert(any())).thenReturn(dto);
        when(judiciaryAssignmentService.assignJudiciaryToSessions(any(AssignJudiciaryToSessionsRequest.class), anyString()))
                .thenThrow(new IllegalArgumentException("invalid session"));

        assertThrows(ValidationException.class, () -> courtSchedulerApi.postBulkAssignJudiciaries(body));

        verify(changeJudiciaryForHearingsHelper, never()).createChangeJudiciaryForHearingsPayloads(anyList());
    }

    // -----------------------------------------------------------------------
    // postRemoveAllJudiciaries
    // -----------------------------------------------------------------------

    @Test
    void removeAllJudiciaries_shouldReturn202AndInvokeHelperWhenServiceSucceeds() throws Exception {
        final Map<String, Object> body = new HashMap<>();
        final List<String> changedIds = List.of("cs-1");
        final List<JsonObject> payloads = List.of(Json.createObjectBuilder().build());

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"courtScheduleIds\":[\"cs-1\"]}");
        when(judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(List.of("cs-1")))
                .thenReturn(changedIds);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedIds))
                .thenReturn(payloads);

        final ResponseEntity<Void> response = courtSchedulerApi.postRemoveAllJudiciaries(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(changedIds);
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(payloads);
    }

    @Test
    void removeAllJudiciaries_shouldThrowBadRequestWhenCourtScheduleIdsMissing() throws Exception {
        final Map<String, Object> body = new HashMap<>();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        final ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> courtSchedulerApi.postRemoveAllJudiciaries(body));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(judiciaryUnassignmentService, never()).removeAllJudiciaryByCourtScheduleIds(anyList());
    }

    @Test
    void removeAllJudiciaries_shouldThrowBadRequestWhenCourtScheduleIdsEmpty() throws Exception {
        final Map<String, Object> body = new HashMap<>();

        when(objectMapper.writeValueAsString(any())).thenReturn("{\"courtScheduleIds\":[]}");

        final ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> courtSchedulerApi.postRemoveAllJudiciaries(body));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(judiciaryUnassignmentService, never()).removeAllJudiciaryByCourtScheduleIds(anyList());
    }
}