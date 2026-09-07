package uk.gov.moj.cpp.courtscheduler.api.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurgeExpiredReservedSessionsTaskTest {

    @Mock
    private AllocatedListingService allocatedListingService;

    @InjectMocks
    private PurgeExpiredReservedSessionsTask task;

    @Test
    void shouldPurgeAndMarkExecutionCompleted() {
        when(allocatedListingService.purgeExpiredReservedSessions()).thenReturn(3);

        final ExecutionInfo input = executionInfo()
                .withAssignedTaskName("PURGE_EXPIRED_RESERVED_SESSIONS_TASK")
                .withExecutionStatus(ExecutionStatus.STARTED)
                .build();

        final ExecutionInfo result = task.execute(input);

        verify(allocatedListingService).purgeExpiredReservedSessions();
        assertEquals(ExecutionStatus.COMPLETED, result.getExecutionStatus());
    }
}
