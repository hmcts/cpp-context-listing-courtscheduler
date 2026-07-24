package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey_;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking_;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Date;
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

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrated from DeltaSpike's {@code AbstractFullEntityRepository<ProvisionalBooking, ProvisionalBookingKey>}
 * to a Spring Data JPA interface. CRUD comes from {@link JpaRepository}; the Criteria-API
 * lookup methods that walk the {@code ProvisionalBookingKey} composite key are declared
 * on the {@link ProvisionalBookingRepositoryCustom} fragment below and implemented on the
 * package-private {@link ProvisionalBookingRepositoryImpl} class — both colocated in this
 * file so a single read shows the whole repository surface.
 */
@Repository
public interface ProvisionalBookingRepository
        extends JpaRepository<ProvisionalBooking, ProvisionalBookingKey>, ProvisionalBookingRepositoryCustom {

    // ---------------------------------------------------------------------
    //  Backwards-compatible alias for DeltaSpike's auto-generated {@code findBy(K)}.
    // ---------------------------------------------------------------------

    default ProvisionalBooking findBy(final ProvisionalBookingKey key) {
        return key == null ? null : findById(key).orElse(null);
    }

    default void remove(final ProvisionalBooking entity) {
        delete(entity);
    }
}

/**
 * Spring Data {@code Custom} fragment for {@link ProvisionalBookingRepository}. The legacy
 * repository wrote every query against the JPA Criteria API to walk the
 * {@code ProvisionalBookingKey} composite key — that's preserved verbatim in the
 * {@link ProvisionalBookingRepositoryImpl} class below.
 */
interface ProvisionalBookingRepositoryCustom {

    /** Map {@code courtScheduleId → hearingStartTime} for every active booking matching the supplied booking IDs. */
    Map<String, Date> getCourtScheduleInfo(List<String> bookingSlots);

    /** Single-booking lookup by {@code bookingId} — uses the same JPA Criteria filter as {@link #findByBookingIdIn}. */
    Optional<ProvisionalBooking> findByBookingId(String bookingId);

    /** Bulk lookup by {@code bookingId IN (...)}. */
    List<ProvisionalBooking> findByBookingIdIn(List<String> bookingIds);

    /** Build + persist a {@link ProvisionalBooking} from a domain {@link ProvisionalSlot}. */
    void saveProvisionalBooking(ProvisionalSlot provisionalSlot, String bookingId, CourtSchedule courtSchedule);
}

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
    public Map<String, Date> getCourtScheduleInfo(final List<String> bookingSlots) {
        final Map<String, Date> courtScheduleInfoMap = new HashMap<>();
        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<ProvisionalBooking> cq = cb.createQuery(ProvisionalBooking.class);
        final Root<ProvisionalBooking> root = cq.from(ProvisionalBooking.class);
        final ParameterExpression<List> bookingSlotsExpr = cb.parameter(List.class);
        cq.where(root.get(ProvisionalBooking_.provisionalBookingKey).get(ProvisionalBookingKey_.bookingId).in(bookingSlotsExpr));
        final TypedQuery<ProvisionalBooking> tq = entityManager.createQuery(cq);
        tq.setParameter(bookingSlotsExpr, bookingSlots);
        tq.getResultList().forEach(provisionalBooking -> {
            final Date hearingStart = provisionalBooking.getHearingStartTime();
            courtScheduleInfoMap.put(
                    provisionalBooking.getProvisionalBookingKey().getCourtSchedule().getCourtScheduleId(),
                    hearingStart == null ? null : new Date(hearingStart.getTime()));
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
        provisionalBooking.setCreatedOn(DateUtils.toSqlDate(LocalDate.now()));
        provisionalBooking.setUpdatedOn(DateUtils.toSqlDate(LocalDate.now()));
        provisionalBooking.setHearingStartTime(DateUtils.toRoundedTimestamp(provisionalSlot.getHearingStartTime()));
        entityManager.persist(provisionalBooking);
    }
}
