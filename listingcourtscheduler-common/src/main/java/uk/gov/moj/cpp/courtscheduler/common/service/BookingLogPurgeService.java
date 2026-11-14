package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.repository.BookingLogRepository;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clean-up of booking_log (LPT-2611, LPT-2633), run by the rota clean-up after it deletes old
 * sessions.
 */
@Service
public class BookingLogPurgeService {

    @Inject
    private BookingLogRepository bookingLogRepository;

    /**
     * Deletes the booking_log rows of sessions that no longer exist (see {@link BookingLogRepository}).
     *
     * @return the number of rows deleted
     */
    @Transactional
    public int deleteRowsOfDeletedSessions() {
        return bookingLogRepository.deleteRowsOfDeletedSessions();
    }
}
