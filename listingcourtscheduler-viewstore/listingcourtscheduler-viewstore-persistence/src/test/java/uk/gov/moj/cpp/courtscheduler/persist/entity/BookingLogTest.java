package uk.gov.moj.cpp.courtscheduler.persist.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A log row is the allocated_listings row as it was, expiry included. No database needed. */
class BookingLogTest {

    @Test
    void aHoldsExpiryIsRecorded() {
        final AllocatedListing hold = new AllocatedListing();
        hold.setBookingId("bk-1");
        hold.setExpiresAt(LocalDate.of(2026, 10, 24));

        final BookingLog log = BookingLog.fromHold("event-1", BookingState.RESERVED, hold, Instant.now());

        assertEquals(LocalDate.of(2026, 10, 24), log.getExpiresAt());
    }

    @Test
    void aConfirmedListingHasNoExpiry() {
        final AllocatedListing listed = new AllocatedListing();
        listed.setBookingId("bk-1");
        listed.setHearingId("hearing-1");

        final BookingLog log = BookingLog.fromHold("event-1", BookingState.CONFIRMED, listed, Instant.now());

        assertNull(log.getExpiresAt());
    }

    @Test
    void eachRowHasItsOwnRandomUuid() {
        final AllocatedListing hold = new AllocatedListing();
        hold.setId("al-1");
        hold.setBookingId("bk-1");

        final BookingLog first = BookingLog.fromHold("event-1", BookingState.RESERVED, hold, Instant.now());
        final BookingLog second = BookingLog.fromHold("event-1", BookingState.RESERVED, hold, Instant.now());

        assertDoesNotThrow(() -> UUID.fromString(first.getId()));
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void theRecordedTimeIsWhenTheRowWasAppended() {
        final AllocatedListing hold = new AllocatedListing();
        hold.setBookingId("bk-1");
        final Instant at = Instant.parse("2026-10-25T10:15:30.123456Z");

        assertEquals(at, BookingLog.fromHold("event-1", BookingState.EXPIRED, hold, at).getRecordedAt());
    }

    // The latest state is the row with the latest recorded_at, so two events written in the same
    // request (a re-pick's REPICK_RELEASED then RESERVED) must never share a timestamp - and must
    // still differ once Postgres stores them at microsecond precision.
    @Test
    void recordedTimesStrictlyIncreaseAtMicrosecondPrecision() {
        Instant previous = BookingLog.nextRecordedAt();
        for (int i = 0; i < 10_000; i++) {
            final Instant next = BookingLog.nextRecordedAt();
            assertTrue(next.isAfter(previous), next + " should be after " + previous);
            assertEquals(next, next.truncatedTo(ChronoUnit.MICROS));
            previous = next;
        }
    }
}
