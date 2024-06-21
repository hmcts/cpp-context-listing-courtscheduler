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
            for (Session session : sessionList) {
                for(DayOfWeek dayOfWeek : session.getRepeatDays()) {
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
                            .withSessionDate(repeatPattern.getStartDate().with(TemporalAdjusters.nextOrSame(dayOfWeek)))
                            .withCourtSession(session.getSessionType())
                            .withPanel(session.getPanelType())
                            .build();
                    courtScheduleList.add(courtSchedule);
                }
            }
        }

        else if(repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_WEEK)) {
            //calculate real dates based on startdate, enddate and  frequency
            final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
            long weekNumber = 0; //start with first week

            while (weekNumber <= weeksBetween) {
                for (Session session : sessionList) {
                    for(DayOfWeek dayOfWeek : session.getRepeatDays()) {
                        LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                        if(sessionDateCandidate.isAfter(endDate)) {
                            continue;
                        }
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
                                .withSessionDate(sessionDateCandidate)
                                .withCourtSession(session.getSessionType())
                                .withPanel(session.getPanelType())
                                .build();
                        courtScheduleList.add(courtSchedule);
                    }
                }
                weekNumber += repeatPattern.getRepeatFor();
            }

        }

        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = new ArrayList<>();
        for( CourtSchedule courtSchedule : courtScheduleList) {
            courtScheduleEntities.add(CourtScheduleMapper.toEntity(courtSchedule));
        }
        courtScheduleEntities.forEach(courtScheduleRepository::save);
    }
}