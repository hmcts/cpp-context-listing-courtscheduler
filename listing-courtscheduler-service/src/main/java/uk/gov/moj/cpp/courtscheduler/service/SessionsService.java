package uk.gov.moj.cpp.courtscheduler.service;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.deltaspike.data.api.QueryInvocationException;

@ApplicationScoped
public class SessionsService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public void create(CreateSessionRequestParam createSessionRequestParam) {
        final List<CourtSchedule> courtScheduleList = new ArrayList<>();
        final List<Session> sessionList = createSessionRequestParam.getSessionList();
        final RepeatPattern repeatPattern = createSessionRequestParam.getRepeatPattern();
        final LocalDate startDate = repeatPattern.getStartDate();
        final LocalDate endDate = repeatPattern.getEndDate();

        if(repeatPattern.getFrequency().equals(RepeatFrequency.ONCE)) {
            createDomainListFromSessionList(sessionList, repeatPattern, courtScheduleList, 0);
        }
        else if(repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_WEEK)) {
            //calculate real dates based on startDate, endDate and  frequency
            final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
            long weekNumber = 0; //start with first week
            while (weekNumber <= weeksBetween) {
                createDomainListFromSessionList(sessionList, repeatPattern, courtScheduleList, weekNumber);
                weekNumber += repeatPattern.getRepeatFor();
            }
        }

        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = new ArrayList<>();
        for( CourtSchedule courtSchedule : courtScheduleList) {
            courtScheduleEntities.add(CourtScheduleMapper.toEntity(courtSchedule));
        }
        courtScheduleEntities.forEach(courtSchedule -> {
            try {
                courtScheduleRepository.save(courtSchedule);
            } catch (QueryInvocationException queryInvocationException) {
                courtScheduleRepository.update(courtSchedule);
            }
        });
    }

    private static void createDomainListFromSessionList(final List<Session> sessionList, final RepeatPattern repeatPattern,
                                                        final List<CourtSchedule> courtScheduleList, long weekNumber) {
        LocalDate sessionDateCandidate = null;
        for (Session session : sessionList) {
            for(DayOfWeek dayOfWeek : session.getRepeatDays()) {
                if(repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_WEEK)) {
                    sessionDateCandidate = repeatPattern.getStartDate().plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                    if(sessionDateCandidate.isAfter(repeatPattern.getEndDate())) {
                        continue;
                    }
                }
                createCourtScheduleDomainList(session, repeatPattern, sessionDateCandidate, courtScheduleList, dayOfWeek);
            }
        }
    }

    private static void createCourtScheduleDomainList(final Session session, final RepeatPattern repeatPattern, final LocalDate sessionDateCandidate,
                                                      final List<CourtSchedule> courtScheduleList, DayOfWeek dayOfWeek) {
        final CourtSchedule courtSchedule = CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withMaxDuration(session.getSlotsOrDuration())
                .withAvailableDuration(session.getSlotsOrDuration())
                .withMaxSlots(session.getSlotsOrDuration())
                .withAvailableSlots(session.getSlotsOrDuration())
                .withBusinessType(session.getBusinessType())
                .withCourtHouseId(session.getCourtCentreId())
                .withCourtRoomId(session.getCourtRoomId())
                .withSlotBased(true)
                .withActive(true)
                .withCourtSession(session.getSessionType())
                .withPanel(session.getPanelType())
                .build();
        LocalDate localDate = repeatPattern.getFrequency().equals(RepeatFrequency.ONCE) ?
                repeatPattern.getStartDate().with(TemporalAdjusters.nextOrSame(dayOfWeek)) :
                sessionDateCandidate;
        courtSchedule.setSessionDate(localDate);
        courtScheduleList.add(courtSchedule);
    }
}
