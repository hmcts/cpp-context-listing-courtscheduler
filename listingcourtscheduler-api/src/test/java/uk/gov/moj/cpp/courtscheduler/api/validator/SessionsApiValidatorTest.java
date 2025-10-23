package uk.gov.moj.cpp.courtscheduler.api.validator;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.domain.Session.SessionBuilder.session;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;

import org.mockito.MockitoAnnotations;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionValidationParams;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionsApiValidatorTest {

    @InjectMocks
    private SessionsApiValidator sessionsApiValidator;

    @Mock
    private CreateSessionRequestParam createSessionRequestParam;

    @Mock
    private RepeatPattern repeatPattern;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private Requester requester;

    @Mock
    private ReferenceDataCache referenceDataCache;

    @Mock
    private AllocatedListingService allocatedListingService;

    private final String courtCentreId = randomUUID().toString();
    private final String courtRoomId = randomUUID().toString();

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private void injectReferenceDataCache() throws Exception {
        Field field = SessionsApiValidator.class.getDeclaredField("referenceDataCache");
        field.setAccessible(true);
        field.set(sessionsApiValidator, referenceDataCache);
    }

    @Test
    void shouldReturnErrorWhenPatternStartDateIsInPast() throws JsonProcessingException {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(pastDate);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals(START_DATE_IS_INVALID + pastDate, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenFrequencyIsEveryWeekAndEndDateIsNull() throws JsonProcessingException {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("Invalid combination of parameters: For More Than once, you should supply a repeat-for and end date ", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenPMSessionStartsBefore14() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session sessionToBeAdded = createPMSessionWithTimes("13:00", "15:00");

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(sessionToBeAdded));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("PM Session Start Time cannot be earlier than 14:00", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenAMSessionEndsAfter13() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session sessionToBeAdded = createAMSessionWithTimes("10:00", "14:00");

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(sessionToBeAdded));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("AM Session End Time cannot exceed 13:00", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenTimeFormatIsInvalid() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session sessionToBeAdded = createAMSessionWithTimes("sometime", "14:00");

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(sessionToBeAdded));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("Invalid time format. Please use HH:mm format.", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSessionTypeIsDuplicateWithRequest() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        final List<Session> sessionList = Arrays.asList(session().withSessionType("AM").
                withRepeatDays(Set.of(DayOfWeek.MONDAY)).withCourtCentreId("123")
                .withCourtRoomId("321").withBusinessType("TRL").build(), session().withSessionType("PM")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY)).withCourtCentreId("123")
                .withCourtRoomId("321").withBusinessType("TRL").build());
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);

        final Session sessionToBeAdded = session().withSessionType("AM").withRepeatDays(Set.of(DayOfWeek.MONDAY)).withCourtCentreId("123")
                .withCourtRoomId("321").withBusinessType("TRL").build();
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("Session to be added has a duplicate", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenDurationIsNotSet() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = createAMSession();
        final List<Session> sessionList = List.of(createPMSession());
        final List<CourtSchedule> clashingAllDaySession = List.of(createCourtScheduleFromSession(sessionToBeAdded, "AM"));

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);
//        when(courtScheduleRepository.getSimilarSessions(any(), any(), any(), any(), any())).thenReturn(clashingAllDaySession);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);
        assertEquals("Duration should be set for this session", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSessionTypeIsDuplicateWithInPayload() throws JsonProcessingException {

        final List<Session> sessionList = Arrays.asList(createAMSession(), createPMSession());
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

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);
        assertEquals("Session to be added has a duplicate", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenDraftIsDuplicateWithInPayload() {

        final List<Session> sessionList = Arrays.asList(createAMSession(), createDraftSession());
        final Session sessionToBeAdded = createDraftSession();


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

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);
        assertEquals("Session to be added has a duplicate", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenValidationIsSuccessful() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals(0, result.size());
    }

    private Session createDraftSession() {
        return createDraftSession("AM", "DVLA", "ADULT", true);
    }

    private Session createAMSession() {
        return createSession("AM", "DVLA", "ADULT");
    }

    private Session createPMSession() {
        return createSession("PM", "DVLA", "ADULT");
    }

    private Session createSession(String sessionType, String businessType, String panelType) {
        return session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType(sessionType)
                .withBusinessType(businessType)
                .withPanelType(panelType)
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .build();
    }

    private Session createDraftSession(String sessionType, String businessType, String panelType, boolean isDraft) {
        return session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType(sessionType)
                .withBusinessType(businessType)
                .withPanelType(panelType)
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withIsDraft(isDraft)
                .build();
    }

    private Session createAMSessionWithTimes(String sessionStartTime, String sessionEndTime) {
        return createSessionWithTimes("AM", "DVLA", "ADULT", sessionStartTime, sessionEndTime);
    }

    private Session createPMSessionWithTimes(String sessionStartTime, String sessionEndTime) {
        return createSessionWithTimes("PM", "DVLA", "ADULT", sessionStartTime, sessionEndTime);
    }

    private Session createSessionWithTimes(String sessionType, String businessType, String panelType, String sessionStartTime, String sessionEndTime) {
        return session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType(sessionType)
                .withBusinessType(businessType)
                .withPanelType(panelType)
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withSessionStartTime(sessionStartTime)
                .withSessionEndTime(sessionEndTime)
                .withSlotsOrDuration(60)
                .build();
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule createCourtScheduleFromSession(final Session session, final String sessionType) {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();
        courtSchedule.setCourtHouseId(session.getCourtCentreId());
        courtSchedule.setCourtRoomId(session.getCourtRoomId());
        courtSchedule.setCourtSession(sessionType);
        return courtSchedule;
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndSessionTypeIsNotAllDay() {
        SessionValidationParams params = new SessionValidationParams(60, 60, true, "AM", "BUSINESS_TYPE", null, null, null, null);
        JsonObject result = sessionsApiValidator.validateSession(params, true, requester);
        assertEquals(ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndMaxDurationIsInvalid() {
        SessionValidationParams params = new SessionValidationParams(null, 60, true, ALL_DAY, "BUSINESS_TYPE", null, null, "10:00", "17:00");

        JsonObject result = sessionsApiValidator.validateSession(params, true, requester);
        assertEquals(ErrorMessages.MAX_DURATION_AM_PM_PROVIDED_FOR_ALL_DAY_SPLIT_SESSION, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndMaxSessionTimeBeforeSessionEndTime() {
        final String courtScheduleId = randomUUID().toString();
        SessionValidationParams params = new SessionValidationParams(0, 60, true, ALL_DAY, "BUSINESS_TYPE", null, courtScheduleId, "10:00", "14:29");

        LocalDate day = LocalDate.now().plusDays(3);
        Date maxHearingStart = Date.from(
                day.atTime(15, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()
        );
        AllocatedListingEachBooked booked = org.mockito.Mockito.mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(maxHearingStart);

        // Validator will fetch allocated listings for this court schedule
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        JsonObject result = sessionsApiValidator.validateSession(params, true, requester);
        assertEquals(ErrorMessages.MAX_HEARING_TIME_BEFORE_SESSION_END_TIME, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndBusinessTypeIsNotDurationBased() {
        SessionValidationParams params = new SessionValidationParams(60, 60, true, ALL_DAY, "BUSINESS_TYPE", null, null, "10:00", "17:00");
        BusinessType businessType = new BusinessType("BUSINESS_TYPE", 1, "Description", "Category", false, false);
        when(referenceDataCache.getRotaBusinessTypeByCode("BUSINESS_TYPE", requester)).thenReturn(Optional.of(businessType));

        JsonObject result = sessionsApiValidator.validateSession(params, true, requester);
        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION, result.getString("errorMessage"));
    }
}
