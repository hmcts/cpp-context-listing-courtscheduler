package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Migrated from DeltaSpike's {@code AbstractEntityRepository<JudiciaryAvailabilityRule, String>}
 * to a Spring Data JPA interface. Standard CRUD comes from {@link JpaRepository}; the two
 * dynamic-query methods that need an {@code EntityManager} (Criteria API + paged native
 * SQL with a window function) live in the package-private
 * {@link JudiciaryAvailabilityRuleRepositoryCustom} fragment + its {@code …Impl} class —
 * both colocated in this file so a single read shows the whole repository.
 */
@Repository
public interface JudiciaryAvailabilityRuleRepository
        extends JpaRepository<JudiciaryAvailabilityRule, String>, JudiciaryAvailabilityRuleRepositoryCustom {

    // ---------------------------------------------------------------------
    //  Backwards-compatible alias for DeltaSpike's auto-generated {@code findBy(K)}.
    //  JpaRepository provides {@code findById(K)} which returns Optional; legacy callers
    //  expect the raw entity (or {@code null}).
    // ---------------------------------------------------------------------

    default JudiciaryAvailabilityRule findBy(final String id) {
        return id == null ? null : findById(id).orElse(null);
    }

    default void remove(final JudiciaryAvailabilityRule rule) {
        delete(rule);
    }
}
