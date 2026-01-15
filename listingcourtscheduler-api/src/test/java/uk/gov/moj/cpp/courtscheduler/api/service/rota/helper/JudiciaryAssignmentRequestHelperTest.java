package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.List;
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
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1), null, "CHAIR", true, false))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getJudiciaryId(), is(judiciaryId1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(0), is(sessionId1.toString()));
        assertThat(result.getJudiciaries().get(0).getPosition(), is("CHAIR"));
        assertThat(result.getJudiciaries().get(0).getIsBenchChairman(), is(true));
        assertThat(result.getJudiciaries().get(0).getIsDeputy(), is(false));
        assertThat(result.getJudiciaries().get(0).getRotaJudiciaryId(), is((String) null));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenSingleJudiciaryWithMultipleSessions() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1, sessionId2, sessionId3), null, "LEFT_WINGER", false, true))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getJudiciaryId(), is(judiciaryId1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().size(), is(3));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(0), is(sessionId1.toString()));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(1), is(sessionId2.toString()));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(2), is(sessionId3.toString()));
        assertThat(result.getJudiciaries().get(0).getPosition(), is("LEFT_WINGER"));
        assertThat(result.getJudiciaries().get(0).getIsBenchChairman(), is(false));
        assertThat(result.getJudiciaries().get(0).getIsDeputy(), is(true));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenMultipleJudiciariesWithSingleSession() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1), null, "CHAIR", true, false)),
                new JudiciaryScheduleAssignment(judiciaryId2, new JudiciaryCourtScheduleData(
                        List.of(sessionId2), null, "RIGHT_WINGER", false, true))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

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
        assertThat(assignment1.getPosition(), is("CHAIR"));
        assertThat(assignment1.getIsBenchChairman(), is(true));
        assertThat(assignment1.getIsDeputy(), is(false));

        // Verify second judiciary
        final JudiciaryAssignment assignment2 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId2))
                .findFirst()
                .orElse(null);
        assertThat(assignment2, is(notNullValue()));
        assertThat(assignment2.getSessionIds().size(), is(1));
        assertThat(assignment2.getSessionIds().get(0), is(sessionId2.toString()));
        assertThat(assignment2.getPosition(), is("RIGHT_WINGER"));
        assertThat(assignment2.getIsBenchChairman(), is(false));
        assertThat(assignment2.getIsDeputy(), is(true));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenMultipleJudiciariesWithMultipleSessions() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1, sessionId2), null, "CHAIR", true, false)),
                new JudiciaryScheduleAssignment(judiciaryId2, new JudiciaryCourtScheduleData(
                        List.of(sessionId3), null, "LEFT_WINGER", false, true))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

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
        assertThat(assignment1.getPosition(), is("CHAIR"));
        assertThat(assignment1.getIsBenchChairman(), is(true));
        assertThat(assignment1.getIsDeputy(), is(false));

        // Verify second judiciary
        final JudiciaryAssignment assignment2 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId2))
                .findFirst()
                .orElse(null);
        assertThat(assignment2, is(notNullValue()));
        assertThat(assignment2.getSessionIds().size(), is(1));
        assertThat(assignment2.getSessionIds().get(0), is(sessionId3.toString()));
        assertThat(assignment2.getPosition(), is("LEFT_WINGER"));
        assertThat(assignment2.getIsBenchChairman(), is(false));
        assertThat(assignment2.getIsDeputy(), is(true));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenEmptySessionList() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(), null, "CHAIR", true, false))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getJudiciaryId(), is(judiciaryId1));
        assertThat(result.getJudiciaries().get(0).getSessionIds().size(), is(0));
        assertThat(result.getJudiciaries().get(0).getPosition(), is("CHAIR"));
        assertThat(result.getJudiciaries().get(0).getIsBenchChairman(), is(true));
        assertThat(result.getJudiciaries().get(0).getIsDeputy(), is(false));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenEmptyList() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = emptyList();

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(0));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenNullList() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = null;

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(0));
    }

    @Test
    void shouldConvertUuidToString_WhenBuildingRequest() {
        // given
        final UUID uuid1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        final UUID uuid2 = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(uuid1, uuid2), null, "CHAIR", true, false))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(0), is("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(result.getJudiciaries().get(0).getSessionIds().get(1), is("6ba7b810-9dad-11d1-80b4-00c04fd430c8"));
    }

    @Test
    void shouldSetSkipValidationsToTrue_WhenBuildingRequest() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1), null, "CHAIR", true, false))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.isSkipValidations(), is(true));
    }

    @Test
    void shouldBuildAssignJudiciariesRequest_WhenMultipleAssignmentsForSameJudiciary() {
        // given
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1), null, "CHAIR", true, false)),
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId2), null, "LEFT_WINGER", false, true))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(2));
        // Both assignments should be present
        assertThat(result.getJudiciaries().stream()
                .anyMatch(a -> a.getJudiciaryId().equals(judiciaryId1) && a.getPosition().equals("CHAIR")), is(true));
        assertThat(result.getJudiciaries().stream()
                .anyMatch(a -> a.getJudiciaryId().equals(judiciaryId1) && a.getPosition().equals("LEFT_WINGER")), is(true));
    }

    @Test
    void shouldSetRotaJudiciaryId_WhenProvidedInScheduleData() {
        // given
        final String rotaJudiciaryId = "rota-judge-123";
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1), rotaJudiciaryId, "CHAIR", true, false))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(1));
        assertThat(result.getJudiciaries().get(0).getRotaJudiciaryId(), is(rotaJudiciaryId));
    }

    @Test
    void shouldSetRotaJudiciaryId_WhenMultipleJudiciariesWithDifferentRotaIds() {
        // given
        final String rotaJudiciaryId1 = "rota-judge-123";
        final String rotaJudiciaryId2 = "rota-judge-456";
        final List<JudiciaryScheduleAssignment> assignmentList = List.of(
                new JudiciaryScheduleAssignment(judiciaryId1, new JudiciaryCourtScheduleData(
                        List.of(sessionId1), rotaJudiciaryId1, "CHAIR", true, false)),
                new JudiciaryScheduleAssignment(judiciaryId2, new JudiciaryCourtScheduleData(
                        List.of(sessionId2), rotaJudiciaryId2, "LEFT_WINGER", false, true))
        );

        // when
        final AssignJudiciariesRequest result = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.getJudiciaries().size(), is(2));

        // Verify first judiciary
        final JudiciaryAssignment assignment1 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId1))
                .findFirst()
                .orElse(null);
        assertThat(assignment1, is(notNullValue()));
        assertThat(assignment1.getRotaJudiciaryId(), is(rotaJudiciaryId1));

        // Verify second judiciary
        final JudiciaryAssignment assignment2 = result.getJudiciaries().stream()
                .filter(a -> a.getJudiciaryId().equals(judiciaryId2))
                .findFirst()
                .orElse(null);
        assertThat(assignment2, is(notNullValue()));
        assertThat(assignment2.getRotaJudiciaryId(), is(rotaJudiciaryId2));
    }

}

