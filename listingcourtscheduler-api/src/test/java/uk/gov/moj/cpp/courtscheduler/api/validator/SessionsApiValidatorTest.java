package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.CREATE_SESSIONS_COURTROOM_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;

import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.SessionValidationParams;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionsApiValidatorTest {
    private static final String EVERY_MONTH_2 = "EVERY_MONTH";
    private static final String EVERY_WEEK_2 = "EVERY_WEEK";
    private static final String ONCE_2 = "ONCE";

    private static final String VALUE_10_00 = "10:00";
    private static final String VALUE_13_00 = "13:00";
    private static final String ADULT_2 = "ADULT";
    private static final String BUSINESS_TYPE_2 = "BUSINESS_TYPE";
    private static final String CROWN_2 = "CROWN";
    private static final String CATEGORY = "Category";
    private static final String COURTROOM_MUST_BELONG_TO_THE_SAME_COURT_HOUSE = "Courtroom must belong to the same court house";
    private static final String DVLA_2 = "DVLA";
    private static final String DESCRIPTION = "Description";
    private static final String LGT_2 = "LGT";
    private static final String MAGISTRATES_2 = "MAGISTRATES";
    private static final String SESSION_TO_BE_ADDED_HAS_A_DUPLICATE = "Session to be added has a duplicate";
    private static final String TRL_2 = "TRL";
    private static final String ERROR_MESSAGE = "errorMessage";


    private static final ZoneId EUROPE_LONDON = ZoneId.of("Europe/London");

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
    private ReferenceDataCache referenceDataCache;

    @Mock
    private AllocatedListingService allocatedListingService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    private final String courtCentreId = randomUUID().toString();
    private final String courtRoomId = randomUUID().toString();

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private void stubMagCourtRoomAvailable(final String courtRoomId) {
        final CourtRoom courtRoom = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(courtCentreId)
                .oucode("B")
                ;
        lenient().when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        lenient().when(referenceDataCache.getCpCourtRoomsByCourtRoomId(eq(courtRoomId)))
                .thenReturn(List.of(courtRoom));
        lenient().when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
    }

    private void stubCrownCourtRoomAvailable(final String courtRoomId) {
        final CourtRoom courtRoom = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(courtCentreId)
                .oucode("C")
                ;
        lenient().when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        lenient().when(referenceDataCache.getCpCourtRoomsByCourtRoomId(eq(courtRoomId)))
                .thenReturn(List.of(courtRoom));
        lenient().when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.empty());
    }

    private void stubBusinessType(final String code, final String jurisdiction, final boolean slot, final boolean duration) {
        lenient().when(referenceDataCache.getRotaBusinessTypeByCode(eq(code)))
                .thenReturn(Optional.of(new BusinessType().id("id-" + code).seqNum(1).typeCode(code).typeDescription("desc-" + code).slot(slot).duration(duration).jurisdiction(jurisdiction)));
    }

    @Test
    void shouldReturnErrorWhenPatternStartDateIsInPast() throws JsonProcessingException {
        final LocalDate pastDate = LocalDate.now().minusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(pastDate.toString());

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(START_DATE_IS_INVALID + pastDate, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenPatternStartDateAfterEndDate() {
        final LocalDate startDate = LocalDate.now().plusDays(5);
        final LocalDate endDate = startDate.minusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(startDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(endDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_WEEK_2);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Start date must be on or before end date", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenFrequencyIsEveryWeekAndEndDateIsNull() throws JsonProcessingException {
        final LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(EVERY_WEEK_2);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Invalid combination of parameters: For More Than once, you should supply a repeat-for and end date ", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenPMSessionStartsBefore14() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = createPMSessionWithTimes(VALUE_13_00, "15:00");

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_WEEK_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionToBeAdded));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("PM Session Start Time cannot be earlier than 14:00", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenAMSessionEndsAfter13() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = createAMSessionWithTimes(VALUE_10_00, "14:00");

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_WEEK_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionToBeAdded));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("AM Session End Time cannot exceed 13:00", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenTimeFormatIsInvalid() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = createAMSessionWithTimes("sometime", "14:00");

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_WEEK_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionToBeAdded));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("Invalid time format. Please use HH:mm format.", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenSessionTypeIsDuplicateWithRequest() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final List<Session> sessionList = Arrays.asList(
                new Session().sessionType("AM")
                        .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                        .courtCentreId("123")
                        .courtRoomId("321")
                        .businessType(TRL_2)
                        .panel(ADULT_2)
                        .duration(20),
                new Session().sessionType("PM")
                        .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                        .courtCentreId("123")
                        .courtRoomId("321")
                        .businessType(TRL_2)
                        .panel(ADULT_2)
                        .duration(20)
        );
        when(createSessionRequestParam.getSessions()).thenReturn(sessionList);

        final Session sessionToBeAdded = new Session()
                .sessionType("AM")
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .courtCentreId("123")
                .courtRoomId("321")
                .businessType(TRL_2)
                .panel(ADULT_2)
                .duration(20)
                ;
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);


        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(SESSION_TO_BE_ADDED_HAS_A_DUPLICATE, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenDurationIsNotSet() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = createAMSession();
        final List<Session> sessionList = List.of(createPMSession());

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        // Business type stub needed for sessionToBeAdded validation
        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals("Duration should be set for this session", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenSessionTypeIsDuplicateWithInPayload() throws JsonProcessingException {

        final List<Session> sessionList = Arrays.asList(createAMSession(), createPMSession());
        final Session sessionToBeAdded = createAMSession();


        final LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);


        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals(SESSION_TO_BE_ADDED_HAS_A_DUPLICATE, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenDraftIsDuplicateWithInPayload() {

        // Create draft sessions with CROWN jurisdiction to pass draft validation
        final Session draftSession1 = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(true)
                .jurisdiction(CROWN_2)
                ;
        final Session draftSession2 = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(true)
                .jurisdiction(CROWN_2)
                ;

        final List<Session> sessionList = Arrays.asList(createAMSession(), draftSession1);
        final Session sessionToBeAdded = draftSession2;


        final LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);


        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals(SESSION_TO_BE_ADDED_HAS_A_DUPLICATE, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenMonthlySameDaySameIndexDuplicateInPayload() {
        // Monthly: same (day, index) with same session type = duplicate
        final Session sessionInList = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .index(4)
                ;
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .index(4)
                .duration(60)
                ;

        final LocalDate futureDate = LocalDate.now().plusDays(1);
        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionInList));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.plusMonths(1).toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_MONTH_2);
        lenient().when(repeatPattern.getRepeatFor()).thenReturn(1); // unused - validation returns early with duplicate
        // Validation returns early with duplicate error, so no need to stub business type or court room

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals(SESSION_TO_BE_ADDED_HAS_A_DUPLICATE, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldNotReturnErrorWhenMonthlySameDayDifferentIndexInPayload() {
        // Monthly: same day but different index = allowed
        final Session sessionInList = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .index(4)
                ;
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .index(1)
                .duration(60)
                ;

        final LocalDate futureDate = LocalDate.now().plusDays(1);
        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionInList));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.plusMonths(1).toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_MONTH_2);
        when(repeatPattern.getRepeatFor()).thenReturn(1);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);
        when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), any(LocalDate.class), any(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldNotReturnErrorWhenMonthlyDifferentDaySameIndexInPayload() {
        // Monthly: different day, same index = allowed (e.g. 4th Friday and 4th Monday)
        final Session sessionInList = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.FRIDAY)))
                .index(4)
                ;
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .index(4)
                .duration(60)
                ;

        final LocalDate futureDate = LocalDate.now().plusDays(1);
        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionInList));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.plusMonths(1).toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_MONTH_2);
        when(repeatPattern.getRepeatFor()).thenReturn(1);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);
        when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), any(LocalDate.class), any(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenValidationIsSuccessful() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = createAMSession();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        // Courtroom exists and belongs to the same court centre as the session
        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(0, result.size());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any(RotaProcessLog.class));
    }

    @Test
    void shouldNotReturnCourtCentreErrorWhenCourtRoomIsSharedBetweenCourtCentres() {
        // A CP courtroom can be nested under more than one organisation unit in the
        // ou-courtrooms reference data. The session's court centre matches the SECOND
        // membership, which previously failed because only one arbitrary entry was considered.
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final String otherCourtCentreId = randomUUID().toString();

        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .duration(60)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));

        final CourtRoom membershipInOtherCentre = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(otherCourtCentreId)
                .oucode("C")
                ;
        final CourtRoom membershipInSessionCentre = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(courtCentreId)
                .oucode("C")
                ;
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId))
                .thenReturn(List.of(membershipInOtherCentre, membershipInSessionCentre));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnErrorWhenBusinessTypeJurisdictionMismatch() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(60)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Business Type jurisdiction CROWN does not match session jurisdiction MAGISTRATES", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenDurationBasedBusinessTypeHasNoDuration() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(TRL_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(null)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(TRL_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(MAGISTRATES_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(TRL_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);
        
        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Duration should be supplied for duration-based business type TRL", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldAcceptDurationZeroForDurationBasedBusinessTypeInAMSession() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(TRL_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(0)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(TRL_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(MAGISTRATES_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(TRL_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldAcceptDurationZeroForDurationBasedBusinessTypeInPMSession() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("PM")
                .businessType(TRL_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(0)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(TRL_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(MAGISTRATES_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(TRL_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldAcceptDurationZeroForDurationBasedBusinessTypeInAllDaySession() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AD")
                .businessType(TRL_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(0)
                .allDaySplit(false)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(TRL_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(MAGISTRATES_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(TRL_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenCourtRoomNotFound() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = createAMSession();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId)).thenReturn(List.of());

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Courtroom does not exist", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomDoesNotBelongToCourtCentreForMagistratesInCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final String mismatchedCourtCentreId = randomUUID().toString();

        final Session session = new Session()
                .courtCentreId(mismatchedCourtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(20)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));

        // Courtroom exists in CP but belongs to a different court centre (oucode B for MAGISTRATES); fails at step 3 before Rota is called
        final CourtRoom courtRoom = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(courtCentreId) // different from mismatchedCourtCentreId
                .oucode("B")
                ;
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId)).thenReturn(List.of(courtRoom));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("This courtroom belongs to a different court centre", result.getString(ERROR_MESSAGE));
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any(RotaProcessLog.class));
    }

    @Test
    void shouldReturnErrorWhenCpCourtRoomNotFoundForCrown() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .duration(60)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId)).thenReturn(List.of());

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Courtroom does not exist", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomDoesNotBelongToCourtCentreForMagistratesInValidateCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final String mismatchedCourtCentreId = randomUUID().toString();

        // Existing session (valid)
        final Session existingSession = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(20)
                ;

        // Session to be added (invalid centre-room combination)
        final Session sessionToBeAdded = new Session()
                .courtCentreId(mismatchedCourtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("PM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.TUESDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(20)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        when(createSessionRequestParam.getSessions()).thenReturn(List.of(existingSession));
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(courtCentreId) // valid for existing, invalid for sessionToBeAdded
                .oucode("B")
                ;
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId)).thenReturn(List.of(courtRoom));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("This courtroom belongs to a different court centre", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCrownSessionUsesMagistratesCourtRoom() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .duration(60)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        // For CROWN, courtroom must exist in CP; not found returns "Courtroom does not exist"
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId)).thenReturn(List.of());

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Courtroom does not exist", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenMagistratesSessionUsesCrownCourtRoom() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .duration(20)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        
        // Courtroom not found in Rota (MAGISTRATES) but found in CP (CROWN) - jurisdiction mismatch
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId)).thenReturn(Optional.empty());
        
        final CourtRoom cpCourtRoom = new CourtRoom()
                .courtroomId(courtRoomId)
                .oucodeUUID(courtCentreId)
                ;
        when(referenceDataCache.getCpCourtRoomsByCourtRoomId(courtRoomId)).thenReturn(List.of(cpCourtRoom));

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Courtroom selected does not exist in Rota", result.getString(ERROR_MESSAGE));
        final ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());
        final RotaProcessLog savedLog = logCaptor.getValue();
        assertEquals(CREATE_SESSIONS_COURTROOM_NOT_FOUND.code(), savedLog.getErrorCode());
        assertEquals(CREATE_SESSIONS_COURTROOM_NOT_FOUND.format(courtRoomId), savedLog.getErrorText());
    }

    @Test
    void shouldAllowSessionsWithSameBusinessTypeCourtRoomAndDateButDifferentSessionType() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);

        final Session sessionInList = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(true)
                .jurisdiction(CROWN_2)
                ;
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("PM")
                .businessType(DVLA_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(true)
                .jurisdiction(CROWN_2)
                .duration(60)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(sessionInList));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(true).duration(false).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        stubCrownCourtRoomAvailable(courtRoomId);
        lenient().when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), nullable(LocalDate.class), nullable(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenTwoDraftSessionsHaveSameBusinessTypeCourtRoomAndDate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);

        final Session draftSession = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(true)
                .jurisdiction(CROWN_2)
                ;
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(true)
                .jurisdiction(CROWN_2)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(draftSession));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(SESSION_TO_BE_ADDED_HAS_A_DUPLICATE, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenTwoFinalSessionsHaveSameBusinessTypeCourtRoomAndDate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);

        final Session finalSession = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(false)
                .jurisdiction(CROWN_2)
                ;
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .isDraft(false)
                .jurisdiction(CROWN_2)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(finalSession));
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(SESSION_TO_BE_ADDED_HAS_A_DUPLICATE, result.getString(ERROR_MESSAGE));
    }

    private Session createAMSession() {
        return createSession("AM", DVLA_2, ADULT_2);
    }

    private Session createPMSession() {
        return createSession("PM", DVLA_2, ADULT_2);
    }

    private static List<String> toRepeatDayStrings(final Set<DayOfWeek> days) {
        return days.stream().map(DayOfWeek::name).collect(java.util.stream.Collectors.toList());
    }

    private Session createSession(final String sessionType, final String businessType, final String panelType) {
        return new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType(sessionType)
                .businessType(businessType)
                .panel(panelType)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                ;
    }

    private Session createAMSessionWithTimes(final String sessionStartTime, final String sessionEndTime) {
        return createSessionWithTimes("AM", DVLA_2, ADULT_2, sessionStartTime, sessionEndTime);
    }

    private Session createPMSessionWithTimes(final String sessionStartTime, final String sessionEndTime) {
        return createSessionWithTimes("PM", DVLA_2, ADULT_2, sessionStartTime, sessionEndTime);
    }

    private Session createSessionWithTimes(final String sessionType, final String businessType, final String panelType, final String sessionStartTime, final String sessionEndTime) {
        return new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType(sessionType)
                .businessType(businessType)
                .panel(panelType)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .sessionStartTime(sessionStartTime)
                .sessionEndTime(sessionEndTime)
                .duration(60)
                ;
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndSessionTypeIsNotAllDay() {
        final SessionValidationParams params = new SessionValidationParams().maxDurationForMorning(60).maxDurationForAfternoon(60).allDaySplit(true).sessionType("AM").businessType(BUSINESS_TYPE_2).slotsOrDuration(null).courtScheduleId(null).sessionStartTime(null).sessionEndTime(null);
        final JsonObject result = sessionsApiValidator.validateSession(params, true);
        assertEquals(ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndMaxDurationIsInvalid() {
        final SessionValidationParams params = new SessionValidationParams().maxDurationForMorning(null).maxDurationForAfternoon(60).allDaySplit(true).sessionType(ALL_DAY).businessType(BUSINESS_TYPE_2).slotsOrDuration(null).courtScheduleId(null).sessionStartTime(VALUE_10_00).sessionEndTime("17:00");

        final JsonObject result = sessionsApiValidator.validateSession(params, true);
        assertEquals(ErrorMessages.MAX_DURATION_AM_PM_PROVIDED_FOR_ALL_DAY_SPLIT_SESSION, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndMaxSessionTimeBeforeSessionEndTime() {
        final String courtScheduleId = randomUUID().toString();
        final SessionValidationParams params = new SessionValidationParams().maxDurationForMorning(0).maxDurationForAfternoon(60).allDaySplit(true).sessionType(ALL_DAY).businessType(BUSINESS_TYPE_2).slotsOrDuration(null).courtScheduleId(courtScheduleId).sessionStartTime(VALUE_10_00).sessionEndTime("14:29");

        final LocalDate day = LocalDate.now().plusDays(3);
        final OffsetDateTime maxHearingStart = day.atTime(15, 0).atZone(EUROPE_LONDON).toOffsetDateTime();
        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(maxHearingStart);

        // Validator will fetch allocated listings for this court schedule
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        final JsonObject result = sessionsApiValidator.validateSession(params, true);
        assertEquals(ErrorMessages.MAX_HEARING_TIME_BEFORE_SESSION_END_TIME, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnBusinessTypeNotFoundWhenCreatingASessionOnAnUnknownBusinessType() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType("FWT")
                .duration(20)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(false)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("FWT"))).thenReturn(Optional.empty());

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.BUSINESS_TYPE_NOT_FOUND + "FWT", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnBusinessTypeNotFoundWhenAnAllDaySplitNamesAnUnknownBusinessType() {
        final SessionValidationParams params = new SessionValidationParams().maxDurationForMorning(60).maxDurationForAfternoon(60).allDaySplit(true).sessionType(ALL_DAY).businessType("FWT").slotsOrDuration(null).courtScheduleId(null).sessionStartTime(VALUE_10_00).sessionEndTime("17:00");
        when(referenceDataCache.getRotaBusinessTypeByCode("FWT")).thenReturn(Optional.empty());

        final JsonObject result = sessionsApiValidator.validateSession(params, true);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.BUSINESS_TYPE_NOT_FOUND + "FWT", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenIsAllDaySplitIsTrueAndBusinessTypeIsNotDurationBased() {
        final SessionValidationParams params = new SessionValidationParams().maxDurationForMorning(60).maxDurationForAfternoon(60).allDaySplit(true).sessionType(ALL_DAY).businessType(BUSINESS_TYPE_2).slotsOrDuration(null).courtScheduleId(null).sessionStartTime(VALUE_10_00).sessionEndTime("17:00");
        final BusinessType businessType = new BusinessType().id(BUSINESS_TYPE_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(false).jurisdiction(null);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE_2)).thenReturn(Optional.of(businessType));

        final JsonObject result = sessionsApiValidator.validateSession(params, true);
        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsMissing() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .maxSlots(10);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("Jurisdiction is mandatory and must be either MAGISTRATES or CROWN", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsInvalid() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction("INVALID")
                .maxSlots(10);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("Jurisdiction must be either MAGISTRATES or CROWN", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenIsDraftIsMissingForCrownJurisdictionInCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .duration(20)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, CROWN_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("isDraft is mandatory for CROWN jurisdiction sessions", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenPanelIsMissingForMagistratesJurisdictionInCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .duration(20)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES.getJurisdiction())
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES.getJurisdiction(), true, false);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("panel is mandatory for MAGISTRATES jurisdiction sessions", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenIsDraftIsSuppliedWithMagistratesJurisdiction() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .isDraft(true)
                .maxSlots(10);

        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("isDraft can only be true when jurisdiction is CROWN", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenIsDraftIsMissingForCrownJurisdictionInUpdate() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .jurisdiction(CROWN_2)
                .maxSlots(10);

        stubCrownCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("isDraft is mandatory for CROWN jurisdiction sessions", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldAcceptIsDraftFalseForMagistratesJurisdiction() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .isDraft(false)
                .maxSlots(10);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(BUSINESS_TYPE_2, MAGISTRATES_2, true, false);
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId()))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should accept isDraft=false for MAGISTRATES silently
        assertTrue(result.isEmpty() || !result.containsKey(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenPanelIsMissingForMagistratesJurisdictionInUpdate() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(10);

        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("panel is mandatory for MAGISTRATES jurisdiction sessions", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenYouthPanelIsSuppliedForCrownJurisdictionInCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel("YOUTH")
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, CROWN_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertTrue(result.getString(ERROR_MESSAGE).contains("YOUTH panel is not allowed for CROWN jurisdiction"));
    }

    @Test
    void shouldAcceptAdultPanelForCrownJurisdictionInCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, CROWN_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        // Should accept ADULT panel for CROWN
        assertTrue(result.isEmpty() || !result.containsKey(ERROR_MESSAGE));
    }

    @Test
    void shouldAcceptYouthPanelForMagistratesJurisdictionInCreate() {
        final LocalDate futureDate = LocalDate.now().plusDays(1);
        final Session session = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .panel("YOUTH")
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY)))
                .jurisdiction(MAGISTRATES_2)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(futureDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(createSessionRequestParam.getSessions()).thenReturn(List.of(session));

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        // Should accept YOUTH panel for MAGISTRATES
        assertTrue(result.isEmpty() || !result.containsKey(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenYouthPanelIsSuppliedForCrownJurisdictionInUpdate() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel("YOUTH")
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .maxSlots(10);

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType(BUSINESS_TYPE_2, CROWN_2, true, false);
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId()))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertTrue(result.getString(ERROR_MESSAGE).contains("YOUTH panel is not allowed for CROWN jurisdiction"));
    }

    @Test
    void shouldAcceptAdultPanelForCrownJurisdictionInUpdate() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .maxSlots(10);

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType(BUSINESS_TYPE_2, CROWN_2, true, false);
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId()))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should accept ADULT panel for CROWN
        assertTrue(result.isEmpty() || !result.containsKey(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomNotFoundForMagistratesUpdate() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(10);

        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId)).thenReturn(Optional.empty());

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("Courtroom selected does not exist in Rota", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomNotFoundForCrownUpdate() {
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .maxSlots(10);

        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId)).thenReturn(Optional.empty());

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("Courtroom selected does not exist in Rota", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCrownIsDraftIsChangedFromFalseToTrue() {
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .maxSlots(10);

        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId))
                .thenReturn(Optional.of(new CourtRoom().courtroomId(courtRoomId)));

        final CourtSchedule persistedCourtSchedule = new CourtSchedule();
        persistedCourtSchedule.setIsDraft(false);
        persistedCourtSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedCourtSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedCourtSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals("Cannot change isDraft from false to true for CROWN jurisdiction sessions", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenMagistratesValidationIsSuccessful() {
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(10);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(BUSINESS_TYPE_2, MAGISTRATES_2, true, false);
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenCrownWithIsDraftValidationIsSuccessful() {
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(false)
                .maxSlots(10);

        final CourtSchedule persistedCourtSchedule = new CourtSchedule();
        persistedCourtSchedule.setIsDraft(false);
        persistedCourtSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedCourtSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId))
                .thenReturn(Optional.of(new CourtRoom().courtroomId(courtRoomId)));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenCrownWithIsDraftTrueAndDatabaseIsDraftTrue() {
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(BUSINESS_TYPE_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .maxSlots(10);

        final CourtSchedule persistedCourtSchedule = new CourtSchedule();
        persistedCourtSchedule.setIsDraft(true);
        persistedCourtSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedCourtSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId))
                .thenReturn(Optional.of(new CourtRoom().courtroomId(courtRoomId)));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(0, result.size());
    }

    @Test
    void shouldValidateUpdateSessionWithValidInputs() {
        // Scenario 3 & 5: Valid inputs should pass validation
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(randomUUID().toString());
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType(DVLA_2);
        updateCourtSchedule.setCourtSession("AM");
        updateCourtSchedule.setPanel(ADULT_2);
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime(VALUE_10_00);
        updateCourtSchedule.setSessionEndTime(VALUE_13_00);
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(MAGISTRATES_2);

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(anyString())).thenReturn(emptyList());

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRejectUpdateWhenSessionStartTimeAfterHearingTime() {
        // Scenario 4: Session timing conflict - start time after hearing time
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType(DVLA_2);
        updateCourtSchedule.setCourtSession("AM");
        updateCourtSchedule.setPanel(ADULT_2);
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("11:00"); // After hearing at 10:00
        updateCourtSchedule.setSessionEndTime(VALUE_13_00);
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(MAGISTRATES_2);

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));

        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        final OffsetDateTime hearingTime = LocalDate.now().plusDays(1).atTime(10, 0).atZone(EUROPE_LONDON).toOffsetDateTime();
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of(booked));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.MIN_HEARING_TIME_AFTER_SESSION_START_TIME, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldRejectUpdateWhenSessionEndTimeBeforeHearingTime() {
        // Scenario 4: Session timing conflict - end time before hearing time
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType(DVLA_2);
        updateCourtSchedule.setCourtSession("AM");
        updateCourtSchedule.setPanel(ADULT_2);
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("09:00");
        updateCourtSchedule.setSessionEndTime("09:30"); // Before hearing at 10:00
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(MAGISTRATES_2);

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));

        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        // Use the same timezone as hearing-time validation (Europe/London)
        final OffsetDateTime hearingTime = LocalDate.now().plusDays(1).atTime(10, 0).atZone(EUROPE_LONDON).toOffsetDateTime();
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of(booked));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.MAX_HEARING_TIME_BEFORE_SESSION_END_TIME, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldValidateUpdateWhenHearingsFitWithinSessionWindow() {
        // Scenario 4: Valid update when hearings fit within session window
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType(DVLA_2);
        updateCourtSchedule.setCourtSession("AM");
        updateCourtSchedule.setPanel(ADULT_2);
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("09:00"); // Before hearing
        updateCourtSchedule.setSessionEndTime("12:00"); // After hearing
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(MAGISTRATES_2);

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));

        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        final OffsetDateTime hearingTime = LocalDate.now().plusDays(1).atTime(10, 0).atZone(EUROPE_LONDON).toOffsetDateTime();
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of(booked));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldValidateUpdateForAllDaySplitSession() {
        // Scenario 3: Valid update for all-day split session
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType(TRL_2);
        updateCourtSchedule.setCourtSession("AD");
        updateCourtSchedule.setPanel(ADULT_2);
        updateCourtSchedule.setMaxDuration(1);
        updateCourtSchedule.setMaxDurationForMorning(120);
        updateCourtSchedule.setMaxDurationForAfternoon(180);
        updateCourtSchedule.setSessionStartTime(VALUE_10_00);
        updateCourtSchedule.setSessionEndTime("17:00");
        updateCourtSchedule.setAllDaySplit(true);
        updateCourtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSupportAdSplit(true);

        stubBusinessType(TRL_2, MAGISTRATES.getJurisdiction(), false, true);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedSchedule);
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(emptyList());

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRetrieveSessionTimesFromPersistedScheduleWhenNullInRequest() {
        // Test that when sessionStartTime and sessionEndTime are null in request,
        // they are retrieved from persisted court schedule
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20)
                .sessionStartTime(null)  // Null in request
                .sessionEndTime(null);    // Null in request

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        final LocalDate sessionDate = LocalDate.now().plusDays(1);
        persistedSchedule.setSessionDate(sessionDate);
        final Instant sessionStartDate = sessionDate.atTime(10, 0).atZone(EUROPE_LONDON).toInstant();
        final Instant sessionEndDate = sessionDate.atTime(13, 0).atZone(EUROPE_LONDON).toInstant();
        persistedSchedule.setSessionStartTime(sessionStartDate);
        persistedSchedule.setSessionEndTime(sessionEndDate);
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        final OffsetDateTime hearingTime = sessionDate.atTime(11, 0).atZone(EUROPE_LONDON).toOffsetDateTime();
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should pass validation since hearing time (11:00) is within session window (10:00-13:00)
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRetrieveSessionStartTimeFromPersistedScheduleWhenNullInRequest() {
        // Test that when only sessionStartTime is null, it's retrieved from persisted schedule
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20)
                .sessionStartTime(null)  // Null in request
                .sessionEndTime("13:00"); // Provided in request

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        final Instant sessionStartDate = LocalDate.now().plusDays(1).atTime(10, 0).atZone(EUROPE_LONDON).toInstant();
        persistedSchedule.setSessionStartTime(sessionStartDate);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRetrieveSessionEndTimeFromPersistedScheduleWhenNullInRequest() {
        // Test that when only sessionEndTime is null, it's retrieved from persisted schedule
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20)
                .sessionStartTime(VALUE_10_00) // Provided in request
                .sessionEndTime(null);      // Null in request

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        final Instant sessionEndDate = LocalDate.now().plusDays(1).atTime(13, 0).atZone(EUROPE_LONDON).toInstant();
        persistedSchedule.setSessionEndTime(sessionEndDate);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldSkipValidationWhenSessionTimesAreNullAndPersistedScheduleNotFound() {
        // Test that when session times are null and persisted schedule doesn't exist,
        // validation is skipped gracefully
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20)
                .sessionStartTime(null)
                .sessionEndTime(null);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        // Mock that persisted schedule is not found (returns null)
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(null);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldSkipValidationWhenSessionTimesAreNullAndPersistedScheduleHasNullTimes() {
        // Test that when session times are null and persisted schedule also has null times,
        // validation is skipped gracefully
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20)
                .sessionStartTime(null)
                .sessionEndTime(null);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        // Mock persisted schedule with null session times
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionStartTime(null);
        persistedSchedule.setSessionEndTime(null);
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldValidateHearingTimesWhenSessionTimesRetrievedFromPersistedSchedule() {
        // Test that hearing time validation works correctly when session times are retrieved from persisted schedule
        // Scenario: Session start time (10:00) retrieved from persisted schedule is AFTER min hearing time (09:00)
        // This should trigger validation error: "Session Start Time can not be updated to a time that is later than the minimum hearing time"
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20)
                .sessionStartTime(null)  // Null - will be retrieved from persisted schedule
                .sessionEndTime(null);    // Null - will be retrieved from persisted schedule

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        final LocalDate sessionDate = LocalDate.now().plusDays(1);
        persistedSchedule.setSessionDate(sessionDate);
        // Session wall-clock times in UK — use Europe/London so behaviour matches CI (often UTC) and local dev
        final Instant sessionStartDate = sessionDate.atTime(10, 0).atZone(EUROPE_LONDON).toInstant();
        final Instant sessionEndDate = sessionDate.atTime(13, 0).atZone(EUROPE_LONDON).toInstant();
        persistedSchedule.setSessionStartTime(sessionStartDate);
        persistedSchedule.setSessionEndTime(sessionEndDate);

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        // Mock allocated listing with hearing time at 09:00 London (before session start at 10:00 London)
        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        final OffsetDateTime hearingTime = sessionDate.atTime(9, 0).atZone(EUROPE_LONDON).toOffsetDateTime();
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should fail validation because session start time (10:00) is AFTER min hearing time (09:00)
        // Error: "Session Start Time can not be updated to a time that is later than the minimum hearing time"
        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.MIN_HEARING_TIME_AFTER_SESSION_START_TIME, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldRejectUpdateWhenCourtroomBelongsToDifferentCourtHouse() {
        // Test that updating to a courtroom from a different court house should be rejected
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        final String originalCourtHouseId = randomUUID().toString();
        final String differentCourtHouseId = randomUUID().toString();

        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(newCourtRoomId) // Different courtroom
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule with original court house ID
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(originalCourtHouseId);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        // Mock new courtroom with different court house ID
        final CourtRoom newCourtRoom = new CourtRoom()
                .courtroomId(newCourtRoomId)
                .oucodeUUID(differentCourtHouseId) // Different court house
                ;
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(newCourtRoomId)))
                .thenReturn(Optional.of(newCourtRoom));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertTrue(result.getString(ERROR_MESSAGE).contains(COURTROOM_MUST_BELONG_TO_THE_SAME_COURT_HOUSE));
        assertTrue(result.getString(ERROR_MESSAGE).contains(originalCourtHouseId));
    }

    @Test
    void shouldAcceptUpdateWhenCourtroomBelongsToSameCourtHouse() {
        // Test that updating to a courtroom from the same court house should be accepted
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();

        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(newCourtRoomId) // Different courtroom but same court house
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule with court house ID
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(courtHouseId);
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        // Mock new courtroom with same court house ID
        final CourtRoom newCourtRoom = new CourtRoom()
                .courtroomId(newCourtRoomId)
                .oucodeUUID(courtHouseId) // Same court house
                ;
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(newCourtRoomId)))
                .thenReturn(Optional.of(newCourtRoom));

        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should pass court house validation (may fail other validations, but not court house check)
        // We check that the error is NOT about court house
        if (result.containsKey(ERROR_MESSAGE)) {
            assertTrue(!result.getString(ERROR_MESSAGE).contains(COURTROOM_MUST_BELONG_TO_THE_SAME_COURT_HOUSE));
        }
    }

    @Test
    void shouldAcceptUpdateWhenCourtroomIsNotChanged() {
        // Test that when courtroom ID is not changed, court house validation is skipped
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();

        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId) // Same courtroom
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule with same courtroom ID
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(courtRoomId); // Same courtroom ID
        persistedSchedule.setCourtHouseId(courtHouseId);
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should pass validation since courtroom is not changed
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldSkipCourtHouseValidationWhenPersistedScheduleNotFound() {
        // Test that when persisted schedule doesn't exist, court house validation is skipped
        final String courtScheduleId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();

        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(newCourtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule as null (not found)
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(null);

        stubMagCourtRoomAvailable(newCourtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should skip court house validation (may fail other validations, but not court house check)
        // We check that the error is NOT about court house
        if (result.containsKey(ERROR_MESSAGE)) {
            assertTrue(!result.getString(ERROR_MESSAGE).contains(COURTROOM_MUST_BELONG_TO_THE_SAME_COURT_HOUSE));
        }
    }

    @Test
    void shouldRejectUpdateWhenCourtroomBelongsToDifferentCourtHouseForCrown() {
        // Test that updating to a courtroom from a different court house should be rejected for CROWN jurisdiction
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        final String originalCourtHouseId = randomUUID().toString();
        final String differentCourtHouseId = randomUUID().toString();

        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(newCourtRoomId) // Different courtroom
                .businessType("GEN")
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .maxSlots(20);

        // Mock persisted court schedule with original court house ID
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(originalCourtHouseId);
        persistedSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubCrownCourtRoomAvailable(newCourtRoomId);
        stubBusinessType("GEN", CROWN_2, true, false);
        
        // Override with courtroom that has different court house ID for CROWN
        final CourtRoom newCourtRoom = new CourtRoom()
                .courtroomId(newCourtRoomId)
                .oucodeUUID(differentCourtHouseId) // Different court house
                ;
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(newCourtRoomId)))
                .thenReturn(Optional.of(newCourtRoom));

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertTrue(result.getString(ERROR_MESSAGE).contains(COURTROOM_MUST_BELONG_TO_THE_SAME_COURT_HOUSE));
        assertTrue(result.getString(ERROR_MESSAGE).contains(originalCourtHouseId));
    }

    @Test
    void shouldRejectUpdateWhenSessionIsInPast() {
        // Test that updating a session with a date in the past should be rejected
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule with a date in the past
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().minusDays(1)); // Yesterday
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldAcceptUpdateWhenSessionIsToday() {
        // Test that updating a session with today's date should be accepted
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule with today's date
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now()); // Today
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should pass past session validation (may fail other validations, but not past session check)
        if (result.containsKey(ERROR_MESSAGE)) {
            assertTrue(!result.getString(ERROR_MESSAGE).contains(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        }
    }

    @Test
    void shouldAcceptUpdateWhenSessionIsInFuture() {
        // Test that updating a session with a future date should be accepted
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule with a future date
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1)); // Tomorrow
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        persistedSchedule.setJurisdiction(MAGISTRATES_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should pass past session validation (may fail other validations, but not past session check)
        if (result.containsKey(ERROR_MESSAGE)) {
            assertTrue(!result.getString(ERROR_MESSAGE).contains(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        }
    }

    @Test
    void shouldSkipPastSessionValidationWhenPersistedScheduleNotFound() {
        // Test that when persisted schedule doesn't exist, past session validation is skipped
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(DVLA_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(MAGISTRATES_2)
                .maxSlots(20);

        // Mock persisted court schedule as null (not found)
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(null);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType(DVLA_2, MAGISTRATES_2, true, false);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Should skip past session validation (may fail other validations, but not past session check)
        if (result.containsKey(ERROR_MESSAGE)) {
            assertTrue(!result.getString(ERROR_MESSAGE).contains(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        }
    }

    @Test
    void shouldRejectUpdateWhenSessionIsInPastForCrown() {
        // Test that updating a CROWN session with a date in the past should be rejected
        final String courtScheduleId = randomUUID().toString();
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType("GEN")
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .maxSlots(20);

        // Mock persisted court schedule with a date in the past
        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().minusDays(1)); // Yesterday
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        persistedSchedule.setJurisdiction(CROWN_2); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubCrownCourtRoomAvailable(courtRoomId);

        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        assertTrue(result.containsKey(ERROR_MESSAGE));
        assertEquals(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldCallValidateSessionIntegrityWithEveryMonthFrequency() {
        // Given
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final LocalDate endDate = LocalDate.now().plusMonths(6);
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AD")
                .businessType(LGT_2)
                .duration(100)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.FRIDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .index(4)
                .allDaySplit(true)
                .maxDurationForMorning(50)
                .maxDurationForAfternoon(50)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(emptyList());
        when(repeatPattern.getStartDate()).thenReturn(startDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(endDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_MONTH_2);
        when(repeatPattern.getRepeatFor()).thenReturn(1);

        final BusinessType businessType = new BusinessType().id(LGT_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(LGT_2)).thenReturn(Optional.of(businessType));
        stubCrownCourtRoomAvailable(courtRoomId);
        when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), any(LocalDate.class), any(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        // When
        sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        // Then
        final ArgumentCaptor<RepeatFrequency> frequencyCaptor = ArgumentCaptor.forClass(RepeatFrequency.class);
        verify(sessionsService).validateSessionIntegrity(
                eq(sessionToBeAdded),
                eq(startDate),
                eq(endDate),
                eq(1),
                frequencyCaptor.capture()
        );
        assertEquals(RepeatFrequency.EVERY_MONTH, frequencyCaptor.getValue());
    }

    @Test
    void shouldCallValidateSessionIntegrityWithEveryWeekFrequency() {
        // Given
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final LocalDate endDate = LocalDate.now().plusWeeks(4);
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AM")
                .businessType(DVLA_2)
                .duration(100)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
                .jurisdiction(MAGISTRATES_2)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(emptyList());
        when(repeatPattern.getStartDate()).thenReturn(startDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(endDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_WEEK_2);
        when(repeatPattern.getRepeatFor()).thenReturn(1);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);
        when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), any(LocalDate.class), any(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        // When
        sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        // Then
        final ArgumentCaptor<RepeatFrequency> frequencyCaptor = ArgumentCaptor.forClass(RepeatFrequency.class);
        verify(sessionsService).validateSessionIntegrity(
                eq(sessionToBeAdded),
                eq(startDate),
                eq(endDate),
                eq(1),
                frequencyCaptor.capture()
        );
        assertEquals(RepeatFrequency.EVERY_WEEK, frequencyCaptor.getValue());
    }

    @Test
    void shouldCallValidateSessionIntegrityWithOnceFrequency() {
        // Given
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("PM")
                .businessType(DVLA_2)
                .duration(100)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.TUESDAY)))
                .jurisdiction(MAGISTRATES_2)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(emptyList());
        when(repeatPattern.getStartDate()).thenReturn(startDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(ONCE_2);
        when(repeatPattern.getRepeatFor()).thenReturn(null);

        final BusinessType businessType = new BusinessType().id(DVLA_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode(DVLA_2)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);
        when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), nullable(LocalDate.class), nullable(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        // When
        sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        // Then
        final ArgumentCaptor<RepeatFrequency> frequencyCaptor = ArgumentCaptor.forClass(RepeatFrequency.class);
        verify(sessionsService).validateSessionIntegrity(
                eq(sessionToBeAdded),
                eq(startDate),
                eq((LocalDate) null),
                eq((Integer) null),
                frequencyCaptor.capture()
        );
        assertEquals(RepeatFrequency.ONCE, frequencyCaptor.getValue());
    }

    @Test
    void shouldCallValidateSessionIntegrityWithCorrectParametersForMonthlyFrequencyWithIndex() {
        // Given - This matches the curl request from the user
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final LocalDate endDate = LocalDate.now().plusMonths(6);
        final Session sessionToBeAdded = new Session()
                .courtCentreId(courtCentreId)
                .courtRoomId(courtRoomId)
                .sessionType("AD")
                .businessType(LGT_2)
                .duration(100)
                .panel(ADULT_2)
                .repeatDays(toRepeatDayStrings(Set.of(DayOfWeek.FRIDAY)))
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .index(4)
                .allDaySplit(true)
                .maxDurationForMorning(50)
                .maxDurationForAfternoon(50)
                ;

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessions()).thenReturn(emptyList());
        when(repeatPattern.getStartDate()).thenReturn(startDate.toString());
        when(repeatPattern.getEndDate()).thenReturn(endDate.toString());
        when(repeatPattern.getFrequency()).thenReturn(EVERY_MONTH_2);
        when(repeatPattern.getRepeatFor()).thenReturn(1);

        final BusinessType businessType = new BusinessType().id(LGT_2).seqNum(1).typeCode(DESCRIPTION).typeDescription(CATEGORY).slot(false).duration(true).jurisdiction(CROWN_2);
        when(referenceDataCache.getRotaBusinessTypeByCode(LGT_2)).thenReturn(Optional.of(businessType));
        stubCrownCourtRoomAvailable(courtRoomId);
        when(sessionsService.validateSessionIntegrity(any(Session.class), any(LocalDate.class), any(LocalDate.class), any(Integer.class), any(RepeatFrequency.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        // When
        sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        // Then - Verify frequency is passed correctly
        final ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        final ArgumentCaptor<LocalDate> startDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        final ArgumentCaptor<LocalDate> endDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        final ArgumentCaptor<Integer> repeatForCaptor = ArgumentCaptor.forClass(Integer.class);
        final ArgumentCaptor<RepeatFrequency> frequencyCaptor = ArgumentCaptor.forClass(RepeatFrequency.class);

        verify(sessionsService).validateSessionIntegrity(
                sessionCaptor.capture(),
                startDateCaptor.capture(),
                endDateCaptor.capture(),
                repeatForCaptor.capture(),
                frequencyCaptor.capture()
        );

        assertEquals(sessionToBeAdded, sessionCaptor.getValue());
        assertEquals(startDate, startDateCaptor.getValue());
        assertEquals(endDate, endDateCaptor.getValue());
        assertEquals(1, repeatForCaptor.getValue());
        assertEquals(RepeatFrequency.EVERY_MONTH, frequencyCaptor.getValue());
        assertEquals(4, sessionCaptor.getValue().getIndex());
    }

    @Test
    void shouldRejectCourtroomAssignmentForCrownDraftSessionWithHearingsBooked() {
        // Given - CROWN draft session with hearings booked trying to change courtroom
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(newCourtRoomId)
                .businessType(LGT_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .maxSlots(20);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtScheduleId(courtScheduleId);
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(courtCentreId);
        persistedSchedule.setIsDraft(true);
        persistedSchedule.setJurisdiction(CROWN_2);
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        stubCrownCourtRoomAvailable(newCourtRoomId);
        stubBusinessType(LGT_2, CROWN_2, true, false);

        // When
        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Then
        assertEquals("Cannot assign courtroom to a CROWN draft session with hearings booked", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldRejectStateChangeForCrownDraftSessionWithHearingsBooked() {
        // Given - CROWN draft session with hearings booked trying to change state to assigned
        final String courtScheduleId = randomUUID().toString();
        
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(courtRoomId)
                .businessType(LGT_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(false) // Trying to change from draft to assigned
                .maxSlots(20);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtScheduleId(courtScheduleId);
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(courtCentreId);
        persistedSchedule.setIsDraft(true); // Currently draft
        persistedSchedule.setJurisdiction(CROWN_2);
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        final AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType(LGT_2, CROWN_2, true, false);

        // When
        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Then
        assertEquals("Cannot assign state to a CROWN draft session with hearings booked", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldAllowCourtroomAssignmentForCrownDraftSessionWithoutHearingsBooked() {
        // Given - CROWN draft session without hearings booked trying to change courtroom
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        
        final UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule()
                .courtScheduleId(courtScheduleId)
                .courtRoomId(newCourtRoomId)
                .businessType(LGT_2)
                .courtSession("AM")
                .panel(ADULT_2)
                .jurisdiction(CROWN_2)
                .isDraft(true)
                .maxSlots(20);

        final CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtScheduleId(courtScheduleId);
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(courtCentreId);
        persistedSchedule.setIsDraft(true);
        persistedSchedule.setJurisdiction(CROWN_2);
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));
        persistedSchedule.setSlotBased(true);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(emptyList()); // No hearings booked

        stubCrownCourtRoomAvailable(newCourtRoomId);
        stubBusinessType(LGT_2, CROWN_2, true, false);

        // When
        final JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule);

        // Then - Should pass validation (may have other validation errors, but not the hearings booked error)
        if (result.containsKey(ERROR_MESSAGE)) {
            assertTrue(!result.getString(ERROR_MESSAGE).contains("Cannot assign courtroom to a CROWN draft session with hearings booked"));
        }
    }

    // Tests for getSessionsAvailabilityValidation

    @Test
    void shouldReturnErrorWhenCourtScheduleIdsIsEmpty() {
        final ValidateSessionAvailabilityRequestParam requestParam =
                ValidateSessionAvailabilityRequestParam.ValidateSessionAvailabilityRequestParamBuilder
                        .validateSessionAvailabilityRequestParam()
                        .withCourtScheduleIds(List.of())
                        .build();

        final JsonObject result = sessionsApiValidator.getSessionsAvailabilityValidation(requestParam);

        assertEquals("Court Schedule Ids cannot be empty", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenListModeReturnsError() {
        final String courtScheduleId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        final ValidateSessionAvailabilityRequestParam requestParam =
                ValidateSessionAvailabilityRequestParam.ValidateSessionAvailabilityRequestParamBuilder
                        .validateSessionAvailabilityRequestParam()
                        .withCourtScheduleIds(List.of(courtScheduleId))
                        .withSlotsOrDuration(60)
                        .build();
        when(sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 60))
                .thenReturn(Optional.of("some error"));

        final JsonObject result = sessionsApiValidator.getSessionsAvailabilityValidation(requestParam);

        assertEquals("some error", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnSuccessWhenListModeReturnsNoError() {
        final String courtScheduleId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        final ValidateSessionAvailabilityRequestParam requestParam =
                ValidateSessionAvailabilityRequestParam.ValidateSessionAvailabilityRequestParamBuilder
                        .validateSessionAvailabilityRequestParam()
                        .withCourtScheduleIds(List.of(courtScheduleId))
                        .withSlotsOrDuration(60)
                        .build();
        when(sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 60))
                .thenReturn(Optional.empty());

        final JsonObject result = sessionsApiValidator.getSessionsAvailabilityValidation(requestParam);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnSuccessWhenSlotBasedWithNoDuration() {
        final String courtScheduleId = "f8254db1-1683-483e-afb3-b87fde5a0a26";
        final ValidateSessionAvailabilityRequestParam requestParam =
                ValidateSessionAvailabilityRequestParam.ValidateSessionAvailabilityRequestParamBuilder
                        .validateSessionAvailabilityRequestParam()
                        .withCourtScheduleIds(List.of(courtScheduleId))
                        .build();
        when(sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), null))
                .thenReturn(Optional.empty());

        final JsonObject result = sessionsApiValidator.getSessionsAvailabilityValidation(requestParam);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    // Tests for getAssignCourtroomValidation
    @Test
    void shouldReturnErrorWhenCourtScheduleIdsIsNullForAssignCourtroom() {
        // Given
        final AssignCourtroomRequest request = new AssignCourtroomRequest()
                .courtScheduleIds(null)
                .courtRoomId(courtRoomId);

        // When
        final JsonObject result = sessionsApiValidator.getAssignCourtroomValidation(request);

        // Then
        assertEquals("At least one court schedule ID must be provided", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtScheduleIdsIsEmptyForAssignCourtroom() {
        // Given
        final AssignCourtroomRequest request = new AssignCourtroomRequest()
                .courtScheduleIds(emptyList())
                .courtRoomId(courtRoomId);

        // When
        final JsonObject result = sessionsApiValidator.getAssignCourtroomValidation(request);

        // Then
        assertEquals("At least one court schedule ID must be provided", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomIdIsNull() {
        // Given
        final AssignCourtroomRequest request = new AssignCourtroomRequest()
                .courtScheduleIds(List.of(randomUUID().toString()))
                .courtRoomId(null);

        // When
        final JsonObject result = sessionsApiValidator.getAssignCourtroomValidation(request);

        // Then
        assertEquals("Courtroom ID must be provided", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomIdIsEmpty() {
        // Given
        final AssignCourtroomRequest request = new AssignCourtroomRequest()
                .courtScheduleIds(List.of(randomUUID().toString()))
                .courtRoomId("");

        // When
        final JsonObject result = sessionsApiValidator.getAssignCourtroomValidation(request);

        // Then
        assertEquals("Courtroom ID must be provided", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomIdIsBlank() {
        // Given
        final AssignCourtroomRequest request = new AssignCourtroomRequest()
                .courtScheduleIds(List.of(randomUUID().toString()))
                .courtRoomId("   ");

        // When
        final JsonObject result = sessionsApiValidator.getAssignCourtroomValidation(request);

        // Then
        assertEquals("Courtroom ID must be provided", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenAllFieldsAreValid() {
        // Given
        final AssignCourtroomRequest request = new AssignCourtroomRequest()
                .courtScheduleIds(List.of(randomUUID().toString()))
                .courtRoomId(courtRoomId);

        // When
        final JsonObject result = sessionsApiValidator.getAssignCourtroomValidation(request);

        // Then
        assertEquals(EMPTY_JSON_OBJECT, result);
    }
}
