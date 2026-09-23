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
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository.getCourtScheduleToBeUpdated;

@Service
@Transactional
public class CourtScheduleRetryService {

    @Inject
    EntityManager entityManager;
    @Inject
    CourtScheduleCriteria courtScheduleCriteria;

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleRetryService.class.getName());


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CourtSchedule retryAndSave(CourtSchedule courtSchedule,boolean isForRotaFile) {
        List<CourtSchedule> persistedCourtSchedules = findPersistedSchedules(courtSchedule);

        logMultiplePersistedSchedules(persistedCourtSchedules, courtSchedule);

        if (isNotEmpty(persistedCourtSchedules)) {
            final CourtSchedule persistedCourtSchedule = getCourtScheduleToBeUpdated(courtSchedule, isForRotaFile, persistedCourtSchedules);

            boolean hasMaxSlotsChanged = hasMaxSlotsChanged(persistedCourtSchedule, courtSchedule);
            boolean hasMaxDurationChanged = hasMaxDurationChanged(persistedCourtSchedule, courtSchedule);
            boolean hasNewMaxSlotsOrDuration = hasNewMaxSlotsOrDuration(courtSchedule);
            boolean hasSupportAdSplitChanged = Boolean.TRUE.equals(courtSchedule.getSupportAdSplit())
                    && (intOrZero(persistedCourtSchedule.getMaxAdMorningDuration()) != intOrZero(courtSchedule.getMaxAdMorningDuration())
                    || intOrZero(persistedCourtSchedule.getMaxAdAfternoonDuration()) != intOrZero(courtSchedule.getMaxAdAfternoonDuration()));
            boolean hasSameADSplit = Objects.equals(persistedCourtSchedule.getSupportAdSplit(), courtSchedule.getSupportAdSplit());

            if ((isForRotaFile || hasMaxSlotsChanged || hasMaxDurationChanged || hasNewMaxSlotsOrDuration || hasSupportAdSplitChanged) && hasSameADSplit) {
                if (Boolean.TRUE.equals(persistedCourtSchedule.getSupportAdSplit())) {
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
                persistedCourtSchedule.setUpdatedOn(new Date());
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
                } catch (RuntimeException retryEx) {
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

    private boolean isUniqueConstraintViolation(Throwable ex) {
        // Walk the cause chain to detect common uniqueness exceptions
        Throwable t = ex;
        while (t != null) {
            String name = t.getClass().getName();
            String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
            if (name.contains("ConstraintViolationException") || name.contains("SQLIntegrityConstraintViolationException")
                    || msg.contains("unique") || msg.contains("duplicate") || msg.contains("constraint")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    public List<CourtSchedule> findPersistedSchedules(CourtSchedule courtSchedule) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
        return entityManager.createQuery(criteriaQuery).getResultList();
    }

    private boolean hasMaxSlotsChanged(CourtSchedule persistedCourtSchedule, CourtSchedule courtSchedule) {
        return intOrZero(persistedCourtSchedule.getMaxSlots()) != intOrZero(courtSchedule.getMaxSlots());
    }

    private boolean hasMaxDurationChanged(CourtSchedule persistedCourtSchedule, CourtSchedule courtSchedule) {
        return intOrZero(persistedCourtSchedule.getMaxDuration()) > 0
                && intOrZero(persistedCourtSchedule.getMaxDuration()) != intOrZero(courtSchedule.getMaxDuration());
    }

    private boolean hasNewMaxSlotsOrDuration(CourtSchedule courtSchedule) {
        return intOrZero(courtSchedule.getMaxSlots()) > 0 || intOrZero(courtSchedule.getMaxDuration()) > 0;
    }

    // A schedule is either slot-based or duration-based, never both — the "other" field is
    // legitimately null. Treating null as 0 here mirrors the pre-migration primitive-int
    // default that these comparisons relied on.
    private int intOrZero(final Integer value) {
        return value == null ? 0 : value;
    }
    private void logMultiplePersistedSchedules(List<CourtSchedule> persistedCourtSchedules, CourtSchedule courtSchedule) {
        if (persistedCourtSchedules.size() > 1) {
            LOGGER.info("having more than one persisted court schedule: {}", courtSchedule);
        }
    }
}