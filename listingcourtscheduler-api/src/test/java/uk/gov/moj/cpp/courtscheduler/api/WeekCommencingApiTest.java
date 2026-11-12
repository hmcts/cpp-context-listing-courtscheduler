package uk.gov.moj.cpp.courtscheduler.api;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;

import uk.gov.moj.cpp.courtscheduler.api.service.WeekCommencingSessionService;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.api.validator.WeekCommencingApiValidator;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class WeekCommencingApiTest {

    private static final String BUSINESS_TYPE = "TRL";

    @Mock
    private SessionsService sessionsService;
    @Mock
    private SessionsApiValidator sessionsApiValidator;
    @Mock
    private WeekCommencingSessionService weekCommencingSessionService;
    @Mock
    private WeekCommencingApiValidator weekCommencingApiValidator;
    @Captor
    private ArgumentCaptor<CreateSessionRequestParam> createCaptor;

    @Test
    void shouldValidateTheConvertedRequestThenCreateItAndReturn202() {
        final WeekCommencingCreateRequest body = new WeekCommencingCreateRequest().businessType(BUSINESS_TYPE).capacity(360);
        final CreateSessionRequestParam createRequest = new CreateSessionRequestParam();
        when(weekCommencingSessionService.toCreateSessionRequest(body)).thenReturn(createRequest);
        when(weekCommencingApiValidator.getWeekCommencingCreateValidation(body, createRequest)).thenReturn(EMPTY_JSON_OBJECT);

        final ResponseEntity<Void> response = api().postCreateWeekCommencingSession(body);

        assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
        assertThat(response.getBody(), is(nullValue()));
        verify(sessionsService).create(createRequest);
    }

    @Test
    void shouldReturnTheValidationErrorAndNotCreate() {
        final WeekCommencingCreateRequest body = new WeekCommencingCreateRequest().businessType(BUSINESS_TYPE).capacity(360);
        final CreateSessionRequestParam createRequest = new CreateSessionRequestParam();
        when(weekCommencingSessionService.toCreateSessionRequest(body)).thenReturn(createRequest);
        when(weekCommencingApiValidator.getWeekCommencingCreateValidation(body, createRequest))
                .thenReturn(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));

        final CourtSchedulerApi api = api();
        final ValidationException exception = assertThrows(ValidationException.class, () -> api.postCreateWeekCommencingSession(body));

        assertThat(exception.getErrors().getString("errorMessage"), is(ErrorMessages.DUPLICATE_SESSIONS));
        verify(sessionsService, never()).create(any());
    }

    @Test
    void genericCreateShouldNeverPersistAClientSuppliedWeekCommencingFlag() {
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        final CreateSessionRequestParam body = new CreateSessionRequestParam()
                .sessions(List.of(new Session().sessionType("AD").isDraft(true).jurisdiction("CROWN").isWeekCommencing(true)))
                .sessionToBeAdded(new Session().sessionType("AD").isWeekCommencing(true))
                .repeatPattern(new RepeatPattern().startDate("2027-01-04").endDate("2027-01-04").frequency("ONCE"));

        api().postCourtschedulerCreateCourtschedule(body);

        verify(sessionsService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().getSessions().getFirst().getIsWeekCommencing(), is(nullValue()));
        assertThat(createCaptor.getValue().getSessionToBeAdded().getIsWeekCommencing(), is(nullValue()));
    }

    private CourtSchedulerApi api() {
        // positional: sessionsService 3rd, sessionsApiValidator 5th, the week commencing service and validator last
        return new CourtSchedulerApi(new ObjectMapper(), mock(HttpServletRequest.class), sessionsService, null,
                sessionsApiValidator, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, weekCommencingSessionService, weekCommencingApiValidator);
    }
}
