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
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_MORNING_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_MORNING_START_TIME;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.converter.CourtScheduleToDeleteResponseConverter;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleMapper;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
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

import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.constraint.Assert;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.data.api.QueryInvocationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionsServiceTest {
    private static final Set<DayOfWeek> WEEK_DAYS_FIRST_HALF = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY));
    private static final Set<DayOfWeek> WEEK_DAYS_SECOND_HALF = new HashSet<>(Arrays.asList(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));
    @Mock
    private CourtScheduleRepository courtScheduleRepository;
    @Mock
    private CourtScheduleService courtScheduleService;
    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;
    @Mock
    private Requester requester;
    @Mock
    private AllocatedListingRepository allocatedListingRepository;
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

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();
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

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));

        // Ensure getCpCourtRoomByCourtRoomId is called, NOT getRotaCourtRoomByCourtRoomId
        when(referenceDataCache.getCpCourtRoomByCourtRoomId(eq("court-room-id"), eq(requester)))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId("court-room-id").build()));

        // When
        sessionsService.create(createSessionRequest, requester);

        // Then
        verify(referenceDataCache).getCpCourtRoomByCourtRoomId(eq("court-room-id"), eq(requester));
        verify(referenceDataCache, never()).getRotaCourtRoomByCourtRoomId(any(), any());
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
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("TRL"),eq(requester))).thenReturn(returnBusinessTypeObject("TRL", false));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest,requester);
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

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"), eq(requester)))
                .thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(), eq(requester)))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(
                createMultipleSessions_WithSameUniqueConstraint(businessType, courtHouseId, courtRoomId, courtSession, panel, 2),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1)
        );

        sessionsService.create(createSessionRequest, requester);
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

        sessionsService.create(createSessionRequest1, requester);

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
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest,requester);

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
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 2));
        sessionsService.create(createSessionRequest,requester);
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
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, repeatWeeks));
        sessionsService.create(createSessionRequest,requester);
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

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        sessionsService.create(createSessionRequest,requester);

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
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest,requester);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(any(List.class));
    }

    @Test
    void shouldCreateSingleCourtSchedulesForOnceFrequencyWithDefaultSessionTimes() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequestWithoutTimes(sessionListWithSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest,requester);
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
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest,requester);
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(any(List.class));
    }
    @Test
    void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParam();
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        given(courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam)).willReturn(List.of(courtSchedule));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(courtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = sessionsService.getCourtSchedules(courtScheduleRequestParam, requester);

        assertThat(courtSchedules.contains(courtSchedule), is(true));
    }

    @Test
    void shouldProcessProvisionalBookingRequestSuccessfully() {
        SessionsParam sessionsParam = new SessionsParam();
        sessionsParam.setSessions(List.of("1", "2"));
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = new ArrayList<>();

        when(courtScheduleRepository.deleteCourtSchedule(anyList())).thenReturn(courtSchedules);

        JsonObject response = sessionsService.deleteCourtScheduleSessions(sessionsParam, requester);

        Assert.assertTrue(response.get("sessions").asJsonArray().isEmpty());
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

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
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
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule, requester);
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
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update, requester);

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
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update, requester);

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
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update, requester);

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
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update, requester);

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
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update, requester);

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
        when(allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(List.of(courtScheduleId))).thenReturn(List.of(booked));

        Result result = sessionsService.update(update, requester);

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
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule, requester);
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

        Result result = sessionsService.update(updateCourtSchedule, requester);

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

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(persistedCourtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVAL", true));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(updateCourtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));

        Result result = sessionsService.update(updateCourtSchedule, requester);

        assertEquals("Business Type cannot be changed from Slot to Non-Slot and vice versa", result.getMsg());
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

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(anyString())).thenReturn(persistedCourtSchedule);

        Result result = sessionsService.update(updateCourtSchedule, requester);

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
        Assert.assertTrue(sessionsService.isMigrated(oucode));

    }

    @Test
    void shouldReturnFalseForNonMigratedCourt() {
        final String oucode = "B01LY00" ;
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(false);

        when(courtMigrationRepository.findByOuCode(oucode)).thenReturn(migrationStatus);
        Assert.assertFalse(sessionsService.isMigrated(oucode));

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
        Assert.assertTrue(sessionsService.isMigratedByCourtCentreId(courtCentreId));
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
        Assert.assertFalse(sessionsService.isMigratedByCourtCentreId(courtCentreId));
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
                businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
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
                System.out.println("courtRoom: " + ((JsonObject) courtRoom).getString("id"));
                e.printStackTrace();
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-1", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(trlBusinessType));

        final BusinessType dvlaBusinessType = new BusinessType();
        dvlaBusinessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).willReturn(Optional.of(dvlaBusinessType));

        final CourtRoom courtRoom1 = new CourtRoom();
        final CourtRoom courtRoom2 = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-3", requester)).willReturn(Optional.of(courtRoom1));
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-4", requester)).willReturn(Optional.of(courtRoom2));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);

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
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).willReturn(Optional.of(businessType));

        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-2", requester)).willReturn(Optional.of(courtRoom));

        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);

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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-5", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
                        .withIndex(5) // Fifth occurrence of Sunday (should fallback to 4th if not available)
                        .build()
        );
        
        // Mock the reference data cache
        final BusinessType businessType = new BusinessType();
        businessType.setSlot(false);
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-6", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-7", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-8", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-9", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-10", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-11", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(businessType));
        
        final CourtRoom courtRoom = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-12", requester)).willReturn(Optional.of(courtRoom));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
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
        given(referenceDataCache.getRotaBusinessTypeByCode("TRL", requester)).willReturn(Optional.of(trlBusinessType));
        
        final BusinessType dvlaBusinessType = new BusinessType();
        dvlaBusinessType.setSlot(true);
        given(referenceDataCache.getRotaBusinessTypeByCode("DVLA", requester)).willReturn(Optional.of(dvlaBusinessType));
        
        final CourtRoom courtRoom1 = new CourtRoom();
        final CourtRoom courtRoom2 = new CourtRoom();
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-13", requester)).willReturn(Optional.of(courtRoom1));
        given(referenceDataCache.getRotaCourtRoomByCourtRoomId("court-room-14", requester)).willReturn(Optional.of(courtRoom2));
        
        // When
        sessionsService.create(createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_MONTH, repeatFor)), requester);
        
        // Then
        verify(courtScheduleRepository, times(1)).saveCourtSchedules(argThat(Objects::nonNull));
    }
}
