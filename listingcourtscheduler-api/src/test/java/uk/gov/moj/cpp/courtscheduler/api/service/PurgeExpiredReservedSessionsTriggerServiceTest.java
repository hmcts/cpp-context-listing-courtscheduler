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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.PropertyPlaceholderHelper;

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
     * The {@code @Scheduled} annotation now holds property placeholders
     * ({@code ${courtscheduler.purge-expired-reserved-sessions.cron:...}}), not a literal cron
     * string, so this asserts the placeholder points at the right property key and carries the
     * correct fallback default — resolved the same way Spring would resolve it with no matching
     * property present (via {@link PropertyPlaceholderHelper}, mirroring the annotation's own
     * {@code ":"} value-separator/{@code true} ignore-unresolvable config).
     */
    @Test
    void scheduledAnnotationShouldReferenceExpectedPropertyKeysWithCorrectDefaults() throws NoSuchMethodException {
        final Method method = PurgeExpiredReservedSessionsTriggerService.class.getMethod("triggerDailyPurge");
        final Scheduled scheduled = method.getAnnotation(Scheduled.class);

        final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
        final String resolvedCron = placeholderHelper.replacePlaceholders(scheduled.cron(), placeholder -> null);
        final String resolvedZone = placeholderHelper.replacePlaceholders(scheduled.zone(), placeholder -> null);

        assertEquals("courtscheduler.purge-expired-reserved-sessions.cron", propertyKeyOf(scheduled.cron()));
        assertEquals("courtscheduler.purge-expired-reserved-sessions.cron-zone", propertyKeyOf(scheduled.zone()));
        assertEquals("0 0 1 * * *", resolvedCron);
        assertEquals("UTC", resolvedZone);
    }

    /**
     * Loads the real {@code application.yaml} (not a stub) so a typo in the property key or its
     * value there — e.g. accidentally shipping "0 0 13 * * *" (1pm) — fails this test, not just a
     * silent misconfiguration discovered in production.
     */
    @Test
    void applicationYamlShouldConfigureCronToFireAtOneAmUtc() throws Exception {
        final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        final List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yaml"));
        final PropertySource<?> propertySource = sources.get(0);

        final Object cronPlaceholder = propertySource.getProperty("courtscheduler.purge-expired-reserved-sessions.cron");
        final Object zonePlaceholder = propertySource.getProperty("courtscheduler.purge-expired-reserved-sessions.cron-zone");
        assertNotNull(cronPlaceholder, "courtscheduler.purge-expired-reserved-sessions.cron must be set in application.yaml");
        assertNotNull(zonePlaceholder, "courtscheduler.purge-expired-reserved-sessions.cron-zone must be set in application.yaml");

        // application.yaml wraps both in ${ENV_VAR:default} — resolve with no env var present,
        // exactly as a fresh environment with neither var set would see them.
        final PropertyPlaceholderHelper placeholderHelper = new PropertyPlaceholderHelper("${", "}", ":", null, true);
        final String cron = placeholderHelper.replacePlaceholders(cronPlaceholder.toString(), placeholder -> null);
        final String zone = placeholderHelper.replacePlaceholders(zonePlaceholder.toString(), placeholder -> null);

        final CronExpression cronExpression = CronExpression.parse(cron);
        final ZonedDateTime from = LocalDateTime.of(2026, 9, 4, 12, 0).atZone(ZoneId.of(zone));
        final ZonedDateTime next = cronExpression.next(from);

        assertNotNull(next);
        assertEquals(1, next.getHour());
        assertEquals(0, next.getMinute());
        assertEquals(5, next.getDayOfMonth());
    }

    private static String propertyKeyOf(final String placeholder) {
        final int start = placeholder.indexOf("${") + 2;
        final int separator = placeholder.indexOf(':', start);
        final int end = separator == -1 ? placeholder.indexOf('}', start) : separator;
        return placeholder.substring(start, end);
    }
}
