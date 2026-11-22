package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingLog;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * The booking log (LPT-2611): what happened to each booking reference, one row per
 * allocated_listings row touched. Append-only apart from the rota clean-up.
 */
@Repository
public interface BookingLogRepository extends JpaRepository<BookingLog, String> {

    /**
     * Appends one row. recorded_at comes from the database, not the JVM: the database clock, and
     * never before the booking's previous event, so a booking's events stay in order whichever pod
     * writes them. A booking's operations change its allocated_listings rows before logging, so a
     * second operation on the same booking waits on those row locks and logs after the first commits.
     * BookingLogRepositoryTest checks the column list against the table.
     */
    String INSERT_SQL = """
            INSERT INTO booking_log (
                id,
                event_id,
                booking_id,
                state,
                court_schedule_id,
                hearing_id,
                oucode,
                court_room_id,
                rota_business_type,
                hearing_start_time,
                duration,
                expires_at,
                recorded_at)
            VALUES (
                :#{#log.id},
                :#{#log.eventId},
                :#{#log.bookingId},
                :#{#log.state.name()},
                :#{#log.courtScheduleId},
                :#{#log.hearingId},
                :#{#log.oucode},
                :#{#log.courtRoomId},
                :#{#log.rotaBusinessType},
                :#{#log.hearingStartTime},
                :#{#log.duration},
                :#{#log.expiresAt},
                GREATEST(clock_timestamp(),
                         (SELECT max(recorded_at) + interval '1 microsecond'
                            FROM booking_log WHERE booking_id = :#{#log.bookingId})))
            """;

    /**
     * Records {@code state} against every row, one event per booking: the rows of one booking
     * share an event id, so the booking's state at that moment can be read back as a set.
     * Rows without a booking id are not bookings and are skipped.
     */
    default void record(final BookingState state, final Collection<AllocatedListing> rows) {
        final Map<String, List<AllocatedListing>> byBooking = rows.stream()
                .filter(row -> Objects.nonNull(row.getBookingId()))
                .collect(Collectors.groupingBy(AllocatedListing::getBookingId, LinkedHashMap::new, Collectors.toList()));
        byBooking.values().forEach(bookingRows -> {
            final String eventId = UUID.randomUUID().toString();
            bookingRows.forEach(row -> insert(BookingLog.fromHold(eventId, state, row)));
        });
    }

    /** Appends one row; see {@link #INSERT_SQL}. */
    @Transactional
    @Modifying
    @Query(value = INSERT_SQL, nativeQuery = true)
    void insert(@Param("log") BookingLog log);

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
     * Deletes the rows on sessions that no longer exist, a booking's latest event included. A row
     * without a session (the release of a booking that held nothing any more) is deleted with the
     * booking's rows on sessions, once none of those sessions exists. Run by the rota clean-up once
     * it has deleted old sessions.
     */
    @Modifying
    @Query(value = """
            DELETE FROM booking_log b
             WHERE (b.court_schedule_id IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM court_schedule cs
                                     WHERE cs.id = b.court_schedule_id))
                OR (b.court_schedule_id IS NULL
                    AND EXISTS (SELECT 1 FROM booking_log o
                                 WHERE o.booking_id = b.booking_id
                                   AND o.court_schedule_id IS NOT NULL)
                    AND NOT EXISTS (SELECT 1 FROM booking_log o
                                      JOIN court_schedule cs ON (cs.id = o.court_schedule_id
                                                                 AND o.booking_id = b.booking_id)))
            """, nativeQuery = true)
    int deleteRowsOfDeletedSessions();
}
