package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

@Service
public class AssignJudiciariesApiValidator {

    private ReferenceDataMapperService referenceDataMapperService;
    private CourtScheduleRepository courtScheduleRepository;

    // Protected no-arg constructor required for CDI proxy creation
    protected AssignJudiciariesApiValidator() {
        // CDI will use the @Inject constructor for actual injection
    }

    @Inject
    public AssignJudiciariesApiValidator(final ReferenceDataMapperService referenceDataMapperService,
                                         final CourtScheduleRepository courtScheduleRepository) {
        this.referenceDataMapperService = referenceDataMapperService;
        this.courtScheduleRepository = courtScheduleRepository;
    }

    public JsonObject validate(final AssignJudiciariesRequest request) {
        // Skip validation if skipValidations flag is set to true
        if (request != null && request.isSkipValidations()) {
            return EMPTY_JSON_OBJECT;
        }

        final List<String> errors = new ArrayList<>();

        if (request == null || isEmpty(request.getJudiciaries())) {
            errors.add("At least one judiciary assignment must be supplied");
        } else {
            validateAssignments(request.getJudiciaries(), errors);
        }

        if (errors.isEmpty()) {
            return EMPTY_JSON_OBJECT;
        }

        return createObjectBuilder()
                .add(ERROR_MESSAGE, String.join(" | ", errors))
                .build();
    }

    private void validateAssignments(final List<JudiciaryAssignment> assignments, final List<String> errors) {
        final Map<String, CourtSchedule> sessionsById = fetchSessionsById(assignments);

        for (int index = 0; index < assignments.size(); index++) {
            final JudiciaryAssignment assignment = assignments.get(index);
            if (assignment == null) {
                errors.add(message(index, "Assignment payload is missing"));
                continue;
            }

            validateSingleAssignment(assignment, index, sessionsById, errors);
        }
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

    private void validateSingleAssignment(final JudiciaryAssignment assignment,
                                          final int index,
                                          final Map<String, CourtSchedule> sessionsById,
                                          final List<String> errors
    ) {
        validateJudiciary(assignment, index, errors);
        validateSessions(assignment, index, sessionsById, errors);
    }

    private void validateJudiciary(final JudiciaryAssignment assignment,
                                   final int index,
                                   final List<String> errors
    ) {
        final String judiciaryId = assignment.getJudiciaryId();
        if (isBlank(judiciaryId)) {
            errors.add(message(index, "Judiciary id is mandatory"));
            return;
        }

        if (referenceDataMapperService.findById(judiciaryId).isEmpty()) {
            errors.add(message(index, "Judiciary not found: " + judiciaryId));
        }
    }

    private void validateSessions(final JudiciaryAssignment assignment,
                                  final int index,
                                  final Map<String, CourtSchedule> sessionsById,
                                  final List<String> errors) {
        final List<String> sessionIds = sanitizeSessionIds(assignment.getSessionIds());
        if (isEmpty(sessionIds)) {
            errors.add(message(index, "At least one session id is required"));
            return;
        }

        for (final String sessionId : sessionIds) {
            validateSessionId(index, sessionId, errors);
            if (isValidUuidFormat(sessionId, errors)) {
                validateSessionExists(index, sessionId, sessionsById, errors);
            }
        }
    }

    private boolean isValidUuidFormat(final String sessionId, final List<String> errors) {
        return errors.stream()
                .noneMatch(e -> e.contains(sessionId) && e.contains("not a valid UUID"));
    }

    private void validateSessionExists(final int index,
                                       final String sessionId,
                                       final Map<String, CourtSchedule> sessionsById,
                                       final List<String> errors) {
        if (!sessionsById.containsKey(sessionId)) {
            errors.add(message(index, "Session not found: " + sessionId));
        }
    }

    private List<String> sanitizeSessionIds(final List<String> sessionIds) {
        if (sessionIds == null) {
            return List.of();
        }
        return sessionIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !isBlank(value))
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private void validateSessionId(final int index, final String sessionId, final List<String> errors) {
        if (isBlank(sessionId)) {
            errors.add(message(index, "Session id cannot be blank"));
            return;
        }
        try {
            UUID.fromString(sessionId);
        } catch (IllegalArgumentException invalidUuid) {
            errors.add(message(index, "Session id %s is not a valid UUID".formatted(sessionId)));
        }
    }

    private String message(final int index, final String detail) {
        return "Judiciary assignment [%d]: %s".formatted(index, detail);
    }
}

