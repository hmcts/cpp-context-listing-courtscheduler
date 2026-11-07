package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.EXPIRED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.REBOOKED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.RESERVED;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingLog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingLogRepositoryTest extends AbstractRepositoryTest {
    private static final String CS_1 = "cs-1";
    private static final String CS_2 = "cs-2";

    private static final String BOOKING_A = "booking-a";
    private static final String BOOKING_B = "booking-b";
    private static final Instant CUTOFF = Instant.now().minus(30, ChronoUnit.DAYS);

    @Autowired
    private BookingLogRepository bookingLogRepository;

    @AfterEach
    void tearDown() {
        bookingLogRepository.deleteAll();
    }

    @Test
    void recordsOneEventPerBooking() {
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1), hold(BOOKING_A, CS_2), hold(BOOKING_B, "cs-3")));

        final List<BookingLog> all = bookingLogRepository.findAll();
        assertEquals(3, all.size());
        final Set<String> eventsOfA = eventIdsOf(all, BOOKING_A);
        final Set<String> eventsOfB = eventIdsOf(all, BOOKING_B);
        assertEquals(1, eventsOfA.size());
        assertEquals(1, eventsOfB.size());
        assertNotEquals(eventsOfA, eventsOfB);
        all.forEach(row -> assertEquals(EXPIRED, row.getState()));
    }

    @Test
    void latestEventReturnsOnlyTheNewestEventsRows() {
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1), hold(BOOKING_A, CS_2)));
        bookingLogRepository.record(REBOOKED, List.of(hold(BOOKING_A, CS_1), hold(BOOKING_A, CS_2)));

        final List<BookingLog> latest = bookingLogRepository.findLatestEvent(BOOKING_A);

        assertEquals(2, latest.size());
        latest.forEach(row -> assertEquals(REBOOKED, row.getState()));
    }

    @Test
    void latestEventOfAnUnknownBookingIsEmpty() {
        assertEquals(List.of(), bookingLogRepository.findLatestEvent("unknown"));
    }

    @Test
    void deleteSupersededRemovesOnlyOlderRowsWithALaterEvent() {
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1)));
        age(bookingLogRepository.findAll(), 100);
        bookingLogRepository.record(REBOOKED, List.of(hold(BOOKING_A, CS_1)));
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_B, CS_2)));

        final int deleted = bookingLogRepository.deleteSupersededRecordedBefore(CUTOFF);

        assertEquals(1, deleted);
        assertEquals(2, bookingLogRepository.findAll().size());
        assertEquals(REBOOKED, bookingLogRepository.findLatestEvent(BOOKING_A).get(0).getState());
    }

    @Test
    void deleteSupersededKeepsEachBookingsLatestEventHoweverOld() {
        // the status check reads the latest event to say why a booking has no rows, and a draft
        // can carry a booking for longer than the retention period
        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_1)));
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1)));
        age(bookingLogRepository.findAll(), 200);

        final int deleted = bookingLogRepository.deleteSupersededRecordedBefore(CUTOFF);

        assertEquals(1, deleted);
        final List<BookingLog> latest = bookingLogRepository.findLatestEvent(BOOKING_A);
        assertEquals(1, latest.size());
        assertEquals(EXPIRED, latest.get(0).getState());
    }

    private void age(final List<BookingLog> rows, final int days) {
        rows.forEach(row -> {
            row.setRecordedAt(row.getRecordedAt().minus(days, ChronoUnit.DAYS));
            bookingLogRepository.save(row);
        });
    }

    private static Set<String> eventIdsOf(final List<BookingLog> rows, final String bookingId) {
        return rows.stream()
                .filter(row -> bookingId.equals(row.getBookingId()))
                .map(BookingLog::getEventId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static AllocatedListing hold(final String bookingId, final String courtScheduleId) {
        final AllocatedListing row = new AllocatedListing();
        row.setId(UUID.randomUUID().toString());
        row.setBookingId(bookingId);
        row.setCourtScheduleId(courtScheduleId);
        row.setOucode("B01LY00");
        row.setCourtRoomId(1);
        row.setRotaBusinessType("REM");
        row.setDuration(20);
        row.setHearingStartTime(Instant.parse("2026-10-26T10:00:00Z"));
        return row;
    }
}
