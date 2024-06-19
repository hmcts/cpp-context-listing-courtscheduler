package uk.gov.moj.cpp.courtscheduler.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionsServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @InjectMocks
    private SessionsService sessionsService;


    @BeforeEach
    void setUp() {

    }

    private List<Session> createSingleSession() {
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

    @Test
    void shouldCreateSingleCourtSchedulesForEveryWeekFrequency() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.EVERY_WEEK, 1));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(4)).save(any(CourtSchedule.class));
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
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(1), RepeatFrequency.ONCE, 1));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(1)).save(any(CourtSchedule.class));
    }


    private Map<LocalDate,DayOfWeek> getDayOfWeekMap(LocalDate startDate, LocalDate endDate, RepeatFrequency frequency, int repeatFor,List<DayOfWeek> daysOfWeek) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        long weekNumber = 1; //start with first week
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

    @Test
    void shouldCreateSingleCourtSchedulesForEveryTwoWeeksFrequency() {
        final CreateSessionRequestParam createSessionRequest = createSessionRequest(createSingleSession(), createRepeatPattern(LocalDate.now(), LocalDate.now().plusMonths(2), RepeatFrequency.EVERY_WEEK, 2));
        sessionsService.create(createSessionRequest);
        verify(courtScheduleRepository, times(4)).save(any(CourtSchedule.class));
    }

}