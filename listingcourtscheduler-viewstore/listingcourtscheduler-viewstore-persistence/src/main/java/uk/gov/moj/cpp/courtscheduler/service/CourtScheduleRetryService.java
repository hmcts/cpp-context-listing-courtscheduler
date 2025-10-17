package uk.gov.moj.cpp.courtscheduler.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.persistence.EntityManager;
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

    public List<CourtSchedule> findPersistedSchedules(CourtSchedule courtSchedule) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<CourtSchedule> criteriaQuery = criteriaBuilder.createQuery(CourtSchedule.class);
        courtScheduleCriteria.createMultipleSessionsCourtScheduleCriteria(courtSchedule, criteriaBuilder, criteriaQuery);
        return entityManager.createQuery(criteriaQuery).getResultList();
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