package uk.gov.moj.cpp.courtscheduler.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.moj.cpp.courtscheduler.domain.*;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import javax.ejb.Local;

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
    void shouldCreateMuiltipleCourtSchedulesForEveryWeekFrequency() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createMultipleSessions(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.EVERY_WEEK, 1));

        ArgumentCaptor<CourtSchedule> courtScheduleCaptor = ArgumentCaptor.forClass(CourtSchedule.class);

        sessionsService.create(createSessionRequest);

        verify(courtScheduleRepository, times(8)).save(courtScheduleCaptor.capture());

        List<CourtSchedule> capturedCourtSchedules = courtScheduleCaptor.getAllValues();
        Map<LocalDate,DayOfWeek> getDayOfWeekMapExpected = getDayOfWeekMap(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.EVERY_WEEK, 1, Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));

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