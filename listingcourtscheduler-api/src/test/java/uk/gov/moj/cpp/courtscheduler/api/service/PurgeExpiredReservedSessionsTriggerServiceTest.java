package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

@ExtendWith(MockitoExtension.class)
class PurgeExpiredReservedSessionsTriggerServiceTest {

    @Mock
    private ExecutionService executionService;

    @InjectMocks
    private PurgeExpiredReservedSessionsTriggerService triggerService;

    @Test
    void shouldEnqueuePurgeTaskWithExpectedExecutionInfo() {
        triggerService.triggerDailyPurge();

        final ArgumentCaptor<ExecutionInfo> captor = ArgumentCaptor.forClass(ExecutionInfo.class);
        verify(executionService).executeWith(captor.capture());

        final ExecutionInfo executionInfo = captor.getValue();
        assertEquals("PURGE_EXPIRED_RESERVED_SESSIONS_TASK", executionInfo.getAssignedTaskName());
        assertEquals(ExecutionStatus.STARTED, executionInfo.getExecutionStatus());
        assertFalse(executionInfo.isShouldRetry());
        assertNotNull(executionInfo.getAssignedTaskStartTime());
        assertNotNull(executionInfo.getJobData());
    }

    @Test
    void shouldNotPropagateWhenExecutionServiceFails() {
        doThrow(new RuntimeException("db down")).when(executionService).executeWith(any());

        assertDoesNotThrow(triggerService::triggerDailyPurge);
    }

    /**
     * Exercises the {@code @Scheduled} cron expression itself (not just the trigger's own logic)
     * so a typo like "0 0 13 * * *" that would fire at 1pm instead of 1am gets caught — calling
     * the method directly never touches the cron field at all.
     */
    @Test
    void cronShouldFireAtOneAmUtc() throws NoSuchMethodException {
        final Method method = PurgeExpiredReservedSessionsTriggerService.class.getMethod("triggerDailyPurge");
        final Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertEquals("0 0 1 * * *", scheduled.cron());
        assertEquals("UTC", scheduled.zone());

        final CronExpression cronExpression = CronExpression.parse(scheduled.cron());
        final ZonedDateTime from = LocalDateTime.of(2026, 9, 4, 12, 0).atZone(ZoneOffset.UTC);
        final ZonedDateTime next = cronExpression.next(from);

        assertNotNull(next);
        assertEquals(1, next.getHour());
        assertEquals(0, next.getMinute());
        assertEquals(5, next.getDayOfMonth());
    }
}
