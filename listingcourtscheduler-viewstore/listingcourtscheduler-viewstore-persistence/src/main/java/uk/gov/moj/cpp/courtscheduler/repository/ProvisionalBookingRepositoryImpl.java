package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking_;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Root;

import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data picks this up by the {@code …Impl} naming convention as the implementation
 * of {@link ProvisionalBookingRepositoryCustom}. The Criteria-API queries are unchanged from
 * the legacy DeltaSpike repository.
 */
@SuppressWarnings("squid:S3740")
class ProvisionalBookingRepositoryImpl implements ProvisionalBookingRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<String, Instant> getCourtScheduleInfo(final List<String> bookingSlots) {
        final Map<String, Instant> courtScheduleInfoMap = new HashMap<>();
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<ProvisionalBooking> cq = cb.createQuery(ProvisionalBooking.class);
        final Root<ProvisionalBooking> root = cq.from(ProvisionalBooking.class);
        final ParameterExpression<List> bookingSlotsExpr = cb.parameter(List.class);
        cq.where(root.get(ProvisionalBooking_.provisionalBookingKey).get(ProvisionalBookingKey_.bookingId).in(bookingSlotsExpr));
        final TypedQuery<ProvisionalBooking> tq = entityManager.createQuery(cq);
        tq.setParameter(bookingSlotsExpr, bookingSlots);
        tq.getResultList().forEach(provisionalBooking -> {
            final Instant hearingStart = provisionalBooking.getHearingStartTime();
            courtScheduleInfoMap.put(
                    provisionalBooking.getProvisionalBookingKey().getCourtSchedule().getCourtScheduleId(),
                    hearingStart);
        });
        return courtScheduleInfoMap;
    }

    @Override
    public Optional<ProvisionalBooking> findByBookingId(final String bookingId) {
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<ProvisionalBooking> cq = cb.createQuery(ProvisionalBooking.class);
        final Root<ProvisionalBooking> root = cq.from(ProvisionalBooking.class);
        final ParameterExpression<List> bookingSlotsExpr = cb.parameter(List.class);
        cq.where(root.get(ProvisionalBooking_.provisionalBookingKey).get(ProvisionalBookingKey_.bookingId).in(bookingSlotsExpr));
        final TypedQuery<ProvisionalBooking> tq = entityManager.createQuery(cq);
        tq.setParameter(bookingSlotsExpr, Collections.singletonList(bookingId));
        return tq.getResultList().stream().findFirst();
    }

    @Override
    public List<ProvisionalBooking> findByBookingIdIn(final List<String> bookingIds) {
        final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<ProvisionalBooking> criteriaBuilderQuery = criteriaBuilder.createQuery(ProvisionalBooking.class);
        final Root<ProvisionalBooking> root = criteriaBuilderQuery.from(ProvisionalBooking.class);

        final CriteriaBuilder.In<String> inClause = criteriaBuilder.in(
                root.get(ProvisionalBooking_.provisionalBookingKey).get(ProvisionalBookingKey_.bookingId));
        for (final String bookingId : bookingIds) {
            inClause.value(bookingId);
        }
        criteriaBuilderQuery.select(root).where(inClause);
        return entityManager.createQuery(criteriaBuilderQuery).getResultList();
    }

    @Override
    @Transactional
    public void saveProvisionalBooking(final ProvisionalSlot provisionalSlot, final String bookingId, final CourtSchedule courtSchedule) {
        final ProvisionalBooking provisionalBooking = new ProvisionalBooking();
        final ProvisionalBookingKey provisionalBookingKey = new ProvisionalBookingKey();
        provisionalBookingKey.setBookingId(bookingId);
        provisionalBookingKey.setCourtSchedule(courtSchedule);
        provisionalBooking.setProvisionalBookingKey(provisionalBookingKey);
        provisionalBooking.setActive(true);
        final Instant now = Instant.now();
        provisionalBooking.setCreatedOn(now);
        provisionalBooking.setUpdatedOn(now);
        provisionalBooking.setHearingStartTime(DateUtils.toRoundedTimestamp(provisionalSlot.getHearingStartTime()).toInstant());
        entityManager.persist(provisionalBooking);
    }
}
