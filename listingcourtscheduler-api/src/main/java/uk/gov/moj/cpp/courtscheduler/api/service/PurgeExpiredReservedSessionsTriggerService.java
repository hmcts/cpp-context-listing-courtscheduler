package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.time.ZonedDateTime.now;
import static uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo.executionInfo;

import uk.gov.hmcts.cp.taskmanager.domain.ExecutionInfo;
import uk.gov.hmcts.cp.taskmanager.domain.ExecutionStatus;
import uk.gov.hmcts.cp.taskmanager.service.ExecutionService;
import uk.gov.moj.cpp.courtscheduler.api.task.PurgeExpiredReservedSessionsTask;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires the daily purge of expired, unconfirmed {@code allocated_listings} reservations
 * (LPT-2433) at 01:00 UTC by enqueuing a {@link PurgeExpiredReservedSessionsTask} job.
 *
 * <p>task-manager-service's worker-locked {@code jobs} table means concurrent replicas don't
 * double-purge in any way that matters, even though every replica's own {@code @Scheduled}
 * trigger fires at the same instant and each enqueues its own job row: the purge itself is
 * idempotent (see {@code AllocatedListingService#purgeExpiredReservedSessions()}), so a second
 * run just finds nothing left to delete.</p>
 */
@Service
public class PurgeExpiredReservedSessionsTriggerService {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeExpiredReservedSessionsTriggerService.class);
    private static final String TASK_NAME = "PURGE_EXPIRED_RESERVED_SESSIONS_TASK";

    @Inject
    private ExecutionService executionService;

    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    @Transactional
    public void triggerDailyPurge() {
        LOG.info("courtscheduler.purge-expired-reserved-sessions daily trigger firing");

        final JsonObject jobData = Json.createObjectBuilder().build();
        final ExecutionInfo executionInfo = executionInfo()
                .withJobData(jobData)
                .withAssignedTaskName(TASK_NAME)
                .withAssignedTaskStartTime(now())
                .withExecutionStatus(ExecutionStatus.STARTED)
                .withShouldRetry(false)
                .build();

        try {
            executionService.executeWith(executionInfo);
            LOG.info("courtscheduler.purge-expired-reserved-sessions daily trigger enqueued job");
        } catch (Exception e) {
            LOG.error("courtscheduler.purge-expired-reserved-sessions daily trigger failed to enqueue job: {}",
                    Encode.forJava(e.getMessage()), e);
        }
    }
}
