package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JudiciariesApiValidatorTest {
    private static final String JUDGE_456 = "judge-456";
    private static final String JUDICIARIES = "judiciaries";
    private static final String JUDICIARY_ID = "judiciaryId";
    private static final String SCHEDULE_123 = "schedule-123";
    private static final String SESSION_IDS = "sessionIds";
    private static final String SESSION_IDS_ARRAY_MUST_CONTAIN_AT_LEAST_ONE_ITEM = "sessionIds array must contain at least one item in judiciaries[0]";


    private JudiciariesApiValidator judiciariesApiValidator;

    @BeforeEach
    void setUp() {
        judiciariesApiValidator = new JudiciariesApiValidator();
    }

    @Test
    void shouldValidateSuccessfullyWhenPayloadIsValid() {
        final String sessionId = SCHEDULE_123;
        final String judiciaryId = JUDGE_456;

        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId)
                .add(SESSION_IDS, createArrayBuilder()
                        .add(sessionId)
                        .build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenJudiciariesArrayIsMissing() {
        final JsonObject payload = createObjectBuilder()
                .build();

        assertThrows(NullPointerException.class,
                () -> judiciariesApiValidator.validateUnassignJudiciaryRequest(payload));
    }

    @Test
    void shouldThrowClassCastExceptionWhenJudiciariesArrayIsNull() {
        final JsonObject payload = createObjectBuilder()
                .addNull(JUDICIARIES)
                .build();

        assertThrows(ClassCastException.class,
                () -> judiciariesApiValidator.validateUnassignJudiciaryRequest(payload));
    }

    @Test
    void shouldValidateSuccessfullyWhenJudiciariesArrayIsEmpty() {
        final JsonArray judiciariesArray = createArrayBuilder()
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldValidateSuccessfullyWhenJudiciaryIdIsMissing() {
        final JsonObject judiciary = createObjectBuilder()
                .add(SESSION_IDS, createArrayBuilder()
                        .add(SCHEDULE_123)
                        .build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldValidateSuccessfullyWhenJudiciaryIdIsEmpty() {
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, "")
                .add(SESSION_IDS, createArrayBuilder()
                        .add(SCHEDULE_123)
                        .build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenSessionIdsIsMissing() {
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);

        assertTrue(!result.isEmpty());
        assertEquals(SESSION_IDS_ARRAY_MUST_CONTAIN_AT_LEAST_ONE_ITEM, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldThrowClassCastExceptionWhenSessionIdsIsNull() {
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .addNull(SESSION_IDS)
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        assertThrows(ClassCastException.class,
                () -> judiciariesApiValidator.validateUnassignJudiciaryRequest(payload));
    }

    @Test
    void shouldReturnErrorWhenSessionIdsArrayIsEmpty() {
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .add(SESSION_IDS, createArrayBuilder().build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);

        assertTrue(!result.isEmpty());
        assertEquals(SESSION_IDS_ARRAY_MUST_CONTAIN_AT_LEAST_ONE_ITEM, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenSessionIdIsEmpty() {
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .add(SESSION_IDS, createArrayBuilder()
                        .add("")
                        .build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);

        assertTrue(!result.isEmpty());
        assertEquals("sessionId is required in judiciaries[0].sessionIds[0]", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldValidateSuccessfullyWithMultipleJudiciariesAndSessions() {
        final String sessionId1 = "8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111";
        final String sessionId2 = "1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666";
        final String sessionId3 = "22223333-4444-5555-6666-777788889999";
        final String judiciaryId1 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        final String judiciaryId2 = "7e6f4a11-1111-2222-3333-444455556666";

        final JsonObject judiciary1 = createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId1)
                .add(SESSION_IDS, createArrayBuilder()
                        .add(sessionId1)
                        .add(sessionId2)
                        .build())
                .build();
        final JsonObject judiciary2 = createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId2)
                .add(SESSION_IDS, createArrayBuilder()
                        .add(sessionId3)
                        .build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary1)
                .add(judiciary2)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldValidateSuccessfullyForSecondJudiciaryWhenFirstIsValid() {
        final String sessionId = SCHEDULE_123;
        final String judiciaryId1 = JUDGE_456;

        final JsonObject judiciary1 = createObjectBuilder()
                .add(JUDICIARY_ID, judiciaryId1)
                .add(SESSION_IDS, createArrayBuilder()
                        .add(sessionId)
                        .build())
                .build();
        final JsonObject judiciary2 = createObjectBuilder()
                .add(JUDICIARY_ID, "")
                .add(SESSION_IDS, createArrayBuilder()
                        .add(sessionId)
                        .build())
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary1)
                .add(judiciary2)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipValidationWhenSkipValidationsIsTrue() {
        // Even with invalid payload (missing sessionIds), validation should pass when skipValidations is true
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .add("skipValidations", true)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPerformValidationWhenSkipValidationsIsFalse() {
        // With invalid payload and skipValidations false, validation should fail
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .add("skipValidations", false)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertFalse(result.isEmpty());
        assertEquals(SESSION_IDS_ARRAY_MUST_CONTAIN_AT_LEAST_ONE_ITEM, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldPerformValidationWhenSkipValidationsIsNotProvided() {
        // When skipValidations is not provided, should default to false and perform validation
        final JsonObject judiciary = createObjectBuilder()
                .add(JUDICIARY_ID, JUDGE_456)
                .build();
        final JsonArray judiciariesArray = createArrayBuilder()
                .add(judiciary)
                .build();
        final JsonObject payload = createObjectBuilder()
                .add(JUDICIARIES, judiciariesArray)
                .build();

        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);
        assertFalse(result.isEmpty());
        assertEquals(SESSION_IDS_ARRAY_MUST_CONTAIN_AT_LEAST_ONE_ITEM, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenPayloadIsNull() {
        final JsonObject result = judiciariesApiValidator.validateUnassignJudiciaryRequest(null);
        assertFalse(result.isEmpty());
        assertEquals("Request payload is required", result.getString(ERROR_MESSAGE));
    }
}

