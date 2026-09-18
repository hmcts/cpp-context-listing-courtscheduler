package uk.gov.moj.cpp.courtscheduler.repository.criteria;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule_;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Service
public class CourtScheduleCriteria {

    public void getCourtScheduleCriteria(final CourtScheduleRequestParam courtScheduleRequestParam,
                                         final CriteriaBuilder criteriaBuilder,
                                         final CriteriaQuery<CourtSchedule> criteriaQuery) {
        final Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);

        Predicate finalPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_HOUSE_ID), courtScheduleRequestParam.courtCentreId());
        finalPredicate = criteriaBuilder.and(finalPredicate, criteriaBuilder.equal(root.get(CourtSchedule_.ACTIVE), true));

        if (isNotBlank(courtScheduleRequestParam.courtRoomId())) {
            final Predicate courtRoomPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID), courtScheduleRequestParam.courtRoomId());
            finalPredicate = criteriaBuilder.and(finalPredicate, courtRoomPredicate);
        }

        if (isNotBlank(courtScheduleRequestParam.businessType())) {
            final Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE), courtScheduleRequestParam.businessType());
            finalPredicate = criteriaBuilder.and(finalPredicate, businessTypePredicate);
        }

        final Predicate sessionDateBetweenPredicate = criteriaBuilder.between(root.get(CourtSchedule_.SESSION_DATE),
                LocalDate.parse(courtScheduleRequestParam.sessionStartDate()),
                LocalDate.parse(courtScheduleRequestParam.sessionEndDate()));

        finalPredicate = criteriaBuilder.and(finalPredicate, sessionDateBetweenPredicate);
        criteriaQuery.where(finalPredicate);

        criteriaQuery.orderBy(
                criteriaBuilder.asc(root.get(CourtSchedule_.COURT_ROOM_NAME)),
                criteriaBuilder.asc(root.get(CourtSchedule_.SESSION_DATE)));

    }

    //Fetch single  courtsession either by courtscheduleId or filters : OuCode+SessionDate+CourtSession+CourtRoomNumber
    public void createFetchCourtScheduleEitherByidOrFiltersCriteria(final String courtScheduleId,
                                                                    final String ouCode,
                                                                    final LocalDate sessionDate,
                                                                    final String courtSession,
                                                                    final String courtRoomNumber,
                                                                    final CriteriaBuilder criteriaBuilder,
                                                                    final CriteriaQuery<CourtSchedule> criteriaQuery) {
        final Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);
        //OR
        final Predicate courtScheduleIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SCHEDULE_ID), courtScheduleId);
        //AND
        final List<Predicate> andPredicates = new ArrayList<>();

        if (nonNull(courtScheduleId)) {
            criteriaQuery.where(courtScheduleIdPredicate);
        } else {
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.OU_CODE), ouCode));
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.SESSION_DATE), sessionDate));
            andPredicates.add(root.get(CourtSchedule_.COURT_SESSION).in("AD", courtSession));
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_NUMBER), courtRoomNumber));
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.ACTIVE), true));
            final Predicate andCombination = criteriaBuilder.and(andPredicates.toArray(new Predicate[0]));
            criteriaQuery.where(andCombination);
        }
    }

    public void createMultipleSessionsCourtScheduleCriteria(final CourtSchedule courtSchedule,
                                                            final CriteriaBuilder criteriaBuilder, final CriteriaQuery<CourtSchedule> criteriaQuery) {
        final List<Predicate> predicateList = new ArrayList<>();
        final Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);
        if (isNotBlank(courtSchedule.getCourtHouseId())) {
            final Predicate courtHouseIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_HOUSE_ID), courtSchedule.getCourtHouseId());
            predicateList.add(courtHouseIdPredicate);
        }
        if (isNotBlank(courtSchedule.getCourtRoomId())) {
            final Predicate courtRoomIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID), courtSchedule.getCourtRoomId());
            predicateList.add(courtRoomIdPredicate);
        }
        if (isNotBlank(courtSchedule.getBusinessType())) {
            final Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE), courtSchedule.getBusinessType());
            predicateList.add(businessTypePredicate);
        }
        if (isNotBlank(courtSchedule.getPanel())) {
            final Predicate panelPredicate;
            final Predicate inputPanelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), courtSchedule.getPanel());
            if("YOUTH".equalsIgnoreCase(courtSchedule.getPanel())) {
                final Predicate youthPanelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), "YOUTH");
                panelPredicate = criteriaBuilder.or(inputPanelPredicate, youthPanelPredicate);
            } else {
                final Predicate adultPanelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), "ADULT");
                panelPredicate = criteriaBuilder.or(inputPanelPredicate, adultPanelPredicate);
            }
            predicateList.add(panelPredicate);
        }
        if (isNotBlank(courtSchedule.getCourtSession())) {
            final Predicate courtSessionPredicate;
            if("AD".equalsIgnoreCase(courtSchedule.getCourtSession())) {
                final Predicate amCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "AM");
                final Predicate pmCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "PM");
                final Predicate allDayCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "AD");
                courtSessionPredicate = criteriaBuilder.or(amCourtSessionPredicate, pmCourtSessionPredicate, allDayCourtSessionPredicate);
            } else {
                final Predicate inputCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), courtSchedule.getCourtSession());
                final Predicate amCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "AM");
                final Predicate pmCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "PM");
                courtSessionPredicate = criteriaBuilder.or(amCourtSessionPredicate, pmCourtSessionPredicate, inputCourtSessionPredicate);
            }
            predicateList.add(courtSessionPredicate);
        }
        if (nonNull(courtSchedule.getSessionDate())) {
            final Predicate sessionDatePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.SESSION_DATE), courtSchedule.getSessionDate());
            predicateList.add(sessionDatePredicate);
        }
        if (nonNull(courtSchedule.getIsDraft())) {
            final Predicate isDraftPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.IS_DRAFT), courtSchedule.getIsDraft());
            predicateList.add(isDraftPredicate);
        }
        criteriaQuery.where(criteriaBuilder.and(predicateList.toArray(new Predicate[]{})));
    }
}
