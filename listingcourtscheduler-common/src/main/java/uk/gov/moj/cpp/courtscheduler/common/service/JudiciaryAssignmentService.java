package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static javax.transaction.Transactional.TxType.REQUIRES_NEW;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.justice.services.core.requester.Requester;
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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class JudiciaryAssignmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAssignmentService.class);
    private static final String DEFAULT_POSITION = "CHAIR";
    private static final String LEFT_WINGER = "LEFT_WINGER";
    private static final String RIGHT_WINGER = "RIGHT_WINGER";

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @Transactional(REQUIRES_NEW)
    public AssignJudiciariesResponse assignJudiciaries(final AssignJudiciariesRequest request,
                                                       final Requester requester,
                                                       final String executionId) {
        final boolean skipValidations = request != null && request.isSkipValidations();
        final List<JudiciaryAssignment> assignments = Optional.ofNullable(request)
                .map(AssignJudiciariesRequest::getJudiciaries)
                .orElse(emptyList());

        if (isEmpty(assignments)) {
            return AssignJudiciariesResponse.builder().build();
        }

        final Set<String> allSessionIds = assignments.stream()
                .filter(Objects::nonNull)
                .flatMap(assignment -> sanitizeSessionIds(assignment.getSessionIds()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        final Map<String, CourtSchedule> sessionsById = allSessionIds.isEmpty()
                ? Map.of()
                : courtScheduleRepository.findByCourtScheduleIds(new ArrayList<>(allSessionIds))
                .stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, Function.identity(), (existing, replacement) -> existing));

        final Set<String> missingSessionIds = new LinkedHashSet<>(allSessionIds);
        missingSessionIds.removeAll(sessionsById.keySet());

        final Set<String> missingJudiciaryIds = new LinkedHashSet<>();
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

            // Skip judiciary existence check if skipValidations is true
            Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findById(requester, judiciaryId);
            if (!skipValidations && judiciaryOptional.isEmpty()) {
                    missingJudiciaryIds.add(judiciaryId);
                    for (final String sessionId : sessionIds) {
                        requestedAssignments++;
                        failures.add(buildFailure(judiciaryId, sessionId, AssignmentFailureReason.JUDICIARY_NOT_FOUND));
                    }
                    continue;
                }

            final Judiciary judiciary = judiciaryOptional.orElse(null);
            for (final String sessionId : sessionIds) {
                requestedAssignments++;
                final CourtSchedule schedule = sessionsById.get(sessionId);

                // Skip session existence check if skipValidations is true
                if (!skipValidations && schedule == null) {
                    missingSessionIds.add(sessionId);
                    failures.add(buildFailure(judiciaryId, sessionId, AssignmentFailureReason.SESSION_NOT_FOUND));
                    continue;
                }

                // If skipValidations is true and judiciary or schedule is null, skip assignment
                if (skipValidations && (judiciary == null || schedule == null)) {
                    continue;
                }

                final CourtScheduleJudiciary courtScheduleJudiciary = buildCourtScheduleJudiciary(judiciary, schedule, sessionId, now);
                try {
                    courtScheduleJudiciaryRepository.save(CourtScheduleJudiciaryMapper.toEntity(courtScheduleJudiciary));
                    successfulAssignments++;
                } catch (Exception ex) {
                    if (isDuplicateAssignment(ex)) {
                        LOGGER.warn("Skipping duplicate judiciary assignment for judiciaryId {} and sessionId {}", judiciaryId, sessionId);
                        failures.add(buildFailure(judiciaryId, sessionId, AssignmentFailureReason.DUPLICATE_ASSIGNMENT));
                    } else {
                        LOGGER.error("Unexpected error while assigning judiciary {} to session {}", judiciaryId, sessionId, ex);
                        failures.add(buildFailure(judiciaryId, sessionId, AssignmentFailureReason.PERSISTENCE_ERROR));
                    }
                }
            }
        }

        // Skip logging missing references if skipValidations is true
        if (!skipValidations) {
            logMissingReferences(missingJudiciaryIds, missingSessionIds, executionId);
        }

        return AssignJudiciariesResponse.builder()
                .withRequestedAssignments(requestedAssignments)
                .withSuccessfulAssignments(successfulAssignments)
                .withFailures(failures)
                .build();
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
                                                               final Date timestamp) {
        final String rotaJudiciaryId = firstNonEmpty(judiciary.getCpUserId(), judiciary.getId());
        final String position = DEFAULT_POSITION;
        final boolean isBenchChair = !(LEFT_WINGER.equals(position) || RIGHT_WINGER.equals(position));

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
                .withPosition(position)
                .withIsBenchChairman(isBenchChair)
                .withIsDeputy(!isBenchChair)
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

    private AssignmentFailure buildFailure(final String judiciaryId,
                                           final String sessionId,
                                           final AssignmentFailureReason reason) {
        return AssignmentFailure.builder()
                .withJudiciaryId(judiciaryId)
                .withSessionId(sessionId)
                .withReason(reason)
                .build();
    }

    private void logMissingReferences(final Set<String> missingJudiciaryIds,
                                      final Set<String> missingSessionIds,
                                      final String executionId) {
        if (!missingJudiciaryIds.isEmpty()) {
            final String joined = String.join(", ", missingJudiciaryIds);
            LOGGER.warn("Missing judiciary ids for assignment: {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode(MissingDataError.JUDICIARY_ID_NOT_FOUND_ASSIGNMENT.code())
                    .withErrorText(MissingDataError.JUDICIARY_ID_NOT_FOUND_ASSIGNMENT.format(joined))
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
        if (!missingSessionIds.isEmpty()) {
            final String joined = String.join(", ", missingSessionIds);
            LOGGER.warn("Missing session ids for assignment: {}", joined);
            final RotaProcessLog log = rotaProcessLog()
                    .withExecutionId(executionId)
                    .withErrorCode(MissingDataError.SESSION_ID_NOT_FOUND_ASSIGNMENT.code())
                    .withErrorText(MissingDataError.SESSION_ID_NOT_FOUND_ASSIGNMENT.format(joined))
                    .build();
            rotaProcessLogService.saveRotaProcessLog(log);
        }
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
}

