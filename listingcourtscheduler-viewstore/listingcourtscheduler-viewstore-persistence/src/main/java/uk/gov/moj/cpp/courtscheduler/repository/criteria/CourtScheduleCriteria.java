package uk.gov.moj.cpp.courtscheduler.repository.criteria;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule_;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

@ApplicationScoped
public class CourtScheduleCriteria {
    public void createHearingSlotsCourtScheduleCriteria(final HearingSlotRequestParam hearingSlotRequestParam,
                                                        CriteriaBuilder criteriaBuilder, CriteriaQuery<CourtSchedule> criteriaQuery) {
        List<Predicate> predicateList = new ArrayList<>();
        Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);
        Predicate activePredicate = criteriaBuilder.equal(root.get("active"), true);
        Predicate panelPredicate;
        if (hearingSlotRequestParam.panel().contains(",")) {
            panelPredicate = root.get(CourtSchedule_.PANEL).in(hearingSlotRequestParam.panel());
        } else {
            panelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), hearingSlotRequestParam.panel());
        }
        Predicate dateBetween = criteriaBuilder.between(root.get(CourtSchedule_.SESSION_DATE),
                LocalDate.parse(hearingSlotRequestParam.sessionStartDate()),
                LocalDate.parse(hearingSlotRequestParam.sessionEndDate()));
        predicateList.add(activePredicate);
        predicateList.add(panelPredicate);
        predicateList.add(dateBetween);
        if (isNotBlank(hearingSlotRequestParam.oucodeL2Code())) {
            Predicate ouLevelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.OPERATIONAL_UNIT), hearingSlotRequestParam.oucodeL2Code());
            predicateList.add(ouLevelPredicate);
        }
        if (isNotBlank(hearingSlotRequestParam.ouCode())) {
            Predicate ouCodePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.OU_CODE), hearingSlotRequestParam.ouCode());
            predicateList.add(ouCodePredicate);
        }
        if (isNotBlank(hearingSlotRequestParam.courtRoomId())) {
            Predicate courtRoomPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID),
                    hearingSlotRequestParam.courtRoomId());
            predicateList.add(courtRoomPredicate);
        }
        if (isNotBlank(hearingSlotRequestParam.courtRoomNumber())) {
            Predicate courtRoomNumberPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_NUMBER),
                    hearingSlotRequestParam.courtRoomNumber());
            predicateList.add(courtRoomNumberPredicate);
        }
        if (isNotBlank(hearingSlotRequestParam.businessType())) {
            Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE),
                    hearingSlotRequestParam.businessType());
            predicateList.add(businessTypePredicate);
        }
        if (isNotBlank(hearingSlotRequestParam.courtSession())) {
            Predicate courtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION),
                    hearingSlotRequestParam.courtSession());
            predicateList.add(courtSessionPredicate);
        }

        criteriaQuery.select(root).where(predicateList.toArray(new Predicate[]{}));

        criteriaQuery.orderBy(criteriaBuilder.asc(root.get(CourtSchedule_.SESSION_DATE)),
                criteriaBuilder.asc(root.get(CourtSchedule_.COURT_HOUSE_NAME)),
                criteriaBuilder.asc(root.get(CourtSchedule_.COURT_ROOM_NAME)),
                criteriaBuilder.asc(root.get(CourtSchedule_.COURT_SESSION)),
                criteriaBuilder.asc(root.get(CourtSchedule_.BUSINESS_TYPE)));
    }

    public void getCourtScheduleCriteria(final CourtScheduleRequestParam courtScheduleRequestParam,
                                         CriteriaBuilder criteriaBuilder,
                                         CriteriaQuery<CourtSchedule> criteriaQuery) {
        Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);

        Predicate finalPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_HOUSE_ID), courtScheduleRequestParam.courtCentreId());

        if (isNotBlank(courtScheduleRequestParam.courtRoomId())) {
            Predicate courtRoomPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID), courtScheduleRequestParam.courtRoomId());
            finalPredicate = criteriaBuilder.and(finalPredicate, courtRoomPredicate);
        }

        if (isNotBlank(courtScheduleRequestParam.businessType())) {
            Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE), courtScheduleRequestParam.businessType());
            finalPredicate = criteriaBuilder.and(finalPredicate, businessTypePredicate);
        }

        Predicate sessionDateBetweenPredicate = criteriaBuilder.between(root.get(CourtSchedule_.SESSION_DATE),
                LocalDate.parse(courtScheduleRequestParam.sessionStartDate()),
                LocalDate.parse(courtScheduleRequestParam.sessionEndDate()));

        finalPredicate = criteriaBuilder.and(finalPredicate, sessionDateBetweenPredicate);
        criteriaQuery.where(finalPredicate);

        criteriaQuery.orderBy(
                criteriaBuilder.asc(root.get(CourtSchedule_.COURT_ROOM_NAME)),
                criteriaBuilder.asc(root.get(CourtSchedule_.SESSION_DATE)));

    }

    public void createCourtScheduleJudiciaryCriteria(List<CourtSchedule> courtScheduleList,
                                                     CriteriaBuilder criteriaBuilder, CriteriaQuery<CourtScheduleJudiciary> criteriaQuery) {
        Root<CourtScheduleJudiciary> root = criteriaQuery.from(CourtScheduleJudiciary.class);
        courtScheduleList.forEach(e -> {
            if (isNotBlank(e.getCourtScheduleId()) && isNotBlank(e.getListingProfileId())) {
                Predicate activePredicate = criteriaBuilder.equal(root.get("active"), true);
                Predicate courtScheduleIdPredicate = criteriaBuilder.equal(root.get(CourtScheduleJudiciary_.id)
                        .get(CourtScheduleJudiciaryKey_.COURT_SCHEDULE_ID), e.getCourtScheduleId());
                Predicate courtListIdPredicate = criteriaBuilder.equal(root.get(CourtScheduleJudiciary_.COURT_LISTING_PROFILE_ID), e.getListingProfileId());
                criteriaQuery.select(root).where(criteriaBuilder.and(activePredicate, courtScheduleIdPredicate, courtListIdPredicate));
            }
        });
    }

    //Fetch single  courtsession either by courtscheduleId or filters : OuCode+SessionDate+CourtSession+CourtRoomNumber
    public void createFetchCourtScheduleEitherByidOrFiltersCriteria(String courtScheduleId,
                                                                    String ouCode,
                                                                    LocalDate sessionDate,
                                                                    String courtSession,
                                                                    String courtRoomNumber,
                                                                    CriteriaBuilder criteriaBuilder,
                                                                    CriteriaQuery<CourtSchedule> criteriaQuery) {
        Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);
        //OR
        Predicate courtScheduleIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SCHEDULE_ID), courtScheduleId);
        //AND
        List<Predicate> andPredicates = new ArrayList<>();

        if (nonNull(courtScheduleId)) {
            criteriaQuery.where(courtScheduleIdPredicate);
        } else {
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.OU_CODE), ouCode));
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.SESSION_DATE), sessionDate));
            andPredicates.add(root.get(CourtSchedule_.COURT_SESSION).in("AD", courtSession));
            andPredicates.add(criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_NUMBER), courtRoomNumber));
            Predicate andCombination = criteriaBuilder.and(andPredicates.toArray(new Predicate[0]));
            criteriaQuery.where(andCombination);
        }
    }

    public void createMultipleSessionsCourtScheduleCriteria(CourtSchedule courtSchedule,
                                                            CriteriaBuilder criteriaBuilder, CriteriaQuery<CourtSchedule> criteriaQuery) {
        List<Predicate> predicateList = new ArrayList<>();
        Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);
        if (isNotBlank(courtSchedule.getCourtHouseId())) {
            Predicate courtHouseIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_HOUSE_ID), courtSchedule.getCourtHouseId());
            predicateList.add(courtHouseIdPredicate);
        }
        if (isNotBlank(courtSchedule.getCourtRoomId())) {
            Predicate courtRoomIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID), courtSchedule.getCourtRoomId());
            predicateList.add(courtRoomIdPredicate);
        }
        if (isNotBlank(courtSchedule.getBusinessType())) {
            Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE), courtSchedule.getBusinessType());
            predicateList.add(businessTypePredicate);
        }
        if (isNotBlank(courtSchedule.getPanel())) {
            Predicate panelPredicate;
            Predicate inputPanelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), courtSchedule.getPanel());
            if("YOUTH".equalsIgnoreCase(courtSchedule.getPanel())) {
                Predicate youthPanelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), "YOUTH");
                panelPredicate = criteriaBuilder.or(inputPanelPredicate, youthPanelPredicate);
            } else {
                Predicate adultPanelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), "ADULT");
                panelPredicate = criteriaBuilder.or(inputPanelPredicate, adultPanelPredicate);
            }
            predicateList.add(panelPredicate);
        }
        if (isNotBlank(courtSchedule.getCourtSession())) {
            Predicate courtSessionPredicate;
            if("AD".equalsIgnoreCase(courtSchedule.getCourtSession())) {
                Predicate amCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "AM");
                Predicate pmCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "PM");
                Predicate allDayCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "AD");
                courtSessionPredicate = criteriaBuilder.or(amCourtSessionPredicate, pmCourtSessionPredicate, allDayCourtSessionPredicate);
            } else {
                Predicate inputCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), courtSchedule.getCourtSession());
                Predicate amCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "AM");
                Predicate pmCourtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), "PM");
                courtSessionPredicate = criteriaBuilder.or(amCourtSessionPredicate, pmCourtSessionPredicate, inputCourtSessionPredicate);
            }
            predicateList.add(courtSessionPredicate);
        }
        if (nonNull(courtSchedule.getSessionDate())) {
            Predicate sessionDatePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.SESSION_DATE), courtSchedule.getSessionDate());
            predicateList.add(sessionDatePredicate);
        }
        criteriaQuery.where(criteriaBuilder.and(predicateList.toArray(new Predicate[]{})));
    }
}
