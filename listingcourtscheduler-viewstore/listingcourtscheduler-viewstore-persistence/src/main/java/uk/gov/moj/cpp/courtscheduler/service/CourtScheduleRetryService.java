package uk.gov.moj.cpp.courtscheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository.getCourtScheduleToBeUpdated;

@Stateless
public class CourtScheduleRetryService {

    @Inject
    EntityManager entityManager;
    @Inject
    CourtScheduleCriteria courtScheduleCriteria;

    @Inject
    CourtScheduleRepository repository;

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleRetryService.class.getName());


    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public CourtSchedule retryAndSave(CourtSchedule courtSchedule,boolean isForRotaFile) {
        List<CourtSchedule> persistedCourtSchedules = findPersistedSchedules(courtSchedule);

        logMultiplePersistedSchedules(persistedCourtSchedules, courtSchedule);

        if (isNotEmpty(persistedCourtSchedules)) {
            final CourtSchedule persistedCourtSchedule = getCourtScheduleToBeUpdated(courtSchedule, isForRotaFile, persistedCourtSchedules);

            boolean hasMaxSlotsChanged = hasMaxSlotsChanged(persistedCourtSchedule, courtSchedule);
            boolean hasMaxDurationChanged = hasMaxDurationChanged(persistedCourtSchedule, courtSchedule);
            boolean hasNewMaxSlotsOrDuration = hasNewMaxSlotsOrDuration(courtSchedule);
            boolean hasSupportAdSplitChanged = courtSchedule.getSupportAdSplit()
                    && (persistedCourtSchedule.getMaxAdMorningDuration().intValue() != courtSchedule.getMaxAdMorningDuration().intValue()
                    || persistedCourtSchedule.getMaxAdAfternoonDuration().intValue() != courtSchedule.getMaxAdAfternoonDuration().intValue());
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

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
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
        try {
            CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
            courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
            return entityManager.createQuery(criteriaQuery).getResultList();
        } catch (Exception ex) {
            LOGGER.warn("findPersistedSchedules criteria query failed: {}. Falling back to key lookup.", ex.getMessage());
            try {
                // Fallback: use repository method to find by core unique keys
                var info = repository.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(
                        courtSchedule.getCourtRoomId(),
                        courtSchedule.getSessionDate(),
                        courtSchedule.getBusinessType(),
                        courtSchedule.getCourtSession());
                if (info != null && info.getCourtScheduleId() != null) {
                    CourtSchedule found = entityManager.find(CourtSchedule.class, info.getCourtScheduleId());
                    return found != null ? List.of(found) : List.of();
                }
            } catch (Exception nested) {
                LOGGER.warn("Fallback key lookup failed: {}", nested.getMessage());
            }
            return List.of();
        }
    }

    private boolean hasMaxSlotsChanged(CourtSchedule persistedCourtSchedule, CourtSchedule courtSchedule) {
        return persistedCourtSchedule.getMaxSlots().intValue() != courtSchedule.getMaxSlots().intValue();
    }

    private boolean hasMaxDurationChanged(CourtSchedule persistedCourtSchedule, CourtSchedule courtSchedule) {
        return persistedCourtSchedule.getMaxDuration() > 0
                && persistedCourtSchedule.getMaxDuration().intValue() != courtSchedule.getMaxDuration().intValue();
    }

    private boolean hasNewMaxSlotsOrDuration(CourtSchedule courtSchedule) {
        return courtSchedule.getMaxSlots() > 0 || courtSchedule.getMaxDuration() > 0;
    }
    private void logMultiplePersistedSchedules(List<CourtSchedule> persistedCourtSchedules, CourtSchedule courtSchedule) {
        if (persistedCourtSchedules.size() > 1) {
            LOGGER.info("having more than one persisted court schedule: {}", courtSchedule);
        }
    }
}