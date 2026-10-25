package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.EXPIRED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.REBOOKED;

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

    private static final String BOOKING_A = "booking-a";
    private static final String BOOKING_B = "booking-b";

    @Autowired
    private BookingLogRepository bookingLogRepository;

    @AfterEach
    void tearDown() {
        bookingLogRepository.deleteAll();
    }

    @Test
    void recordsOneEventPerBooking() {
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, "cs-1"), hold(BOOKING_A, "cs-2"), hold(BOOKING_B, "cs-3")));

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
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, "cs-1"), hold(BOOKING_A, "cs-2")));
        bookingLogRepository.record(REBOOKED, List.of(hold(BOOKING_A, "cs-1"), hold(BOOKING_A, "cs-2")));

        final List<BookingLog> latest = bookingLogRepository.findLatestEvent(BOOKING_A);

        assertEquals(2, latest.size());
        latest.forEach(row -> assertEquals(REBOOKED, row.getState()));
    }

    @Test
    void latestEventOfAnUnknownBookingIsEmpty() {
        assertEquals(List.of(), bookingLogRepository.findLatestEvent("unknown"));
    }

    @Test
    void deleteRecordedBeforeRemovesOnlyOlderRows() {
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, "cs-1")));
        final BookingLog old = bookingLogRepository.findAll().get(0);
        old.setRecordedAt(Instant.now().minus(100, ChronoUnit.DAYS));
        bookingLogRepository.save(old);
        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_B, "cs-2")));

        final int deleted = bookingLogRepository.deleteRecordedBefore(Instant.now().minus(90, ChronoUnit.DAYS));

        assertEquals(1, deleted);
        final List<BookingLog> left = bookingLogRepository.findAll();
        assertEquals(1, left.size());
        assertEquals(BOOKING_B, left.get(0).getBookingId());
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
