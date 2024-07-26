package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.domain.Session.SessionBuilder.session;

import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Session;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
public class SessionsApiValidatorTest {

    private SessionsApiValidator sessionsApiValidator;

    @Mock
    private CreateSessionRequestParam createSessionRequestParam;

    @Mock
    private RepeatPattern repeatPattern;

    @Mock
    private SessionsService sessionsService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionsApiValidator = new SessionsApiValidator();
    }

    @Test
    public void shouldReturnErrorWhenPatternStartDateIsInPast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(pastDate);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(START_DATE_IS_INVALID + pastDate.toString(), result.getString("errorMessage"));
    }

    @Test
    public void shouldReturnErrorWhenFrequencyIsEveryWeekAndEndDateIsNull() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Invalid combination of parameters: For More Than once, you should supply a repeat-for and end date ", result.getString("errorMessage"));
    }

    @Test
    @Disabled
    public void shouldReturnErrorWhenSessionTypeIsDuplicateWithRequest() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        final List<Session> sessionList  = Arrays.asList(session().withSessionType("AM").build(), session().withSessionType("PM").build());
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);

        final Session sessionToBeAdded = session().withSessionType("AM").build();
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ", result.getString("errorMessage"));
    }

    @Test
    @Disabled
    public void shouldReturnErrorWhenSessionTypeIsDuplicateWithDatabase() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        final JsonObject errorResult = createObjectBuilder().add("validationResult",createObjectBuilder()
                .add("status", ValidationStatus.FAILURE.getValidationStatus())
                .add("validationError", "Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ")
                .build()).build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(session().withSessionType("AM").build());
        when(createSessionRequestParam.getSessionList()).thenReturn(new ArrayList<>());
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(sessionsService.validateSessionIntegrity(any(), any(), any())).thenReturn(errorResult);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals("Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ", result.getString("errorMessage"));
    }


    @Test
    public void shouldReturnEmptyJsonObjectWhenValidationIsSuccessful() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getRepeatFor()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(0, result.size());
    }
}
