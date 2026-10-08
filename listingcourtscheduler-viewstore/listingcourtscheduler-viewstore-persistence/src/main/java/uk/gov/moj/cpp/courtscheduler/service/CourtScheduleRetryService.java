package uk.gov.moj.cpp.courtscheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository.getCourtScheduleToBeUpdated;

@Service
@Transactional
public class CourtScheduleRetryService {

    private static final int SINGLE_SCHEDULE_COUNT = 1;

    @Inject
    /* package */ EntityManager entityManager;
    @Inject
    /* package */ CourtScheduleCriteria courtScheduleCriteria;

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleRetryService.class.getName());


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CourtSchedule retryAndSave(final CourtSchedule courtSchedule,final boolean isForRotaFile) {
        final List<CourtSchedule> persistedCourtSchedules = findPersistedSchedules(courtSchedule);

        logMultiplePersistedSchedules(persistedCourtSchedules, courtSchedule);

        if (isNotEmpty(persistedCourtSchedules)) {
            final CourtSchedule persistedCourtSchedule = getCourtScheduleToBeUpdated(courtSchedule, isForRotaFile, persistedCourtSchedules);

            final boolean hasMaxSlotsChanged = hasMaxSlotsChanged(persistedCourtSchedule, courtSchedule);
            final boolean hasMaxDurationChanged = hasMaxDurationChanged(persistedCourtSchedule, courtSchedule);
            final boolean hasNewMaxSlotsOrDuration = hasNewMaxSlotsOrDuration(courtSchedule);
            final boolean hasSupportAdSplitChanged = courtSchedule.isSupportAdSplit()
                    && (!Objects.equals(persistedCourtSchedule.getMaxAdMorningDuration(), courtSchedule.getMaxAdMorningDuration())
                    || !Objects.equals(persistedCourtSchedule.getMaxAdAfternoonDuration(), courtSchedule.getMaxAdAfternoonDuration()));
            final boolean hasSameADSplit = Objects.equals(persistedCourtSchedule.isSupportAdSplit(), courtSchedule.isSupportAdSplit());

            if ((isForRotaFile || hasMaxSlotsChanged || hasMaxDurationChanged || hasNewMaxSlotsOrDuration || hasSupportAdSplitChanged) && hasSameADSplit) {
                if (Boolean.TRUE.equals(persistedCourtSchedule.isSupportAdSplit())) {
                    persistedCourtSchedule.setMaxAdMorningDuration(courtSchedule.getMaxAdMorningDuration());
                    persistedCourtSchedule.setMaxAdAfternoonDuration(courtSchedule.getMaxAdAfternoonDuration());
                } else {
                    persistedCourtSchedule.setMaxDuration(courtSchedule.getMaxDuration());
                }
                persistedCourtSchedule.setMaxDuration(courtSchedule.getMaxDuration());
                persistedCourtSchedule.setMaxSlots(courtSchedule.getMaxSlots());
                persistedCourtSchedule.setAvailableSlots(courtSchedule.getAvailableSlots());
                persistedCourtSchedule.setAvailableDuration(courtSchedule.getAvailableDuration());
                persistedCourtSchedule.setCreatedOn(persistedCourtSchedule.getCreatedOn());
                persistedCourtSchedule.setUpdatedOn(Instant.now());
                if (isForRotaFile) {
                    persistedCourtSchedule.setActive(true);
                }
                entityManager.flush();
            }
            return courtSchedule;
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsertOne(final CourtSchedule courtSchedule) {
        try {
            entityManager.persist(courtSchedule);
            entityManager.flush();
        } catch (PersistenceException ex) {
            if (isUniqueConstraintViolation(ex)) {
                LOGGER.debug("Constraint violation while persisting schedule {} - retrying update", courtSchedule.getCourtScheduleId());
                try {
                    retryAndSave(courtSchedule, false);
                    return;
                } catch (@SuppressWarnings("PMD.AvoidCatchingGenericException") final RuntimeException retryEx) {
                    // Intentional safety net: retryAndSave can surface a variety of runtime failures
                    // (constraint violations, Hibernate/Spring exceptions); all must be wrapped
                    // consistently rather than letting some propagate raw from this retry path.
                    final String errorMessage = String.format("Failed to upsert court schedule %s after retry: %s",
                            courtSchedule.getCourtScheduleId(), retryEx.getMessage());
                    throw new PersistenceStoreException(errorMessage, retryEx);
                }
            }
            final String errorMessage = String.format("Persistence exception during upsert for court schedule %s: %s",
                    courtSchedule.getCourtScheduleId(), ex.getMessage());
            LOGGER.warn(errorMessage);
            throw new PersistenceStoreException(errorMessage, ex);
        }
    }

    private boolean isUniqueConstraintViolation(final Throwable ex) {
        // Walk the cause chain to detect common uniqueness exceptions
        Throwable t = ex;
        while (t != null) {
            final String name = t.getClass().getName();
            final String msg = t.getMessage() != null ? t.getMessage().toLowerCase(Locale.ROOT) : "";
            if (name.contains("ConstraintViolationException") || name.contains("SQLIntegrityConstraintViolationException")
                    || msg.contains("unique") || msg.contains("duplicate") || msg.contains("constraint")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    public List<CourtSchedule> findPersistedSchedules(final CourtSchedule courtSchedule) {
        final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
        return entityManager.createQuery(criteriaQuery).getResultList();
    }

    private boolean hasMaxSlotsChanged(final CourtSchedule persistedCourtSchedule, final CourtSchedule courtSchedule) {
        return !Objects.equals(persistedCourtSchedule.getMaxSlots(), courtSchedule.getMaxSlots());
    }

    private boolean hasMaxDurationChanged(final CourtSchedule persistedCourtSchedule, final CourtSchedule courtSchedule) {
        return persistedCourtSchedule.getMaxDuration() > 0
                && !Objects.equals(persistedCourtSchedule.getMaxDuration(), courtSchedule.getMaxDuration());
    }

    private boolean hasNewMaxSlotsOrDuration(final CourtSchedule courtSchedule) {
        return courtSchedule.getMaxSlots() > 0 || courtSchedule.getMaxDuration() > 0;
    }
    private void logMultiplePersistedSchedules(final List<CourtSchedule> persistedCourtSchedules, final CourtSchedule courtSchedule) {
        if (persistedCourtSchedules.size() > SINGLE_SCHEDULE_COUNT) {
            LOGGER.info("having more than one persisted court schedule: {}", courtSchedule);
        }
    }
}