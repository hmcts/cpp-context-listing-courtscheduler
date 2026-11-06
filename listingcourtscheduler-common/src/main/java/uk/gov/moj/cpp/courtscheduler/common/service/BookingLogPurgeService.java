package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.repository.BookingLogRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retention of booking_log (LPT-2611, LPT-2633), run by the nightly purge after expired holds
 * are released.
 */
@Service
public class BookingLogPurgeService {

    @Inject
    private BookingLogRepository bookingLogRepository;

    // How long booking_log keeps a superseded row before the nightly purge deletes it.
    @Value("${courtscheduler.booking-log.retention-days:60}")
    private int retentionDays;

    /**
     * Deletes booking_log rows older than the retention period that a later event of the same
     * booking supersedes. Each booking's latest event is kept (see {@link BookingLogRepository}).
     *
     * @return the number of rows deleted
     */
    @Transactional
    public int purge() {
        return bookingLogRepository.deleteSupersededRecordedBefore(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
    }
}
