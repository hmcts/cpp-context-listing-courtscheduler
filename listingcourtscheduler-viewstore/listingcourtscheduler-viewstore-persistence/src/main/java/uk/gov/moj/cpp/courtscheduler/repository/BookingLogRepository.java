package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingLog;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * The booking log (LPT-2611): what happened to each booking reference, one row per
 * allocated_listings row touched. Append-only apart from the retention clean-up.
 */
@Repository
public interface BookingLogRepository extends JpaRepository<BookingLog, String> {

    /**
     * Records {@code state} against every row, one event per booking: the rows of one booking
     * share an event id, so the booking's state at that moment can be read back as a set.
     * Rows without a booking id are not bookings and are skipped.
     */
    default void record(final BookingState state, final Collection<AllocatedListing> rows) {
        final Instant now = BookingLog.nextRecordedAt();
        final Map<String, List<AllocatedListing>> byBooking = rows.stream()
                .filter(row -> Objects.nonNull(row.getBookingId()))
                .collect(Collectors.groupingBy(AllocatedListing::getBookingId, LinkedHashMap::new, Collectors.toList()));
        byBooking.values().forEach(bookingRows -> {
            final String eventId = UUID.randomUUID().toString();
            saveAll(bookingRows.stream().map(row -> BookingLog.fromHold(eventId, state, row, now)).toList());
        });
    }

    /** The rows of the booking's most recent event; empty when nothing was ever logged for it. */
    @Query(value = """
            SELECT * FROM booking_log
             WHERE event_id = (SELECT event_id FROM booking_log
                                WHERE booking_id = :bookingId
                                ORDER BY recorded_at DESC LIMIT 1)
             ORDER BY recorded_at
            """, nativeQuery = true)
    List<BookingLog> findLatestEvent(@Param("bookingId") String bookingId);

    /**
     * Deletes history recorded before {@code cutoff}, but never a booking's latest event: the
     * status check reads it to say why a booking has no rows, and a draft can carry a booking for
     * longer than the retention period.
     */
    @Modifying
    @Query(value = """
            DELETE FROM booking_log b
             WHERE b.recorded_at < :cutoff
               AND b.event_id <> (SELECT l.event_id FROM booking_log l
                                   WHERE l.booking_id = b.booking_id
                                   ORDER BY l.recorded_at DESC LIMIT 1)
            """, nativeQuery = true)
    int deleteRecordedBefore(@Param("cutoff") Instant cutoff);
}
