package uk.gov.moj.cpp.courtscheduler.api;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;

import uk.gov.moj.cpp.courtscheduler.api.service.WeekCommencingSessionService;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.api.validator.WeekCommencingApiValidator;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Result;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleGroupedSession;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerGetCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class WeekCommencingApiTest {

    private static final String BUSINESS_TYPE = "TRL";
    private static final String FIRST_MONDAY = "2027-01-04";
    private static final String COURT_SCHEDULE_ID = "cs-1";
    private static final String ERROR_MESSAGE = "errorMessage";

    @Mock
    private SessionsService sessionsService;
    @Mock
    private SessionsApiValidator sessionsApiValidator;
    @Mock
    private CourtScheduleApiValidator courtScheduleApiValidator;
    @Mock
    private WeekCommencingSessionService weekCommencingSessionService;
    @Mock
    private WeekCommencingApiValidator weekCommencingApiValidator;
    @Mock
    private HttpServletRequest request;
    @Captor
    private ArgumentCaptor<CreateSessionRequestParam> createCaptor;
    @Captor
    private ArgumentCaptor<CourtScheduleRequestParam> courtScheduleParamCaptor;

    @Test
    void shouldCheckForDuplicatesThenEnrichValidateAndCreateAndReturn202() {
        final WeekCommencingCreateRequest body = new WeekCommencingCreateRequest().businessType(BUSINESS_TYPE).capacityInMinutes(360);
        final CreateSessionRequestParam createRequest = new CreateSessionRequestParam();
        when(weekCommencingApiValidator.getWeekCommencingDuplicateValidation(body)).thenReturn(EMPTY_JSON_OBJECT);
        when(weekCommencingSessionService.enrichCreateSessionRequest(body)).thenReturn(createRequest);
        when(weekCommencingApiValidator.getWeekCommencingCreateValidation(body, createRequest)).thenReturn(EMPTY_JSON_OBJECT);

        final ResponseEntity<Void> response = api().postWeekCommencingSession(body);

        assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
        assertThat(response.getBody(), is(nullValue()));
        final InOrder inOrder = inOrder(weekCommencingApiValidator, weekCommencingSessionService, sessionsService);
        inOrder.verify(weekCommencingApiValidator).getWeekCommencingDuplicateValidation(body);
        inOrder.verify(weekCommencingSessionService).enrichCreateSessionRequest(body);
        inOrder.verify(weekCommencingApiValidator).getWeekCommencingCreateValidation(body, createRequest);
        inOrder.verify(sessionsService).create(createRequest);
    }

    @Test
    void shouldRejectADuplicateBeforeEnrichingTheRequest() {
        final WeekCommencingCreateRequest body = new WeekCommencingCreateRequest().businessType(BUSINESS_TYPE).capacityInMinutes(360);
        when(weekCommencingApiValidator.getWeekCommencingDuplicateValidation(body))
                .thenReturn(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));

        final CourtSchedulerApi api = api();
        final ValidationException exception = assertThrows(ValidationException.class, () -> api.postWeekCommencingSession(body));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.DUPLICATE_SESSIONS));
        verifyNoInteractions(weekCommencingSessionService, sessionsService);
    }

    @Test
    void shouldReturnTheValidationErrorAndNotCreate() {
        final WeekCommencingCreateRequest body = new WeekCommencingCreateRequest().businessType(BUSINESS_TYPE).capacityInMinutes(360);
        final CreateSessionRequestParam createRequest = new CreateSessionRequestParam();
        when(weekCommencingApiValidator.getWeekCommencingDuplicateValidation(body)).thenReturn(EMPTY_JSON_OBJECT);
        when(weekCommencingSessionService.enrichCreateSessionRequest(body)).thenReturn(createRequest);
        when(weekCommencingApiValidator.getWeekCommencingCreateValidation(body, createRequest))
                .thenReturn(buildErrorResponse(ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));

        final CourtSchedulerApi api = api();
        final ValidationException exception = assertThrows(ValidationException.class, () -> api.postWeekCommencingSession(body));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
        verify(sessionsService, never()).create(any());
    }

    @Test
    void genericCreateShouldNeverPersistAClientSuppliedWeekCommencingFlag() {
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        final CreateSessionRequestParam body = new CreateSessionRequestParam()
                .sessions(List.of(new Session().sessionType("AD").isDraft(true).jurisdiction("CROWN").isWeekCommencing(true)))
                .sessionToBeAdded(new Session().sessionType("AD").isWeekCommencing(true))
                .repeatPattern(new RepeatPattern().startDate(FIRST_MONDAY).endDate(FIRST_MONDAY).frequency("ONCE"));

        api().postCourtschedulerCreateCourtschedule(body);

        verify(sessionsService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().getSessions().getFirst().getIsWeekCommencing(), is(nullValue()));
        assertThat(createCaptor.getValue().getSessionToBeAdded().getIsWeekCommencing(), is(nullValue()));
    }

    @Test
    void courtScheduleQueryShouldPassTheWeekCommencingFilterAndReturnTheFlagOnEachSession() {
        when(courtScheduleApiValidator.getCourtSchedulesValidation(any(CourtScheduleRequestParam.class))).thenReturn(EMPTY_JSON_OBJECT);
        final String courtRoomId = "room-1";
        when(sessionsService.getCourtSchedules(any(CourtScheduleRequestParam.class))).thenReturn(new ArrayList<>(List.of(
                new CourtSchedule().courtScheduleId("wc-1").courtRoomId(courtRoomId).sessionDate(LocalDate.of(2027, 1, 4))
                        .draft(true).isWeekCommencing(true),
                new CourtSchedule().courtScheduleId("wc-2").courtRoomId(courtRoomId).sessionDate(LocalDate.of(2027, 1, 11))
                        .draft(true).isWeekCommencing(true))));

        final CourtschedulerGetCourtSchedule body = api()
                .getCourtschedule("centre", FIRST_MONDAY, "2027-01-17", "10", "1", null, null, null, true).getBody();

        verify(sessionsService).getCourtSchedules(courtScheduleParamCaptor.capture());
        assertThat(courtScheduleParamCaptor.getValue().isWeekCommencing(), is(true));
        final List<CourtScheduleGroupedSession> sessions = body.getCourtSchedules().getFirst().getSessions();
        assertThat(sessions.stream().map(CourtScheduleGroupedSession::getCourtScheduleId).toList(), is(List.of("wc-1", "wc-2")));
        assertThat(sessions.stream().map(CourtScheduleGroupedSession::getIsWeekCommencing).toList(), is(List.of(true, true)));
        assertThat(sessions.stream().map(CourtScheduleGroupedSession::getIsDraft).toList(), is(List.of(true, true)));
    }

    @Test
    void shouldValidateEnrichUpdateAndReturn202ForWeekCommencingEdit() {
        final WeekCommencingUpdateRequest body = new WeekCommencingUpdateRequest().courtScheduleId(COURT_SCHEDULE_ID).capacityInMinutes(1800);
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persisted =
                new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();
        final UpdateCourtSchedule updateRequest = new UpdateCourtSchedule().courtScheduleId(COURT_SCHEDULE_ID);
        when(weekCommencingSessionService.loadPersistedSession(COURT_SCHEDULE_ID)).thenReturn(persisted);
        when(weekCommencingApiValidator.getWeekCommencingUpdateValidation(body, persisted)).thenReturn(EMPTY_JSON_OBJECT);
        when(weekCommencingSessionService.enrichUpdateSessionRequest(body, persisted)).thenReturn(updateRequest);
        when(sessionsApiValidator.getWeekCommencingSessionsUpdateValidation(updateRequest)).thenReturn(EMPTY_JSON_OBJECT);
        when(sessionsService.update(updateRequest)).thenReturn(new Result().success(true));

        final ResponseEntity<Void> response = api().putWeekCommencingSession(body);

        assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
        final InOrder inOrder = inOrder(weekCommencingApiValidator, weekCommencingSessionService, sessionsApiValidator, sessionsService);
        inOrder.verify(weekCommencingSessionService).loadPersistedSession(COURT_SCHEDULE_ID);
        inOrder.verify(weekCommencingApiValidator).getWeekCommencingUpdateValidation(body, persisted);
        inOrder.verify(weekCommencingSessionService).enrichUpdateSessionRequest(body, persisted);
        inOrder.verify(sessionsApiValidator).getWeekCommencingSessionsUpdateValidation(updateRequest);
        inOrder.verify(sessionsService).update(updateRequest);
    }

    @Test
    void shouldRejectEditWhenSessionIsNotWeekCommencing() {
        final WeekCommencingUpdateRequest body = new WeekCommencingUpdateRequest().courtScheduleId(COURT_SCHEDULE_ID).capacityInMinutes(1800);
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persisted =
                new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();
        when(weekCommencingSessionService.loadPersistedSession(COURT_SCHEDULE_ID)).thenReturn(persisted);
        when(weekCommencingApiValidator.getWeekCommencingUpdateValidation(body, persisted))
                .thenReturn(buildErrorResponse(ErrorMessages.WEEK_COMMENCING_SESSION_NOT_WC));

        final CourtSchedulerApi api = api();
        final ValidationException exception = assertThrows(ValidationException.class, () -> api.putWeekCommencingSession(body));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_SESSION_NOT_WC));
        verify(weekCommencingSessionService, never()).enrichUpdateSessionRequest(any(), any());
        verifyNoInteractions(sessionsService);
    }

    @Test
    void shouldReturnUpdateValidationErrorForWeekCommencingEdit() {
        final WeekCommencingUpdateRequest body = new WeekCommencingUpdateRequest().courtScheduleId(COURT_SCHEDULE_ID).capacityInMinutes(10);
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persisted =
                new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();
        final UpdateCourtSchedule updateRequest = new UpdateCourtSchedule().courtScheduleId(COURT_SCHEDULE_ID);
        when(weekCommencingSessionService.loadPersistedSession(COURT_SCHEDULE_ID)).thenReturn(persisted);
        when(weekCommencingApiValidator.getWeekCommencingUpdateValidation(body, persisted)).thenReturn(EMPTY_JSON_OBJECT);
        when(weekCommencingSessionService.enrichUpdateSessionRequest(body, persisted)).thenReturn(updateRequest);
        when(sessionsApiValidator.getWeekCommencingSessionsUpdateValidation(updateRequest))
                .thenReturn(buildErrorResponse(ErrorMessages.MAX_DURATION_LESS_THAN_TOTAL_BOOKED));

        final CourtSchedulerApi api = api();
        final ValidationException exception = assertThrows(ValidationException.class, () -> api.putWeekCommencingSession(body));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.MAX_DURATION_LESS_THAN_TOTAL_BOOKED));
        verify(sessionsService, never()).update(any());
    }

    @Test
    void courtScheduleQueryWithoutTheParamShouldLeaveTheFilterUnset() {
        when(courtScheduleApiValidator.getCourtSchedulesValidation(any(CourtScheduleRequestParam.class))).thenReturn(EMPTY_JSON_OBJECT);
        when(sessionsService.getCourtSchedules(any(CourtScheduleRequestParam.class))).thenReturn(new ArrayList<>());

        api().getCourtschedule("centre", FIRST_MONDAY, "2027-01-10", "10", "1", null, null, null, null);

        verify(sessionsService).getCourtSchedules(courtScheduleParamCaptor.capture());
        assertThat(courtScheduleParamCaptor.getValue().isWeekCommencing(), is(nullValue()));
    }

    private CourtSchedulerApi api() {
        // positional: sessionsService 3rd, sessionsApiValidator 5th, courtScheduleApiValidator 6th,
        // weekCommencingSessionService and weekCommencingApiValidator last
        return new CourtSchedulerApi(new ObjectMapper().findAndRegisterModules(), request, sessionsService, null,
                sessionsApiValidator, courtScheduleApiValidator, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, weekCommencingSessionService, weekCommencingApiValidator);
    }
}
