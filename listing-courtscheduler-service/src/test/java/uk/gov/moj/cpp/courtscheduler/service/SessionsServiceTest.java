package uk.gov.moj.cpp.courtscheduler.service;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    @InjectMocks
    private SessionsService sessionsService;
    @Captor
    private ArgumentCaptor<CourtSchedule> courtScheduleArgumentCaptor;

    @BeforeEach
    void setUp() {


    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEveryWeekStartingToday() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF,true),
                singleSession(WEEK_DAYS_SECOND_HALF,false)
        );
        final LocalDate startDate = LocalDate.of(2024,06,20);
        final LocalDate endDate = startDate.plusMonths(1);
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        LocalDate lastDate = findTheLastDateThatIsInOneOfTheWeekDays(endDate, allSessionDays);

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest);
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
    public void shouldUpdateMultipleSessions_OnMaxSlotsValue_GreaterThanZero() {
        String courtHouseId = random(String.class);
        String courtRoomId = random(String.class);
        String businessType = "DVLA";
        String panel = random(String.class);
        String courtSession = random(String.class);

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createMultipleSessions_WithSameUniqueConstraint(businessType,
                        courtHouseId, courtRoomId, courtSession, panel, 2),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor = ArgumentCaptor.forClass(CourtSchedule.class);

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(1)).save(courtScheduleCaptor.capture());

        final CreateSessionRequestParam createSessionRequest1 = createSessionRequest(createMultipleSessions_WithSameUniqueConstraint(businessType,
                        courtHouseId, courtRoomId, courtSession, panel, 4),
                createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor1 = ArgumentCaptor.forClass(CourtSchedule.class);
        when(courtScheduleRepository.save(any())).thenThrow(QueryInvocationException.class);
        sessionsService.create(createSessionRequest1);
        verify(courtScheduleRepository, times(1)).update(courtScheduleCaptor1.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleCaptor1.getAllValues();
        Map<LocalDate,DayOfWeek> getDayOfWeekMapExpected =
                getDayOfWeekMap(LocalDate.now(), LocalDate.now().plusMonths(1),
                        RepeatFrequency.EVERY_WEEK, 1, Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertEquals(1, capturedCourtSchedules.size());
        assertEquals("DVLA", capturedCourtSchedules.get(0).getBusinessType());
        assertEquals(4, capturedCourtSchedules.get(0).getMaxSlots());

        //assert that capturedCourtSchedules are created on the correct dates and days of week considering getDayOfWeekMapExpected
        capturedCourtSchedules.forEach(courtSchedule -> {
            assertEquals(true,courtSchedule.isActive());
            assertEquals("DVLA",courtSchedule.getBusinessType());
        });
    }

    @Test
    void shouldStayInDateBoundsWhenRepeatPatternIsEveryWeekStartingWithLaterDate() {
        final List<Session> sessions = Arrays.asList(
                singleSession(WEEK_DAYS_FIRST_HALF,true)
        );
        final LocalDate startDate = LocalDate.of(2024,06,20).plusWeeks(2);
        final LocalDate endDate = startDate.plusMonths(3);
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        LocalDate lastDate = findTheLastDateThatIsInOneOfTheWeekDays(endDate, allSessionDays);

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest);
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
                singleSession(WEEK_DAYS_FIRST_HALF,true)
        );
        final LocalDate startDate = LocalDate.of(2024,06,20).plusWeeks(2);
        final LocalDate endDate = startDate.plusMonths(3);
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);
        LocalDate lastDate = findTheLastDateThatIsInOneOfTheWeekDays(endDate, allSessionDays);

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 2));
        sessionsService.create(createSessionRequest);
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
                singleSession(WEEK_DAYS_FIRST_HALF,true)
        );
        final LocalDate startDate = LocalDate.of(2024,06,20).plusWeeks(2);
        final LocalDate endDate = startDate.plusMonths(3);
        final int repeatWeeks = 3;
        final Set<DayOfWeek> allSessionDays = sessions.stream().map(Session::getRepeatDays).reduce((first, second) -> {
            Set<DayOfWeek> allDays = new HashSet<>(first);
            allDays.addAll(second);
            return allDays;
        }).get();

        LocalDate firstDate = findTheFirstDateThatIsInOneOfTheWeekDays(startDate, allSessionDays);

        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessions, createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, repeatWeeks));
        sessionsService.create(createSessionRequest);
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
        final LocalDate startDate = LocalDate.of(2024,06,20);
        final LocalDate endDate = startDate.plusMonths(1);
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createMultipleSessions(), createRepeatPattern(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1));

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor = ArgumentCaptor.forClass(CourtSchedule.class);

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(8)).save(courtScheduleCaptor.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleCaptor.getAllValues();
        Map<LocalDate,DayOfWeek> getDayOfWeekMapExpected = getDayOfWeekMap(startDate, endDate, RepeatFrequency.EVERY_WEEK, 1, Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

        assertEquals(8, capturedCourtSchedules.size());

        assertEquals("DVLA", capturedCourtSchedules.get(0).getBusinessType());

        //assert that capturedCourtSchedules are created on the correct dates and days of week considering getDayOfWeekMapExpected
        capturedCourtSchedules.forEach(courtSchedule -> {
            assertTrue(getDayOfWeekMapExpected.containsKey(courtSchedule.getSessionDate()));
            assertEquals(getDayOfWeekMapExpected.get(courtSchedule.getSessionDate()), courtSchedule.getSessionDate().getDayOfWeek());
            assertEquals(true,courtSchedule.isActive());
            assertEquals("DVLA",courtSchedule.getBusinessType());
        });
    }

    @Test
    void shouldCreateSingleCourtSchedulesForOnceFrequency() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(sessionListWithSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).save(any(CourtSchedule.class));
    }

    @Test
    void shouldCreateMultipleCourtSchedulesForOnceFrequency() {
        final LocalDate startDate = LocalDate.of(2024,06,20);
        final Session session =  singleSession(WEEK_DAYS_FIRST_HALF,true);
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(Collections.singletonList(session), createRepeatPattern(startDate, LocalDate.now().plusMonths(3), RepeatFrequency.ONCE, 1));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(3)).save(any(CourtSchedule.class));
    }

    private Map<LocalDate,DayOfWeek> getDayOfWeekMap(LocalDate startDate, LocalDate endDate, RepeatFrequency frequency, int repeatFor,List<DayOfWeek> daysOfWeek) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        long weekNumber = 0; //start with first week
        Map<LocalDate,DayOfWeek> dayOfWeekMap = new HashMap<>();

        if(frequency.equals(RepeatFrequency.ONCE)){
            dayOfWeekMap.put(startDate,daysOfWeek.get(0));
            return dayOfWeekMap;
        }
        //else RepeatFrequency.EVERY_WEEK
        while (weekNumber <= weeksBetween) {
            for(DayOfWeek dayOfWeek : daysOfWeek) {
                dayOfWeekMap.put(startDate.plusWeeks(weekNumber).with(TemporalAdjusters.next(dayOfWeek)),dayOfWeek);
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

    private Session singleSession(Set<DayOfWeek> daysOfWeek,boolean slotBased) {
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
}
