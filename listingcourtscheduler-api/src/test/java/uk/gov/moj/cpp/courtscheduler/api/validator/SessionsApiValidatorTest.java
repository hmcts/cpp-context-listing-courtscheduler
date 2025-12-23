package uk.gov.moj.cpp.courtscheduler.api.validator;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.COURTROOM_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.Session.SessionBuilder.session;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionValidationParams;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule.UpdateCourtScheduleBuilder;
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

import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private void stubMagCourtRoomAvailable(String courtRoomId) {
        lenient().when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(courtRoomId), eq(requester)))
                .thenReturn(Optional.of(
                        CourtRoom.CourtRoomBuilder.aCourtRoom()
                                .withCourtRoomId(courtRoomId)
                                .withOucodeUUID(courtCentreId)
                                .build()));
        // Explicitly stub CP source to return empty for MAGISTRATES jurisdiction
        lenient().when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId), eq(requester)))
                .thenReturn(Optional.empty());
    }

    private void stubCrownCourtRoomAvailable(String courtRoomId) {
        lenient().when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId), eq(requester)))
                .thenReturn(Optional.of(
                        CourtRoom.CourtRoomBuilder.aCourtRoom()
                                .withCourtRoomId(courtRoomId)
                                .withOucodeUUID(courtCentreId)
                                .build()));
        // Explicitly stub Rota source to return empty for CROWN jurisdiction
        lenient().when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(courtRoomId), eq(requester)))
                .thenReturn(Optional.empty());
    }

    private void stubBusinessType(String code, String jurisdiction, boolean slot, boolean duration) {
        lenient().when(referenceDataCache.getRotaBusinessTypeByCode(eq(code), eq(requester)))
                .thenReturn(Optional.of(new BusinessType("id-" + code, 1, code, "desc-" + code, slot, duration, jurisdiction)));
    }

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
    void shouldReturnErrorWhenPatternStartDateAfterEndDate() {
        LocalDate startDate = LocalDate.now().plusDays(5);
        LocalDate endDate = startDate.minusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(startDate);
        when(repeatPattern.getEndDate()).thenReturn(endDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("Start date must be on or before end date", result.getString("errorMessage"));
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
        final List<Session> sessionList = Arrays.asList(
                session().withSessionType("AM")
                        .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                        .withCourtCentreId("123")
                        .withCourtRoomId("321")
                        .withBusinessType("TRL")
                        .withPanelType("ADULT")
                        .withSlotsOrDuration(20)
                        .build(),
                session().withSessionType("PM")
                        .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                        .withCourtCentreId("123")
                        .withCourtRoomId("321")
                        .withBusinessType("TRL")
                        .withPanelType("ADULT")
                        .withSlotsOrDuration(20)
                        .build()
        );
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);

        final Session sessionToBeAdded = session()
                .withSessionType("AM")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withCourtCentreId("123")
                .withCourtRoomId("321")
                .withBusinessType("TRL")
                .withPanelType("ADULT")
                .withSlotsOrDuration(20)
                .build();
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

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);
        when(createSessionRequestParam.getSessionList()).thenReturn(sessionList);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        // Business type stub needed for sessionToBeAdded validation
        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));
        stubMagCourtRoomAvailable(courtRoomId);

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

        // Create draft sessions with CROWN jurisdiction to pass draft validation
        final Session draftSession1 = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withIsDraft(true)
                .withJurisdiction("CROWN")
                .build();
        final Session draftSession2 = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withIsDraft(true)
                .withJurisdiction("CROWN")
                .build();

        final List<Session> sessionList = Arrays.asList(createAMSession(), draftSession1);
        final Session sessionToBeAdded = draftSession2;


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
        Session session = createAMSession();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));
        // Courtroom exists and belongs to the same court centre as the session
        stubMagCourtRoomAvailable(courtRoomId);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnErrorWhenBusinessTypeJurisdictionMismatch() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withSlotsOrDuration(60)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, "CROWN");
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("Business Type jurisdiction CROWN does not match session jurisdiction MAGISTRATES", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenDurationBasedBusinessTypeHasNoDuration() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("TRL")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withSlotsOrDuration(0)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("TRL", 1, "Description", "Category", false, true, "MAGISTRATES");
        when(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).thenReturn(Optional.of(businessType));
        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("Duration should be supplied for duration-based business type TRL", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomNotFound() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = createAMSession();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.empty());

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals(COURTROOM_NOT_FOUND + courtRoomId, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomDoesNotBelongToCourtCentreForMagistratesInCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        String mismatchedCourtCentreId = randomUUID().toString();

        Session session = session()
                .withCourtCentreId(mismatchedCourtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withSlotsOrDuration(20)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));

        // Courtroom exists but belongs to a different court centre
        CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId) // different from mismatchedCourtCentreId
                .build();
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.of(courtRoom));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("The courtroom must belong to the same court centre as specified in courtCentreId",
                result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCpCourtRoomNotFoundForCrown() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .withSlotsOrDuration(60)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, "CROWN");
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.empty());

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals(COURTROOM_NOT_FOUND + courtRoomId, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomDoesNotBelongToCourtCentreForMagistratesInValidateCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        String mismatchedCourtCentreId = randomUUID().toString();

        // Existing session (valid)
        Session existingSession = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withSlotsOrDuration(20)
                .build();

        // Session to be added (invalid centre-room combination)
        Session sessionToBeAdded = session()
                .withCourtCentreId(mismatchedCourtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("PM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.TUESDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withSlotsOrDuration(20)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(existingSession));
        when(createSessionRequestParam.getSessionToBeAdded()).thenReturn(sessionToBeAdded);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));

        CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId) // valid for existing, invalid for sessionToBeAdded
                .build();
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.of(courtRoom));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("The courtroom must belong to the same court centre as specified in courtCentreId",
                result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCrownSessionUsesMagistratesCourtRoom() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .withSlotsOrDuration(60)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, "CROWN");
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));
        
        // Courtroom not found in CP (CROWN) but found in Rota (MAGISTRATES) - jurisdiction mismatch
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.empty());
        
        CourtRoom rotaCourtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .build();
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.of(rotaCourtRoom));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("The courtroom belongs to a court centre with MAGISTRATES jurisdiction, which does not match the session jurisdiction CROWN",
                result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenMagistratesSessionUsesCrownCourtRoom() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withSlotsOrDuration(20)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        BusinessType businessType = new BusinessType("DVLA", 1, "Description", "Category", true, false, MAGISTRATES.getJurisdiction());
        when(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).thenReturn(Optional.of(businessType));
        
        // Courtroom not found in Rota (MAGISTRATES) but found in CP (CROWN) - jurisdiction mismatch
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.empty());
        
        CourtRoom cpCourtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .build();
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.of(cpCourtRoom));

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertEquals("The courtroom belongs to a court centre with CROWN jurisdiction, which does not match the session jurisdiction MAGISTRATES",
                result.getString("errorMessage"));
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
                .withJurisdiction(MAGISTRATES.getJurisdiction())
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
        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
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
        BusinessType businessType = new BusinessType("BUSINESS_TYPE", 1, "Description", "Category", false, false, null);
        when(referenceDataCache.getRotaBusinessTypeByCode("BUSINESS_TYPE", requester)).thenReturn(Optional.of(businessType));

        JsonObject result = sessionsApiValidator.validateSession(params, true, requester);
        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsMissing() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withMaxSlots(10)
                .build();

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("Jurisdiction is mandatory and must be either MAGISTRATES or CROWN", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsInvalid() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("INVALID")
                .withMaxSlots(10)
                .build();

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("Jurisdiction must be either MAGISTRATES or CROWN", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsDraftIsMissingForCrownJurisdictionInCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withSlotsOrDuration(20)
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction("CROWN")
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "CROWN", true, false);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("isDraft is mandatory for CROWN jurisdiction sessions", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenPanelIsMissingForMagistratesJurisdictionInCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withSlotsOrDuration(20)
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", MAGISTRATES.getJurisdiction(), true, false);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("panel is mandatory for MAGISTRATES jurisdiction sessions", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsDraftIsSuppliedWithMagistratesJurisdiction() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withIsDraft(true)
                .withMaxSlots(10)
                .build();

        stubMagCourtRoomAvailable(courtRoomId);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("isDraft can only be true when jurisdiction is CROWN", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenIsDraftIsMissingForCrownJurisdictionInUpdate() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withJurisdiction("CROWN")
                .withMaxSlots(10)
                .build();

        stubCrownCourtRoomAvailable(courtRoomId);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("isDraft is mandatory for CROWN jurisdiction sessions", result.getString("errorMessage"));
    }

    @Test
    void shouldAcceptIsDraftFalseForMagistratesJurisdiction() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withIsDraft(false)
                .withMaxSlots(10)
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("BUSINESS_TYPE", "MAGISTRATES", true, false);
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId()))
                .thenReturn(persistedSchedule);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(updateCourtSchedule.getCourtScheduleId(), 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should accept isDraft=false for MAGISTRATES silently
        assertTrue(result.isEmpty() || !result.containsKey("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenPanelIsMissingForMagistratesJurisdictionInUpdate() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(10)
                .build();

        stubMagCourtRoomAvailable(courtRoomId);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("panel is mandatory for MAGISTRATES jurisdiction sessions", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenYouthPanelIsSuppliedForCrownJurisdictionInCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("YOUTH")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "CROWN", true, false);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertTrue(result.getString("errorMessage").contains("YOUTH panel is not allowed for CROWN jurisdiction"));
    }

    @Test
    void shouldAcceptAdultPanelForCrownJurisdictionInCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "CROWN", true, false);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        // Should accept ADULT panel for CROWN
        assertTrue(result.isEmpty() || !result.containsKey("errorMessage"));
    }

    @Test
    void shouldAcceptYouthPanelForMagistratesJurisdictionInCreate() {
        LocalDate futureDate = LocalDate.now().plusDays(1);
        Session session = session()
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId(courtRoomId)
                .withSessionType("AM")
                .withBusinessType("DVLA")
                .withPanelType("YOUTH")
                .withRepeatDays(Set.of(DayOfWeek.MONDAY))
                .withJurisdiction("MAGISTRATES")
                .build();

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(futureDate);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);
        when(createSessionRequestParam.getSessionList()).thenReturn(List.of(session));

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        // Should accept YOUTH panel for MAGISTRATES
        assertTrue(result.isEmpty() || !result.containsKey("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenYouthPanelIsSuppliedForCrownJurisdictionInUpdate() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("YOUTH")
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .withMaxSlots(10)
                .build();

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType("BUSINESS_TYPE", "CROWN", true, false);
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId()))
                .thenReturn(persistedSchedule);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertTrue(result.getString("errorMessage").contains("YOUTH panel is not allowed for CROWN jurisdiction"));
    }

    @Test
    void shouldAcceptAdultPanelForCrownJurisdictionInUpdate() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .withMaxSlots(10)
                .build();

        stubCrownCourtRoomAvailable(courtRoomId);
        stubBusinessType("BUSINESS_TYPE", "CROWN", true, false);
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId()))
                .thenReturn(persistedSchedule);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(updateCourtSchedule.getCourtScheduleId(), 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should accept ADULT panel for CROWN
        assertTrue(result.isEmpty() || !result.containsKey("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomNotFoundForMagistratesUpdate() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(10)
                .build();

        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.empty());

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals(COURTROOM_NOT_FOUND + courtRoomId, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCourtRoomNotFoundForCrownUpdate() {
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withMaxSlots(10)
                .build();

        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester)).thenReturn(Optional.empty());

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals(COURTROOM_NOT_FOUND + courtRoomId, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenCrownIsDraftIsChangedFromFalseToTrue() {
        String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .withMaxSlots(10)
                .build();

        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId(courtRoomId).build()));

        CourtSchedule persistedCourtSchedule = new CourtSchedule();
        persistedCourtSchedule.setIsDraft(false);
        persistedCourtSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedCourtSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedCourtSchedule);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals("Cannot change isDraft from false to true for CROWN jurisdiction sessions", result.getString("errorMessage"));
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenMagistratesValidationIsSuccessful() {
        String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(10)
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("BUSINESS_TYPE", "MAGISTRATES", true, false);
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenCrownWithIsDraftValidationIsSuccessful() {
        String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withIsDraft(false)
                .withMaxSlots(10)
                .build();

        CourtSchedule persistedCourtSchedule = new CourtSchedule();
        persistedCourtSchedule.setIsDraft(false);
        persistedCourtSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedCourtSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedCourtSchedule);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId(courtRoomId).build()));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(0, result.size());
    }

    @Test
    void shouldReturnEmptyJsonObjectWhenCrownWithIsDraftTrueAndDatabaseIsDraftTrue() {
        String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("BUSINESS_TYPE")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withIsDraft(true)
                .withMaxSlots(10)
                .build();

        CourtSchedule persistedCourtSchedule = new CourtSchedule();
        persistedCourtSchedule.setIsDraft(true);
        persistedCourtSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedCourtSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedCourtSchedule);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId(courtRoomId).build()));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(0, result.size());
    }

    @Test
    void shouldValidateUpdateSessionWithValidInputs() {
        // Scenario 3 & 5: Valid inputs should pass validation
        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(randomUUID().toString());
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType("AM");
        updateCourtSchedule.setPanel("ADULT");
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction("MAGISTRATES");

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType("DVLA", "MAGISTRATES", true, false);
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(anyString())).thenReturn(emptyList());

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRejectUpdateWhenSessionStartTimeAfterHearingTime() {
        // Scenario 4: Session timing conflict - start time after hearing time
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType("AM");
        updateCourtSchedule.setPanel("ADULT");
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("11:00"); // After hearing at 10:00
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction("MAGISTRATES");

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        Date hearingTime = Date.from(LocalDate.now().plusDays(1).atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of(booked));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.MIN_HEARING_TIME_AFTER_SESSION_START_TIME, result.getString("errorMessage"));
    }

    @Test
    void shouldRejectUpdateWhenSessionEndTimeBeforeHearingTime() {
        // Scenario 4: Session timing conflict - end time before hearing time
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType("AM");
        updateCourtSchedule.setPanel("ADULT");
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("09:00");
        updateCourtSchedule.setSessionEndTime("09:30"); // Before hearing at 10:00
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction("MAGISTRATES");

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        // Use the same timezone as getMaxHearingTime (systemDefault)
        Date hearingTime = Date.from(LocalDate.now().plusDays(1).atTime(10, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant());
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of(booked));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.MAX_HEARING_TIME_BEFORE_SESSION_END_TIME, result.getString("errorMessage"));
    }

    @Test
    void shouldValidateUpdateWhenHearingsFitWithinSessionWindow() {
        // Scenario 4: Valid update when hearings fit within session window
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType("AM");
        updateCourtSchedule.setPanel("ADULT");
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setSessionStartTime("09:00"); // Before hearing
        updateCourtSchedule.setSessionEndTime("12:00"); // After hearing
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction("MAGISTRATES");

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1));

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        Date hearingTime = Date.from(LocalDate.now().plusDays(1).atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of(booked));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldValidateUpdateForAllDaySplitSession() {
        // Scenario 3: Valid update for all-day split session
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(randomUUID().toString());
        updateCourtSchedule.setBusinessType("TRL");
        updateCourtSchedule.setSessionType("AD");
        updateCourtSchedule.setPanel("ADULT");
        updateCourtSchedule.setMaxDuration(1);
        updateCourtSchedule.setMaxDurationForMorning(120);
        updateCourtSchedule.setMaxDurationForAfternoon(180);
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("17:00");
        updateCourtSchedule.setAllDaySplit(true);
        updateCourtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());

        stubMagCourtRoomAvailable(updateCourtSchedule.getCourtRoomId());

        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSupportAdSplit(true);

        stubBusinessType("TRL", MAGISTRATES.getJurisdiction(), false, true);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedSchedule);
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(emptyList());

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRetrieveSessionTimesFromPersistedScheduleWhenNullInRequest() {
        // Test that when sessionStartTime and sessionEndTime are null in request,
        // they are retrieved from persisted court schedule
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .withSessionStartTime(null)  // Null in request
                .withSessionEndTime(null)    // Null in request
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        persistedSchedule.setSessionDate(sessionDate);
        Date sessionStartDate = Date.from(sessionDate.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        Date sessionEndDate = Date.from(sessionDate.atTime(13, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        persistedSchedule.setSessionStartTime(sessionStartDate);
        persistedSchedule.setSessionEndTime(sessionEndDate);
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        Date hearingTime = Date.from(sessionDate.atTime(11, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should pass validation since hearing time (11:00) is within session window (10:00-13:00)
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRetrieveSessionStartTimeFromPersistedScheduleWhenNullInRequest() {
        // Test that when only sessionStartTime is null, it's retrieved from persisted schedule
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .withSessionStartTime(null)  // Null in request
                .withSessionEndTime("13:00") // Provided in request
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        Date sessionStartDate = Date.from(LocalDate.now().plusDays(1).atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        persistedSchedule.setSessionStartTime(sessionStartDate);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldRetrieveSessionEndTimeFromPersistedScheduleWhenNullInRequest() {
        // Test that when only sessionEndTime is null, it's retrieved from persisted schedule
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .withSessionStartTime("10:00") // Provided in request
                .withSessionEndTime(null)      // Null in request
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        Date sessionEndDate = Date.from(LocalDate.now().plusDays(1).atTime(13, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        persistedSchedule.setSessionEndTime(sessionEndDate);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldSkipValidationWhenSessionTimesAreNullAndPersistedScheduleNotFound() {
        // Test that when session times are null and persisted schedule doesn't exist,
        // validation is skipped gracefully
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .withSessionStartTime(null)
                .withSessionEndTime(null)
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        // Mock that persisted schedule is not found (returns null)
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(null);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldSkipValidationWhenSessionTimesAreNullAndPersistedScheduleHasNullTimes() {
        // Test that when session times are null and persisted schedule also has null times,
        // validation is skipped gracefully
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .withSessionStartTime(null)
                .withSessionEndTime(null)
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        // Mock persisted schedule with null session times
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionStartTime(null);
        persistedSchedule.setSessionEndTime(null);
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldValidateHearingTimesWhenSessionTimesRetrievedFromPersistedSchedule() {
        // Test that hearing time validation works correctly when session times are retrieved from persisted schedule
        // Scenario: Session start time (10:00) retrieved from persisted schedule is AFTER min hearing time (09:00)
        // This should trigger validation error: "Session Start Time can not be updated to a time that is later than the minimum hearing time"
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .withSessionStartTime(null)  // Null - will be retrieved from persisted schedule
                .withSessionEndTime(null)    // Null - will be retrieved from persisted schedule
                .build();

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        CourtSchedule persistedSchedule = new CourtSchedule();
        LocalDate sessionDate = LocalDate.now().plusDays(1);
        persistedSchedule.setSessionDate(sessionDate);
        // Session starts at 10:00 in persisted schedule
        Date sessionStartDate = Date.from(sessionDate.atTime(10, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        Date sessionEndDate = Date.from(sessionDate.atTime(13, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        persistedSchedule.setSessionStartTime(sessionStartDate);
        persistedSchedule.setSessionEndTime(sessionEndDate);

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        // Mock allocated listing with hearing time at 09:00 (before session start at 10:00)
        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        Date hearingTime = Date.from(sessionDate.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant());
        when(booked.getHearingStartTime()).thenReturn(hearingTime);

        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId))
                .thenReturn(List.of(booked));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should fail validation because session start time (10:00) is AFTER min hearing time (09:00)
        // Error: "Session Start Time can not be updated to a time that is later than the minimum hearing time"
        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.MIN_HEARING_TIME_AFTER_SESSION_START_TIME, result.getString("errorMessage"));
    }

    @Test
    void shouldRejectUpdateWhenCourtroomBelongsToDifferentCourtHouse() {
        // Test that updating to a courtroom from a different court house should be rejected
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        final String originalCourtHouseId = randomUUID().toString();
        final String differentCourtHouseId = randomUUID().toString();

        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(newCourtRoomId) // Different courtroom
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with original court house ID
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(originalCourtHouseId);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        // Mock new courtroom with different court house ID
        CourtRoom newCourtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(newCourtRoomId)
                .withOucodeUUID(differentCourtHouseId) // Different court house
                .build();
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(newCourtRoomId), eq(requester)))
                .thenReturn(Optional.of(newCourtRoom));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertTrue(result.getString("errorMessage").contains("Courtroom must belong to the same court house"));
        assertTrue(result.getString("errorMessage").contains(originalCourtHouseId));
    }

    @Test
    void shouldAcceptUpdateWhenCourtroomBelongsToSameCourtHouse() {
        // Test that updating to a courtroom from the same court house should be accepted
        final String courtScheduleId = randomUUID().toString();
        final String originalCourtRoomId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();

        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(newCourtRoomId) // Different courtroom but same court house
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with court house ID
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(courtHouseId);
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        // Mock new courtroom with same court house ID
        CourtRoom newCourtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(newCourtRoomId)
                .withOucodeUUID(courtHouseId) // Same court house
                .build();
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(newCourtRoomId), eq(requester)))
                .thenReturn(Optional.of(newCourtRoom));

        stubBusinessType("DVLA", "MAGISTRATES", true, false);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should pass court house validation (may fail other validations, but not court house check)
        // We check that the error is NOT about court house
        if (result.containsKey("errorMessage")) {
            assertTrue(!result.getString("errorMessage").contains("Courtroom must belong to the same court house"));
        }
    }

    @Test
    void shouldAcceptUpdateWhenCourtroomIsNotChanged() {
        // Test that when courtroom ID is not changed, court house validation is skipped
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();

        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId) // Same courtroom
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with same courtroom ID
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(courtRoomId); // Same courtroom ID
        persistedSchedule.setCourtHouseId(courtHouseId);
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should pass validation since courtroom is not changed
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldSkipCourtHouseValidationWhenPersistedScheduleNotFound() {
        // Test that when persisted schedule doesn't exist, court house validation is skipped
        final String courtScheduleId = randomUUID().toString();
        final String newCourtRoomId = randomUUID().toString();

        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(newCourtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule as null (not found)
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(null);

        stubMagCourtRoomAvailable(newCourtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should skip court house validation (may fail other validations, but not court house check)
        // We check that the error is NOT about court house
        if (result.containsKey("errorMessage")) {
            assertTrue(!result.getString("errorMessage").contains("Courtroom must belong to the same court house"));
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

        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(newCourtRoomId) // Different courtroom
                .withBusinessType("GEN")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with original court house ID
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setCourtRoomId(originalCourtRoomId);
        persistedSchedule.setCourtHouseId(originalCourtHouseId);
        persistedSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubCrownCourtRoomAvailable(newCourtRoomId);
        stubBusinessType("GEN", "CROWN", true, false);
        
        // Override with courtroom that has different court house ID for CROWN
        CourtRoom newCourtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(newCourtRoomId)
                .withOucodeUUID(differentCourtHouseId) // Different court house
                .build();
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(newCourtRoomId), eq(requester)))
                .thenReturn(Optional.of(newCourtRoom));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertTrue(result.getString("errorMessage").contains("Courtroom must belong to the same court house"));
        assertTrue(result.getString("errorMessage").contains(originalCourtHouseId));
    }

    @Test
    void shouldRejectUpdateWhenSessionIsInPast() {
        // Test that updating a session with a date in the past should be rejected
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with a date in the past
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().minusDays(1)); // Yesterday
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED, result.getString("errorMessage"));
    }

    @Test
    void shouldAcceptUpdateWhenSessionIsToday() {
        // Test that updating a session with today's date should be accepted
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with today's date
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now()); // Today
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should pass past session validation (may fail other validations, but not past session check)
        if (result.containsKey("errorMessage")) {
            assertTrue(!result.getString("errorMessage").contains(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        }
    }

    @Test
    void shouldAcceptUpdateWhenSessionIsInFuture() {
        // Test that updating a session with a future date should be accepted
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with a future date
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().plusDays(1)); // Tomorrow
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        persistedSchedule.setJurisdiction("MAGISTRATES"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(any()))
                .thenReturn(java.util.Map.of(courtScheduleId, 0));

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should pass past session validation (may fail other validations, but not past session check)
        if (result.containsKey("errorMessage")) {
            assertTrue(!result.getString("errorMessage").contains(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        }
    }

    @Test
    void shouldSkipPastSessionValidationWhenPersistedScheduleNotFound() {
        // Test that when persisted schedule doesn't exist, past session validation is skipped
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("DVLA")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("MAGISTRATES")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule as null (not found)
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(null);

        stubMagCourtRoomAvailable(courtRoomId);
        stubBusinessType("DVLA", "MAGISTRATES", true, false);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        // Should skip past session validation (may fail other validations, but not past session check)
        if (result.containsKey("errorMessage")) {
            assertTrue(!result.getString("errorMessage").contains(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        }
    }

    @Test
    void shouldRejectUpdateWhenSessionIsInPastForCrown() {
        // Test that updating a CROWN session with a date in the past should be rejected
        final String courtScheduleId = randomUUID().toString();
        UpdateCourtSchedule updateCourtSchedule = UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId(courtRoomId)
                .withBusinessType("GEN")
                .withSessionType("AM")
                .withPanel("ADULT")
                .withJurisdiction("CROWN")
                .withMaxSlots(20)
                .build();

        // Mock persisted court schedule with a date in the past
        CourtSchedule persistedSchedule = new CourtSchedule();
        persistedSchedule.setSessionDate(LocalDate.now().minusDays(1)); // Yesterday
        persistedSchedule.setCourtRoomId(courtRoomId);
        persistedSchedule.setCourtHouseId(randomUUID().toString());
        persistedSchedule.setJurisdiction("CROWN"); // Match the update request jurisdiction
        persistedSchedule.setSlotBased(true); // Set slotBased to avoid NPE
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenReturn(persistedSchedule);

        stubCrownCourtRoomAvailable(courtRoomId);

        JsonObject result = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        assertTrue(result.containsKey("errorMessage"));
        assertEquals(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED, result.getString("errorMessage"));
    }
}
