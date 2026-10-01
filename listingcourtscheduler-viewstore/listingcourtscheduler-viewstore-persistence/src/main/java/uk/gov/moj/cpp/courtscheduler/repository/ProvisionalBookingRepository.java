package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
