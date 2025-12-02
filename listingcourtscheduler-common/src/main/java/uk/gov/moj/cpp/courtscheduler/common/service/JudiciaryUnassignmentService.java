package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.singletonList;
import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class JudiciaryUnassignmentService {

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryUnassignmentService.class);

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @PersistenceContext(unitName = "courtscheduler-persistence-unit")
    private EntityManager entityManager;

    @Transactional(REQUIRES_NEW)
    public void unassignJudiciary(final Map<String, List<String>> judiciaryToSessionIds) {
        logger.info("unassignJudiciary: attempting to unassign judiciaries from sessions : {}", judiciaryToSessionIds);

        for (Map.Entry<String, List<String>> entry : judiciaryToSessionIds.entrySet()) {
            final String judiciaryId = entry.getKey();
            final List<String> sessionIds = entry.getValue();

            // Check if judiciary exists in any assignment
            final List<CourtScheduleJudiciary> judiciaryAssignments = courtScheduleJudiciaryRepository.findByJudiciaryId(judiciaryId);
            final boolean judiciaryExists = !judiciaryAssignments.isEmpty();

            for (String courtScheduleId : sessionIds) {
                logger.info("unassignJudiciary: attempting to unassign judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);

                // Check if session (court schedule) exists
                final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId);
                if (courtSchedule == null) {
                    final String errorText = String.format("Session ID %s not found for unassign judiciary operation", courtScheduleId);
                    logger.warn("unassignJudiciary: {}", errorText);
                    logToRotaProcessTable("SESSION_ID_NOT_FOUND_ASSIGNMENT", errorText);
                    continue;
                }

                // Check if judiciary exists in any assignment
                if (!judiciaryExists) {
                    final String errorText = String.format("Judiciary ID %s not found for unassign judiciary operation", judiciaryId);
                    logger.warn("unassignJudiciary: {}", errorText);
                    logToRotaProcessTable("JUDICIARY_ID_NOT_FOUND_ASSIGNMENT", errorText);
                    continue;
                }

                // Check if there are allocated listings for this court schedule
                final Map<String, Integer> allocatedListings = allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(courtScheduleId));
                if (allocatedListings.containsKey(courtScheduleId) && allocatedListings.get(courtScheduleId) > 0) {
                    final String errorMessage = String.format("Cannot unassign judiciary %s from courtSchedule %s: court schedule has allocated listings", judiciaryId, courtScheduleId);
                    logger.warn("unassignJudiciary: {}", errorMessage);
                    logToRotaProcessTable("ALLOCATED_LISTING_FOUND_FOR_JUDICIARY", errorMessage);
                    throw new IllegalStateException(errorMessage);
                }

                // Find the CourtScheduleJudiciary entity
                final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId);
                final CourtScheduleJudiciary courtScheduleJudiciary = courtScheduleJudiciaryRepository.findBy(key);

                if (courtScheduleJudiciary == null) {
                    // Judiciary exists but not for this specific session - skip this assignment
                    final String errorText = String.format("CourtScheduleJudiciary not found for judiciary %s and courtSchedule %s", judiciaryId, courtScheduleId);
                    logger.info("unassignJudiciary: Judiciary {} not assigned to courtSchedule {}, skipping", judiciaryId, courtScheduleId);
                    logToRotaProcessTable("COURT_SCHEDULE_JUDICIARY_NOT_FOUND", errorText);
                    continue;
                }

                // Remove the judiciary assignment using EntityManager
                final CourtScheduleJudiciary managed = entityManager.merge(courtScheduleJudiciary);
                entityManager.remove(managed);
                logger.info("unassignJudiciary: successfully unassigned judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);
            }
        }

        entityManager.flush();
        logger.info("unassignJudiciary: successfully completed unassigning judiciaries from sessions");
    }

    private void logToRotaProcessTable(final String errorCode, final String errorText) {
        try {
            final RotaProcessLog rotaProcessLog = new RotaProcessLog();
            rotaProcessLog.setErrorCode(errorCode);
            rotaProcessLog.setErrorText(errorText);
            rotaProcessLog.setTimestamp(new Date());
            rotaProcessLogService.saveRotaProcessLog(rotaProcessLog);
            logger.info("unassignJudiciary: Logged to rota_process_log - errorCode: {}, errorText: {}", errorCode, errorText);
        } catch (Exception e) {
            logger.error("unassignJudiciary: Failed to log to rota_process_log - errorCode: {}, errorText: {}", errorCode, errorText, e);
        }
    }
}

