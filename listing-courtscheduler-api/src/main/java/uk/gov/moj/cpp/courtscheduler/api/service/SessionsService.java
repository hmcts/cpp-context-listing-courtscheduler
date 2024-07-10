package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
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

    @Inject
    private ReferenceDataCache referenceDataCache;

    public void create(CreateSessionRequestParam createSessionRequestParam, Requester requester) {
        final List<CourtSchedule> courtScheduleList = new ArrayList<>();
        final List<Session> sessionList = createSessionRequestParam.getSessionList();
        final RepeatPattern repeatPattern = createSessionRequestParam.getRepeatPattern();
        final LocalDate startDate = repeatPattern.getStartDate();
        final LocalDate endDate = repeatPattern.getEndDate();

        if (repeatPattern.getFrequency().equals(RepeatFrequency.ONCE)) {
            processOnceFrequency(sessionList, startDate, courtScheduleList,requester);
        } else if (repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_WEEK)) {
            processWeeklyFrequency(sessionList, startDate, endDate, repeatPattern.getRepeatFor(), courtScheduleList,requester);
        }

        saveCourtSchedules(courtScheduleList);
    }

    private void processOnceFrequency(List<Session> sessionList, LocalDate startDate, List<CourtSchedule> courtScheduleList,Requester requester) {
        for (Session session : sessionList) {
            for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                LocalDate sessionDateCandidate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate,requester);
                courtScheduleList.add(courtSchedule);
            }
        }
    }

    private void processWeeklyFrequency(List<Session> sessionList, LocalDate startDate, LocalDate endDate, int repeatFor, List<CourtSchedule> courtScheduleList,Requester requester) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        for (long weekNumber = 0; weekNumber <= weeksBetween; weekNumber += repeatFor) {
            for (Session session : sessionList) {
                for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                    LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                    if (sessionDateCandidate.isAfter(endDate)) {
                        continue;
                    }
                    CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate,requester);
                    courtScheduleList.add(courtSchedule);
                }
            }
        }
    }

    private CourtSchedule buildCourtSchedule(Session session, LocalDate sessionDateCandidate,Requester requester) {
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new CourtSchedule.CourtScheduleBuilder();
        courtScheduleBuilder.withCourtScheduleId(UUID.randomUUID().toString())
                .withBusinessType(session.getBusinessType())
                .withCourtHouseId(session.getCourtCentreId())
                .withCourtRoomId(session.getCourtRoomId())
                .withActive(true)
                .withSessionDate(sessionDateCandidate)
                .withCourtSession(session.getSessionType())
                .withPanel(session.getPanelType());
        enrichSession(courtScheduleBuilder, session.getSlotsOrDuration(),requester);
        return courtScheduleBuilder.build();
    }

    private void saveCourtSchedules(List<CourtSchedule> courtScheduleList) {
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleList.stream()
                .map(CourtScheduleMapper::toEntity)
                .toList();
        courtScheduleEntities.forEach(courtSchedule -> {
            try {
                courtScheduleRepository.save(courtSchedule);
            } catch (QueryInvocationException queryInvocationException) {
                courtScheduleRepository.update(courtSchedule);
            }
        });
    }

    private void enrichSession(CourtSchedule.CourtScheduleBuilder builder, int maxSlotsorDuration,Requester requester) {
        final BusinessType businessType = referenceDataCache.getRotaBusinessTypeByCode(builder.getBusinessType(),requester).orElseThrow(() -> new RuntimeException("Business Type not found" + builder.getBusinessType()));
        final CourtRoom courtRoom = referenceDataCache.getRotaCourtRoomByCourtRoomId(builder.getCourtRoomId(),requester).orElseThrow(() -> new RuntimeException("Court Room not found" + builder.getCourtRoomId()));
        if (businessType.isSlot()) {
            builder.withSlotBased(true);
            builder.withMaxSlots(maxSlotsorDuration);
            builder.withAvailableSlots(maxSlotsorDuration);
            builder.withMaxDuration(0);
            builder.withAvailableDuration(0);
        } else {
            builder.withSlotBased(false);
            builder.withMaxDuration(maxSlotsorDuration);
            builder.withAvailableDuration(maxSlotsorDuration);
            builder.withMaxSlots(0);
            builder.withAvailableSlots(0);
        }

        if (courtRoom != null) {
            builder.withOuCode(courtRoom.getOucode());
            builder.withCourtRoomName(courtRoom.getCourtroomName());
            builder.withCourtRoomNumber(courtRoom.getCppCourtRoomId());
            builder.withCourtHouseName(courtRoom.getOucodeL3Name());
            builder.withOperationalUnit(courtRoom.getOucodeL2Code());
        }
    }
}