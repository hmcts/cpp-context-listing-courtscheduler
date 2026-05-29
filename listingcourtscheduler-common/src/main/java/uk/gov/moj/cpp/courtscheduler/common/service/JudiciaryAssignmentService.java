package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.AssignmentFailure;
import uk.gov.moj.cpp.courtscheduler.domain.AssignmentFailureReason;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JudiciaryAssignmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAssignmentService.class);

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public AssignJudiciariesResponse assignJudiciaries(final AssignJudiciariesRequest request,
                                                       final String executionId) {
        return assignJudiciaries(request, executionId, false);
    }

    @Transactional
    public AssignJudiciariesResponse assignJudiciaries(final AssignJudiciariesRequest request,
                                                       final String executionId,
                                                       final boolean useRepository) {
        final boolean skipValidations = request != null && request.isSkipValidations();
        final List<JudiciaryAssignment> assignments = Optional.ofNullable(request)
                .map(AssignJudiciariesRequest::getJudiciaries)
                .orElse(emptyList());

        if (isEmpty(assignments)) {
            return AssignJudiciariesResponse.builder().build();
        }

        final Map<String, CourtSchedule> sessionsById = fetchSessionsById(assignments);
        final AssignmentResult result = processAssignments(assignments, sessionsById, skipValidations, useRepository);

        // Flush once at the end for EntityManager operations (API calls) - only if there were successful assignments
        if (!useRepository && result.successfulAssignments() > 0) {
            entityManager.flush();
        }

        if (skipValidations && executionId != null) {
            logMissingReferences(result.missingJudiciaryIds(), result.missingSessionIds(), executionId);
        }

        return AssignJudiciariesResponse.builder()
                .withRequestedAssignments(result.requestedAssignments())
                .withSuccessfulAssignments(result.successfulAssignments())
                .withFailures(result.failures())
                .build();
    }

    private Map<String, CourtSchedule> fetchSessionsById(final List<JudiciaryAssignment> assignments) {
        final Set<String> allSessionIds = assignments.stream()
                .filter(Objects::nonNull)
                .flatMap(assignment -> sanitizeSessionIds(assignment.getSessionIds()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (allSessionIds.isEmpty()) {
            return Map.of();
        }

        return courtScheduleRepository.findByCourtScheduleIds(new ArrayList<>(allSessionIds))
                .stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, Function.identity(),
                        (existing, replacement) -> existing));
    }

    private AssignmentResult processAssignments(final List<JudiciaryAssignment> assignments,
                                                final Map<String, CourtSchedule> sessionsById,
                                                final boolean skipValidations,
                                                final boolean useRepository) {
        final Set<String> missingJudiciaryIds = new LinkedHashSet<>();
        final Set<String> missingSessionIds = new LinkedHashSet<>();
        final List<AssignmentFailure> failures = new ArrayList<>();
        final Date now = Calendar.getInstance().getTime();

        int requestedAssignments = 0;
        int successfulAssignments = 0;

        for (final JudiciaryAssignment assignment : assignments) {
            if (assignment == null) {
                continue;
            }

            final List<String> sessionIds = sanitizeSessionIds(assignment.getSessionIds());
            if (sessionIds.isEmpty()) {
                continue;
            }

            final String judiciaryId = assignment.getJudiciaryId();
            final Judiciary judiciary = referenceDataMapperService.findById(judiciaryId).orElse(null);

            for (final String sessionId : sessionIds) {
                requestedAssignments++;
                final CourtSchedule schedule = sessionsById.get(sessionId);

                if (shouldSkipAssignment(skipValidations, judiciary, schedule)) {
                    trackMissingData(skipValidations, judiciaryId, sessionId, judiciary, schedule,
                            missingJudiciaryIds, missingSessionIds);
                    continue;
                }

                final AssignmentAttempt attempt = attemptAssignment(judiciary, schedule, sessionId, now, assignment, useRepository);
                if (attempt.isSuccess()) {
                    successfulAssignments++;
                } else {
                    failures.add(attempt.getFailure());
                }
            }
        }

        return new AssignmentResult(requestedAssignments, successfulAssignments, failures,
                missingJudiciaryIds, missingSessionIds);
    }

    private boolean shouldSkipAssignment(final boolean skipValidations,
                                         final Judiciary judiciary,
                                         final CourtSchedule schedule) {
        if (skipValidations && (judiciary == null || schedule == null)) {
            return true;
        }
        // If not skipping validations, judiciary and schedule should exist (validated in validator)
        // But we still check here as a safety measure
        return judiciary == null || schedule == null;
    }

    private void trackMissingData(final boolean skipValidations,
                                  final String judiciaryId,
                                  final String sessionId,
                                  final Judiciary judiciary,
                                  final CourtSchedule schedule,
                                  final Set<String> missingJudiciaryIds,
                                  final Set<String> missingSessionIds) {
        if (!skipValidations) {
            return;
        }

        if (judiciary == null) {
            missingJudiciaryIds.add(judiciaryId);
        }
        if (schedule == null) {
            missingSessionIds.add(sessionId);
        }
    }

    private AssignmentAttempt attemptAssignment(final Judiciary judiciary,
                                                final CourtSchedule schedule,
                                                final String sessionId,
                                                final Date timestamp,
                                                final JudiciaryAssignment assignment,
                                                final boolean useRepository) {
        final CourtScheduleJudiciary courtScheduleJudiciary = buildCourtScheduleJudiciary(judiciary, schedule, sessionId, timestamp, assignment);
        try {
            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary entity = 
                    CourtScheduleJudiciaryMapper.toEntity(courtScheduleJudiciary);
            
            if (useRepository) {
                persistEntityWithRepository(entity);
            } else {
                persistEntityWithEntityManager(entity);
            }
            
            return AssignmentAttempt.success();
        } catch (Exception ex) {
            return handleAssignmentException(ex, judiciary.getId(), sessionId);
        }
    }

    /**
     * Persists entity using EntityManager - used for API calls.
     * @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW) on assignJudiciaries() ensures transaction is active.
     * Uses merge() instead of persist() for better entity state management, similar to unassignJudiciary.
     * merge() handles both new and existing entities, making it more robust for detached entity states.
     * Flush is done once at the end of the method to batch operations.
     */
    private void persistEntityWithEntityManager(final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary entity) {
        // Use merge() which handles both new and existing entities, similar to unassignJudiciary approach
        entityManager.merge(entity);
        // Note: flush() is called once at the end of assignJudiciaries() method to batch operations
    }

    /**
     * Persists entity using repository - used for Rota processing.
     * DeltaSpike's BeanManagedUserTransactionStrategy handles transaction management.
     */
    private void persistEntityWithRepository(final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary entity) {
        courtScheduleJudiciaryRepository.save(entity);
    }

    private AssignmentAttempt handleAssignmentException(final Exception ex,
                                                        final String judiciaryId,
                                                        final String sessionId) {
        if (isDuplicateAssignment(ex)) {
            LOGGER.warn("Skipping duplicate judiciary assignment for judiciaryId {} and sessionId {}", judiciaryId, sessionId);
            return AssignmentAttempt.failure(judiciaryId, sessionId, AssignmentFailureReason.DUPLICATE_ASSIGNMENT);
        } else {
            LOGGER.error("Unexpected error while assigning judiciary {} to session {}", judiciaryId, sessionId, ex);
            return AssignmentAttempt.failure(judiciaryId, sessionId, AssignmentFailureReason.PERSISTENCE_ERROR);
        }
    }

    private List<String> sanitizeSessionIds(final List<String> sessionIds) {
        if (isNull(sessionIds)) {
            return emptyList();
        }
        return sessionIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !isBlank(value))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private CourtScheduleJudiciary buildCourtScheduleJudiciary(final Judiciary judiciary,
                                                               final CourtSchedule schedule,
                                                               final String sessionId,
                                                               final Date timestamp,
                                                               final JudiciaryAssignment assignment) {
        final String rotaJudiciaryId = firstNonEmpty(assignment.getRotaJudiciaryId(), 
                firstNonEmpty(judiciary.getCpUserId(), judiciary.getId()));

        return CourtScheduleJudiciary.judiciary()
                .withCourtScheduleId(sessionId)
                .withCourtListingProfileId(schedule.getListingProfileId())
                .withJudiciaryId(judiciary.getId())
                .withRotaJudiciaryId(rotaJudiciaryId)
                .withTitle(nonNullOrDefault(firstNonEmpty(judiciary.getTitlePrefix(), judiciary.getTitleJudicialPrefix())))
                .withForenames(nonNullOrDefault(judiciary.getForenames()))
                .withSurname(nonNullOrDefault(judiciary.getSurname()))
                .withEmailAddress(nonNullOrDefault(judiciary.getEmailAddress()))
                .withJudiciaryType(nonNullOrDefault(judiciary.getJudiciaryType()))
                .withPosition(assignment.getPosition())
                .withIsBenchChairman(assignment.getIsBenchChairman() != null ? assignment.getIsBenchChairman() : false)
                .withIsDeputy(assignment.getIsDeputy() != null ? assignment.getIsDeputy() : false)
                .withCreatedOn(timestamp)
                .withUpdatedOn(timestamp)
                .withActive(true)
                .build();
    }

    private String firstNonEmpty(final String primary, final String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        if (!isBlank(fallback)) {
            return fallback;
        }
        return null;
    }

    private String nonNullOrDefault(final String value) {
        return value == null ? "" : value;
    }

    private boolean isDuplicateAssignment(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            final String className = current.getClass().getName();
            final String message = current.getMessage() != null ? current.getMessage().toLowerCase() : "";
            if (className.contains("ConstraintViolationException")
                    || className.contains("SQLIntegrityConstraintViolationException")
                    || message.contains("duplicate")
                    || message.contains("unique")
                    || message.contains("constraint")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Logs missing references to RotaProcessLog for monitoring purposes.
     * This is only called when skipValidations=true to track data quality issues
     * without failing the request.
     */
    private void logMissingReferences(final Set<String> missingJudiciaryIds,
                                      final Set<String> missingSessionIds,
                                      final String executionId) {
        if (!missingJudiciaryIds.isEmpty()) {
            final String joined = String.join(", ", missingJudiciaryIds);
            LOGGER.warn("Missing judiciary ids for assignment (skipValidations=true): {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode(MissingDataError.JUDICIARY_ID_NOT_FOUND_ASSIGNMENT.code())
                    .withErrorText(MissingDataError.JUDICIARY_ID_NOT_FOUND_ASSIGNMENT.format(joined))
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
        if (!missingSessionIds.isEmpty()) {
            final String joined = String.join(", ", missingSessionIds);
            LOGGER.warn("Missing session ids for assignment (skipValidations=true): {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode(MissingDataError.SESSION_ID_NOT_FOUND_ASSIGNMENT.code())
                    .withErrorText(MissingDataError.SESSION_ID_NOT_FOUND_ASSIGNMENT.format(joined))
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
    }

    /**
     * Internal record to hold assignment processing results.
     */
    private record AssignmentResult(int requestedAssignments,
                                    int successfulAssignments,
                                    List<AssignmentFailure> failures,
                                    Set<String> missingJudiciaryIds,
                                    Set<String> missingSessionIds) {
    }

    /**
     * Internal class to hold assignment attempt results.
     */
    private static class AssignmentAttempt {
        private final boolean success;
        private final AssignmentFailure failure;

        private AssignmentAttempt(final boolean success, final AssignmentFailure failure) {
            this.success = success;
            this.failure = failure;
        }

        static AssignmentAttempt success() {
            return new AssignmentAttempt(true, null);
        }

        static AssignmentAttempt failure(final String judiciaryId,
                                         final String sessionId,
                                         final AssignmentFailureReason reason) {
            return new AssignmentAttempt(false, AssignmentFailure.builder()
                    .withJudiciaryId(judiciaryId)
                    .withSessionId(sessionId)
                    .withReason(reason)
                    .build());
        }

        boolean isSuccess() {
            return success;
        }

        AssignmentFailure getFailure() {
            return failure;
        }
    }
}

