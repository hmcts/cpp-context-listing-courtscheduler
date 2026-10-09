package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryUnassignmentServiceTest {

    private static final String JUDICIARY_1 = "judiciary-1";
    private static final String SESSION_1 = "session-1";
    private static final String EXECUTION_ID = "exec-id";

    @InjectMocks
    private JudiciaryUnassignmentService judiciaryUnassignmentService;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private AllocatedListingService allocatedListingService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private JudiciaryChangeDetectionService judiciaryChangeDetectionService;

    @Mock
    private EntityManager entityManager;

    @Test
    void shouldDeleteAllJudiciaryAssignmentsForProvidedCourtScheduleIds() {
        final List<String> courtScheduleIds = List.of("schedule-1", "schedule-2");
        when(courtScheduleJudiciaryRepository.deleteAllAssignmentsForCourtScheduleIds(courtScheduleIds)).thenReturn(3);

        final List<String> result = judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(courtScheduleIds);

        assertEquals(courtScheduleIds, result);
        verify(courtScheduleJudiciaryRepository).deleteAllAssignmentsForCourtScheduleIds(courtScheduleIds);
    }

    @Test
    void shouldReturnEmptyListAndSkipDeleteWhenCourtScheduleIdsAreEmpty() {
        final List<String> result = judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(List.of());

        assertTrue(result.isEmpty());
        verify(courtScheduleJudiciaryRepository, never()).deleteAllAssignmentsForCourtScheduleIds(any());
    }


    @Test
    void shouldUnassignJudiciaryFromSessionAndReturnChangedCourtScheduleIds() {
        final String judiciaryId = JUDICIARY_1;
        final String courtScheduleId = SESSION_1;
        final Map<String, List<String>> request = Map.of(judiciaryId, List.of(courtScheduleId));

        final CourtScheduleJudiciary existingAssignment = buildEntity(courtScheduleId, judiciaryId);

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(judiciaryId)).thenReturn(List.of(existingAssignment));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId)).thenReturn(new CourtSchedule());
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(courtScheduleId))).thenReturn(Map.of());
        when(courtScheduleJudiciaryRepository.findBy(any())).thenReturn(existingAssignment);
        when(entityManager.merge(existingAssignment)).thenReturn(existingAssignment);
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList()))
                .thenReturn(Map.of())
                .thenReturn(Map.of(courtScheduleId, List.of(2)));
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any()))
                .thenReturn(List.of(courtScheduleId));

        final List<String> changedIds = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        assertEquals(List.of(courtScheduleId), changedIds);
        verify(entityManager).merge(existingAssignment);
        verify(entityManager).remove(existingAssignment);
        verify(entityManager).flush();
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldFlushAfterProcessingAllPairs() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));
        final CourtScheduleJudiciary existingAssignment = buildEntity(SESSION_1, JUDICIARY_1);

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(JUDICIARY_1)).thenReturn(List.of(existingAssignment));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(SESSION_1)).thenReturn(new CourtSchedule());
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(SESSION_1))).thenReturn(Map.of());
        when(courtScheduleJudiciaryRepository.findBy(any())).thenReturn(existingAssignment);
        when(entityManager.merge(existingAssignment)).thenReturn(existingAssignment);
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        verify(entityManager).flush();
    }


    @Test
    void shouldSkipAndLogWhenJudiciaryHasNoExistingAssignments() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(JUDICIARY_1)).thenReturn(List.of());
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        final List<String> changedIds = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        assertTrue(changedIds.isEmpty());
        verify(entityManager, never()).merge(any());
        verify(entityManager, never()).remove(any());
        verify(rotaProcessLogService).saveRotaProcessLog(any());
    }

    @Test
    void shouldSkipAndLogWhenSessionNotFound() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));
        final CourtScheduleJudiciary existingAssignment = buildEntity(SESSION_1, JUDICIARY_1);

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(JUDICIARY_1)).thenReturn(List.of(existingAssignment));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(SESSION_1)).thenReturn(null);
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        final List<String> changedIds = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        assertTrue(changedIds.isEmpty());
        verify(entityManager, never()).merge(any());
        verify(rotaProcessLogService).saveRotaProcessLog(any());
    }

    @Test
    void shouldSkipAndLogWhenAllocatedListingsPresentForSession() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));
        final CourtScheduleJudiciary existingAssignment = buildEntity(SESSION_1, JUDICIARY_1);

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(JUDICIARY_1)).thenReturn(List.of(existingAssignment));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(SESSION_1)).thenReturn(new CourtSchedule());
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(SESSION_1)))
                .thenReturn(Map.of(SESSION_1, 2));
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        final List<String> changedIds = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        assertTrue(changedIds.isEmpty());
        verify(entityManager, never()).merge(any());
        verify(rotaProcessLogService).saveRotaProcessLog(any());
    }

    @Test
    void shouldSkipAndLogWhenJudiciaryIsNotAssignedToSession() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));
        final CourtScheduleJudiciary existingAssignment = buildEntity(SESSION_1, JUDICIARY_1);

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(JUDICIARY_1)).thenReturn(List.of(existingAssignment));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(SESSION_1)).thenReturn(new CourtSchedule());
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(SESSION_1))).thenReturn(Map.of());
        when(courtScheduleJudiciaryRepository.findBy(any())).thenReturn(null);
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        final List<String> changedIds = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        assertTrue(changedIds.isEmpty());
        verify(entityManager, never()).merge(any());
        verify(rotaProcessLogService).saveRotaProcessLog(any());
    }


    @Test
    void shouldReturnEmptyListWhenCourtScheduleIdsIsNull() {
        final List<String> result = judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(null);

        assertTrue(result.isEmpty());
        verify(courtScheduleJudiciaryRepository, never()).deleteAllAssignmentsForCourtScheduleIds(any());
    }

    @Test
    void shouldCatchExceptionDuringMergeAndContinueProcessing() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));
        final CourtScheduleJudiciary existingAssignment = buildEntity(SESSION_1, JUDICIARY_1);

        when(courtScheduleJudiciaryRepository.findByJudiciaryId(JUDICIARY_1)).thenReturn(List.of(existingAssignment));
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(SESSION_1)).thenReturn(new CourtSchedule());
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(SESSION_1))).thenReturn(Map.of());
        when(courtScheduleJudiciaryRepository.findBy(any())).thenReturn(existingAssignment);
        when(entityManager.merge(existingAssignment)).thenThrow(new RuntimeException("DB connection lost"));
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        final List<String> result = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID);

        assertTrue(result.isEmpty());
        verify(entityManager).flush();
        verify(entityManager, never()).remove(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenJudiciaryIdIsBlank() {
        final Map<String, List<String>> request = Map.of("  ", List.of(SESSION_1));
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID, false));
    }


    @Test
    void shouldBypassJudiciaryAndListingChecksWhenSkipValidationsIsTrue() {
        final Map<String, List<String>> request = Map.of(JUDICIARY_1, List.of(SESSION_1));

        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(SESSION_1)).thenReturn(new CourtSchedule());
        when(courtScheduleJudiciaryRepository.findBy(any())).thenReturn(null);
        when(judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(anyList())).thenReturn(Map.of());
        when(judiciaryChangeDetectionService.findChangedCourtScheduleIds(any(), any())).thenReturn(List.of());

        final List<String> result = judiciaryUnassignmentService.unassignJudiciary(request, EXECUTION_ID, true);

        assertTrue(result.isEmpty());
        verify(courtScheduleJudiciaryRepository, never()).findByJudiciaryId(anyString());
        verify(allocatedListingService, never()).getAllocatedListingsByCourtScheduleId(anyList());
        verify(entityManager, never()).merge(any());
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }


    private CourtScheduleJudiciary buildEntity(final String courtScheduleId, final String judiciaryId) {
        return CourtScheduleJudiciary.CourtScheduleJudiciaryBuilder.courtScheduleJudiciary()
                .withId(new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId))
                .withTitle("HHJ")
                .withForenames("Test")
                .withSurname("Judge")
                .withEmail("test@example.com")
                .withJudiciaryType("CHAIR")
                .withIsBenchChairman(false)
                .withIsDeputy(false)
                .withActive(true)
                .build();
    }
}