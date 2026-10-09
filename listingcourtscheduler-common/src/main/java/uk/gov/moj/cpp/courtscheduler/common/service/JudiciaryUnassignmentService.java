package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.singletonList;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Inject
    private JudiciaryChangeDetectionService judiciaryChangeDetectionService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public List<String> unassignJudiciary(final Map<String, List<String>> judiciaryToSessionIds, final String executionId) {
        return unassignJudiciary(judiciaryToSessionIds, executionId, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> unassignJudiciary(final Map<String, List<String>> judiciaryToSessionIds, final String executionId,
                                          final boolean skipValidations) {
        LOGGER.info("unassignJudiciary: attempting to unassign judiciaries from sessions : {} (skipValidations: {})", judiciaryToSessionIds, skipValidations);

        final List<String> allCourtScheduleIds = judiciaryToSessionIds.values().stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        final Map<String, List<Integer>> preHashMap =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(allCourtScheduleIds);
        LOGGER.info("Pre-unassignment: captured judiciary hash map for {} court schedule(s), executionId={}",
                preHashMap.size(), executionId);

        final Set<String> missingSessionIds = new LinkedHashSet<>();
        final Set<String> missingJudiciaryIds = new LinkedHashSet<>();
        final Set<String> allocatedListingSessionIds = new LinkedHashSet<>();
        final Set<String> missingCourtScheduleJudiciaryIds = new LinkedHashSet<>();

        for (final Map.Entry<String, List<String>> entry : judiciaryToSessionIds.entrySet()) {
            final String judiciaryId = entry.getKey();

            // The validator deliberately no longer rejects missing judiciaryId; it's the service's
            // job to surface that as an IllegalArgumentException so the controller returns 400
            // (mapped by GlobalExceptionHandler#handleIllegalArg).
            if (!skipValidations && (judiciaryId == null || judiciaryId.isBlank())) {
                throw new IllegalArgumentException("judiciaryId is required");
            }

            // Check if judiciary exists in any assignment (skip if skipValidations is true)
            if (!skipValidations) {
                final List<CourtScheduleJudiciary> judiciaryAssignments = courtScheduleJudiciaryRepository.findByJudiciaryId(judiciaryId);
                if (judiciaryAssignments.isEmpty()) {
                    missingJudiciaryIds.add(judiciaryId);
                    LOGGER.warn("unassignJudiciary: Judiciary ID {} not found for unassign judiciary operation", judiciaryId);
                    continue;
                }
            }

            final List<String> sessionIds = entry.getValue();
            for (final String courtScheduleId : sessionIds) {
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
                        LOGGER.warn("unassignJudiciary: Cannot unassign judiciary {} from courtSchedule {}: court schedule has allocated listings",
                                judiciaryId, courtScheduleId);
                        continue;
                    }
                }

                // Find the CourtScheduleJudiciary entity
                // Suppressed: the key is built from this iteration's courtScheduleId/judiciaryId pair,
                // so it must be created fresh per iteration and cannot be hoisted out of the loop.
                @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
                final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId);
                final CourtScheduleJudiciary courtScheduleJudiciary = courtScheduleJudiciaryRepository.findBy(key);

                if (courtScheduleJudiciary == null) {
                    final String identifier = String.format("%s-%s", judiciaryId, courtScheduleId);
                    missingCourtScheduleJudiciaryIds.add(identifier);
                    LOGGER.info("unassignJudiciary: Judiciary {} not assigned to courtSchedule {}, skipping", judiciaryId, courtScheduleId);
                    continue;
                }

                try {
                    final CourtScheduleJudiciary managed = entityManager.merge(courtScheduleJudiciary);
                    entityManager.remove(managed);
                    LOGGER.info("unassignJudiciary: successfully unassigned judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);
                } catch (@SuppressWarnings("PMD.AvoidCatchingGenericException") // Deliberate broad safety net:
                        // this loop must keep unassigning the remaining judiciary/session pairs even if one
                        // entityManager.merge/remove call fails, and the JPA provider can throw a variety of
                        // unchecked exceptions here. Narrowing would risk letting one bad pair abort the batch.
                        final Exception ex) {
                    LOGGER.error("Unexpected error while unassigning judiciary {} from session {}", judiciaryId, courtScheduleId, ex);
                }
            }
        }

        entityManager.flush();

        final Map<String, List<Integer>> postHashMap =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(allCourtScheduleIds);
        LOGGER.info("Post-unassignment: captured judiciary hash map for {} court schedule(s), executionId={}",
                postHashMap.size(), executionId);

        final List<String> changedCourtScheduleIds =
                judiciaryChangeDetectionService.findChangedCourtScheduleIds(preHashMap, postHashMap);
        LOGGER.info("Found {} changed court schedule IDs after unassignment, executionId={}",
                changedCourtScheduleIds.size(), executionId);

        if (!skipValidations) {
            logMissingReferences(missingJudiciaryIds, missingSessionIds, allocatedListingSessionIds, missingCourtScheduleJudiciaryIds, executionId);
        }
        LOGGER.info("unassignJudiciary: successfully completed unassigning judiciaries from sessions");
        return changedCourtScheduleIds;
    }

    @Transactional
    public int removeAllJudiciaryByCourtScheduleIds(final List<String> courtScheduleIds) {
        if (courtScheduleIds == null || courtScheduleIds.isEmpty()) {
            return 0;
        }
        final int deleted = courtScheduleJudiciaryRepository.deleteAllAssignmentsForCourtScheduleIds(courtScheduleIds);
        LOGGER.info("removeAllJudiciaryByCourtScheduleIds: removed {} judiciary assignment(s) for {} court schedule(s)",
                deleted, courtScheduleIds.size());
        return deleted;
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
                    .withTimestamp(Instant.now())
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
                    .withTimestamp(Instant.now())
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
                    .withTimestamp(Instant.now())
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
                    .withTimestamp(Instant.now())
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
    }
}