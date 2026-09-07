package uk.gov.moj.cpp.courtscheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support, required both by
 * {@code PurgeExpiredReservedSessionsScheduler}'s daily cron trigger and by
 * uk.gov.hmcts.cp:task-manager-service's own internal JobExecutor, which polls the
 * {@code jobs} table for unassigned work.
 */
@Configuration
@EnableScheduling
public class TaskManagerConfig {
}
