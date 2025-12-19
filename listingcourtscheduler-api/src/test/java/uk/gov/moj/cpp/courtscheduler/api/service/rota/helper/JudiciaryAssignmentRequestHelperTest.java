package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAssignmentRequestHelperTest {

    @InjectMocks
    private JudiciaryAssignmentRequestHelper judiciaryAssignmentRequestHelper;

    private String judiciaryId1;
    private String judiciaryId2;
    private UUID sessionId1;
    private UUID sessionId2;
    private UUID sessionId3;

    @BeforeEach
    void setUp() {
        judiciaryId1 = UUID.randomUUID().toString();
        judiciaryId2 = UUID.randomUUID().toString();
        sessionId1 = UUID.randomUUID();
        sessionId2 = UUID.randomUUID();
        sessionId3 = UUID.randomUUID();
    }

    // ============================================================================
    // Tests for buildAssignJudiciariesRequest
    // ============================================================================

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenSingleJudiciaryWithSingleSession() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of(sessionId1));

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getJudiciaryId(), is(judiciaryId1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(0), is(sessionId1.toString()));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenSingleJudiciaryWithMultipleSessions() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of(sessionId1, sessionId2, sessionId3));

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getJudiciaryId(), is(judiciaryId1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().size(), is(3));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(0), is(sessionId1.toString()));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(1), is(sessionId2.toString()));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(2), is(sessionId3.toString()));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenMultipleJudiciariesWithSingleSession() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of(sessionId1));
        judiciaryAssignmentMap.put(judiciaryId2, List.of(sessionId2));

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(2));

        // Verify first judiciary
        final JudiciaryAssignment assignment1 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId1))
                .findFirst()
                .orElse(null);
        assertThat(assignment1, is(notNullValue()));
        assertThat(assignment1.getSessionIds().size(), is(1));
        assertThat(assignment1.getSessionIds().get(0), is(sessionId1.toString()));

        // Verify second judiciary
        final JudiciaryAssignment assignment2 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId2))
                .findFirst()
                .orElse(null);
        assertThat(assignment2, is(notNullValue()));
        assertThat(assignment2.getSessionIds().size(), is(1));
        assertThat(assignment2.getSessionIds().get(0), is(sessionId2.toString()));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenMultipleJudiciariesWithMultipleSessions() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of(sessionId1, sessionId2));
        judiciaryAssignmentMap.put(judiciaryId2, List.of(sessionId3));

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(2));

        // Verify first judiciary
        final JudiciaryAssignment assignment1 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId1))
                .findFirst()
                .orElse(null);
        assertThat(assignment1, is(notNullValue()));
        assertThat(assignment1.getSessionIds().size(), is(2));
        assertThat(assignment1.getSessionIds().contains(sessionId1.toString()), is(true));
        assertThat(assignment1.getSessionIds().contains(sessionId2.toString()), is(true));

        // Verify second judiciary
        final JudiciaryAssignment assignment2 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId2))
                .findFirst()
                .orElse(null);
        assertThat(assignment2, is(notNullValue()));
        assertThat(assignment2.getSessionIds().size(), is(1));
        assertThat(assignment2.getSessionIds().get(0), is(sessionId3.toString()));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenEmptySessionList() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of());

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getJudiciaryId(), is(judiciaryId1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().size(), is(0));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenEmptyMap() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = emptyMap();

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(0));
    }

    @Test
    void shouldConvertUuidToString_WhenBuildingRequest() {
        // given
        final UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        final UUID uuid2 = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of(uuid1, uuid2));

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(0), is("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(1), is("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));
    }

    @Test
    void shouldSetSkipValidationsToTrue_WhenBuildingRequest() {
        // given
        final Map<String, List<UUID>> judiciaryAssignmentMap = new HashMap<>();
        judiciaryAssignmentMap.put(judiciaryId1, List.of(sessionId1));

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.isSkipValidations(), is(true));
    }

    // ============================================================================
    // Tests for convertToUnassignmentMap
    // ============================================================================

    @Test
    void shouldConvertToUnassignmentMap_WhenSingleJudiciaryWithSingleSession() {
        // given
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of(sessionId1));

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId1), is(true));
        assertThat(result.get(judiciaryId1).size(), is(1));
        assertThat(result.get(judiciaryId1).get(0), is(sessionId1.toString()));
    }

    @Test
    void shouldConvertToUnassignmentMap_WhenSingleJudiciaryWithMultipleSessions() {
        // given
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of(sessionId1, sessionId2, sessionId3));

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId1), is(true));
        assertThat(result.get(judiciaryId1).size(), is(3));
        assertThat(result.get(judiciaryId1).get(0), is(sessionId1.toString()));
        assertThat(result.get(judiciaryId1).get(1), is(sessionId2.toString()));
        assertThat(result.get(judiciaryId1).get(2), is(sessionId3.toString()));
    }

    @Test
    void shouldConvertToUnassignmentMap_WhenMultipleJudiciariesWithSingleSession() {
        // given
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of(sessionId1));
        judiciaryUnAssignmentMap.put(judiciaryId2, List.of(sessionId2));

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(2));
        assertThat(result.containsKey(judiciaryId1), is(true));
        assertThat(result.containsKey(judiciaryId2), is(true));
        assertThat(result.get(judiciaryId1).size(), is(1));
        assertThat(result.get(judiciaryId1).get(0), is(sessionId1.toString()));
        assertThat(result.get(judiciaryId2).size(), is(1));
        assertThat(result.get(judiciaryId2).get(0), is(sessionId2.toString()));
    }

    @Test
    void shouldConvertToUnassignmentMap_WhenMultipleJudiciariesWithMultipleSessions() {
        // given
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of(sessionId1, sessionId2));
        judiciaryUnAssignmentMap.put(judiciaryId2, List.of(sessionId3));

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(2));
        assertThat(result.get(judiciaryId1).size(), is(2));
        assertThat(result.get(judiciaryId1).contains(sessionId1.toString()), is(true));
        assertThat(result.get(judiciaryId1).contains(sessionId2.toString()), is(true));
        assertThat(result.get(judiciaryId2).size(), is(1));
        assertThat(result.get(judiciaryId2).get(0), is(sessionId3.toString()));
    }

    @Test
    void shouldConvertToUnassignmentMap_WhenEmptySessionList() {
        // given
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of());

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId1), is(true));
        assertThat(result.get(judiciaryId1).size(), is(0));
    }

    @Test
    void shouldConvertToUnassignmentMap_WhenEmptyMap() {
        // given
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = emptyMap();

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldConvertUuidToString_WhenConvertingUnassignmentMap() {
        // given
        final UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        final UUID uuid2 = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of(uuid1, uuid2));

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result.get(judiciaryId1).get(0), is("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(result.get(judiciaryId1).get(1), is("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));
    }

    @Test
    void shouldPreserveOrder_WhenConvertingUnassignmentMap() {
        // given
        final UUID uuid1 = UUID.randomUUID();
        final UUID uuid2 = UUID.randomUUID();
        final UUID uuid3 = UUID.randomUUID();
        final Map<String, List<UUID>> judiciaryUnAssignmentMap = new HashMap<>();
        judiciaryUnAssignmentMap.put(judiciaryId1, List.of(uuid1, uuid2, uuid3));

        // when
        final Map<String, List<String>> result = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);

        // then
        assertThat(result.get(judiciaryId1).get(0), is(uuid1.toString()));
        assertThat(result.get(judiciaryId1).get(1), is(uuid2.toString()));
        assertThat(result.get(judiciaryId1).get(2), is(uuid3.toString()));
    }
}

