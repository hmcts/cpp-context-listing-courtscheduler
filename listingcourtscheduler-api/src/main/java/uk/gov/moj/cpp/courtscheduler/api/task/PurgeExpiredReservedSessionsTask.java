package uk.gov.moj.cpp.courtscheduler.api.task;

import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus.COMPLETED;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.service.task.ExecutableTask;
import uk.gov.hmcts.cp.taskmanager.service.task.Task;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes the daily purge of expired, unconfirmed {@code allocated_listings} reservations
 * (LPT-2433) — the same {@link AllocatedListingService#purgeExpiredReservedSessions()} the
 * {@code application/vnd.purge-expired-reserved-session+json} {@code POST /sessions} endpoint
 * calls, run here via uk.gov.hmcts.cp:task-manager-service instead of an HTTP self-call so that,
 * when there are multiple replicas, only one worker claims the job row rather than every replica
 * racing to purge the same rows. See {@code PurgeExpiredReservedSessionsTriggerService} for the
 * daily 01:00 UTC cron trigger that enqueues this task.
 */
@Task("PURGE_EXPIRED_RESERVED_SESSIONS_TASK")
@Component
public class PurgeExpiredReservedSessionsTask implements ExecutableTask {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeExpiredReservedSessionsTask.class);

    @Inject
    private AllocatedListingService allocatedListingService;

    @Override
    public ExecutionInfo execute(final ExecutionInfo executionInfo) {
        LOG.info("courtscheduler.purge-expired-reserved-sessions job started [job {}]", executionInfo);
        final int purged = allocatedListingService.purgeExpiredReservedSessions();
        LOG.info("courtscheduler.purge-expired-reserved-sessions job purged {} allocated listing(s)", purged);

        return executionInfo().from(executionInfo)
                .withExecutionStatus(COMPLETED)
                .build();
    }
}
