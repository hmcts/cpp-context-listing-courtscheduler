package uk.gov.moj.cpp.courtscheduler.service;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

/**
 * Persist-and-flush a batch of {@link CourtSchedule} rows in its own JPA
 * transaction.
 *
 * <p>The fast batch path in {@code CourtScheduleRepositoryImpl} previously
 * called {@code entityManager.persist(...)} + {@code flush()} inside the
 * caller's transaction. When a unique-index collision came down the wire
 * (priming re-runs against existing court+business+date data), Hibernate
 * marked the caller's transaction rollback-only — Spring then refused to
 * commit it and the request blew up with
 * {@code UnexpectedRollbackException} → HTTP 500, even though the
 * per-record retry path was perfectly capable of resolving the collision
 * by updating the existing row.</p>
 *
 * <p>Isolating the flush in {@code Propagation.REQUIRES_NEW} contains the
 * rollback-only flag to this inner transaction. The exception still
 * propagates so the caller knows the batch failed and can fall back to
 * the per-record retry, but the caller's outer transaction stays
 * committable.</p>
 */
@Service
public class CourtScheduleBatchInsertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleBatchInsertService.class);

    @Inject
    EntityManager entityManager;

    /**
     * Persist every record in {@code batch} and flush. Throws on the first
     * failure; the inner transaction is rolled back cleanly by Spring and the
     * caller's transaction is unaffected.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistBatch(final List<CourtSchedule> batch) {
        for (final CourtSchedule cs : batch) {
            entityManager.persist(cs);
        }
        entityManager.flush();
        LOGGER.debug("Persisted batch of {} court schedules in isolated transaction", batch.size());
    }
}
