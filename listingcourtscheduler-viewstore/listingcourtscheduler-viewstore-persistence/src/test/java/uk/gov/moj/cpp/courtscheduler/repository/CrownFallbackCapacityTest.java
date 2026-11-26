package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import org.junit.jupiter.api.Test;

/**
 * Whether a Crown fallback session can take the hearing is what its bookings leave of
 * max_duration_mins, not court_schedule.available_duration_mins: bookings no longer keep that
 * counter up to date. No database needed.
 */
class CrownFallbackCapacityTest {

    @Test
    void lacksCapacityWhenItsBookingsLeaveTooLittleWhateverTheCounterSays() {
        final CourtSchedule session = session(360);
        session.setAvailableDuration(360);

        assertTrue(CourtScheduleRepositoryImpl.lacksCapacity(session, 300, 120));
    }

    @Test
    void hasCapacityWhenItsBookingsLeaveRoomWhateverTheCounterSays() {
        final CourtSchedule session = session(360);
        session.setAvailableDuration(0);

        assertFalse(CourtScheduleRepositoryImpl.lacksCapacity(session, 0, 120));
    }

    @Test
    void lacksCapacityWhenTheSessionHasNoMaximum() {
        assertTrue(CourtScheduleRepositoryImpl.lacksCapacity(session(null), 0, 10));
    }

    private static CourtSchedule session(final Integer maxDuration) {
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId("cs-1");
        session.setMaxDuration(maxDuration);
        return session;
    }
}
