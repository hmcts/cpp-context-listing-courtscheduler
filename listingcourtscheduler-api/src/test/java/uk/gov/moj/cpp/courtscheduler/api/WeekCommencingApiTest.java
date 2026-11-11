package uk.gov.moj.cpp.courtscheduler.api;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.WeekCommencingSessionService;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
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

/** SPRDT-1550: the controller side of POST /session/weekCommencing and the generic create path. */
@ExtendWith(MockitoExtension.class)
class WeekCommencingApiTest {

    @Mock
    private SessionsService sessionsService;
    @Mock
    private SessionsApiValidator sessionsApiValidator;
    @Mock
    private WeekCommencingSessionService weekCommencingSessionService;
    @Captor
    private ArgumentCaptor<CreateSessionRequestParam> createCaptor;

    @Test
    void shouldDelegateToTheServiceAndReturn202() {
        final WeekCommencingCreateRequest body = new WeekCommencingCreateRequest().businessType("TRL").capacity(360);

        final ResponseEntity<Void> response = api().postCreateWeekCommencingSession(body);

        assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
        assertThat(response.getBody(), is(nullValue()));
        verify(weekCommencingSessionService).create(body);
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
        // positional: sessionsService 3rd, sessionsApiValidator 5th, weekCommencingSessionService last
        return new CourtSchedulerApi(new ObjectMapper(), mock(HttpServletRequest.class), sessionsService, null,
                sessionsApiValidator, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, weekCommencingSessionService);
    }
}
