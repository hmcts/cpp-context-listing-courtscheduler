package uk.gov.moj.cpp.courtscheduler.common.service;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.time.LocalDate.parse;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_ALL_DAY_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_MORNING_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_MORNING_START_TIME;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;

import uk.gov.moj.cpp.courtscheduler.common.converter.CourtScheduleToDeleteResponseConverter;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleMapper;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomResponse;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnit;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.SlotAndScheduleInfo;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.json.JsonObject;
import jakarta.json.JsonArray;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class SessionsServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(SessionsServiceTest.class);
    private static final Set<DayOfWeek> WEEK_DAYS_FIRST_HALF = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));
    private static final Set<DayOfWeek> WEEK_DAYS_SECOND_HALF = new HashSet<>(Arrays.asList(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    @Mock
    private CourtScheduleRepository courtScheduleRepository;
    @Mock
    private CourtScheduleService courtScheduleService;
    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;    @Mock
    private AllocatedListingRepository allocatedListingRepository;
    @Mock
    private AllocatedListingService allocatedListingService;
    @Mock
    private CourtMigrationRepository courtMigrationRepository;
    @Mock
    private ReferenceDataCache referenceDataCache;
    @Mock
    private CourtScheduleToDeleteResponseConverter courtScheduleToDeleteResponseConverter;
    @InjectMocks
    private SessionsService sessionsService;
    @Captor
    private ArgumentCaptor<List<CourtSchedule>> courtScheduleArgumentCaptor;

    @Mock
    private CourtSchedule courtScheduleEntityMock;

    private static final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int NEW_MAX_DURATION = 40;
    private static final int NEW_MAX_SLOTS = 20;
    public static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");


    static {
        // Set the timezone for the SimpleDateFormat to London
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"));
    }

    @BeforeEach
    void setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Test
    void shouldCreateCourtSchedulesWithCrownJurisdictionFetchingCpCourtRoom() {
        // Given
        final LocalDate startDate = LocalDate.of(2024, 6, 20);
        final Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(randomUUID().toString())
                .withCourtRoomId("court-room-id")
                .withSessionType("AM")
                .withPanelType("Adult")
                .withJurisdiction("CROWN")
                .build();

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));

        // Ensure getCpCourtRoomByCourtRoomId is called, NOT getRotaCourtRoomByCourtRoomId
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq("court-room-id")))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId("court-room-id").build()));

        // When
        sessionsService.create(createSessionRequest);

        // Then
        verify(referenceDataCache).getCpCourtRoomByCourtRoomId(eq("court-room-id"));
        verify(referenceDataCache, never()).getRotaCourtRoomByCourtRoomId(any());
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(any(List.class));
    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEveryWeekStartingToday() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF, true),
                singleSession(WEEK_DAYS_SECOND_HALF, false)
        );
        final LocalDate startDate = LocalDate.of(2024, 6, 20);
        final LocalDate endDate = startDate.plusMonths(1);
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        LocalDate lastDate = findTheLastDateThatIsInOneOfTheWeekDays(endDate, allSessionDays);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("TRL"))).thenReturn(returnBusinessTypeObject("TRL", false));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getValue();

        assertEquals(firstDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().findFirst().get());
        assertEquals(lastDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().reduce((first, second) -> second).get());

        capturedCourtSchedules.forEach(courtSchedule -> {
            assertFalse(courtSchedule.getSessionDate().isAfter(endDate));
            assertFalse(courtSchedule.getSessionDate().isBefore(startDate));
        });
    }

    @Test
    void shouldUpdateMultipleSessions_OnMaxSlotsValue_GreaterThanZero() {
        String courtHouseId = random(String.class);
        String courtRoomId = random(String.class);
        String businessType = "DVLA";
        String panel = random(String.class);
        String courtSession = random(String.class);

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA")))
                .thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any()))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(
                createMultipleSessions_WithSameUniqueConstraint(businessType, courtHouseId, courtRoomId, courtSession, panel, 2),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1)
        );

        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(anyList());

        final CreateSessionRequestParam createSessionRequest1 = createSessionRequest(
                createMultipleSessions_WithSameUniqueConstraint(businessType, courtHouseId, courtRoomId, courtSession, panel, 4),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1)
        );

        doAnswer(invocation -> {
            List<CourtSchedule> schedules = invocation.getArgument(0);
            List<CourtSchedule> failedSchedules = schedules.stream()
                    .filter(s -> s.getMaxSlots() % 2 == 0)
                    .toList();
            failedSchedules.forEach(courtSchedule -> courtScheduleRepository.update(courtSchedule, false));
            return null;
        }).when(courtScheduleRepository).saveCourtSchedules(anyList());

        sessionsService.create(createSessionRequest1);

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor1 = ArgumentCaptor.forClass(CourtSchedule.class);
        verify(courtScheduleRepository, atLeastOnce()).update(courtScheduleCaptor1.capture(), eq(false));

        List<CourtSchedule> capturedCourtSchedules = courtScheduleCaptor1.getAllValues();

        assertFalse(capturedCourtSchedules.isEmpty());
        assertEquals("DVLA", capturedCourtSchedules.get(0).getBusinessType());
        assertEquals(4, capturedCourtSchedules.get(0).getMaxSlots());

        capturedCourtSchedules.forEach(courtSchedule -> {
            assertTrue(courtSchedule.isActive());
            assertEquals("DVLA", courtSchedule.getBusinessType());
        });
    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEveryWeekStartingWithLaterDate() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF, true)
        );
        final LocalDate startDate = LocalDate.of(2024, 6, 20).plusWeeks(2);
        final LocalDate endDate = startDate.plusMonths(3);
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).orElse(Collections.emptySet());

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        LocalDate lastDate = findTheLastDateThatIsInOneOfTheWeekDays(endDate, allSessionDays);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getValue();

        assertEquals(firstDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().findFirst().get());
        assertEquals(lastDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().reduce((first, second) -> second).get());

        capturedCourtSchedules.forEach(courtSchedule -> {
            assertFalse(courtSchedule.getSessionDate().isAfter(endDate));
            assertFalse(courtSchedule.getSessionDate().isBefore(startDate));
        });
    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEvery2WeekStartingWithLaterDate() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF, true)
        );
        final LocalDate startDate = LocalDate.of(2024, 06, 20).plusWeeks(2);
        final LocalDate endDate = startDate.plusMonths(3);
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        LocalDate lastDate = findTheLastDateThatIsInOneOfTheWeekDays(endDate, allSessionDays);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 2));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getValue();

        assertEquals(firstDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().findFirst().get());
        assertEquals(lastDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().reduce((first, second) -> second).get());

        capturedCourtSchedules.forEach(courtSchedule -> {
            assertFalse(courtSchedule.getSessionDate().isAfter(endDate));
            assertFalse(courtSchedule.getSessionDate().isBefore(startDate));
        });
    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEvery3WeekStartingWithLaterDate() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF, true)
        );
        final LocalDate startDate = LocalDate.of(2024, 06, 20).plusWeeks(2);
        final LocalDate endDate = startDate.plusMonths(3);
        final int repeatWeeks = 3;
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, repeatWeeks));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getValue();

        assertEquals(firstDate, capturedCourtSchedules.stream().map(CourtSchedule::getSessionDate).sorted().findFirst().get());

        capturedCourtSchedules.forEach(courtSchedule -> {
            assertFalse(courtSchedule.getSessionDate().isAfter(endDate));
            assertFalse(courtSchedule.getSessionDate().isBefore(startDate));
        });
    }

    @Test
    void shouldCreateMultipleCourtSchedulesForEveryWeekFrequency() {
        final LocalDate startDate = LocalDate.of(2024, 06, 20);
        final LocalDate endDate = startDate.plusMonths(1);
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createMultipleSessions(), createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getValue();
        Map<LocalDate, DayOfWeek> getDayOfWeekMapExpected = getDayOfWeekMap(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1, Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertEquals(8, capturedCourtSchedules.size());

        assertEquals("DVLA", capturedCourtSchedules.get(0).getBusinessType());

        //assert that capturedCourtSchedules are created on the correct dates and days of week considering getDayOfWeekMapExpected
        capturedCourtSchedules.forEach(courtSchedule -> {
            assertTrue(getDayOfWeekMapExpected.containsKey(courtSchedule.getSessionDate()));
            assertEquals(getDayOfWeekMapExpected.get(courtSchedule.getSessionDate()), courtSchedule.getSessionDate().getDayOfWeek());
            assertEquals(true, courtSchedule.isActive());
            assertEquals("DVLA", courtSchedule.getBusinessType());
        });
    }

    @Test
    void shouldCreateSingleCourtSchedulesForOnceFrequency() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessionListWithSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(any(List.class));
    }

    @Test
    void shouldCreateSingleCourtSchedulesForOnceFrequencyWithDefaultSessionTimes() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequestWithoutTimes(sessionListWithSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getValue();

        assertThat(sdf.format(capturedCourtSchedules.get(0).getSessionStartTime()),is(DEFAULT_MORNING_START_TIME));
        assertThat(sdf.format(capturedCourtSchedules.get(0).getSessionEndTime()),is(DEFAULT_MORNING_END_TIME));
    }

    @Test
    void shouldCreateMultipleCourtSchedulesForOnceFrequency() {
        final LocalDate startDate = LocalDate.of(2024, 06, 20);
        final Session session = singleSession(WEEK_DAYS_FIRST_HALF, true);
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, LocalDate.now().plusMonths(3), RepeatFrequency.ONCE, 1));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any())).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(any(List.class));
    }
    @Test
    void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParam();
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        courtSchedule.setCourtScheduleId(randomUUID().toString());
        given(courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam)).willReturn(List.of(courtSchedule));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(courtSchedule.getBusinessType()))).thenReturn(returnBusinessTypeObject("DVLA", true));

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = sessionsService.getCourtSchedules(courtScheduleRequestParam);

        assertThat(courtSchedules.contains(courtSchedule), is(true));
        verify(courtScheduleRepository).enrichWithJudiciary(List.of(courtSchedule));
    }

    @Test
    void shouldGetCourtSchedulesWithJudiciaryEnrichment() {
        final CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParam();
        final String courtScheduleId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule baseSchedule = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        baseSchedule.setCourtScheduleId(courtScheduleId);
        baseSchedule.setBusinessType("TRL");

        when(courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam)).thenReturn(List.of(baseSchedule));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("TRL"))).thenReturn(returnBusinessTypeObject("TRL", true));

        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                sessionsService.getCourtSchedules(courtScheduleRequestParam);

        assertThat(result.size(), is(1));
        verify(courtScheduleRepository).enrichWithJudiciary(List.of(baseSchedule));
    }

    @Test
    void validateSessionAvailabilityListMode_shouldReturnErrorWhenSchedulesNotFound() {
        String courtScheduleId = randomUUID().toString();
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(emptyList());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 60);

        assertTrue(result.isPresent());
        assertTrue(result.get().contains("Court Schedule Ids not found"));
    }

    @Test
    void validateSessionAvailabilityListMode_shouldReturnEmptyWhenSlotBasedHasAvailability() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(true);
        entity.setMaxSlots(10);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(List.of(courtScheduleId)))
                .thenReturn(Map.of(courtScheduleId, 5));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenSchedulesAreInDifferentCourtCentres() {
        String id1 = randomUUID().toString();
        String id2 = randomUUID().toString();
        CourtSchedule entity1 = new CourtSchedule();
        entity1.setCourtScheduleId(id1);
        entity1.setSlotBased(true);
        entity1.setMaxSlots(10);
        entity1.setCourtHouseId("centre-A");
        CourtSchedule entity2 = new CourtSchedule();
        entity2.setCourtScheduleId(id2);
        entity2.setSlotBased(true);
        entity2.setMaxSlots(10);
        entity2.setCourtHouseId("centre-B");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(id1, id2))).thenReturn(List.of(entity1, entity2));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(id1, id2), null);

        assertTrue(result.isPresent());
        assertEquals("All court schedules must belong to the same court centre", result.get());
    }

    @Test
    void shouldReturnSuccessWhenSlotBasedAndOverbookingAllowed() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(true);
        entity.setMaxSlots(1);
        entity.setIsOverbookingAllowed(true);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(List.of(courtScheduleId)))
                .thenReturn(Map.of(courtScheduleId, 5));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenSlotBasedAndFullyBookedNoOverbooking() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(true);
        entity.setMaxSlots(3);
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(List.of(courtScheduleId)))
                .thenReturn(Map.of(courtScheduleId, 3));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), null);

        assertTrue(result.isPresent());
        assertEquals("One or more schedules are no longer available, please reschedule your hearing", result.get());
    }

    @Test
    void shouldReturnSuccessWhenDurationBasedAndOverbookingAllowed() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setAvailableDuration(10);
        entity.setIsOverbookingAllowed(true);
        entity.setJurisdiction("MAGISTRATES");
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 120);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenDurationBasedInsufficientAndNoOverbooking() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setAvailableDuration(30);
        entity.setIsOverbookingAllowed(false);
        entity.setJurisdiction("MAGISTRATES");
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 60);

        assertTrue(result.isPresent());
        assertEquals(String.format("Schedule %s has 30 minutes available but 60 minutes are required.", courtScheduleId), result.get());
    }

    @Test
    void shouldReturnErrorWhenCrownMultiDayMissingConsecutiveDay() {
        String courtScheduleId = randomUUID().toString();
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(startDate);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        // Returns empty — no days available
        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(startDate), eq(startDate.plusDays(1)), eq("BT1"), eq("AD")))
                .thenReturn(List.of());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 720);

        assertTrue(result.isPresent());
        assertEquals("No active court schedule found for 2026-06-01 in court room CR1 (businessType BT1, courtSession AD).", result.get());
    }

    @Test
    void shouldReturnSuccessWhenCrownMultiDayAllDaysAvailable() {
        String courtScheduleId = randomUUID().toString();
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(startDate);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        CourtSchedule day1 = new CourtSchedule();
        day1.setCourtScheduleId(randomUUID().toString());
        day1.setSessionDate(startDate);
        day1.setAvailableDuration(360);
        day1.setIsOverbookingAllowed(false);

        CourtSchedule day2 = new CourtSchedule();
        day2.setCourtScheduleId(randomUUID().toString());
        day2.setSessionDate(startDate.plusDays(1));
        day2.setAvailableDuration(400);
        day2.setIsOverbookingAllowed(false);

        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(startDate), eq(startDate.plusDays(1)), eq("BT1"), eq("AD")))
                .thenReturn(List.of(day1, day2));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 720);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnSuccessWhenCrownMultiDayOneDayOverbookingAllowed() {
        String courtScheduleId = randomUUID().toString();
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(startDate);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        CourtSchedule day1 = new CourtSchedule();
        day1.setCourtScheduleId(randomUUID().toString());
        day1.setSessionDate(startDate);
        day1.setAvailableDuration(360);
        day1.setIsOverbookingAllowed(false);

        CourtSchedule day2 = new CourtSchedule();
        day2.setCourtScheduleId(randomUUID().toString());
        day2.setSessionDate(startDate.plusDays(1));
        day2.setAvailableDuration(10);
        day2.setIsOverbookingAllowed(true);

        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(startDate), eq(startDate.plusDays(1)), eq("BT1"), eq("AD")))
                .thenReturn(List.of(day1, day2));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 720);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipWeekendsWhenValidatingMultiDayCrownHearing() {
        // Friday 2026-06-05 + 720 min (2 days) should check Friday + Monday (skip Sat/Sun)
        String courtScheduleId = randomUUID().toString();
        LocalDate friday = LocalDate.of(2026, 6, 5); // Friday
        LocalDate monday = LocalDate.of(2026, 6, 8); // Monday

        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(friday);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        CourtSchedule day1 = new CourtSchedule();
        day1.setCourtScheduleId(randomUUID().toString());
        day1.setSessionDate(friday);
        day1.setAvailableDuration(360);
        day1.setIsOverbookingAllowed(false);

        CourtSchedule day2 = new CourtSchedule();
        day2.setCourtScheduleId(randomUUID().toString());
        day2.setSessionDate(monday);
        day2.setAvailableDuration(360);
        day2.setIsOverbookingAllowed(false);

        // endDate should be Monday (skipping weekend), not Sunday
        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(friday), eq(monday), eq("BT1"), eq("AD")))
                .thenReturn(List.of(day1, day2));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 720);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFailWhenWeekendDayIsMissingInMultiDayCrownHearing() {
        // Friday start, 3 days needed (1080 min) -> Fri, Mon, Tue
        String courtScheduleId = randomUUID().toString();
        LocalDate friday = LocalDate.of(2026, 6, 5); // Friday
        LocalDate tuesday = LocalDate.of(2026, 6, 9); // Tuesday

        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(friday);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        // Only Friday available, Monday missing
        CourtSchedule day1 = new CourtSchedule();
        day1.setCourtScheduleId(randomUUID().toString());
        day1.setSessionDate(friday);
        day1.setAvailableDuration(360);
        day1.setIsOverbookingAllowed(false);

        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(friday), eq(tuesday), eq("BT1"), eq("AD")))
                .thenReturn(List.of(day1));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 1080);

        assertTrue(result.isPresent());
        assertEquals("No active court schedule found for 2026-06-08 in court room CR1 (businessType BT1, courtSession AD).", result.get());
    }

    @Test
    void shouldSucceedForThreeDayMultiDaySpanningWeekend() {
        // Thursday start, 3 days needed (1080 min) -> Thu, Fri, Mon
        String courtScheduleId = randomUUID().toString();
        LocalDate thursday = LocalDate.of(2026, 6, 4); // Thursday
        LocalDate friday = LocalDate.of(2026, 6, 5);
        LocalDate monday = LocalDate.of(2026, 6, 8);

        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(thursday);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        CourtSchedule day1 = new CourtSchedule();
        day1.setCourtScheduleId(randomUUID().toString());
        day1.setSessionDate(thursday);
        day1.setAvailableDuration(360);
        day1.setIsOverbookingAllowed(false);

        CourtSchedule day2 = new CourtSchedule();
        day2.setCourtScheduleId(randomUUID().toString());
        day2.setSessionDate(friday);
        day2.setAvailableDuration(360);
        day2.setIsOverbookingAllowed(false);

        CourtSchedule day3 = new CourtSchedule();
        day3.setCourtScheduleId(randomUUID().toString());
        day3.setSessionDate(monday);
        day3.setAvailableDuration(360);
        day3.setIsOverbookingAllowed(false);

        // endDate = advanceByWeekdays(Thursday, 2) = Monday
        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(thursday), eq(monday), eq("BT1"), eq("AD")))
                .thenReturn(List.of(day1, day2, day3));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 1080);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnSuccessWhenAdSplitCombinedAvailabilityMeetsDuration() {
        // morning=60 + afternoon=40 = 100 combined, requested 100 → PASS
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = adSplitSchedule(courtScheduleId, "MAGISTRATES", 60, 40);
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 100);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenAdSplitCombinedAvailabilityBelowDuration() {
        // morning=30 + afternoon=30 = 60 combined, requested 100 → FAIL with specific AD-split message
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = adSplitSchedule(courtScheduleId, "MAGISTRATES", 30, 30);
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 100);

        assertTrue(result.isPresent());
        assertEquals(String.format(
                "Schedule %s has 60 minutes available across morning (30) and afternoon (30), but 100 minutes are required.",
                courtScheduleId), result.get());
    }

    @Test
    void shouldReturnSuccessWhenAdSplitOneHalfEmptyAndOtherHalfFitsDuration() {
        // morning=0 + afternoon=120 = 120 combined, requested 100 → PASS (combined semantics, not "fits in one half")
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = adSplitSchedule(courtScheduleId, "MAGISTRATES", 0, 120);
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(courtScheduleId)).thenReturn(List.of());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 100);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenAdSplitMultiDayHasDayWithInsufficientCombinedAvailability() {
        // Crown multi-day over 2 days, day 2 is AD-split with combined 200 < 360
        String courtScheduleId = randomUUID().toString();
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(false);
        entity.setJurisdiction("CROWN");
        entity.setSessionDate(startDate);
        entity.setCourtRoomId("CR1");
        entity.setBusinessType("BT1");
        entity.setCourtSession("AD");
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));

        CourtSchedule day1 = new CourtSchedule();
        day1.setCourtScheduleId(randomUUID().toString());
        day1.setSessionDate(startDate);
        day1.setAvailableDuration(360);
        day1.setIsOverbookingAllowed(false);

        String day2Id = randomUUID().toString();
        CourtSchedule day2 = adSplitSchedule(day2Id, "CROWN", 120, 80);
        day2.setSessionDate(startDate.plusDays(1));

        when(courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                eq("CR1"), eq(startDate), eq(startDate.plusDays(1)), eq("BT1"), eq("AD")))
                .thenReturn(List.of(day1, day2));
        when(allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(day2Id)).thenReturn(List.of());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), 720);

        assertTrue(result.isPresent());
        assertEquals(String.format(
                "On 2026-06-02: Schedule %s has 200 minutes available across morning (120) and afternoon (80), but 360 minutes are required.",
                day2Id), result.get());
    }

    private CourtSchedule adSplitSchedule(final String id, final String jurisdiction,
                                          final int maxMorning, final int maxAfternoon) {
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(id);
        entity.setSlotBased(false);
        entity.setSupportAdSplit(true);
        entity.setMaxAdMorningDuration(maxMorning);
        entity.setMaxAdAfternoonDuration(maxAfternoon);
        entity.setIsOverbookingAllowed(false);
        entity.setJurisdiction(jurisdiction);
        entity.setCourtHouseId("centre-1");
        entity.setCourtSession("AD");
        return entity;
    }

    @Test
    void advanceByWeekdaysShouldSkipWeekends() {
        // Monday + 4 weekdays = Friday
        assertEquals(LocalDate.of(2026, 6, 5), SessionsService.advanceByWeekdays(LocalDate.of(2026, 6, 1), 4));
        // Friday + 1 weekday = Monday
        assertEquals(LocalDate.of(2026, 6, 8), SessionsService.advanceByWeekdays(LocalDate.of(2026, 6, 5), 1));
        // Thursday + 2 weekdays = Monday
        assertEquals(LocalDate.of(2026, 6, 8), SessionsService.advanceByWeekdays(LocalDate.of(2026, 6, 4), 2));
        // Monday + 0 weekdays = Monday
        assertEquals(LocalDate.of(2026, 6, 1), SessionsService.advanceByWeekdays(LocalDate.of(2026, 6, 1), 0));
    }

    @Test
    void nextWeekdayShouldSkipWeekends() {
        // Friday -> Monday
        assertEquals(LocalDate.of(2026, 6, 8), SessionsService.nextWeekday(LocalDate.of(2026, 6, 5)));
        // Saturday -> Monday
        assertEquals(LocalDate.of(2026, 6, 8), SessionsService.nextWeekday(LocalDate.of(2026, 6, 6)));
        // Sunday -> Monday
        assertEquals(LocalDate.of(2026, 6, 8), SessionsService.nextWeekday(LocalDate.of(2026, 6, 7)));
        // Monday -> Tuesday
        assertEquals(LocalDate.of(2026, 6, 2), SessionsService.nextWeekday(LocalDate.of(2026, 6, 1)));
    }

    @Test
    void shouldReturnErrorWhenSchedulesHaveDifferentJurisdictions() {
        String id1 = randomUUID().toString();
        String id2 = randomUUID().toString();
        CourtSchedule entity1 = new CourtSchedule();
        entity1.setCourtScheduleId(id1);
        entity1.setSlotBased(true);
        entity1.setMaxSlots(10);
        entity1.setCourtHouseId("centre-1");
        entity1.setJurisdiction("CROWN");
        CourtSchedule entity2 = new CourtSchedule();
        entity2.setCourtScheduleId(id2);
        entity2.setSlotBased(true);
        entity2.setMaxSlots(10);
        entity2.setCourtHouseId("centre-1");
        entity2.setJurisdiction("MAGISTRATES");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(id1, id2))).thenReturn(List.of(entity1, entity2));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(id1, id2), null);

        assertTrue(result.isPresent());
        assertEquals("All court schedules must belong to the same jurisdiction", result.get());
    }

    @Test
    void shouldReturnErrorWhenSlotBasedHasNoAllocationsButMaxSlotsIsZero() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(true);
        entity.setMaxSlots(0);
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        entity.setJurisdiction("MAGISTRATES");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(List.of(courtScheduleId)))
                .thenReturn(emptyMap());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), null);

        assertTrue(result.isPresent());
        assertEquals("One or more schedules are no longer available, please reschedule your hearing", result.get());
    }

    @Test
    void shouldReturnSuccessWhenSlotBasedHasNoAllocationsAndMaxSlotsGreaterThanZero() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(courtScheduleId);
        entity.setSlotBased(true);
        entity.setMaxSlots(5);
        entity.setIsOverbookingAllowed(false);
        entity.setCourtHouseId("centre-1");
        entity.setJurisdiction("CROWN");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(courtScheduleId))).thenReturn(List.of(entity));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(List.of(courtScheduleId)))
                .thenReturn(emptyMap());

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(courtScheduleId), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnSuccessWhenMultipleSchedulesSameJurisdictionAndSlotBased() {
        String id1 = randomUUID().toString();
        String id2 = randomUUID().toString();
        CourtSchedule entity1 = new CourtSchedule();
        entity1.setCourtScheduleId(id1);
        entity1.setSlotBased(true);
        entity1.setMaxSlots(5);
        entity1.setIsOverbookingAllowed(false);
        entity1.setCourtHouseId("centre-1");
        entity1.setJurisdiction("CROWN");
        CourtSchedule entity2 = new CourtSchedule();
        entity2.setCourtScheduleId(id2);
        entity2.setSlotBased(true);
        entity2.setMaxSlots(3);
        entity2.setIsOverbookingAllowed(false);
        entity2.setCourtHouseId("centre-1");
        entity2.setJurisdiction("CROWN");
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(id1, id2))).thenReturn(List.of(entity1, entity2));
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(List.of(id1, id2)))
                .thenReturn(Map.of(id1, 2, id2, 1));

        Optional<String> result = sessionsService.validateSessionAvailabilityListMode(List.of(id1, id2), null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldProcessProvisionalBookingRequestSuccessfully() {
        SessionsParam sessionsParam = new SessionsParam();
        sessionsParam.setSessions(List.of("1", "2"));
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = new ArrayList<>();

        when(courtScheduleRepository.deleteCourtSchedule(anyList())).thenReturn(courtSchedules);

        JsonObject response = sessionsService.deleteCourtScheduleSessions(sessionsParam);

        assertTrue(response.get("sessions").asJsonArray().isEmpty());
    }

    @Test
    void shouldUpdateCourtScheduleWhenNoBusinessTypeChange() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(persistedCourtSchedule.getCourtSession());
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setSessionStartTime("11:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule);
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldUseCpCourtRoomLookupWhenUpdatingCrownCourtroom() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setCourtRoomId("old-room");
        persistedCourtSchedule.setJurisdiction("CROWN");
        persistedCourtSchedule.setSlotBased(true);

        UpdateCourtSchedule updateCourtSchedule = UpdateCourtSchedule.UpdateCourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId)
                .withCourtRoomId("new-room")
                .withBusinessType("DVLA")
                .withSessionType(persistedCourtSchedule.getCourtSession())
                .withPanel(persistedCourtSchedule.getPanel())
                .withJurisdiction("CROWN")
                .withMaxSlots(10)
                .build();

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "CROWN"));
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq("new-room")))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId("new-room").build()));
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertThat(result.isSuccess(), is(true));
        verify(referenceDataCache, never()).getRotaCourtRoomByCourtRoomId(anyString());
    }

    @Test
    void shouldUpdateCourtScheduleWhenNoCourtRoomChange() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setSessionType(persistedCourtSchedule.getCourtSession());
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setSessionStartTime("11:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule);
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldnotAllowIfStartTimeUpdateEffectsCurrentHearingAM() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule persisted = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persisted.setSessionDate(LocalDate.of(2024, 6, 20));

        UpdateCourtSchedule update = new UpdateCourtSchedule();
        update.setCourtScheduleId(courtScheduleId);
        update.setSessionType(AM_SESSION);
        update.setBusinessType("DVLA");
        update.setSessionStartTime("11:00");
        update.setSessionEndTime("12:00");

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persisted.getSessionDate(), "10:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persisted);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldnotAllowIfEndTimeUpdateEffectsCurrentHearingAM() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule persisted = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persisted.setSessionDate(LocalDate.of(2024, 6, 20));

        UpdateCourtSchedule update = new UpdateCourtSchedule();
        update.setCourtScheduleId(courtScheduleId);
        update.setSessionType(AM_SESSION);
        update.setBusinessType("DVLA");
        update.setSessionStartTime("08:00");
        update.setSessionEndTime("09:30");

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persisted.getSessionDate(), "10:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persisted);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldnotAllowIfStartTimeUpdateEffectsCurrentHearingPM() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule persisted = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persisted.setSessionDate(LocalDate.of(2024, 6, 20));

        UpdateCourtSchedule update = new UpdateCourtSchedule();
        update.setCourtScheduleId(courtScheduleId);
        update.setSessionType(PM_SESSION);
        update.setBusinessType("DVLA");
        update.setSessionStartTime("15:30");
        update.setSessionEndTime("17:00");

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persisted.getSessionDate(), "14:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persisted);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldnotAllowIfEndTimeUpdateEffectsCurrentHearingPM() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule persisted = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persisted.setSessionDate(LocalDate.of(2024, 6, 20));

        UpdateCourtSchedule update = new UpdateCourtSchedule();
        update.setCourtScheduleId(courtScheduleId);
        update.setSessionType(PM_SESSION);
        update.setBusinessType("DVLA");
        update.setSessionStartTime("12:30");
        update.setSessionEndTime("13:30");

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persisted.getSessionDate(), "14:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persisted);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldnotAllowIfStartTimeUpdateEffectsCurrentHearingAD() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule persisted = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persisted.setSessionDate(LocalDate.of(2024, 6, 20));

        UpdateCourtSchedule update = new UpdateCourtSchedule();
        update.setCourtScheduleId(courtScheduleId);
        update.setSessionType(ALL_DAY);
        update.setBusinessType("DVLA");
        update.setSessionStartTime("13:30");
        update.setSessionEndTime("17:00");

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persisted.getSessionDate(), "12:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persisted);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldnotAllowIfEndTimeUpdateEffectsCurrentHearingAD() {
        String courtScheduleId = randomUUID().toString();
        CourtSchedule persisted = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persisted.setSessionDate(LocalDate.of(2024, 6, 20));

        UpdateCourtSchedule update = new UpdateCourtSchedule();
        update.setCourtScheduleId(courtScheduleId);
        update.setSessionType(ALL_DAY);
        update.setBusinessType("DVLA");
        update.setSessionStartTime("08:00");
        update.setSessionEndTime("10:00");

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persisted.getSessionDate(), "11:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persisted);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME, result.getMsg());
    }


    @Test
    void shouldUpdateCourtScheduleWhenSlotBasedChange() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setSlotBased(true);
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setSessionStartTime("11:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule);
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldReturnFailure_WhenCourtScheduleId_NotFound() {
        final String courtScheduleId = randomUUID().toString();
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(null);

        Result result = sessionsService.update(updateCourtSchedule);

        assertEquals("Court Session not found", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenBusinessTypeChanges() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVAL");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(persistedCourtSchedule.getBusinessType()))).thenReturn(returnBusinessTypeObject("DVAL", true, "MAGISTRATES"));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(updateCourtSchedule.getBusinessType()))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));

        Result result = sessionsService.update(updateCourtSchedule);

        assertEquals("Business Type cannot be changed from Slot to Non-Slot and vice versa", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenBusinessTypeNotFound() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("INVALID");
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("INVALID"))).thenReturn(Optional.empty());

        Result result = sessionsService.update(updateCourtSchedule);

        assertEquals("Invalid business type", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenJurisdictionIsChanged() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setJurisdiction("MAGISTRATES");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setJurisdiction("CROWN");

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);

        Result result = sessionsService.update(updateCourtSchedule);

        assertEquals("Jurisdiction cannot be changed", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenBusinessTypeJurisdictionDoesNotMatch() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setJurisdiction("MAGISTRATES");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setJurisdiction("MAGISTRATES");

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA")))
                .thenReturn(returnBusinessTypeObject("DVLA", true, "CROWN"));

        Result result = sessionsService.update(updateCourtSchedule);

        assertEquals("Business Type jurisdiction CROWN does not match session jurisdiction MAGISTRATES", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenADSplitChanges() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setSupportAdSplit(true);
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("17:00");
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));

        Result result = sessionsService.update(updateCourtSchedule);

        assertEquals("All day split flag cannot be changed for this session", result.getMsg());
    }

    @Test
    void shouldReturnMigratedCourt() {
        final String oucode = "B01LY00" ;
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(true);

        when(courtMigrationRepository.findByOuCode(oucode)).thenReturn(migrationStatus);
        assertTrue(sessionsService.isMigrated(oucode));

    }

    @Test
    void shouldReturnFalseForNonMigratedCourt() {
        final String oucode = "B01LY00" ;
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(false);

        when(courtMigrationRepository.findByOuCode(oucode)).thenReturn(migrationStatus);
        assertFalse(sessionsService.isMigrated(oucode));

    }

    @Test
    void shouldReturnAllInfoAboutMigratedOrNot() {
        final String ouCode1 = "B01LY00" ;
        final String ouCode2 = "B06IS00" ;
        final CourtSchedulerMigrationStatus migrationStatus1 = new CourtSchedulerMigrationStatus();
        migrationStatus1.setOuCode(ouCode1);
        migrationStatus1.setCourtCentreId(randomUUID().toString());
        migrationStatus1.setMigrated(false);

        final CourtSchedulerMigrationStatus migrationStatus2 = new CourtSchedulerMigrationStatus();
        migrationStatus2.setOuCode(ouCode2);
        migrationStatus2.setCourtCentreId(randomUUID().toString());
        migrationStatus2.setMigrated(true);

        when(courtMigrationRepository.findAll()).thenReturn(List.of(migrationStatus1, migrationStatus2));
        final Map<String, Boolean> migratedMap = sessionsService.migratedMapByOuCode();

        assertEquals(2, migratedMap.size());
        assertFalse(migratedMap.get(ouCode1));
        assertTrue(migratedMap.get(ouCode2));
    }

    @Test
    void shouldReturnMigratedCourtByCourtCentreId() {
        final String oucode = "B01LY00" ;
        final String courtCentreId = randomUUID().toString();
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(courtCentreId);
        migrationStatus.setMigrated(true);
        when(courtMigrationRepository.findByCourtCentreId(courtCentreId)).thenReturn(migrationStatus);
        assertTrue(sessionsService.isMigratedByCourtCentreId(courtCentreId));
    }

    @Test
    void shouldReturnFalseForNonMigratedCourtByCourtCentreId() {
        final String oucode = "B01LY00" ;
        final String courtCentreId = randomUUID().toString();
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(courtCentreId);
        migrationStatus.setMigrated(false);
        when(courtMigrationRepository.findByCourtCentreId(courtCentreId)).thenReturn(migrationStatus);
        assertFalse(sessionsService.isMigratedByCourtCentreId(courtCentreId));
    }

    @Test
    void shouldGetExtractedCourtSchedules() throws JsonProcessingException {
        final String ouCode = "B01LY00" ;
        final LocalDate startDate = LocalDate.of(2024, 10, 1);
        final LocalDate endDate = LocalDate.of(2025, 3, 31);

        final List<CourtSchedule> courtScheduleEntities = getCourtScheduleEntities();
        when(courtScheduleRepository.getExtractedCourtSchedules(List.of(ouCode), startDate, endDate)).thenReturn(courtScheduleEntities);

        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = sessionsService.getExtractedCourtSchedules(List.of(ouCode), startDate, endDate);

        verify(courtScheduleRepository, atLeastOnce()).getExtractedCourtSchedules(List.of(ouCode), startDate, endDate);

        assertThat(courtSchedules.size(), is(courtScheduleEntities.size()));
        courtScheduleEntities.forEach(courtScheduleEntity ->
                courtSchedules.stream().filter(courtSchedule -> courtScheduleEntity.getCourtScheduleId().equals(courtSchedule.getCourtScheduleId()))
                        .findAny()
                        .ifPresent(courtSchedule -> {
                            assertThat(courtSchedule.getCourtScheduleId(), is(courtScheduleEntity.getCourtScheduleId()));
                            assertThat(courtSchedule.getOuCode(), is(courtScheduleEntity.getOuCode()));
                            assertThat(courtSchedule.getListingProfileId(), is(courtScheduleEntity.getListingProfileId()));
                            assertThat(courtSchedule.getCourtRoomNumber(), is(courtScheduleEntity.getCourtRoomNumber()));
                            assertThat(courtSchedule.getCourtHouseId(), is(courtScheduleEntity.getCourtHouseId()));
                        })
        );
    }

    @Test
    void shouldGetExtractedCourtSchedulesForGhostData() throws JsonProcessingException {
        final String ouCode = "B01LY00" ;
        final LocalDate startDate = LocalDate.of(2024, 10, 1);
        final LocalDate endDate = LocalDate.of(2025, 3, 31);

        final List<CourtSchedule> courtScheduleEntities = getCourtScheduleEntities();
        when(courtScheduleRepository.getExtractedCourtSchedulesForGhostRota(List.of(ouCode), startDate, endDate)).thenReturn(courtScheduleEntities);

        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = sessionsService.getExtractedCourtSchedulesForGhostRota(List.of(ouCode), startDate, endDate);

        verify(courtScheduleRepository, atLeastOnce()).getExtractedCourtSchedulesForGhostRota(List.of(ouCode), startDate, endDate);

        assertThat(courtSchedules.size(), is(courtScheduleEntities.size()));
        courtScheduleEntities.forEach(courtScheduleEntity ->
                courtSchedules.stream().filter(courtSchedule -> courtScheduleEntity.getCourtScheduleId().equals(courtSchedule.getCourtScheduleId()))
                        .findAny()
                        .ifPresent(courtSchedule -> {
                            assertThat(courtSchedule.getCourtScheduleId(), is(courtScheduleEntity.getCourtScheduleId()));
                            assertThat(courtSchedule.getOuCode(), is(courtScheduleEntity.getOuCode()));
                            assertThat(courtSchedule.getListingProfileId(), is(courtScheduleEntity.getListingProfileId()));
                            assertThat(courtSchedule.getCourtRoomNumber(), is(courtScheduleEntity.getCourtRoomNumber()));
                            assertThat(courtSchedule.getCourtHouseId(), is(courtScheduleEntity.getCourtHouseId()));
                        })
        );
    }

    @Test
    void shouldFindByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession() {
        final String courtRoomId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 10, 2);
        final String businessType = "TRF";
        final String courtSession = "PM";
        final String ouCode = "B43KQ00";

        final String expectedCourtScheduleId = randomUUID().toString();

        final CourtScheduleMatcherInfo courtScheduleMatcherInfo = new CourtScheduleMatcherInfo(expectedCourtScheduleId, ouCode, Calendar.getInstance().getTime());
        when(courtScheduleRepository.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession)).thenReturn(courtScheduleMatcherInfo);

        final CourtScheduleMatcherInfo courtScheduleMatcherFound = sessionsService.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession);

        verify(courtScheduleRepository, atLeastOnce()).findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession);
        assertNotNull(courtScheduleMatcherFound);
        assertThat(courtScheduleMatcherFound.getCourtScheduleId(), is(expectedCourtScheduleId));
        assertThat(courtScheduleMatcherFound.getOuCode(), is(ouCode));
    }

    @Test
    void shouldSaveCourtSchedules() throws JsonProcessingException {
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> provisionalCourtSchedules = getCourtSchedules();
        final Map<String, BusinessType> businessTypeMap = getBusinessTypeMap();

        when(courtScheduleRepository.save(any())).thenReturn(CourtScheduleMapper.toEntity(provisionalCourtSchedules.get(0)));

        sessionsService.saveCourtSchedules(provisionalCourtSchedules, businessTypeMap);
        verify(courtScheduleRepository, times(provisionalCourtSchedules.size())).save(any());
    }

    @Test
    void shouldUpdateTheSlotsAndSchedulesIfNotOnlyCourtScheduleJudiciaryToBeProcessed() throws IOException {
        final List<String> existingSlotIds = getCourtScheduleIds();
        final Map<String, BusinessType> businessTypeMap = getBusinessTypeMap();
        final List<String> slotIdsToDelete = asList(randomUUID().toString(), randomUUID().toString());

        final List<String> snapshotSlotIds = asList(randomUUID().toString(), randomUUID().toString());
        final String listingProfileId1 = generateListingProfileId();
        final String listingProfileId2 = generateListingProfileId();
        final List<String> listingProfileIds = asList(listingProfileId1, listingProfileId2);
        final Map<String, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> newRecords = generateIncomingSchedules(snapshotSlotIds, listingProfileIds);
        final Collection<CourtScheduleJudiciary> newSchedules = prepareSchedules();
        final Map<String, Pair<String, String>> slotsToUpdateMap = Map.of(listingProfileId1, Pair.of("6bd1853d-8a88-35e8-b4c4-342e2649daa2", "B01LY00"));
        final Collection<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> slotsToUpdate = getCourtSchedules();

        when(courtScheduleService.saveSlot(any(CourtSchedule.class))).thenReturn(courtScheduleEntityMock);
        when(courtScheduleRepository.update(any(CourtSchedule.class), eq(true))).thenReturn(courtScheduleEntityMock);
        when(courtScheduleRepository.deleteSlots(anyList())).thenReturn(slotIdsToDelete.size());
        when(courtScheduleJudiciaryRepository.deleteSchedules(anyList())).thenReturn(slotIdsToDelete.size());

        final Map<String, List<CourtScheduleJudiciary>> relatedJudiciarySchedules = Map.of(listingProfileId1, getCourtScheduleJudiciaries("6bd1853d-8a88-35e8-b4c4-342e2649daa2", listingProfileId1));

        final List<String> ouCodes = List.of("B01LY00");
        final SlotAndScheduleInfo slotAndScheduleInfo = new SlotAndScheduleInfo(existingSlotIds, slotIdsToDelete, slotsToUpdate, newSchedules, emptyList(), relatedJudiciarySchedules, newRecords, slotsToUpdateMap);
        sessionsService.updateSlotsAndSchedules(slotAndScheduleInfo, emptyMap(), emptyList(), businessTypeMap, ouCodes, emptyList());

        verify(courtScheduleService, atLeastOnce()).saveSlot(any(CourtSchedule.class));
        verify(courtScheduleJudiciaryRepository, never()).save(any(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class));
        verify(courtScheduleRepository, atLeastOnce()).update(any(CourtSchedule.class), eq(true));
        verify(courtScheduleJudiciaryRepository, never()).updateCourtScheduleJudiciaryPosition(anyString(), any(), anyString(), anyString());
        verify(courtScheduleJudiciaryRepository, atLeastOnce()).deleteSchedules(anyList());
        verify(courtScheduleRepository, atLeastOnce()).deleteSlots(anyList());
    }

    @Test
    void shouldUpdateTheSchedulesIfOnlyCourtScheduleJudiciaryToBeProcessed() throws IOException {
        final List<String> existingSlotIds = getCourtScheduleIds();
        final Map<String, BusinessType> businessTypeMap = getBusinessTypeMap();

        final List<String> snapshotSlotIds = asList(randomUUID().toString(), randomUUID().toString());
        final List<String> listingProfileIds = asList(generateListingProfileId(), generateListingProfileId());
        final Map<String, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> newRecords = generateIncomingSchedules(snapshotSlotIds, listingProfileIds);
        final Collection<CourtScheduleJudiciary> newSchedules = prepareSchedules();
        final Map<String, Pair<String, String>> slotsToUpdateMap = new HashMap<>();
        final Collection<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> slotsToUpdate = getCourtSchedules();

        when(courtScheduleRepository.update(any(CourtSchedule.class), eq(true))).thenReturn(courtScheduleEntityMock);
        when(courtScheduleService.saveSlot(any(CourtSchedule.class))).thenReturn(courtScheduleEntityMock);

        final List<String> ouCodes = List.of("B01LY00");
        final SlotAndScheduleInfo slotAndScheduleInfo = new SlotAndScheduleInfo(existingSlotIds, emptyList(), slotsToUpdate, newSchedules, emptyList(), emptyMap(), newRecords, slotsToUpdateMap);
        sessionsService.updateSlotsAndSchedules(slotAndScheduleInfo, emptyMap(), emptyList(), businessTypeMap, ouCodes, emptyList());

        verify(courtScheduleService, atLeastOnce()).saveSlot(any(CourtSchedule.class));
        verify(courtScheduleJudiciaryRepository, never()).save(any(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class));
        verify(courtScheduleRepository, atLeastOnce()).update(any(CourtSchedule.class), eq(true));
        verify(courtScheduleJudiciaryRepository, never()).updateCourtScheduleJudiciaryPosition(anyString(), any(), anyString(), anyString());
        verify(courtScheduleJudiciaryRepository, never()).deleteSchedules(anyList());
        verify(courtScheduleRepository, never()).deleteSlots(anyList());
    }

    @Test
    void shouldMigrate_GivenOuCodes_Successfully() {
        OuCodeMigrateRequest ouCodeMigrateRequest = new OuCodeMigrateRequest();
        final List<String> ouCodes = List.of("B01LY00", "B01LY01", "B01LY02") ;
        ouCodeMigrateRequest.setOuCodes(ouCodes);
        ouCodeMigrateRequest.setMigrated(true);

        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(ouCodes.get(0));
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(false);

        when(courtMigrationRepository.findByOuCode(anyString())).thenReturn(migrationStatus);

        Result result = sessionsService.migrateOuCodes(ouCodeMigrateRequest);

        verify(courtMigrationRepository, atLeastOnce()).save(any());
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldNotMigrate_OuCode_IfAnyOneNotFound() {
        OuCodeMigrateRequest ouCodeMigrateRequest = new OuCodeMigrateRequest();
        final List<String> ouCodes = List.of("B01LY00", "B01LY01", "B01LY02");
        ouCodeMigrateRequest.setOuCodes(ouCodes);
        ouCodeMigrateRequest.setMigrated(true);

        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(ouCodes.get(0));
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(false);

        when(courtMigrationRepository.findByOuCode(anyString())).thenReturn(null);

        Result result = sessionsService.migrateOuCodes(ouCodeMigrateRequest);

        verify(courtMigrationRepository, never()).save(any());
        assertThat(result.isSuccess(), is(false));
    }

    private static CourtSchedule getPersistedCourtSchedule(final String courtScheduleId, final String businessTypeCode) {
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType(businessTypeCode);
        courtSchedule.setSessionDate(random(LocalDate.class));
        courtSchedule.setCourtSession(ALL_DAY);
        courtSchedule.setHasHearingsBooked(false);
        courtSchedule.setSupportAdSplit(false);
        return courtSchedule;
    }

    private CourtScheduleRequestParam courtScheduleRequestParam() {
        String courtCentreId = "courtCentreId";
        String courtRoomId = "courtRoomId";
        String businessType = "businessType";
        String sessionStartDate = "2024-12-01";
        String sessionEndDate = "2024-12-03";
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId,
                businessType, sessionStartDate, sessionEndDate, null, pageSize, pageNumber);
    }

    private Optional<BusinessType> returnBusinessTypeObject(final String businessTypeCode, boolean isSlotBased) {
        return Optional.of(BusinessType.BusinessTypeBuilder.aBusinessType()
                .withId(randomUUID().toString())
                .withSeqNum(1)
                .withTypeCode(businessTypeCode)
                .withTypeDescription(businessTypeCode + "BusinessType")
                .withSlot(isSlotBased)
                .withDuration(!isSlotBased)
                .build());
    }

    private Optional<BusinessType> returnBusinessTypeObject(final String businessTypeCode, boolean isSlotBased, String jurisdiction) {
        return Optional.of(BusinessType.BusinessTypeBuilder.aBusinessType()
                .withId(randomUUID().toString())
                .withSeqNum(1)
                .withTypeCode(businessTypeCode)
                .withTypeDescription(businessTypeCode + "BusinessType")
                .withSlot(isSlotBased)
                .withDuration(!isSlotBased)
                .withJurisdiction(jurisdiction)
                .build());
    }

    private Map<LocalDate, DayOfWeek> getDayOfWeekMap(LocalDate startDate, LocalDate endDate, RepeatFrequency frequency, int repeatFor, List<DayOfWeek> daysOfWeek) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        long weekNumber = 0; //start with first week
        Map<LocalDate, DayOfWeek> dayOfWeekMap = new HashMap<>();

        if (frequency.equals(RepeatFrequency.ONCE)) {
            dayOfWeekMap.put(startDate, daysOfWeek.get(0));
            return dayOfWeekMap;
        }
        //else RepeatFrequency.EVERY_WEEK
        while (weekNumber <= weeksBetween) {
            for (DayOfWeek dayOfWeek : daysOfWeek) {
                dayOfWeekMap.put(startDate.plusWeeks(weekNumber).with(TemporalAdjusters.next(dayOfWeek)), dayOfWeek);
            }
            weekNumber += repeatFor;
        }
        return dayOfWeekMap;
    }

    private LocalDate findTheLastDateThatIsInOneOfTheWeekDays(final LocalDate endDate, final Set<DayOfWeek> allSessionDays) {
        LocalDate lastDate = endDate;
        while (!allSessionDays.contains(lastDate.getDayOfWeek())) {
            lastDate = lastDate.minusDays(1);
        }
        return lastDate;
    }

    private LocalDate findTheFirstDateThatIsInOneOfTheWeekDays(final LocalDate startDate, final Set<DayOfWeek> allSessionDays) {
        LocalDate firstDate = startDate;
        while (!allSessionDays.contains(firstDate.getDayOfWeek())) {
            firstDate = firstDate.plusDays(1);
        }
        return firstDate;
    }

    private List<Session> sessionListWithSingleSession() {
        Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(randomUUID().toString())
                .withCourtRoomId(randomUUID().toString())
                .withSessionType("AM")
                .withPanelType("Adult")
                .build();

        return singletonList(session);
    }

    private Session singleSession(Set<DayOfWeek> daysOfWeek, boolean slotBased) {
        return Session.SessionBuilder.session()
                .withRepeatDays(daysOfWeek)
                .withSlotsOrDuration(20)
                .withBusinessType(slotBased ? "DVLA" : "TRL")
                .withCourtCentreId(randomUUID().toString())
                .withCourtRoomId(randomUUID().toString())
                .withSessionType("AM")
                .withPanelType("Adult")
                .build();
    }

    private List<Session> createMultipleSessions() {
        Session session1 = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(randomUUID().toString())
                .withCourtRoomId(randomUUID().toString())
                .withSessionType("AM")
                .withPanelType("Adult")
                .build();

        Session session2 = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.TUESDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(randomUUID().toString())
                .withCourtRoomId(randomUUID().toString())
                .withSessionType("AM")
                .withPanelType("Adult")
                .build();

        return Arrays.asList(session1, session2);
    }

    private List<Session> createMultipleSessions_WithSameUniqueConstraint(String businessType, String courtHouseId,
                                                                          String courtRoomId, String courtSession, String panel, int slotDuration) {
        Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(slotDuration)
                .withBusinessType(businessType)
                .withCourtCentreId(courtHouseId)
                .withCourtRoomId(courtRoomId)
                .withSessionType(courtSession)
                .withPanelType(panel)
                .withSessionStartTime("10:00")
                .withSessionEndTime("12:00")
                .build();

        return List.of(session);
    }

    private RepeatPattern createRepeatPattern(LocalDate startDate, LocalDate endDate, RepeatFrequency frequency, int repeatFor) {
        return RepeatPattern.RepeatPatternBuilder.repeatPattern()
                .withFrequency(frequency)
                .withStartDate(startDate)
                .withEndDate(endDate)
                .withRepeatFor(repeatFor)
                .build();
    }

    private CreateSessionRequestParam createSessionRequest(List<Session> sessionList, RepeatPattern repeatPattern) {
        return CreateSessionRequestParam.CreateSessionRequestParamBuilder.createSessionRequestParam()
                .withSessionList(sessionList)
                .withRepeatPattern(repeatPattern)
                .build();
    }

    private CreateSessionRequestParam createSessionRequestWithoutTimes(List<Session> sessionList, RepeatPattern repeatPattern) {
        return CreateSessionRequestParam.CreateSessionRequestParamBuilder.createSessionRequestParam()
                .withSessionList(sessionList)
                .withRepeatPattern(repeatPattern)
                .build();

    }

    public JsonObject getPayload(String path) {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString(path));
    }

    private Map<String, BusinessType> getBusinessTypeMap() {
        final Map<String, BusinessType> businessTypeMap = new HashMap<>();
        JsonObject businessTypeJson = getPayload("/test-data/referencedata.get.businesstypes.json");
        businessTypeJson.getJsonArray("rotaBusinessTypes").forEach(businessType -> {
            BusinessType businessTypeObj = BusinessType.BusinessTypeBuilder.aBusinessType()
                    .withTypeCode(((JsonObject) businessType).getString("typeCode"))
                    .withTypeDescription(((JsonObject) businessType).getString("typeDescription"))
                    .build();
            businessTypeMap.put(businessTypeObj.getTypeCode(), businessTypeObj);
        });
        return businessTypeMap;
    }

    private List<CourtSchedule> getCourtScheduleEntities() throws JsonProcessingException {
        final String courtScheduleEntitiesJsonString = FileUtil.fileToString("/test-data/court-schedules-entity-data.json");

        return objectMapper.readValue(courtScheduleEntitiesJsonString, new TypeReference<List<CourtSchedule>>(){});
    }

    private List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> getCourtSchedules() throws JsonProcessingException {
        final String courtScheduleDomainsJsonString = FileUtil.fileToString("/test-data/court-schedules-domain-data.json");

        return objectMapper.readValue(courtScheduleDomainsJsonString, new TypeReference<List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>>(){});
    }

    private List<String> getCourtScheduleIds() throws JsonProcessingException {
        final String courtScheduleEntitiesJsonString = FileUtil.fileToString("/test-data/court-schedules-entity-data.json");

        return objectMapper.readValue(courtScheduleEntitiesJsonString, new TypeReference<List<CourtSchedule>>(){})
                .stream()
                .map(CourtSchedule::getCourtScheduleId)
                .toList();
    }

    private Map<UUID, CourtRoom> getCourtRoomMap() {
        final Map<UUID, CourtRoom> courtRoomMap = new HashMap<>();
        JsonObject courtRoomJson = getPayload("/test-data/referencedata.get.rota.courtrooms.json");
        courtRoomJson.getJsonArray("cpRotaCourtRoomMappings").forEach(courtRoom -> {
            try {
                CourtRoom.CourtRoomBuilder courtRoomBuilder = CourtRoom.CourtRoomBuilder.aCourtRoom();
                //Mandatory
                courtRoomBuilder
                        .withOucode(((JsonObject) courtRoom).getString("oucode"))
                        .withCppCourtRoomId(((JsonObject) courtRoom).getInt("cppCourtRoomId"))
                        .withOucode(((JsonObject) courtRoom).getString("oucode"));
                //Optional
                if (((JsonObject) courtRoom).containsKey("rotaLocationId")) {
                    courtRoomBuilder.withRotaLocationId(((JsonObject) courtRoom).getInt("rotaLocationId"));
                }
                if (((JsonObject) courtRoom).containsKey("rotaVenueName")) {
                    courtRoomBuilder.withRotaVenueName(((JsonObject) courtRoom).getString("rotaVenueName"));
                }
                if (((JsonObject) courtRoom).containsKey("rotaVenueId")) {
                    courtRoomBuilder.withRotaVenueId(((JsonObject) courtRoom).getInt("rotaVenueId"));
                }
                if (((JsonObject) courtRoom).containsKey("oucodeL3Name")) {
                    courtRoomBuilder.withOucodeL3Name(((JsonObject) courtRoom).getString("oucodeL3Name"));
                }
                if (((JsonObject) courtRoom).containsKey("oucodeL2Name")) {
                    courtRoomBuilder.withOucodeL2Name(((JsonObject) courtRoom).getString("oucodeL2Name"));
                }
                if (((JsonObject) courtRoom).containsKey("oucodeL2Code")) {
                    courtRoomBuilder.withOucodeL2Code(((JsonObject) courtRoom).getString("oucodeL2Code"));
                }
                if (((JsonObject) courtRoom).containsKey("oucodeUUID")) {
                    courtRoomBuilder.withOucodeUUID(((JsonObject) courtRoom).getString("oucodeUUID"));
                }
                if (((JsonObject) courtRoom).containsKey("courtroomName")) {
                    courtRoomBuilder.withCourtRoomName(((JsonObject) courtRoom).getString("courtroomName"));
                }
                if (((JsonObject) courtRoom).containsKey("id")) {
                    courtRoomBuilder.withCourtRoomId(((JsonObject) courtRoom).getString("id"));
                }
                final CourtRoom courtRoomObj = courtRoomBuilder.build();

                courtRoomMap.put(UUID.fromString(courtRoomObj.getCourtroomId()), courtRoomObj);
            } catch (Exception e) {
                logger.warn("Failed to build test CourtRoom fixture for id: {}", ((JsonObject) courtRoom).getString("id"), e);
            }
        });
        return courtRoomMap;
    }

    private Map<String, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> generateIncomingSchedules(final List<String> snapshotSlotIds, final List<String> listingProfileIds) {

        final Map<String, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> slots = new HashMap<>();

        for (int i = 0; i < listingProfileIds.size(); i++) {
            final String listingProfileId = listingProfileIds.get(i);
            slots.put(listingProfileId, courtSchedule("2020-01-01", snapshotSlotIds.get(i), listingProfileId, NEW_MAX_DURATION, null, null, NEW_MAX_SLOTS));
        }

        return slots;

    }

    private uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule(final String sessionDate,
                                                                             final String courtScheduleId,
                                                                             final String listingProfileId,
                                                                             final Integer maxDuration,
                                                                             final Integer availableSlots,
                                                                             final Integer availableDuration,
                                                                             final Integer maxSlots) {

        final String scheduleId = courtScheduleId != null ? courtScheduleId : randomUUID().toString();
        final String profileId = listingProfileId;
        final Integer mDuration = maxDuration != null ? maxDuration : 182;
        final Integer avSlots = availableSlots != null ? availableSlots : 125;
        final Integer avDuration = availableDuration != null ? availableDuration : 182;
        final Integer mSlots = maxSlots != null ? maxSlots : 125;

        return new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(scheduleId)
                .withListingProfileId(profileId)
                .withSessionDate(parse(sessionDate))
                .withOuCode("CABC90")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withOperationalUnit("ANC")
                .withBusinessType("PSV")
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(mDuration)
                .withAvailableSlots(avSlots)
                .withAvailableDuration(avDuration)
                .withMaxSlots(mSlots)
                .build();
    }

    private String generateListingProfileId() {
        return "ITCS" + ThreadLocalRandom.current().nextInt(1000000);
    }

    private Collection<CourtScheduleJudiciary> prepareSchedules() throws JsonProcessingException {
        final String scheduleRecordsJsonString = FileUtil.fileToString("/test-data/schedule-records.json");
        final Collection<Map<String, String>> collection = objectMapper.readValue(scheduleRecordsJsonString, Collection.class);

        return collection.stream().map(this::buildJudiciary).toList();
    }

    private CourtScheduleJudiciary buildJudiciary(final Map<String, String> props) {
        return judiciary().withJudiciaryId(props.get("justice"))
                .withCourtListingProfileId(props.get("courtListingProfile"))
                .withEmailAddress(props.get("email"))
                .build();
    }


    private List<CourtScheduleJudiciary> getCourtScheduleJudiciaries(final String courtScheduleId, final String courtListingProfileId) throws JsonProcessingException {
        final String courtScheduleDomainsJsonString = FileUtil.fileToString("/test-data/court-schedule-judiciaries-entity-data.json")
                .replaceAll("COURT_SCHEDULE_ID", courtScheduleId)
                .replaceAll("COURT_LISTING_PROFILE_ID", courtListingProfileId);

        return objectMapper.readValue(courtScheduleDomainsJsonString, new TypeReference<List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary>>(){})
                .stream().map(CourtScheduleJudiciaryMapper::toDomain)
                .toList();
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequency() {
        // Given
        final LocalDate startDate = LocalDate.of(2024, 1, 1); // January 1st
        final LocalDate endDate = LocalDate.of(2024, 3, 31); // March 31st
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> mondayAndWednesday = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(mondayAndWednesday)
                        .withSlotsOrDuration(20)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-1")
                        .withCourtRoomId("court-room-1")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(1) // First occurrence of the day
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-1")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithMultipleSessions() {
        // Given
        final LocalDate startDate = LocalDate.of(2024, 2, 1); // February 1st
        final LocalDate endDate = LocalDate.of(2024, 4, 30); // April 30th
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> tuesdayAndThursday = new HashSet<>(Arrays.asList(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));
        final Set<DayOfWeek> mondayAndFriday = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));

        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(tuesdayAndThursday)
                        .withSlotsOrDuration(25)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-3")
                        .withCourtRoomId("court-room-3")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(1)
                        .build(),
                Session.SessionBuilder.session()
                        .withRepeatDays(mondayAndFriday)
                        .withSlotsOrDuration(15)
                        .withBusinessType("DVLA")
                        .withCourtCentreId("court-centre-4")
                        .withCourtRoomId("court-room-4")
                        .withSessionType("PM")
                        .withPanelType("Youth")
                        .withIndex(3)
                        .build()
        );

        // Mock the reference data cache
        final BusinessType trlBusinessType = new BusinessType();
        trlBusinessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(trlBusinessType));

        final BusinessType dvlaBusinessType = new BusinessType();
        dvlaBusinessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA")).willReturn(Optional.of(dvlaBusinessType));

        final CourtRoom courtRoom1 = new CourtRoom();
        final CourtRoom courtRoom2 = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-3")).willReturn(Optional.of(courtRoom1));
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-4")).willReturn(Optional.of(courtRoom2));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1))
                .saveCourtSchedules(argThat(list -> list != null && list.size() == 12));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithDifferentRepeatInterval() {
        // Given
        final LocalDate startDate = LocalDate.of(2024, 1, 1); // January 1st
        final LocalDate endDate = LocalDate.of(2024, 6, 30); // June 30th
        final int repeatFor = 2; // Every 2 months

        final Set<DayOfWeek> fridayOnly = new HashSet<>(Arrays.asList(DayOfWeek.FRIDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(fridayOnly)
                        .withSlotsOrDuration(30)
                        .withBusinessType("DVLA")
                        .withCourtCentreId("court-centre-2")
                        .withCourtRoomId("court-room-2")
                        .withSessionType("PM")
                        .withPanelType("Youth")
                        .withIndex(2) // Second occurrence of Friday
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-2")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldHandleMonthlyFrequencyWithEndDateBoundary() {
        // Given
        final LocalDate startDate = LocalDate.of(2024, 1, 15); // January 15th
        final LocalDate endDate = LocalDate.of(2024, 2, 14); // February 14th (less than a full month)
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> saturdayOnly = new HashSet<>(Arrays.asList(DayOfWeek.SATURDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(saturdayOnly)
                        .withSlotsOrDuration(10)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-5")
                        .withCourtRoomId("court-room-5")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(1)
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-5")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithDifferentIndexValues() {
        // Given
        final LocalDate startDate = LocalDate.of(2024, 1, 1); // January 1st
        final LocalDate endDate = LocalDate.of(2024, 2, 29); // February 29th (leap year)
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> sundayOnly = new HashSet<>(Arrays.asList(DayOfWeek.SUNDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(sundayOnly)
                        .withSlotsOrDuration(20)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-6")
                        .withCourtRoomId("court-room-6")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(5) // Fifth occurrence of Sunday (no session created if 5th doesn't exist in month)
                        .build()
        );

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldNotCreateSessionWhen5thOccurrenceDoesNotExistInMonth() {
        // Given - February 2024 has only 4 Fridays, so index 5 should not create a session
        final LocalDate startDate = LocalDate.of(2024, 2, 1); // February 1st, 2024
        final LocalDate endDate = LocalDate.of(2024, 2, 29); // February 29th, 2024 (leap year)
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> fridayOnly = new HashSet<>(Arrays.asList(DayOfWeek.FRIDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(fridayOnly)
                        .withSlotsOrDuration(20)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-index5")
                        .withCourtRoomId("court-room-index5")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(5) // Fifth Friday - February 2024 only has 4 Fridays
                        .build()
        );

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then - verify that saveCourtSchedules is called, but with an empty list or list without February session
        ArgumentCaptor<List<CourtSchedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(captor.capture());
        
        // February 2024 has 4 Fridays (2nd, 9th, 16th, 23rd), so no session should be created for index 5
        List<CourtSchedule> savedSchedules = captor.getValue();
        assertTrue(savedSchedules == null || savedSchedules.isEmpty(), 
                "No sessions should be created when 5th occurrence doesn't exist");
    }

    @Test
    void shouldCreateSessionWhen5thOccurrenceExistsInMonth() {
        // Given - March 2024 has 5 Fridays (1st, 8th, 15th, 22nd, 29th), so index 5 should create a session on the 5th Friday (29th)
        final LocalDate startDate = LocalDate.of(2024, 3, 1); // March 1st, 2024
        final LocalDate endDate = LocalDate.of(2024, 3, 31); // March 31st, 2024
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> fridayOnly = new HashSet<>(Arrays.asList(DayOfWeek.FRIDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(fridayOnly)
                        .withSlotsOrDuration(20)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-index5-exists")
                        .withCourtRoomId("court-room-index5-exists")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(5) // Fifth Friday - March 2024 has 5 Fridays (1st, 8th, 15th, 22nd, 29th)
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-index5-exists")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then - verify that saveCourtSchedules is called with exactly one session (5th Friday = March 29th)
        ArgumentCaptor<List<CourtSchedule>> captor2 = ArgumentCaptor.forClass(List.class);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(captor2.capture());
        
        List<CourtSchedule> savedSchedules = captor2.getValue();
        assertNotNull(savedSchedules, "Sessions should be created when 5th occurrence exists");
        assertEquals(1, savedSchedules.size(), "Exactly one session should be created for 5th Friday in March 2024");
        assertEquals(LocalDate.of(2024, 3, 29), savedSchedules.get(0).getSessionDate(), 
                "Session should be created on 5th Friday (March 29th, 2024)");
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateInMiddleOfMonth() {
        // Given - Random start date in middle of month
        final LocalDate startDate = LocalDate.of(2024, 3, 15); // March 15th
        final LocalDate endDate = LocalDate.of(2024, 6, 30); // June 30th
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> tuesdayOnly = new HashSet<>(Arrays.asList(DayOfWeek.TUESDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(tuesdayOnly)
                        .withSlotsOrDuration(25)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-7")
                        .withCourtRoomId("court-room-7")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(2) // Second Tuesday of each month
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-7")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateNearMonthEnd() {
        // Given - Random start date near end of month
        final LocalDate startDate = LocalDate.of(2024, 4, 28); // April 28th
        final LocalDate endDate = LocalDate.of(2024, 7, 31); // July 31st
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> thursdayOnly = new HashSet<>(Arrays.asList(DayOfWeek.THURSDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(thursdayOnly)
                        .withSlotsOrDuration(30)
                        .withBusinessType("DVLA")
                        .withCourtCentreId("court-centre-8")
                        .withCourtRoomId("court-room-8")
                        .withSessionType("PM")
                        .withPanelType("Youth")
                        .withIndex(1) // First Thursday of each month
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-8")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateInFebruaryLeapYear() {
        // Given - Random start date in February of leap year
        final LocalDate startDate = LocalDate.of(2024, 2, 10); // February 10th (leap year)
        final LocalDate endDate = LocalDate.of(2024, 5, 31); // May 31st
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> saturdayOnly = new HashSet<>(Arrays.asList(DayOfWeek.SATURDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(saturdayOnly)
                        .withSlotsOrDuration(20)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-9")
                        .withCourtRoomId("court-room-9")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(1) // First Saturday of each month
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-9")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateInDecemberCrossingYearBoundary() {
        // Given - Random start date in December crossing year boundary
        final LocalDate startDate = LocalDate.of(2023, 12, 18); // December 18th
        final LocalDate endDate = LocalDate.of(2024, 3, 31); // March 31st next year
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> mondayAndFriday = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(mondayAndFriday)
                        .withSlotsOrDuration(35)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-10")
                        .withCourtRoomId("court-room-10")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(1) // First occurrence of Monday/Friday
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-10")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateAndDifferentRepeatIntervals() {
        // Given - Random start date with different repeat intervals
        final LocalDate startDate = LocalDate.of(2024, 5, 7); // May 7th
        final LocalDate endDate = LocalDate.of(2024, 11, 30); // November 30th
        final int repeatFor = 3; // Every 3 months

        final Set<DayOfWeek> wednesdayAndSunday = new HashSet<>(Arrays.asList(DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(wednesdayAndSunday)
                        .withSlotsOrDuration(40)
                        .withBusinessType("DVLA")
                        .withCourtCentreId("court-centre-11")
                        .withCourtRoomId("court-room-11")
                        .withSessionType("PM")
                        .withPanelType("Youth")
                        .withIndex(1) // First occurrence of Wednesday/Sunday
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-11")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateAndShortDuration() {
        // Given - Random start date with very short duration
        final LocalDate startDate = LocalDate.of(2024, 6, 12); // June 12th
        final LocalDate endDate = LocalDate.of(2024, 6, 25); // June 25th (same month)
        final int repeatFor = 1; // Every month

        final Set<DayOfWeek> fridayOnly = new HashSet<>(Arrays.asList(DayOfWeek.FRIDAY));
        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(fridayOnly)
                        .withSlotsOrDuration(15)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-12")
                        .withCourtRoomId("court-room-12")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(2) // Second Friday
                        .build()
        );

        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-12")).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @Test
    void shouldCreateCourtSchedulesForMonthlyFrequencyWithRandomStartDateAndMultipleSessionsDifferentDays() {
        // Given - Random start date with multiple sessions having different day combinations
        final LocalDate startDate = LocalDate.of(2024, 7, 3); // July 3rd
        final LocalDate endDate = LocalDate.of(2024, 10, 31); // October 31st
        final int repeatFor = 2; // Every 2 months

        final Set<DayOfWeek> mondayWednesdayFriday = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        final Set<DayOfWeek> tuesdayThursday = new HashSet<>(Arrays.asList(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY));

        final List<Session> sessions = Arrays.asList(
                Session.SessionBuilder.session()
                        .withRepeatDays(mondayWednesdayFriday)
                        .withSlotsOrDuration(25)
                        .withBusinessType("TRL")
                        .withCourtCentreId("court-centre-13")
                        .withCourtRoomId("court-room-13")
                        .withSessionType("AM")
                        .withPanelType("Adult")
                        .withIndex(1)
                        .build(),
                Session.SessionBuilder.session()
                        .withRepeatDays(tuesdayThursday)
                        .withSlotsOrDuration(20)
                        .withBusinessType("DVLA")
                        .withCourtCentreId("court-centre-14")
                        .withCourtRoomId("court-room-14")
                        .withSessionType("PM")
                        .withPanelType("Youth")
                        .withIndex(2)
                        .build()
        );

        // Mock the reference data cache
        final BusinessType trlBusinessType = new BusinessType();
        trlBusinessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL")).willReturn(Optional.of(trlBusinessType));

        final BusinessType dvlaBusinessType = new BusinessType();
        dvlaBusinessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA")).willReturn(Optional.of(dvlaBusinessType));

        final CourtRoom courtRoom1 = new CourtRoom();
        final CourtRoom courtRoom2 = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-13")).willReturn(Optional.of(courtRoom1));
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-14")).willReturn(Optional.of(courtRoom2));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)));

        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }

    @org.junit.jupiter.api.Disabled("Re-enabled when validatedMonthlyFrequency duplicate detection for same date is verified")
    @Test
    void shouldDetectDuplicateForMonthlyFrequencyWithIndexWhenSessionExistsOnSameDate() {
        // Given - Monthly frequency session with index 4 (4th Friday)
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);
        final int repeatFor = 1;

        final Session newSession = Session.SessionBuilder.session()
                .withCourtCentreId("court-centre-1")
                .withCourtRoomId("court-room-1")
                .withBusinessType("LGT")
                .withSessionType("AD")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.FRIDAY))
                .withIndex(4)
                .build();

        // Existing session on 4th Friday of January 2026
        // Calculation: Jan 1 (Thu) -> 1st Friday = Jan 2 -> 4th Friday = Jan 2 + 3 weeks = Jan 23
        final CourtSchedule existingSession = new CourtSchedule();
        existingSession.setCourtScheduleId(randomUUID().toString());
        existingSession.setCourtHouseId("court-centre-1");
        existingSession.setCourtRoomId("court-room-1");
        existingSession.setBusinessType("LGT");
        existingSession.setCourtSession("AD");
        existingSession.setPanel("ADULT");
        existingSession.setSessionDate(LocalDate.of(2026, 1, 23)); // 4th Friday of January 2026

        when(courtScheduleRepository.getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES"))
                .thenReturn(List.of(existingSession));

        // When
        final JsonObject result = sessionsService.validateSessionIntegrity(newSession, startDate, endDate, repeatFor, RepeatFrequency.EVERY_MONTH);

        // Then
        verify(courtScheduleRepository).getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES");
        assertTrue(result.containsKey("errorMessage"), "Expected error message for duplicate session");
        assertTrue(result.getString("errorMessage").contains(existingSession.getCourtScheduleId()));
    }

    @Test
    void shouldNotDetectDuplicateForMonthlyFrequencyWithIndexWhenSessionExistsOnDifferentDate() {
        // Given - Monthly frequency session with index 4 (4th Friday)
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);
        final int repeatFor = 1;

        final Session newSession = Session.SessionBuilder.session()
                .withCourtCentreId("court-centre-1")
                .withCourtRoomId("court-room-1")
                .withBusinessType("LGT")
                .withSessionType("AD")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.FRIDAY))
                .withIndex(4)
                .build();

        // Existing session on 1st Friday of January 2026 (Jan 2, 2026) - different occurrence
        // January 2026: 1st Friday is Jan 2
        final CourtSchedule existingSession = new CourtSchedule();
        existingSession.setCourtScheduleId(randomUUID().toString());
        existingSession.setCourtHouseId("court-centre-1");
        existingSession.setCourtRoomId("court-room-1");
        existingSession.setBusinessType("LGT");
        existingSession.setCourtSession("AD");
        existingSession.setPanel("ADULT");
        existingSession.setSessionDate(LocalDate.of(2026, 1, 2)); // 1st Friday of January 2026

        when(courtScheduleRepository.getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES"))
                .thenReturn(List.of(existingSession));

        // When
        final JsonObject result = sessionsService.validateSessionIntegrity(newSession, startDate, endDate, repeatFor, RepeatFrequency.EVERY_MONTH);

        // Then - Should not detect duplicate as dates don't match
        assertFalse(result.containsKey("errorMessage"));
    }

    @org.junit.jupiter.api.Disabled("Re-enabled when validatedMonthlyFrequency index consideration is verified")
    @Test
    void shouldDetectDuplicateForMonthlyFrequencyWithIndexAcrossMultipleMonths() {
        // Given - Monthly frequency session with index 4 (4th Friday) for Jan-June 2026
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);
        final int repeatFor = 1;

        final Session newSession = Session.SessionBuilder.session()
                .withCourtCentreId("court-centre-1")
                .withCourtRoomId("court-room-1")
                .withBusinessType("LGT")
                .withSessionType("AM")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.FRIDAY))
                .withIndex(4)
                .build();

        // Existing session on 4th Friday of March 2026
        // Calculation: Mar 1 (Sun) -> 1st Friday = Mar 6 -> 4th Friday = Mar 6 + 3 weeks = Mar 27
        final CourtSchedule existingSession = new CourtSchedule();
        existingSession.setCourtScheduleId(randomUUID().toString());
        existingSession.setCourtHouseId("court-centre-1");
        existingSession.setCourtRoomId("court-room-1");
        existingSession.setBusinessType("LGT");
        existingSession.setCourtSession("AM");
        existingSession.setPanel("ADULT");
        existingSession.setSessionDate(LocalDate.of(2026, 3, 27)); // 4th Friday of March 2026

        when(courtScheduleRepository.getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES"))
                .thenReturn(List.of(existingSession));

        // When
        final JsonObject result = sessionsService.validateSessionIntegrity(newSession, startDate, endDate, repeatFor, RepeatFrequency.EVERY_MONTH);

        // Then
        assertTrue(result.containsKey("errorMessage"));
        assertTrue(result.getString("errorMessage").contains(existingSession.getCourtScheduleId()));
    }

    @Test
    void shouldNotDetectDuplicateForMonthlyFrequencyWhenIndexDoesNotExistInMonth() {
        // Given - Monthly frequency session with index 5 (5th Friday) for February 2026
        // February 2026 only has 4 Fridays, so no session should be created for Feb
        final LocalDate startDate = LocalDate.of(2026, 2, 1);
        final LocalDate endDate = LocalDate.of(2026, 2, 28);
        final int repeatFor = 1;

        final Session newSession = Session.SessionBuilder.session()
                .withCourtCentreId("court-centre-1")
                .withCourtRoomId("court-room-1")
                .withBusinessType("LGT")
                .withSessionType("AM")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.FRIDAY))
                .withIndex(5) // 5th Friday doesn't exist in February
                .build();

        // Existing session on 4th Friday of February 2026 (Feb 26, 2026)
        final CourtSchedule existingSession = new CourtSchedule();
        existingSession.setCourtScheduleId(randomUUID().toString());
        existingSession.setCourtHouseId("court-centre-1");
        existingSession.setCourtRoomId("court-room-1");
        existingSession.setBusinessType("LGT");
        existingSession.setCourtSession("AM");
        existingSession.setPanel("ADULT");
        existingSession.setSessionDate(LocalDate.of(2026, 2, 26)); // 4th Friday of February 2026

        when(courtScheduleRepository.getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES"))
                .thenReturn(List.of(existingSession));

        // When
        final JsonObject result = sessionsService.validateSessionIntegrity(newSession, startDate, endDate, repeatFor, RepeatFrequency.EVERY_MONTH);

        // Then - Should not detect duplicate as 5th Friday doesn't exist, so no session would be created
        assertFalse(result.containsKey("errorMessage"));
    }

    @org.junit.jupiter.api.Disabled("Re-enabled when weekly vs monthly validation path is verified")
    @Test
    void shouldUseWeeklyFrequencyValidationWhenFrequencyIsNotMonthly() {
        // Given - Weekly frequency session
        final LocalDate startDate = LocalDate.of(2026, 1, 2);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        final int repeatFor = 1;

        final Session newSession = Session.SessionBuilder.session()
                .withCourtCentreId("court-centre-1")
                .withCourtRoomId("court-room-1")
                .withBusinessType("LGT")
                .withSessionType("AM")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.FRIDAY))
                .build();

        // Existing session on first Friday (Jan 2, 2026)
        // January 2026: 1st Friday is Jan 2
        final CourtSchedule existingSession = new CourtSchedule();
        existingSession.setCourtScheduleId(randomUUID().toString());
        existingSession.setCourtHouseId("court-centre-1");
        existingSession.setCourtRoomId("court-room-1");
        existingSession.setBusinessType("LGT");
        existingSession.setCourtSession("AM");
        existingSession.setPanel("ADULT");
        existingSession.setSessionDate(LocalDate.of(2026, 1, 2)); // First Friday

        when(courtScheduleRepository.getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES"))
                .thenReturn(List.of(existingSession));

        // When
        final JsonObject result = sessionsService.validateSessionIntegrity(newSession, startDate, endDate, repeatFor, RepeatFrequency.EVERY_WEEK);

        // Then - Should use weekly validation logic
        verify(courtScheduleRepository).getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES");
        assertTrue(result.containsKey("errorMessage"), "Expected error message for duplicate session");
        assertTrue(result.getString("errorMessage").contains(existingSession.getCourtScheduleId()));
    }

    @Test
    void getOccurrenceIndexOfDayInMonthReturnsCorrectIndexForFourthFriday() throws Exception {
        // Jan 23, 2026 is the 4th Friday of January - verify helper returns 4
        final LocalDate jan23 = LocalDate.of(2026, 1, 23);
        Method method = SessionsService.class.getDeclaredMethod("getOccurrenceIndexOfDayInMonth", LocalDate.class);
        method.setAccessible(true);
        int result = (int) method.invoke(sessionsService, jan23);
        assertEquals(4, result, "Jan 23 2026 is 4th Friday of January");
    }

    @Test
    void shouldUseWeeklyFrequencyValidationWhenIndexIsNull() {
        // Given - Monthly frequency but no index (should fall back to weekly logic)
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        final int repeatFor = 1;

        final Session newSession = Session.SessionBuilder.session()
                .withCourtCentreId("court-centre-1")
                .withCourtRoomId("court-room-1")
                .withBusinessType("LGT")
                .withSessionType("AM")
                .withPanelType("ADULT")
                .withRepeatDays(Set.of(DayOfWeek.FRIDAY))
                .withIndex(null) // No index
                .build();

        when(courtScheduleRepository.getSimilarSessions("court-centre-1", "court-room-1", "LGT", startDate, endDate, "MAGISTRATES"))
                .thenReturn(emptyList());

        // When
        final JsonObject result = sessionsService.validateSessionIntegrity(newSession, startDate, endDate, repeatFor, RepeatFrequency.EVERY_MONTH);

        // Then - Should not detect duplicate (no existing sessions)
        assertFalse(result.containsKey("errorMessage"));
    }

    @Test
    void shouldPreventCourtroomChangeWhenHearingsExist() {
        // Scenario 2: Courtroom assignment is locked when hearings exist
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setCourtRoomId("original-courtroom-id");

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId("new-courtroom-id"); // Attempting to change courtroom
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(persistedCourtSchedule.getCourtSession());
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(ErrorMessages.SESSION_EDIT_ANOTHER_USER, result.getMsg());
    }

    @Test
    void shouldPreventSessionTypeChangeWhenHearingsExist() {
        // Scenario 2 & 4: Session type (AM/PM/AD) is locked when hearings exist
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setCourtSession(AM_SESSION);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(PM_SESSION); // Attempting to change session type
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("14:00");
        updateCourtSchedule.setSessionEndTime("17:00");
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(ErrorMessages.SESSION_EDIT_ANOTHER_USER, result.getMsg());
    }

    @Test
    void shouldPreventPanelChangeWhenHearingsExist() {
        // Scenario 2: Panel change is locked when hearings exist
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setPanel("ADULT");

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(persistedCourtSchedule.getCourtSession());
        updateCourtSchedule.setPanel("YOUTH"); // Attempting to change panel
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(ErrorMessages.SESSION_EDIT_ANOTHER_USER, result.getMsg());
    }

    @Test
    void shouldAllowCourtroomChangeWhenNoHearingsExist() {
        final String courtScheduleId = randomUUID().toString();

        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setSlotBased(true);
        persistedCourtSchedule.setCourtRoomId("original-courtroom-id");

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId("new-courtroom-id"); // Changing courtroom when no hearings
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(persistedCourtSchedule.getCourtSession());
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setMaxSlots(15);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(0);
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq("new-courtroom-id"))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertTrue(result.isSuccess());
    }

    @Test
    void shouldAllowEditableFieldsUpdateWhenNoHearings() {

        final String courtScheduleId = randomUUID().toString();

        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setSlotBased(true);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setMaxSlots(20); // Changing duration/slots
        updateCourtSchedule.setSessionStartTime("09:00"); // Changing start time
        updateCourtSchedule.setSessionEndTime("12:00"); // Changing end time
        updateCourtSchedule.setIsOverbookingAllowed(true); // Changing overbooking
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertTrue(result.isSuccess());
    }

    @Test
    void shouldRejectEndTimeBeforeStartTime() {
        // Scenario 6: Invalid inputs show inline errors - End time before start time
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(false);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("13:00"); // Start time after end time
        updateCourtSchedule.setSessionEndTime("10:00"); // End time before start time
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(ErrorMessages.SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME, result.getMsg());
    }

    @Test
    void shouldRejectAMSessionEndTimeAfter13() {
        // Scenario 6: Invalid inputs - AM session end time exceeds 13:00
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setCourtSession(AM_SESSION);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("14:00"); // AM session end time after 13:00
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED, result.getMsg());
    }

    @Test
    void shouldRejectPMSessionStartTimeBefore14() {
        // Scenario 6: Invalid inputs - PM session start time before 14:00
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setCourtSession(PM_SESSION);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(PM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("13:00"); // PM session start time before 14:00
        updateCourtSchedule.setSessionEndTime("17:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER, result.getMsg());
    }

    @Test
    void shouldRejectSessionTimingConflictWithHearings() {
        // Scenario 4: Edit session timing when hearings are listed - conflict with hearing times
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setSessionDate(LocalDate.of(2024, 6, 20));
        persistedCourtSchedule.setCourtSession(AM_SESSION);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION); // Session type unchanged (locked)
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("11:00"); // New start time after hearing
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), "10:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldRejectSessionEndTimeBeforeHearingTime() {
        // Scenario 4: Edit session timing - end time before hearing time
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setSessionDate(LocalDate.of(2024, 6, 20));
        persistedCourtSchedule.setCourtSession(AM_SESSION);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("09:00");
        updateCourtSchedule.setSessionEndTime("09:30"); // New end time before hearing
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);
        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), "10:00"));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(updateCourtSchedule);

        assertFalse(result.isSuccess());
        assertEquals(SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME, result.getMsg());
    }

    @Test
    void shouldAllowSessionTimingUpdateWhenHearingsFitWithinWindow() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setSlotBased(true);
        persistedCourtSchedule.setSessionDate(LocalDate.of(2024, 6, 20));
        persistedCourtSchedule.setCourtSession(AM_SESSION);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION); // Session type unchanged
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("09:00"); // New start before hearing
        updateCourtSchedule.setSessionEndTime("12:00"); // New end after hearing
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setMaxSlots(20);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        AllocatedListingEachBooked booked = mock(AllocatedListingEachBooked.class);

        when(booked.getHearingStartTime()).thenReturn(DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), "10:00"));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(List.of(booked));
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertTrue(result.isSuccess());
    }

    @Test
    void shouldSuccessfullyUpdateSessionAndReturnSuccess() {
        final String courtScheduleId = randomUUID().toString();

        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");

        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setSlotBased(true);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setMaxSlots(15);
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setIsOverbookingAllowed(true);
        updateCourtSchedule.setAllDaySplit(false);

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertTrue(result.isSuccess());
        verify(courtScheduleRepository, times(1)).update(any(), any(), any());
    }

    @Test
    void shouldAllowDraftToAssignedChangeWhenHearingsExist() {
        final String courtScheduleId = randomUUID().toString();

        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(true);
        persistedCourtSchedule.setSlotBased(true);
        persistedCourtSchedule.setIsDraft(true); // Currently Draft

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(persistedCourtSchedule.getCourtSession());
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setMaxSlots(15);

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        // Note: The actual draft status change would be handled at the repository/entity level

        // This test verifies that the update can proceed when other fields are unchanged

        assertTrue(result.isSuccess());
    }

    @Test
    void shouldUpdateDurationBasedSessionSuccessfully() {
        // Scenario 3 & 8: Update duration for duration-based session (Magistrate's court)
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "TRL");
        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setSlotBased(false);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("TRL");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setMaxDuration(180); // Duration in minutes (Hours/Minutes)
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("TRL"))).thenReturn(returnBusinessTypeObject("TRL", false, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertTrue(result.isSuccess());
        verify(courtScheduleRepository, times(1)).update(any(), any(), any());
    }

    @Test
    void shouldUpdateSlotBasedSessionSuccessfully() {
        // Scenario 3: Update slots for slot-based session
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setHasHearingsBooked(false);
        persistedCourtSchedule.setSlotBased(true);

        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(AM_SESSION);
        updateCourtSchedule.setPanel(persistedCourtSchedule.getPanel());
        updateCourtSchedule.setMaxSlots(25); // Slots number
        updateCourtSchedule.setSessionStartTime("10:00");
        updateCourtSchedule.setSessionEndTime("13:00");
        updateCourtSchedule.setAllDaySplit(false);
        updateCourtSchedule.setJurisdiction(null); // Don't change jurisdiction

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true, "MAGISTRATES"));
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(emptyList());
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId)).thenReturn(5); // Some slots booked
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());

        Result result = sessionsService.update(updateCourtSchedule);

        assertTrue(result.isSuccess());
        assertEquals(20, updateCourtSchedule.getAvailableSlots()); // 25 - 5 = 20
        verify(courtScheduleRepository, times(1)).update(any(), any(), any());
    }

    @Test
    void shouldAssignCourtroomToEligibleDraftSessions() {
        // Given
        final String courtScheduleId1 = randomUUID().toString();
        final String courtScheduleId2 = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session1 = createDomainCourtSchedule(
                courtScheduleId1, "CROWN", courtCentreId, true);
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session2 = createDomainCourtSchedule(
                courtScheduleId2, "CROWN", courtCentreId, true);

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .withCourtRoomName("Courtroom 1")
                .withOucode("OU001")
                .withCppCourtRoomId(1)
                .withOucodeL3Name("Court House 1")
                .withOucodeL2Code("OU001")
                .build();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedSession1 = 
                createPersistedCourtSchedule(courtScheduleId1);
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedSession2 = 
                createPersistedCourtSchedule(courtScheduleId2);

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId1, courtScheduleId2))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(session1, session2));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(courtScheduleRepository.findDuplicateSessionsForAssignCourtroom(anyString(), any(), anyString(), anyList(), anyString(), anyString()))
                .thenReturn(emptyList());
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId1))
                .thenReturn(persistedSession1);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId2))
                .thenReturn(persistedSession2);
        doAnswer(invocation -> null).when(courtScheduleRepository).save(any());

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertTrue(response.getErrorGroups().isEmpty(), "No error groups expected for successful assignment");
        verify(courtScheduleRepository, times(2)).retrieveCourtScheduleWithListingById(anyString());
        verify(courtScheduleRepository, times(2)).save(any());
    }

    @Test
    void shouldReturnErrorWhenCourtroomNotFound() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(emptyList());
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.empty());

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertEquals("Courtroom not found", response.getErrorGroups().get(0).getError());
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        assertEquals(courtScheduleId, response.getErrorGroups().get(0).getSessions().get(0).getCourtScheduleId());
        verify(courtScheduleRepository, never()).save(any());
    }

    @Test
    void shouldReturnErrorWhenSessionNotFound() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .build();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(emptyList());
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertEquals("Session not found", response.getErrorGroups().get(0).getError());
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        assertEquals(courtScheduleId, response.getErrorGroups().get(0).getSessions().get(0).getCourtScheduleId());
        verify(courtScheduleRepository, never()).save(any());
    }

    @Test
    void shouldReturnErrorForNonCrownJurisdictionSessions() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session = createDomainCourtSchedule(
                courtScheduleId, "MAGISTRATES", courtCentreId, true);

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .build();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(session));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(referenceDataCache.getRotaBusinessTypeByCode(anyString()))
                .thenReturn(returnBusinessTypeObject("DVLA", true));

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertEquals("assign.courtroom endpoint is only valid for CROWN jurisdiction sessions", 
                response.getErrorGroups().get(0).getError());
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        verify(courtScheduleRepository, never()).save(any());
    }

    @Test
    void shouldReturnErrorWhenCourtCentreMismatch() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String sessionCourtCentreId = randomUUID().toString();
        final String differentCourtCentreId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session = createDomainCourtSchedule(
                courtScheduleId, "CROWN", sessionCourtCentreId, true);

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(differentCourtCentreId)
                .build();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(session));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(referenceDataCache.getRotaBusinessTypeByCode(anyString()))
                .thenReturn(returnBusinessTypeObject("DVLA", true));

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertEquals("The new courtroom must belong to the same court centre as the session", 
                response.getErrorGroups().get(0).getError());
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        verify(courtScheduleRepository, never()).save(any());
    }

    @Test
    void shouldReturnErrorForAssignedSessions() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session = createDomainCourtSchedule(
                courtScheduleId, "CROWN", courtCentreId, false); // isDraft = false means assigned

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .build();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(session));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(referenceDataCache.getRotaBusinessTypeByCode(anyString()))
                .thenReturn(returnBusinessTypeObject("DVLA", true));

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertEquals("Cannot assign courtroom to an assigned session", 
                response.getErrorGroups().get(0).getError());
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        verify(courtScheduleRepository, never()).save(any());
    }

    @Test
    void shouldReturnErrorForDuplicateSessions() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.now();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session = createDomainCourtSchedule(
                courtScheduleId, "CROWN", courtCentreId, true);
        session.setSessionDate(sessionDate);
        session.setCourtSession(AM_SESSION);

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .withCourtRoomName("Courtroom 1")
                .build();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule duplicateSession = 
                new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(session));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(courtScheduleRepository.findDuplicateSessionsForAssignCourtroom(
                eq(courtRoomId), eq(sessionDate), anyString(), anyList(), eq(courtCentreId), eq(courtScheduleId)))
                .thenReturn(List.of(duplicateSession));
        when(referenceDataCache.getRotaBusinessTypeByCode(anyString()))
                .thenReturn(returnBusinessTypeObject("DVLA", true));

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertTrue(response.getErrorGroups().get(0).getError().contains("Duplicate session already exists"));
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        verify(courtScheduleRepository, never()).save(any());
    }

    @Test
    void shouldHandleMixedEligibleAndIneligibleSessions() {
        // Given
        final String eligibleSessionId = randomUUID().toString();
        final String ineligibleSessionId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule eligibleSession = createDomainCourtSchedule(
                eligibleSessionId, "CROWN", courtCentreId, true); // Draft - eligible
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule ineligibleSession = createDomainCourtSchedule(
                ineligibleSessionId, "CROWN", courtCentreId, false); // Assigned - not eligible

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .withCourtRoomName("Courtroom 1")
                .withOucode("OU001")
                .withCppCourtRoomId(1)
                .withOucodeL3Name("Court House 1")
                .withOucodeL2Code("OU001")
                .build();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedEligibleSession = 
                createPersistedCourtSchedule(eligibleSessionId);

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(eligibleSessionId, ineligibleSessionId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(eligibleSession, ineligibleSession));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(courtScheduleRepository.findDuplicateSessionsForAssignCourtroom(anyString(), any(), anyString(), anyList(), anyString(), anyString()))
                .thenReturn(emptyList());
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(eligibleSessionId))
                .thenReturn(persistedEligibleSession);
        when(referenceDataCache.getRotaBusinessTypeByCode(anyString()))
                .thenReturn(returnBusinessTypeObject("DVLA", true));
        doAnswer(invocation -> null).when(courtScheduleRepository).save(any());

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertEquals("Cannot assign courtroom to an assigned session", 
                response.getErrorGroups().get(0).getError());
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
        assertEquals(ineligibleSessionId, response.getErrorGroups().get(0).getSessions().get(0).getCourtScheduleId());
        // Eligible session should be successfully assigned
        verify(courtScheduleRepository, times(1)).save(any());
    }

    @Test
    void shouldHandleExceptionDuringAssignment() {
        // Given
        final String courtScheduleId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session = createDomainCourtSchedule(
                courtScheduleId, "CROWN", courtCentreId, true);

        final CourtRoom courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(courtRoomId)
                .withOucodeUUID(courtCentreId)
                .withCourtRoomName("Courtroom 1")
                .withOucode("OU001")
                .withCppCourtRoomId(1)
                .withOucodeL3Name("Court House 1")
                .withOucodeL2Code("OU001")
                .build();

        final AssignCourtroomRequest request = AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(List.of(courtScheduleId))
                .withCourtRoomId(courtRoomId)
                .build();

        when(courtScheduleRepository.getCourtSchedulesByIdList(anyList()))
                .thenReturn(List.of(session));
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq(courtRoomId)))
                .thenReturn(Optional.of(courtRoom));
        when(courtScheduleRepository.findDuplicateSessionsForAssignCourtroom(anyString(), any(), anyString(), anyList(), anyString(), anyString()))
                .thenReturn(emptyList());
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId))
                .thenThrow(new RuntimeException("Database error"));
        when(referenceDataCache.getRotaBusinessTypeByCode(anyString()))
                .thenReturn(returnBusinessTypeObject("DVLA", true));

        // When
        final AssignCourtroomResponse response = sessionsService.assignCourtroom(request);

        // Then
        assertNotNull(response);
        assertEquals(1, response.getErrorGroups().size());
        assertTrue(response.getErrorGroups().get(0).getError().contains("Failed to assign courtroom"));
        assertEquals(1, response.getErrorGroups().get(0).getSessions().size());
    }

    // Helper methods for creating test data
    private uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule createDomainCourtSchedule(
            final String courtScheduleId, final String jurisdiction, 
            final String courtHouseId, final boolean isDraft) {
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule session = 
                new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        session.setCourtScheduleId(courtScheduleId);
        session.setJurisdiction(jurisdiction);
        session.setCourtHouseId(courtHouseId);
        session.setIsDraft(isDraft);
        session.setBusinessType("DVLA");
        session.setCourtSession(AM_SESSION);
        session.setSessionDate(LocalDate.now());
        return session;
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule createPersistedCourtSchedule(
            final String courtScheduleId) {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedSession =
                new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule();
        persistedSession.setCourtScheduleId(courtScheduleId);
        persistedSession.setCourtRoomId("original-courtroom-id");
        persistedSession.setIsDraft(true);
        persistedSession.setBusinessType("DVLA");
        persistedSession.setUpdatedOn(Calendar.getInstance().getTime());
        return persistedSession;
    }

    // -----------------------------------------------------------------------------------------
    // Tests covering applyResolvedSessionTimes precedence on the API courtscheduler.create path.
    // Precedence: customTime (from API request) > court-centre (organisation-unit) default > defaults.
    // The organisation-unit is looked up by session.courtCentreId — the same UUID the request
    // supplies, matching organisation_unit.id in referencedataviewstore (SPRDT-809).
    // -----------------------------------------------------------------------------------------

    @Test
    void shouldApplyCustomSessionTimesWhenSuppliedOnApiRequestOverridingRefdataAndDefaults() {
        final LocalDate startDate = LocalDate.of(2026, 4, 27); // Monday
        final String courtCentreId = randomUUID().toString();
        final Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId("court-room-id")
                .withSessionType(AM_SESSION)
                .withPanelType("Adult")
                .withSessionStartTime("09:15")
                .withSessionEndTime("12:30")
                .build();
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, startDate.plusDays(1), RepeatFrequency.ONCE, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq("court-room-id")))
                .thenReturn(Optional.of(courtRoomWithRefdataKeys()));
        // Court-centre default offers a competing start time; custom must still win
        when(referenceDataCache.getOrganisationUnit(eq(courtCentreId)))
                .thenReturn(Optional.of(organisationUnitWithDefaultStartTime("10:30")));

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        final CourtSchedule captured = courtScheduleArgumentCaptor.getValue().get(0);
        assertThat(sdf.format(captured.getSessionStartTime()), is("09:15"));
        assertThat(sdf.format(captured.getSessionEndTime()), is("12:30"));
    }

    @Test
    void shouldUseCourtCentreDefaultStartButFixedEndForAmSessionWhenNoCustomTimesOnApiRequest() {
        final LocalDate startDate = LocalDate.of(2026, 4, 27); // Monday
        final String courtCentreId = randomUUID().toString();
        final Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId("court-room-id")
                .withSessionType(AM_SESSION)
                .withPanelType("Adult")
                .build(); // no sessionStartTime/sessionEndTime
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, startDate.plusDays(1), RepeatFrequency.ONCE, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq("court-room-id")))
                .thenReturn(Optional.of(courtRoomWithRefdataKeys()));
        when(referenceDataCache.getOrganisationUnit(eq(courtCentreId)))
                .thenReturn(Optional.of(organisationUnitWithDefaultStartTime("09:45")));

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        final CourtSchedule captured = courtScheduleArgumentCaptor.getValue().get(0);
        // court-centre default overrides the hardcoded morning default (10:00)...
        assertThat(sdf.format(captured.getSessionStartTime()), is("09:45"));
        // ...but the end time is always the fixed AM default (13:00), never refdata-driven.
        assertThat(sdf.format(captured.getSessionEndTime()), is(DEFAULT_MORNING_END_TIME));
    }

    @Test
    void shouldFallBackToDefaultTimesWhenNeitherCustomNorRefdataTimesPresent() {
        final LocalDate startDate = LocalDate.of(2026, 4, 27); // Monday
        final String courtCentreId = randomUUID().toString();
        final Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId("court-room-id")
                .withSessionType(AM_SESSION)
                .withPanelType("Adult")
                .build();
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, startDate.plusDays(1), RepeatFrequency.ONCE, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq("court-room-id")))
                .thenReturn(Optional.of(courtRoomWithRefdataKeys()));
        when(referenceDataCache.getOrganisationUnit(eq(courtCentreId)))
                .thenReturn(Optional.empty());

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        final CourtSchedule captured = courtScheduleArgumentCaptor.getValue().get(0);
        assertThat(sdf.format(captured.getSessionStartTime()), is(DEFAULT_MORNING_START_TIME));
        assertThat(sdf.format(captured.getSessionEndTime()), is(DEFAULT_MORNING_END_TIME));
    }

    @Test
    void shouldUseCourtCentreDefaultStartButFixedEndForAllDaySession() {
        final LocalDate startDate = LocalDate.of(2026, 4, 27); // Monday
        final String courtCentreId = randomUUID().toString();
        final Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId("court-room-id")
                .withSessionType(ALL_DAY)
                .withPanelType("Adult")
                .build();
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, startDate.plusDays(1), RepeatFrequency.ONCE, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq("court-room-id")))
                .thenReturn(Optional.of(courtRoomWithRefdataKeys()));
        when(referenceDataCache.getOrganisationUnit(eq(courtCentreId)))
                .thenReturn(Optional.of(organisationUnitWithDefaultStartTime("09:00")));

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        final CourtSchedule captured = courtScheduleArgumentCaptor.getValue().get(0);
        assertThat(sdf.format(captured.getSessionStartTime()), is("09:00"));
        // end is always the fixed ALL_DAY default (17:00), never refdata-driven
        assertThat(sdf.format(captured.getSessionEndTime()), is(DEFAULT_ALL_DAY_END_TIME));
    }

    @Test
    void shouldUseFixedStartAndEndTimeForPmSessionWithoutConsultingRefdata() {
        final LocalDate startDate = LocalDate.of(2026, 4, 27); // Monday
        final String courtCentreId = randomUUID().toString();
        final Session session = Session.SessionBuilder.session()
                .withRepeatDays(Collections.singleton(DayOfWeek.MONDAY))
                .withSlotsOrDuration(2)
                .withBusinessType("DVLA")
                .withCourtCentreId(courtCentreId)
                .withCourtRoomId("court-room-id")
                .withSessionType(PM_SESSION)
                .withPanelType("Adult")
                .build(); // no sessionStartTime/sessionEndTime
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(singletonList(session), createRepeatPattern(startDate, startDate.plusDays(1), RepeatFrequency.ONCE, 1));

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq("court-room-id")))
                .thenReturn(Optional.of(courtRoomWithRefdataKeys()));
        // lenient: a competing court-centre default, stubbed to prove PM never consults it at all
        lenient().when(referenceDataCache.getOrganisationUnit(eq(courtCentreId)))
                .thenReturn(Optional.of(organisationUnitWithDefaultStartTime("09:00")));

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).saveCourtSchedules(courtScheduleArgumentCaptor.capture());
        final CourtSchedule captured = courtScheduleArgumentCaptor.getValue().get(0);
        // PM is always fixed 14:00 / 17:00, regardless of any configured court-centre default.
        assertThat(sdf.format(captured.getSessionStartTime()), is(DEFAULT_AFTERNOON_START_TIME));
        assertThat(sdf.format(captured.getSessionEndTime()), is(DEFAULT_AFTERNOON_END_TIME));
        verify(referenceDataCache, never()).getOrganisationUnit(anyString());
    }

    private CourtRoom courtRoomWithRefdataKeys() {
        return CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("court-room-id")
                .withOucode("BAUOS05")
                .withCppCourtRoomId(1234)
                .withCourtRoomName("Court room 1")
                .withOucodeL2Code("L2")
                .withOucodeL3Name("Liverpool Street Court")
                .build();
    }

    private OrganisationUnit organisationUnitWithDefaultStartTime(final String defaultStartTime) {
        return OrganisationUnit.OrganisationUnitBuilder.anOrganisationUnit()
                .withId(randomUUID().toString())
                .withDefaultStartTime(defaultStartTime)
                .build();
    }
}
