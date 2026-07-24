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
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciaryToSessionsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignmentFailure;
import uk.gov.moj.cpp.courtscheduler.domain.AssignmentFailureReason;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;
import uk.gov.moj.cpp.courtscheduler.domain.SessionJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    private static final int MAX_JUDICIARY_PER_BENCH = 4;
    private static final int MAX_MAGISTRATES = 2;
    private static final int MAX_JUDGE_OR_RECORDER = 1;

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

    /**
     * User-facing replace-all: clears all judiciary rows for the given court schedules, then assigns
     * the Cartesian product of {@code judiciary} × {@code courtScheduleIds}. Validates same courthouse
     * and bench composition (max 4, max 2 magistrates, max 1 judge/recorder).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void assignJudiciaryToSessions(final AssignJudiciaryToSessionsRequest request,
                                          final String executionId) {
        if (request == null || request.getCourtScheduleIds() == null || request.getCourtScheduleIds().isEmpty()) {
            throw new IllegalArgumentException("courtScheduleIds must contain at least one court schedule id.");
        }
        final List<String> courtScheduleIds = request.getCourtScheduleIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (courtScheduleIds.isEmpty()) {
            throw new IllegalArgumentException("courtScheduleIds must contain at least one court schedule id.");
        }

        final List<SessionJudiciary> sessionJudiciaries =
                Optional.ofNullable(request.getJudiciary()).orElse(emptyList());
        validateSessionJudiciaries(sessionJudiciaries);
        validateBenchComposition(sessionJudiciaries);

        final List<CourtSchedule> schedules =
                courtScheduleRepository.findByCourtScheduleIds(new ArrayList<>(courtScheduleIds));
        final Map<String, CourtSchedule> scheduleById = schedules.stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, Function.identity(), (a, b) -> a));

        if (scheduleById.size() != courtScheduleIds.size()) {
            throw new IllegalArgumentException("One or more court schedule ids were not found.");
        }

        final List<CourtSchedule> orderedSchedules = courtScheduleIds.stream()
                .map(scheduleById::get)
                .collect(Collectors.toList());
        validateSameCourthouse(orderedSchedules);

        courtScheduleJudiciaryRepository.deleteAllAssignmentsForCourtScheduleIds(courtScheduleIds);

        final Date now = Calendar.getInstance().getTime();
        int persisted = 0;
        for (final String sessionId : courtScheduleIds) {
            final CourtSchedule schedule = scheduleById.get(sessionId);
            for (final SessionJudiciary sessionJudiciary : sessionJudiciaries) {
                final String judicialId = sessionJudiciary.getJudicialId().trim();
                final Judiciary ref = referenceDataMapperService.findById(judicialId)
                        .orElseThrow(() -> new IllegalArgumentException("Judiciary not found: " + judicialId));
                final CourtScheduleJudiciary domain =
                        buildCourtScheduleJudiciaryForSessionsUi(ref, schedule, sessionId, now, sessionJudiciary);
                persistEntityWithEntityManager(CourtScheduleJudiciaryMapper.toEntity(domain));
                persisted++;
            }
        }
        if (persisted > 0) {
            entityManager.flush();
        }
        LOGGER.info("assignJudiciaryToSessions: courtSchedules={}, sessionJudiciaries={}, persisted={}, executionId={}",
                courtScheduleIds.size(), sessionJudiciaries.size(), persisted, executionId);
    }

    private void validateSessionJudiciaries(final List<SessionJudiciary> sessionJudiciaries) {
        final Set<String> seen = new HashSet<>();
        for (final SessionJudiciary sessionJudiciary : sessionJudiciaries) {
            if (sessionJudiciary == null || isBlank(sessionJudiciary.getJudicialId())) {
                throw new IllegalArgumentException("Each judiciary entry must include a judicialId.");
            }
            if (isBlank(sessionJudiciary.getJudiciaryType())) {
                throw new IllegalArgumentException("Each judiciary entry must include judicialRoleType.judiciaryType.");
            }
            final String id = sessionJudiciary.getJudicialId().trim();
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate judicialId in request: " + id);
            }
        }
    }

    private void validateBenchComposition(final List<SessionJudiciary> sessionJudiciaries) {
        if (sessionJudiciaries.size() > MAX_JUDICIARY_PER_BENCH) {
            throw new IllegalArgumentException("A maximum of " + MAX_JUDICIARY_PER_BENCH + " judiciary members is allowed.");
        }
        int magistrates = 0;
        int judgesOrRecorders = 0;
        for (final SessionJudiciary sessionJudiciary : sessionJudiciaries) {
            if (sessionJudiciary == null) {
                continue;
            }
            final String raw = sessionJudiciary.getJudiciaryType();
            if (isBlank(raw)) {
                continue;
            }
            final String t = raw.trim().toUpperCase(Locale.UK);
            if (t.contains("MAGISTRATE")) {
                magistrates++;
            } else if (t.contains("RECORDER") || (t.contains("JUDGE") && !t.contains("MAGISTRATE"))) {
                judgesOrRecorders++;
            }
        }
        if (magistrates > MAX_MAGISTRATES) {
            throw new IllegalArgumentException("A maximum of " + MAX_MAGISTRATES + " magistrates is allowed.");
        }
        if (judgesOrRecorders > MAX_JUDGE_OR_RECORDER) {
            throw new IllegalArgumentException("A maximum of one judge or recorder is allowed.");
        }
    }

    private void validateSameCourthouse(final List<CourtSchedule> schedules) {
        String house = null;
        for (final CourtSchedule schedule : schedules) {
            if (schedule == null) {
                continue;
            }
            final String hid = schedule.getCourtHouseId();
            if (house == null) {
                house = hid;
            } else if (!Objects.equals(house, hid)) {
                throw new IllegalArgumentException("All court schedules must belong to the same courthouse.");
            }
        }
    }

    private CourtScheduleJudiciary buildCourtScheduleJudiciaryForSessionsUi(final Judiciary judiciary,
                                                                            final CourtSchedule schedule,
                                                                            final String sessionId,
                                                                            final Date timestamp,
                                                                            final SessionJudiciary sessionJudiciary) {
        final String judiciaryType = !isBlank(sessionJudiciary.getJudiciaryType())
                ? sessionJudiciary.getJudiciaryType().trim()
                : nonNullOrDefault(judiciary.getJudiciaryType());
        return CourtScheduleJudiciary.judiciary()
                .withCourtScheduleId(sessionId)
                .withCourtListingProfileId(schedule.getListingProfileId())
                .withJudiciaryId(judiciary.getId())
                .withRotaJudiciaryId(null)
                .withTitle(nonNullOrDefault(firstNonEmpty(judiciary.getTitlePrefix(), judiciary.getTitleJudicialPrefix())))
                .withForenames(nonNullOrDefault(judiciary.getForenames()))
                .withSurname(nonNullOrDefault(judiciary.getSurname()))
                .withEmailAddress(nonNullOrDefault(judiciary.getEmailAddress()))
                .withJudiciaryType(judiciaryType)
                .withPosition(null)
                .withIsBenchChairman(Boolean.TRUE.equals(sessionJudiciary.getIsBenchChairman()))
                .withIsDeputy(Boolean.TRUE.equals(sessionJudiciary.getIsDeputy()))
                .withCreatedOn(timestamp)
                .withUpdatedOn(timestamp)
                .withActive(true)
                .build();
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

