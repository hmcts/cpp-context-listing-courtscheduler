package uk.gov.moj.cpp.courtscheduler.persist.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A log row is the allocated_listings row as it was, expiry included. No database needed. */
class BookingLogTest {
    private static final String BK_1 = "bk-1";
    private static final String EVENT_1 = "event-1";

    @Test
    void aHoldsExpiryIsRecorded() {
        final AllocatedListing hold = new AllocatedListing();
        hold.setBookingId(BK_1);
        hold.setExpiresAt(LocalDate.of(2026, 10, 24));

        final BookingLog log = BookingLog.fromHold(EVENT_1, BookingState.RESERVED, hold, Instant.now());

        assertEquals(LocalDate.of(2026, 10, 24), log.getExpiresAt());
    }

    @Test
    void aConfirmedListingHasNoExpiry() {
        final AllocatedListing listed = new AllocatedListing();
        listed.setBookingId(BK_1);
        listed.setHearingId("hearing-1");

        final BookingLog log = BookingLog.fromHold(EVENT_1, BookingState.CONFIRMED, listed, Instant.now());

        assertNull(log.getExpiresAt());
    }

    @Test
    void eachRowHasItsOwnRandomUuid() {
        final AllocatedListing hold = new AllocatedListing();
        hold.setId("al-1");
        hold.setBookingId(BK_1);

        final BookingLog first = BookingLog.fromHold(EVENT_1, BookingState.RESERVED, hold, Instant.now());
        final BookingLog second = BookingLog.fromHold(EVENT_1, BookingState.RESERVED, hold, Instant.now());

        assertDoesNotThrow(() -> UUID.fromString(first.getId()));
        assertNotEquals(first.getId(), second.getId());
    }

    @Test
    void theRecordedTimeIsWhenTheRowWasAppended() {
        final AllocatedListing hold = new AllocatedListing();
        hold.setBookingId(BK_1);
        final Instant at = Instant.parse("2026-10-25T10:15:30.123456Z");

        assertEquals(at, BookingLog.fromHold(EVENT_1, BookingState.EXPIRED, hold, at).getRecordedAt());
    }
}
