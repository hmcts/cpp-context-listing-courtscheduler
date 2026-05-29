package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.singletonList;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JudiciaryUnassignmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryUnassignmentService.class);

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void unassignJudiciary(final Map<String, List<String>> judiciaryToSessionIds, final String executionId) {
        unassignJudiciary(judiciaryToSessionIds, executionId, false);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void unassignJudiciary(final Map<String, List<String>> judiciaryToSessionIds, final String executionId, final boolean skipValidations) {
        LOGGER.info("unassignJudiciary: attempting to unassign judiciaries from sessions : {} (skipValidations: {})", judiciaryToSessionIds, skipValidations);

        final Set<String> missingSessionIds = new LinkedHashSet<>();
        final Set<String> missingJudiciaryIds = new LinkedHashSet<>();
        final Set<String> allocatedListingSessionIds = new LinkedHashSet<>();
        final Set<String> missingCourtScheduleJudiciaryIds = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : judiciaryToSessionIds.entrySet()) {
            final String judiciaryId = entry.getKey();
            final List<String> sessionIds = entry.getValue();

            // The validator deliberately no longer rejects missing judiciaryId; it's the service's
            // job to surface that as an IllegalArgumentException so the controller returns 400
            // (mapped by GlobalExceptionHandler#handleIllegalArg).
            if (!skipValidations && (judiciaryId == null || judiciaryId.isBlank())) {
                throw new IllegalArgumentException("judiciaryId is required");
            }

            // Check if judiciary exists in any assignment (skip if skipValidations is true)
            if (!skipValidations) {
                final List<CourtScheduleJudiciary> judiciaryAssignments = courtScheduleJudiciaryRepository.findByJudiciaryId(judiciaryId);
                final boolean judiciaryExists = !judiciaryAssignments.isEmpty();

                if (!judiciaryExists) {
                    missingJudiciaryIds.add(judiciaryId);
                    LOGGER.warn("unassignJudiciary: Judiciary ID {} not found for unassign judiciary operation", judiciaryId);
                    continue;
                }
            }

            for (String courtScheduleId : sessionIds) {
                LOGGER.info("unassignJudiciary: attempting to unassign judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);

                // Check if session (court schedule) exists (skip if skipValidations is true)
                final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId);
                if (!skipValidations && courtSchedule == null) {
                    missingSessionIds.add(courtScheduleId);
                    LOGGER.warn("unassignJudiciary: Session ID {} not found for unassign judiciary operation", courtScheduleId);
                    continue;
                }

                // Check if there are allocated listings for this court schedule (skip if skipValidations is true)
                if (!skipValidations) {
                    final Map<String, Integer> allocatedListings = allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(courtScheduleId));
                    if (allocatedListings.containsKey(courtScheduleId) && allocatedListings.get(courtScheduleId) > 0) {
                        allocatedListingSessionIds.add(courtScheduleId);
                        final String errorMessage = String.format("Cannot unassign judiciary %s from courtSchedule %s: court schedule has allocated listings", judiciaryId, courtScheduleId);
                        LOGGER.warn("unassignJudiciary: {}", errorMessage);
                        continue;
                    }
                }

                // Find the CourtScheduleJudiciary entity
                final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId);
                final CourtScheduleJudiciary courtScheduleJudiciary = courtScheduleJudiciaryRepository.findBy(key);

                if (courtScheduleJudiciary == null) {
                    // Judiciary exists but not for this specific session - skip this assignment
                    final String identifier = String.format("%s-%s", judiciaryId, courtScheduleId);
                    missingCourtScheduleJudiciaryIds.add(identifier);
                    LOGGER.info("unassignJudiciary: Judiciary {} not assigned to courtSchedule {}, skipping", judiciaryId, courtScheduleId);
                    continue;
                }

                // Remove the judiciary assignment using EntityManager
                try {
                    final CourtScheduleJudiciary managed = entityManager.merge(courtScheduleJudiciary);
                    entityManager.remove(managed);
                    LOGGER.info("unassignJudiciary: successfully unassigned judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);
                } catch (Exception ex) {
                    LOGGER.error("Unexpected error while unassigning judiciary {} from session {}", judiciaryId, courtScheduleId, ex);
                    // Continue processing other assignments
                }
            }
        }

        entityManager.flush();
        // Skip logging missing references if skipValidations is true
        if (!skipValidations) {
            logMissingReferences(missingJudiciaryIds, missingSessionIds, allocatedListingSessionIds, missingCourtScheduleJudiciaryIds, executionId);
        }
        LOGGER.info("unassignJudiciary: successfully completed unassigning judiciaries from sessions");
    }

    private void logMissingReferences(final Set<String> missingJudiciaryIds,
                                      final Set<String> missingSessionIds,
                                      final Set<String> allocatedListingSessionIds,
                                      final Set<String> missingCourtScheduleJudiciaryIds,
                                      final String executionId) {
        if (!missingJudiciaryIds.isEmpty()) {
            final String joined = String.join(", ", missingJudiciaryIds);
            LOGGER.warn("Missing judiciary ids for unassignment: {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode(MissingDataError.JUDICIARY_ID_NOT_FOUND_ASSIGNMENT.code())
                    .withErrorText(MissingDataError.JUDICIARY_ID_NOT_FOUND_ASSIGNMENT.format(joined))
                    .withTimestamp(Calendar.getInstance().getTime())
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
        if (!missingSessionIds.isEmpty()) {
            final String joined = String.join(", ", missingSessionIds);
            LOGGER.warn("Missing session ids for unassignment: {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode(MissingDataError.SESSION_ID_NOT_FOUND_ASSIGNMENT.code())
                    .withErrorText(MissingDataError.SESSION_ID_NOT_FOUND_ASSIGNMENT.format(joined))
                    .withTimestamp(Calendar.getInstance().getTime())
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
        if (!allocatedListingSessionIds.isEmpty()) {
            final String joined = String.join(", ", allocatedListingSessionIds);
            LOGGER.warn("Session ids with allocated listings for unassignment: {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode("ALLOCATED_LISTING_FOUND_FOR_JUDICIARY")
                    .withErrorText(String.format("SCSLMissingData: Cannot unassign judiciary from sessions with allocated listings: %s", joined))
                    .withTimestamp(Calendar.getInstance().getTime())
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
        if (!missingCourtScheduleJudiciaryIds.isEmpty()) {
            final String joined = String.join(", ", missingCourtScheduleJudiciaryIds);
            LOGGER.warn("Missing court schedule judiciary assignments for unassignment: {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode("COURT_SCHEDULE_JUDICIARY_NOT_FOUND")
                    .withErrorText(String.format("SCSLMissingData: CourtScheduleJudiciary not found for: %s", joined))
                    .withTimestamp(Calendar.getInstance().getTime())
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
    }
}
