package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.List.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.AssignmentFailureReason;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;
import java.util.Optional;

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
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private Requester requester;

    @Test
    void shouldAssignJudiciaryToSession() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), requester, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        verify(courtScheduleJudiciaryRepository).save(any());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldRecordFailureWhenJudiciaryMissing() {
        final String judiciaryId = "missing-judiciary";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.empty());
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), requester, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(1, response.getFailures().size());
        assertEquals(AssignmentFailureReason.JUDICIARY_NOT_FOUND, response.getFailures().get(0).getReason());

        verify(courtScheduleJudiciaryRepository, never()).save(any());
        verifyRotaLog(MissingLogType.JUDICIARY);
    }

    @Test
    void shouldRecordFailureWhenSessionMissing() {
        final String judiciaryId = "judiciary-1";
        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(List.of());

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, "unknown-session"), requester, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(AssignmentFailureReason.SESSION_NOT_FOUND, response.getFailures().get(0).getReason());

        verifyRotaLog(MissingLogType.SESSION);
    }

    @Test
    void shouldHandleDuplicateAssignmentsGracefully() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));
        when(courtScheduleJudiciaryRepository.save(any())).thenThrow(new RuntimeException("duplicate key constraint"));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), requester, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(AssignmentFailureReason.DUPLICATE_ASSIGNMENT, response.getFailures().get(0).getReason());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldHandleUnexpectedPersistenceErrors() {
        final String judiciaryId = "judiciary-1";
        final String sessionId = "session-1";

        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));
        when(courtScheduleJudiciaryRepository.save(any())).thenThrow(new RuntimeException("connection lost"));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(buildRequest(judiciaryId, sessionId), requester, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(AssignmentFailureReason.PERSISTENCE_ERROR, response.getFailures().get(0).getReason());
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

    private void verifyRotaLog(final MissingLogType type) {
        final ArgumentCaptor<RotaProcessLog> captor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(captor.capture());
        final RotaProcessLog log = captor.getValue();
        if (type == MissingLogType.JUDICIARY) {
            assertEquals("JUDICIARY_ID_NOT_FOUND_ASSIGNMENT", log.getErrorCode());
        } else {
            assertEquals("SESSION_ID_NOT_FOUND_ASSIGNMENT", log.getErrorCode());
        }
    }

    @Test
    void shouldSkipValidationsWhenSkipValidationsIsTrue() {
        final String judiciaryId = "missing-judiciary";
        final String sessionId = "missing-session";

        // Even though judiciary and session don't exist, with skipValidations=true, should not fail or log
        final AssignJudiciariesRequest request = AssignJudiciariesRequest.builder()
                .addJudiciary(JudiciaryAssignment.builder()
                        .withJudiciaryId(judiciaryId)
                        .addSessionId(sessionId)
                        .build())
                .withSkipValidations(true)
                .build();

        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.empty());
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(List.of());

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(request, requester, EXECUTION_ID);

        // Should not record failures or log errors when skipValidations is true
        assertEquals(1, response.getRequestedAssignments());
        assertEquals(0, response.getSuccessfulAssignments());
        assertEquals(0, response.getFailures().size());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
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

        when(referenceDataMapperService.findById(requester, judiciaryId)).thenReturn(Optional.of(buildJudiciary(judiciaryId)));
        when(courtScheduleRepository.findByCourtScheduleIds(anyList())).thenReturn(of(buildCourtSchedule(sessionId)));

        final AssignJudiciariesResponse response = judiciaryAssignmentService.assignJudiciaries(request, requester, EXECUTION_ID);

        assertEquals(1, response.getRequestedAssignments());
        assertEquals(1, response.getSuccessfulAssignments());
        assertTrue(response.getFailures().isEmpty());
        verify(courtScheduleJudiciaryRepository).save(any());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    private enum MissingLogType {
        JUDICIARY,
        SESSION
    }
}

