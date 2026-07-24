package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryUnassignmentServiceTest {

    @InjectMocks
    private JudiciaryUnassignmentService judiciaryUnassignmentService;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Test
    void shouldDeleteAllJudiciaryAssignmentsForProvidedCourtScheduleIds() {
        final List<String> courtScheduleIds = List.of("schedule-1", "schedule-2");
        when(courtScheduleJudiciaryRepository.deleteAllAssignmentsForCourtScheduleIds(courtScheduleIds)).thenReturn(3);

        final int deleted = judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(courtScheduleIds);

        assertEquals(3, deleted);
        verify(courtScheduleJudiciaryRepository).deleteAllAssignmentsForCourtScheduleIds(courtScheduleIds);
    }

    @Test
    void shouldReturnZeroAndSkipDeleteWhenCourtScheduleIdsAreEmpty() {
        final int deleted = judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(List.of());

        assertEquals(0, deleted);
        verify(courtScheduleJudiciaryRepository, never()).deleteAllAssignmentsForCourtScheduleIds(org.mockito.ArgumentMatchers.any());
    }
}
