package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.json.JsonObject;

@ApplicationScoped
public class AssignJudiciariesApiValidator {

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
        for (int index = 0; index < assignments.size(); index++) {
            final JudiciaryAssignment assignment = assignments.get(index);
            if (assignment == null) {
                errors.add(message(index, "Assignment payload is missing"));
                continue;
            }

            if (isBlank(assignment.getJudiciaryId())) {
                errors.add(message(index, "Judiciary id is mandatory"));
            }

            if (isEmpty(assignment.getSessionIds())) {
                errors.add(message(index, "At least one session id is required"));
            } else {
                final int assignmentIndex = index;
                assignment.getSessionIds().forEach(sessionId -> validateSessionId(assignmentIndex, sessionId, errors));
            }

            if (assignment.getIsDeputy() == null) {
                errors.add(message(index, "isDeputy is mandatory"));
            }

            if (assignment.getIsBenchChairman() == null) {
                errors.add(message(index, "isBenchChairman is mandatory"));
            }
        }
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

