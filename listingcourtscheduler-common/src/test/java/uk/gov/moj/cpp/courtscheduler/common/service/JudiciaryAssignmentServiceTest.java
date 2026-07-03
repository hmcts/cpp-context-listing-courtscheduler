package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.AssignmentFailureReason;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAssignmentServiceTest {

    private static final String EXECUTION_ID = "exec-id";

    @InjectMocks
    private JudiciaryAssignmentService judiciaryAssignmentService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Test
    void shouldAssignJudiciaryToSession() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        verify(entityManager).merge(any());
        verify(entityManager).flush();
    }

    @Test
    void shouldSkipAssignmentWhenJudiciaryMissing() {
        // When judiciary is missing, service should skip the assignment (validation happens in validator layer)
        final String judiciaryId = "missing-judiciary";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.empty());
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), EXECUTION_ID);

        // Service skips assignment when judiciary is null (validation should have caught this in validator)
        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(0, response.getFailures().size()); // No failures recorded - validation happens in validator layer

        verify(entityManager, never()).merge(any());
        verify(entityManager, never()).flush();
    }

    @Test
    void shouldSkipAssignmentWhenSessionMissing() {
        // When session is missing, service should skip the assignment (validation happens in validator layer)
        final String judiciaryId = "judiciary-1";
        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(List.of());

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, "unknown-session"), EXECUTION_ID);

        // Service skips assignment when session is null (validation should have caught this in validator)
        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(0, response.getFailures().size()); // No failures recorded - validation happens in validator layer
    }

    @Test
    void shouldHandleDuplicateAssignmentsGracefully() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));
        final PersistenceException persistenceException = new PersistenceException(
                new SQLIntegrityConstraintViolationException("duplicate key constraint"));
        doThrow(persistenceException).when(entityManager).merge(any());

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(AssignmentFailureReason.DUPLICATE_ASSIGNMENT, response.getFailures().get(0).getReason());
    }

    @Test
    void shouldHandleUnexpectedPersistenceErrors() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));
        doThrow(new RuntimeException("connection lost")).when(entityManager).merge(any());

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(AssignmentFailureReason.PERSISTENCE_ERROR, response.getFailures().get(0).getReason());
        verify(entityManager).merge(any());
    }

    private AssignJudiciariesRequest buildRequest(final String judiciaryId, final String sessionId) {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId(judiciaryId)
                .addSessionId(sessionId)
                .build();
        return AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();
    }

    private AssignJudiciariesRequest buildRequestWithRotaJudiciaryId(final String judiciaryId, final String sessionId, final String rotaJudiciaryId) {
        final JudiciaryAssignment assignment = JudiciaryAssignment.builder()
                .withJudiciaryId(judiciaryId)
                .withRotaJudiciaryId(rotaJudiciaryId)
                .addSessionId(sessionId)
                .build();
        return AssignJudiciariesRequest.builder()
                .addJudiciary(assignment)
                .build();
    }

    private Judiciary buildJudiciary(final String judiciaryId) {
        final Judiciary judiciary = new Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setForenames("Test");
        judiciary.setSurname("Judge");
        judiciary.setEmailAddress("test.judge@example.com");
        judiciary.setJudiciaryType("CHAIR");
        judiciary.setCpUserId("CP-" + judiciaryId);
        judiciary.setTitlePrefix("HHJ");
        return judiciary;
    }

    private CourtSchedule buildCourtSchedule(final String sessionId) {
        final CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCourtScheduleId(sessionId);
        courtSchedule.setListingProfileId("profile-" + sessionId);
        return courtSchedule;
    }

    @Test
    void shouldSkipValidationsWhenSkipValidationsIsTrue() {
        final String judiciaryId = "missing-judiciary";
        final String sessionId = "missing-session";

        // Even though judiciary and session don't exist, with skipValidations=true, should not fail but should log
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(JudiciaryAssignment.builder()
                        .withJudiciaryId(judiciaryId)
                        .addSessionId(sessionId)
                        .build())
                .withSkipValidations(true)
                .build();

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.empty());
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(List.of());

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(request, EXECUTION_ID);

        // Should not record failures when skipValidations is true (skips assignment silently)
        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(0, response.getFailures().size());

        // Should log missing references for monitoring purposes (both judiciary and session are missing)
        verify(rotaProcessLogService, atLeastOnce()).saveRotaProcessLog(any());
    }

    @Test
    void shouldSkipValidationsAndStillAssignWhenJudiciaryAndSessionExist() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(JudiciaryAssignment.builder()
                        .withJudiciaryId(judiciaryId)
                        .addSessionId(sessionId)
                        .build())
                .withSkipValidations(true)
                .build();

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(request, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        verify(entityManager).merge(any());
        verify(entityManager).flush();
    }

    @Test
    void shouldUseRotaJudiciaryIdFromAssignment_WhenProvided() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";
        final String rotaJudiciaryId = "rota-judge-123";

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(
                buildRequestWithRotaJudiciaryId(judiciaryId, sessionId, rotaJudiciaryId), EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        
        // Verify that the saved CourtScheduleJudiciary has the rotaJudiciaryId from the assignment
        final ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary> entityCaptor = 
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class);
        verify(entityManager).merge(entityCaptor.capture());
        assertEquals(rotaJudiciaryId, entityCaptor.getValue().getRotaJudiciaryId());
    }

    @Test
    void shouldFallbackToJudiciaryCpUserId_WhenRotaJudiciaryIdNotProvided() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";
        final String cpUserId = "CP-" + judiciaryId;

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(
                buildRequest(judiciaryId, sessionId), EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        
        // Verify that the saved CourtScheduleJudiciary falls back to cpUserId when rotaJudiciaryId is null
        final ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary> entityCaptor = 
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class);
        verify(entityManager).merge(entityCaptor.capture());
        assertEquals(cpUserId, entityCaptor.getValue().getRotaJudiciaryId());
    }

    @Test
    void shouldFallbackToJudiciaryId_WhenRotaJudiciaryIdAndCpUserIdNotProvided() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";
        final Judiciary judiciary = buildJudiciary(judiciaryId);
        judiciary.setCpUserId(null); // Clear cpUserId to test fallback to judiciaryId

        when(referenceDataMapperService.findById(judiciaryId)).thenReturn(Optional.of(judiciary));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(
                buildRequest(judiciaryId, sessionId), EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        
        // Verify that the saved CourtScheduleJudiciary falls back to judiciaryId when rotaJudiciaryId and cpUserId are null
        final ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary> entityCaptor = 
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class);
        verify(entityManager).merge(entityCaptor.capture());
        assertEquals(judiciaryId, entityCaptor.getValue().getRotaJudiciaryId());
    }

    @Test
    void shouldFlushOnceAtEnd_WhenMultipleAssignments() {
        // Verify that flush is called only once at the end, not per entity
        final String judiciaryId1 = "judiciary-1";
        final String judiciaryId2 = "judiciary-2";
        final String sessionId1 = "session-1";
        final String sessionId2 = "session-2";

        when(referenceDataMapperService.findById(judiciaryId1)).thenReturn(Optional.of(buildJudiciary(judiciaryId1)));
        when(referenceDataMapperService.findById(judiciaryId2)).thenReturn(Optional.of(buildJudiciary(judiciaryId2)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(
                of(buildCourtSchedule(sessionId1), buildCourtSchedule(sessionId2)));

        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(JudiciaryAssignment.builder()
                        .withJudiciaryId(judiciaryId1)
                        .addSessionId(sessionId1)
                        .build())
                .addJudiciary(JudiciaryAssignment.builder()
                        .withJudiciaryId(judiciaryId2)
                        .addSessionId(sessionId2)
                        .build())
                .build();

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(request, EXECUTION_ID);

        assertEquals(2, response.getRequestedAssignments());
        assertEquals(2, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        
        // Verify merge is called for each assignment
        verify(entityManager, atLeastOnce()).merge(any());
        // Verify flush is called only once at the end (not per entity)
        verify(entityManager).flush();
    }
}

