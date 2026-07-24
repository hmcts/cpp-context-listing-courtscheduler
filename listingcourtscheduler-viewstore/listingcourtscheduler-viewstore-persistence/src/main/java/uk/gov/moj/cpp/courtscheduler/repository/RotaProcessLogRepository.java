package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migrated from DeltaSpike Data {@code AbstractEntityRepository<RotaProcessLog, UUID>}
 * to a plain Spring {@code @Repository} bean using {@link EntityManager} directly.
 * The persisted entity continues to be {@link RotaProcessLog}; CRUD operations on it
 * are now provided through the {@code EntityManager}.
 */
@Repository
@Transactional
public class RotaProcessLogRepository {

    private static final String DELETE_REDUNDANT_ROTA_DATA =
            "DELETE FROM rota_process_log WHERE timestamp < (CURRENT_DATE - :numberOfDays)";

    @PersistenceContext
    EntityManager entityManager;

    @Transactional
    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }

    public void save(final RotaProcessLog log) {
        entityManager.persist(log);
    }
}
