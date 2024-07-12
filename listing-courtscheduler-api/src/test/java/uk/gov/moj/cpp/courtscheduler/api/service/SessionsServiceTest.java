package uk.gov.moj.cpp.courtscheduler.api.service;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.json.JsonObject;

import io.smallrye.common.constraint.Assert;
import org.apache.deltaspike.data.api.QueryInvocationException;
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
    private Requester requester;
    @Mock
    private AllocatedListingRepository allocatedListingRepository;
    @Mock
    private CourtMigrationRepository courtMigrationRepository;
    @Mock
    private ReferenceDataCache referenceDataCache;
    @InjectMocks
    private SessionsService sessionsService;
    @Captor
    private ArgumentCaptor<CourtSchedule> courtScheduleArgumentCaptor;

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEveryWeekStartingToday() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF, true),
                singleSession(WEEK_DAYS_SECOND_HALF, false)
        );
        final LocalDate startDate = LocalDate.of(2024, 06, 20);
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
        verify(courtScheduleRepository, times(27)).save(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getAllValues();

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

        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createMultipleSessions_WithSameUniqueConstraint(businessType,
                        courtHouseId, courtRoomId, courtSession, panel, 2),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor = ArgumentCaptor.forClass(CourtSchedule.class);

        sessionsService.create(createSessionRequest,requester);

        verify(courtScheduleRepository, times(1)).save(courtScheduleCaptor.capture());

        final CreateSessionRequestParam createSessionRequest1 = createSessionRequest(createMultipleSessions_WithSameUniqueConstraint(businessType,
                        courtHouseId, courtRoomId, courtSession, panel, 4),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor1 = ArgumentCaptor.forClass(CourtSchedule.class);
        when(courtScheduleRepository.save(any())).thenThrow(QueryInvocationException.class);
        sessionsService.create(createSessionRequest1,requester);
        verify(courtScheduleRepository, times(1)).update(courtScheduleCaptor1.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleCaptor1.getAllValues();
        Map<LocalDate, DayOfWeek> getDayOfWeekMapExpected =
                getDayOfWeekMap(LocalDate.now(), LocalDate.now().plusMonths(1),
                        RepeatFrequency.EVERY_WEEK, 1, Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertEquals(1, capturedCourtSchedules.size());
        assertEquals("DVLA", capturedCourtSchedules.get(0).getBusinessType());
        assertEquals(4, capturedCourtSchedules.get(0).getMaxSlots());

        //assert that capturedCourtSchedules are created on the correct dates and days of week considering getDayOfWeekMapExpected
        capturedCourtSchedules.forEach(courtSchedule -> {
            assertEquals(true, courtSchedule.isActive());
            assertEquals("DVLA", courtSchedule.getBusinessType());
        });
    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEveryWeekStartingWithLaterDate() {
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

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest,requester);
        verify(courtScheduleRepository, times(39)).save(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getAllValues();

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
        verify(courtScheduleRepository, times(21)).save(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getAllValues();

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
        verify(courtScheduleRepository, times(15)).save(courtScheduleArgumentCaptor.capture());
        List<CourtSchedule> capturedCourtSchedules = courtScheduleArgumentCaptor.getAllValues();

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

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor = ArgumentCaptor.forClass(CourtSchedule.class);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));

        sessionsService.create(createSessionRequest,requester);

        verify(courtScheduleRepository, times(8)).save(courtScheduleCaptor.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleCaptor.getAllValues();
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
        verify(courtScheduleRepository, times(1)).save(any(CourtSchedule.class));
    }

    @Test
    void shouldCreateMultipleCourtSchedulesForOnceFrequency() {
        final LocalDate startDate = LocalDate.of(2024, 06, 20);
        final Session session = singleSession(WEEK_DAYS_FIRST_HALF, true);
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(Collections.singletonList(session), createRepeatPattern(startDate, LocalDate.now().plusMonths(3), RepeatFrequency.ONCE, 1));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq("DVLA"),eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(any(),eq(requester))).thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom().build()));
        sessionsService.create(createSessionRequest,requester);
        verify(courtScheduleRepository, times(3)).save(any(CourtSchedule.class));
    }
    @Test
    void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParam();
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule courtSchedule = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule();
        given(courtScheduleRepository.findBy(courtScheduleRequestParam)).willReturn(List.of(courtSchedule));
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

        JsonObject response = sessionsService.deleteCourtScheduleSessions(sessionsParam);

        Assert.assertTrue(response.get("sessions").asJsonArray().isEmpty());
    }

    @Test
    void shouldUpdateCourtScheduleWhenNoBusinessTypeChange() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(updateCourtSchedule.getCourtRoomId()), eq(requester))).thenReturn(Optional.of(random(CourtRoom.class)));
        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldUpdateCourtScheduleWhenNoCourtRoomChange() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = sessionsService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
    }


    @Test
    void shouldUpdateCourtScheduleWhenSlotBasedChange() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setSlotBased(true);
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
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

        when(courtScheduleRepository.findBy(anyString())).thenReturn(null);

        Result result = sessionsService.update(updateCourtSchedule, requester);

        assertEquals("Court Schedule not found", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenBusinessTypeChanges() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVAL");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));

        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(persistedCourtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVAL", true));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(updateCourtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));

        Result result = sessionsService.update(updateCourtSchedule, requester);

        assertEquals("Business Type cannot be changed from Slot to Non-Slot and vice versa", result.getMsg());
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

    private static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule getPersistedCourtSchedule(final String courtScheduleId, final String businessTypeCode) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = random(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType(businessTypeCode);
        courtSchedule.setSessionDate(random(LocalDate.class));
        courtSchedule.setCourtSession(random(String.class));
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

        return Collections.singletonList(session);
    }

    private Session singleSession(Set<DayOfWeek> daysOfWeek, boolean slotBased) {
        Session session = Session.SessionBuilder.session()
                .withRepeatDays(daysOfWeek)
                .withSlotsOrDuration(20)
                .withBusinessType(slotBased ? "DVLA" : "TRL")
                .withCourtCentreId(randomUUID().toString())
                .withCourtRoomId(randomUUID().toString())
                .withSessionType("AM")
                .withPanelType("Adult")
                .build();

        return session;
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
}
