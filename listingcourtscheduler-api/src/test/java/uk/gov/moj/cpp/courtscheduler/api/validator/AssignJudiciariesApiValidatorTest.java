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
                .withIsDeputy(false)
                .withIsBenchChairman(true)
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
                .withIsDeputy(false)
                .withIsBenchChairman(true)
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

    @Test
    void shouldSkipValidationWhenSkipValidationsIsTrue() {
        // Even with invalid assignment (empty judiciaryId, null isDeputy/isBenchChairman), validation should pass when skipValidations is true
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("")
                .withSessionIds(List.of("not-a-uuid"))
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .withSkipValidations(true)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPerformValidationWhenSkipValidationsIsFalse() {
        // With invalid assignment and skipValidations false, validation should fail
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("")
                .withSessionIds(List.of("not-a-uuid"))
                .withIsDeputy(false)
                .withIsBenchChairman(true)
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .withSkipValidations(false)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary id is mandatory"));
    }

    @Test
    void shouldPerformValidationWhenSkipValidationsIsNotSet() {
        // When skipValidations is not set, should default to false and perform validation
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("")
                .withSessionIds(List.of("not-a-uuid"))
                .withIsDeputy(false)
                .withIsBenchChairman(true)
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("Judiciary id is mandatory"));
    }

    @Test
    void shouldReturnErrorWhenIsDeputyIsNull() {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("judiciary-1")
                .withSessionIds(List.of(UUID.randomUUID().toString()))
                .withIsBenchChairman(true)
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("isDeputy is mandatory"));
    }

    @Test
    void shouldReturnErrorWhenIsBenchChairmanIsNull() {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("judiciary-1")
                .withSessionIds(List.of(UUID.randomUUID().toString()))
                .withIsDeputy(false)
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("isBenchChairman is mandatory"));
    }

    @Test
    void shouldReturnErrorWhenBothIsDeputyAndIsBenchChairmanAreNull() {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId("judiciary-1")
                .withSessionIds(List.of(UUID.randomUUID().toString()))
                .build();
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();

        final JsonObject result = validator.validate(request);

        assertTrue(result.containsKey("errorMessage"));
        final String message = result.getString("errorMessage");
        assertTrue(message.contains("isDeputy is mandatory"));
        assertTrue(message.contains("isBenchChairman is mandatory"));
    }
}

