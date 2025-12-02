package uk.gov.moj.cpp.courtscheduler.api.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;

class AssignJudiciariesApiValidatorTest {

    private final AssignJudiciariesApiValidator validator = new AssignJudiciariesApiValidator();

    @Test
    void shouldReturnEmptyWhenValid() {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("judiciary-1")
                .withSessionIds(List.of(UUID.randomUUID().toString()))
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorsWhenMissingData() {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("")
                .withSessionIds(List.of("not-a-uuid"))
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary id is mandatory"));
        assertTrue(message.contains("not-a-uuid"));
    }

    @Test
    void shouldRequireAssignments() {
        final JsonObject result = validator.validate(null);
        assertEquals("At least one judiciary assignment must be supplied", result.getString("errorMessage"));
    }
}

