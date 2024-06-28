package uk.gov.moj.cpp.courtscheduler.repository.criteria;

import static java.util.stream.Collectors.joining;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule_;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class CourtScheduleCriteria {
    public void createHearingSlotsCourtScheduleCriteria(final HearingSlotRequestParam hearingSlotRequestParam,
                                                        CriteriaBuilder criteriaBuilder, CriteriaQuery<CourtSchedule> criteriaQuery) {
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
        criteriaQuery.select(root).where(criteriaBuilder.and(activePredicate, panelPredicate, dateBetween));
        if (StringUtils.isNotBlank(hearingSlotRequestParam.oucodeL2Code())) {
            Predicate ouLevelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.OPERATIONAL_UNIT), hearingSlotRequestParam.oucodeL2Code());
            criteriaQuery.where(criteriaBuilder.and(ouLevelPredicate));
        }
        if (StringUtils.isNotBlank(hearingSlotRequestParam.ouCode())) {
            Predicate ouCodePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.OU_CODE), hearingSlotRequestParam.ouCode());
            criteriaQuery.where(criteriaBuilder.and(ouCodePredicate));
        }
        if (StringUtils.isNotBlank(hearingSlotRequestParam.courtRoomId())) {
            Predicate courtRoomPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID),
                    hearingSlotRequestParam.courtRoomId());
            criteriaQuery.where(criteriaBuilder.and(courtRoomPredicate));
        }
        if (StringUtils.isNotBlank(hearingSlotRequestParam.courtRoomNumber())) {
            Predicate courtRoomNumberPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_NUMBER),
                    hearingSlotRequestParam.courtRoomNumber());
            criteriaQuery.where(criteriaBuilder.and(courtRoomNumberPredicate));
        }
        if (StringUtils.isNotBlank(hearingSlotRequestParam.businessType())) {
            Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE),
                    hearingSlotRequestParam.businessType());
            criteriaQuery.where(criteriaBuilder.and(businessTypePredicate));
        }
        if (StringUtils.isNotBlank(hearingSlotRequestParam.courtSession())) {
            final String courtSessionParam = hearingSlotRequestParam.courtSession();
            final String courtSessionPlaceholder = Arrays.stream(courtSessionParam.split(","))
                    .map(s -> "?").collect(joining(","));
            Predicate courtSessionPredicate = root.get(CourtSchedule_.COURT_SESSION).in(courtSessionPlaceholder);
            criteriaQuery.where(criteriaBuilder.and(courtSessionPredicate));
        }

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

        if (StringUtils.isNotBlank(courtScheduleRequestParam.courtRoomId())) {
            criteriaBuilder.and(criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID), courtScheduleRequestParam.courtRoomId()));
        }

        if (StringUtils.isNotBlank(courtScheduleRequestParam.businessType())) {
            criteriaBuilder.and(criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE), courtScheduleRequestParam.businessType()));
        }

        Predicate sessionDateBetweenPredicate = criteriaBuilder.between(root.get(CourtSchedule_.SESSION_DATE),
                LocalDate.parse(courtScheduleRequestParam.sessionStartDate()),
                LocalDate.parse(courtScheduleRequestParam.sessionEndDate()));

        criteriaQuery.select(root).where(criteriaBuilder.and(criteriaBuilder.equal(root.get(CourtSchedule_.COURT_HOUSE_ID),
                courtScheduleRequestParam.courtCentreId()), sessionDateBetweenPredicate));

        criteriaQuery.orderBy(
                criteriaBuilder.asc(root.get(CourtSchedule_.COURT_ROOM_ID)),
                criteriaBuilder.asc(root.get(CourtSchedule_.SESSION_DATE)));

    }

    public void createCourtScheduleJudiciaryCriteria(List<CourtSchedule> courtScheduleList,
                                                     CriteriaBuilder criteriaBuilder, CriteriaQuery<CourtScheduleJudiciary> criteriaQuery) {
        Root<CourtScheduleJudiciary> root = criteriaQuery.from(CourtScheduleJudiciary.class);
        courtScheduleList.forEach(e -> {
            if (StringUtils.isNotBlank(e.getCourtScheduleId()) && StringUtils.isNotBlank(e.getListingProfileId())) {
                Predicate activePredicate = criteriaBuilder.equal(root.get("active"), true);
                Predicate courtScheduleIdPredicate = criteriaBuilder.equal(root.get(CourtScheduleJudiciary_.id)
                        .get(CourtScheduleJudiciaryKey_.COURT_SCHEDULE_ID), e.getCourtScheduleId());
                Predicate courtListIdPredicate = criteriaBuilder.equal(root.get(CourtScheduleJudiciary_.COURT_LISTING_PROFILE_ID), e.getListingProfileId());
                criteriaQuery.select(root).where(criteriaBuilder.and(activePredicate, courtScheduleIdPredicate, courtListIdPredicate));
            }
        });
    }

    public void createAllocatedListingCriteria(Set<String> countBasedScheduleIds, CriteriaQuery<AllocatedListing> criteriaQuery) {
        Root<AllocatedListing> root = criteriaQuery.from(AllocatedListing.class);
        final String courtScheduledIds = countBasedScheduleIds.stream().map(s -> "?").collect(joining(","));
        Predicate courtScheduleIdPredicate = root.get(AllocatedListing_.COURT_SCHEDULE_ID).in(courtScheduledIds);
        criteriaQuery.select(root).where(courtScheduleIdPredicate).groupBy(root.get(AllocatedListing_.ID),
                root.get(AllocatedListing_.COURT_SCHEDULE_ID),
                root.get(AllocatedListing_.HEARING_START_TIME));
    }

    public void createMultipleSessionsCourtScheduleCriteria(CourtSchedule courtSchedule,
                                                            CriteriaBuilder criteriaBuilder, CriteriaQuery<CourtSchedule> criteriaQuery) {
        Root<CourtSchedule> root = criteriaQuery.from(CourtSchedule.class);
        if (StringUtils.isNotBlank(courtSchedule.getCourtHouseId())) {
            Predicate courtHouseIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_HOUSE_ID), courtSchedule.getCourtHouseId());
            criteriaQuery.where(criteriaBuilder.and(courtHouseIdPredicate));
        }
        if (StringUtils.isNotBlank(courtSchedule.getCourtRoomId())) {
            Predicate courtRoomIdPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_ROOM_ID), courtSchedule.getCourtRoomId());
            criteriaQuery.where(criteriaBuilder.and(courtRoomIdPredicate));
        }
        if (StringUtils.isNotBlank(courtSchedule.getCourtRoomId())) {
            Predicate businessTypePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.BUSINESS_TYPE), courtSchedule.getBusinessType());
            criteriaQuery.where(criteriaBuilder.and(businessTypePredicate));
        }
        if (StringUtils.isNotBlank(courtSchedule.getCourtRoomId())) {
            Predicate panelPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.PANEL), courtSchedule.getPanel());
            criteriaQuery.where(criteriaBuilder.and(panelPredicate));
        }
        if (StringUtils.isNotBlank(courtSchedule.getCourtRoomId())) {
            Predicate courtSessionPredicate = criteriaBuilder.equal(root.get(CourtSchedule_.COURT_SESSION), courtSchedule.getCourtSession());
            criteriaQuery.where(criteriaBuilder.and(courtSessionPredicate));
        }
        if (StringUtils.isNotBlank(courtSchedule.getCourtRoomId())) {
            Predicate sessionDatePredicate = criteriaBuilder.equal(root.get(CourtSchedule_.SESSION_DATE), courtSchedule.getSessionDate());
            criteriaQuery.where(criteriaBuilder.and(sessionDatePredicate));
        }
    }
}
