package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.domain.Session.SessionBuilder.session;

import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    private final String courtCentreId = randomUUID().toString();
    private final String courtRoomId = randomUUID().toString();

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
    @Disabled("will be handled separately")
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
    @Disabled("will be handled separately")
    public void shouldReturnErrorWhenSessionTypeIsDuplicateWithDatabase() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = createAMSession();
        final List<Session> sessionList  = List.of(createPMSession());
        final List<CourtSchedule> clashingAllDaySession = List.of(createCourtScheduleFromSession(sessionToBeAdded,"AM"));

        final JsonObject errorResult = createObjectBuilder().add("validationResult",createObjectBuilder()
                .add("status", ValidationStatus.FAILURE.getValidationStatus())
                .add("validationError", "Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ")
                .build()).build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);
        when(courtScheduleRepository.getSimilarSessions(any(), any(), any(), any(), any())).thenReturn(clashingAllDaySession);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(sessionsService.validateSessionIntegrity(any(), any(), any())).thenReturn(errorResult);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(sessionsService.validateSessionIntegrity(any(), any(), any())).thenReturn(errorResult);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals("Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ", result.getString("errorMessage"));
    }

    @Test
    public void shouldReturnErrorWhenSessionTypeIsDuplicateWithInPayload() {

        final List<Session> sessionList  = Arrays.asList(createAMSession(), createPMSession());
        final Session sessionToBeAdded = createAMSession();


        LocalDate futureDate = LocalDate.now().plusDays(1);

        final JsonObject errorResult = createObjectBuilder()
                .add("errorMessage", "Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ")
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(sessionsService.validateSessionIntegrity(any(), any(), any())).thenReturn(errorResult);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals("Session to be added has a duplicate", result.getString("errorMessage"));
    }

    @Test
    @Disabled
    public void shouldReturnErrorWhenSessionToBeAddedIsNotValidForAllDayWithInPayload() {

        final List<Session> sessionList  = Arrays.asList(createAMSession(), createPMSession());
        final Session sessionToBeAdded = createAllDaySession();


        LocalDate futureDate = LocalDate.now().plusDays(1);

        final JsonObject errorResult = createObjectBuilder().add("validationResult",createObjectBuilder()
                .add("status", ValidationStatus.FAILURE.getValidationStatus())
                .add("validationError", "Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ")
                .build()).build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(sessionsService.validateSessionIntegrity(any(), any(), any())).thenReturn(errorResult);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertValidationFailure(result,"SessionsToBe Added has a duplicate entry within SessionList: CourtCentreId,courtroomId,businessType,SessionType,RepeatDays");
    }

    @Test
    @Disabled
    public void shouldReturnErrorWhenSessionTypeIsNotValidForAllDayWithInPayload() {

        final List<Session> sessionList  = Arrays.asList(createAllDaySession());
        final Session sessionToBeAdded = createPMSession();


        LocalDate futureDate = LocalDate.now().plusDays(1);

        final JsonObject errorResult = createObjectBuilder().add("validationResult",createObjectBuilder()
                .add("status", ValidationStatus.FAILURE.getValidationStatus())
                .add("validationError", "Invalid combination of parameters: For Once, you should not supply a repeat-for and end date ")
                .build()).build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(sessionsService.validateSessionIntegrity(any(), any(), any())).thenReturn(errorResult);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertValidationFailure(result,"SessionsToBe Added has a duplicate entry within SessionList: CourtCentreId,courtroomId,businessType,SessionType,RepeatDays");
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

    private boolean assertValidationSuccess(final JsonObject result) {
        return  result.get("validationResult").asJsonObject().getString("status").equals("SUCCESS") && result.get("validationResult").asJsonObject().getString("validationError").isEmpty();
    }
    private boolean assertValidationFailure(final JsonObject result,final String errorMessage) {
        return  result.get("validationResult").asJsonObject().getString("status").equals("FAILURE") && result.get("validationResult").asJsonObject().getString("validationError").equalsIgnoreCase(errorMessage);
    }


    private Session createAMSession() {
        return createSession("AM", "DVLA", "ADULT");
    }

    private Session createPMSession() {
        return createSession("PM", "DVLA", "ADULT");
    }

    private Session createAllDaySession() {
        return createSession("ALL_DAY", "DVLA", "ADULT");
    }

    private Session createSession(String sessionType, String businessType, String panelType) {
        return session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType(sessionType)
                .withBusinessType(businessType)
                .withPanelType(panelType)
                .withRepeatDays(Set.of(DayOfWeek.MONDAY)).build();
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule createCourtScheduleFromSession(final Session session,final String sessionType) {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();
        courtSchedule.setCourtHouseId(session.getCourtCentreId());
        courtSchedule.setCourtRoomId(session.getCourtRoomId());
        courtSchedule.setCourtSession(sessionType);
        return courtSchedule;
    }
}
