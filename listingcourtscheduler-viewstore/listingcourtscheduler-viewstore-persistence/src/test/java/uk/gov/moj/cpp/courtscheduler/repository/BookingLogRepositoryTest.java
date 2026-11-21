package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.CONFIRMED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.EXPIRED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.REBOOKED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.REPICK_RELEASED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState.RESERVED;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingLog;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;

class BookingLogRepositoryTest extends AbstractRepositoryTest {
    private static final String CS_1 = "cs-1";
    private static final String CS_2 = "cs-2";

    private static final String BOOKING_A = "booking-a";
    private static final String BOOKING_B = "booking-b";
    private static final String DELETED_SESSION = "deleted-session";

    @Autowired
    private BookingLogRepository bookingLogRepository;

    @Autowired
    private CourtScheduleRepository courtScheduleRepository;

    @Autowired
    private EntityManager entityManager;

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
    void deleteRowsOfDeletedSessionsRemovesEveryRowWhoseSessionIsGoneLatestEventIncluded() {
        final String liveSession = persistSessionOn(LocalDate.now().minusDays(10));
        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, DELETED_SESSION)));
        bookingLogRepository.record(CONFIRMED, List.of(hold(BOOKING_A, DELETED_SESSION)));
        bookingLogRepository.record(CONFIRMED, List.of(hold(BOOKING_B, liveSession)));
        bookingLogRepository.record(EXPIRED, List.of(hold("booking-no-session", null)));

        final int deleted = bookingLogRepository.deleteRowsOfDeletedSessions();

        assertEquals(2, deleted);
        assertEquals(List.of(), bookingLogRepository.findLatestEvent(BOOKING_A));
        assertEquals(CONFIRMED, bookingLogRepository.findLatestEvent(BOOKING_B).get(0).getState());
        assertEquals(EXPIRED, bookingLogRepository.findLatestEvent("booking-no-session").get(0).getState());
    }

    // ─── the native insert writes every column ─────────────────────────────────

    @Test
    void theInsertNamesEveryColumnOfTheTable() {
        @SuppressWarnings("unchecked")
        final List<String> tableColumns = entityManager.createNativeQuery(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'booking_log'").getResultList();

        final String insert = BookingLogRepository.INSERT_SQL;
        final String columnList = insert.substring(insert.indexOf("booking_log (") + "booking_log (".length(), insert.indexOf(')'));
        assertEquals(new HashSet<>(tableColumns),
                Arrays.stream(columnList.split(",")).map(String::trim).collect(Collectors.toSet()));
    }

    @Test
    void everyFieldOfAHoldIsRecorded() {
        final AllocatedListing hold = hold(BOOKING_A, CS_1);
        hold.setHearingStartTime(Instant.parse("2026-10-26T10:00:00.123456Z"));
        hold.setExpiresAt(LocalDate.of(2026, 10, 26));

        bookingLogRepository.record(RESERVED, List.of(hold));

        final BookingLog row = bookingLogRepository.findAll().get(0);
        assertEquals(BOOKING_A, row.getBookingId());
        assertEquals(RESERVED, row.getState());
        assertEquals(CS_1, row.getCourtScheduleId());
        assertNull(row.getHearingId());
        assertEquals("B01LY00", row.getOucode());
        assertEquals(1, row.getCourtRoomId());
        assertEquals("REM", row.getRotaBusinessType());
        assertEquals(Instant.parse("2026-10-26T10:00:00.123456Z"), row.getHearingStartTime());
        assertEquals(20, row.getDuration());
        assertEquals(LocalDate.of(2026, 10, 26), row.getExpiresAt());
        assertTrue(UUID.fromString(row.getId()) != null && UUID.fromString(row.getEventId()) != null);
    }

    @Test
    void aConfirmedListingIsRecordedWithItsHearingAndNoExpiry() {
        final AllocatedListing listed = hold(BOOKING_A, CS_1);
        listed.setHearingId("hearing-1");

        bookingLogRepository.record(CONFIRMED, List.of(listed));

        final BookingLog row = bookingLogRepository.findAll().get(0);
        assertEquals("hearing-1", row.getHearingId());
        assertNull(row.getExpiresAt());
    }

    @Test
    void aBookingThatHoldsNothingIsRecordedWithoutASession() {
        final AllocatedListing nothingHeld = new AllocatedListing();
        nothingHeld.setBookingId(BOOKING_A);

        bookingLogRepository.record(EXPIRED, List.of(nothingHeld));

        final BookingLog row = bookingLogRepository.findLatestEvent(BOOKING_A).get(0);
        assertEquals(EXPIRED, row.getState());
        assertNull(row.getCourtScheduleId());
    }

    @Test
    void rowsWithoutABookingAreNotRecorded() {
        bookingLogRepository.record(RESERVED, List.of(hold(null, CS_1)));

        assertEquals(List.of(), bookingLogRepository.findAll());
    }

    // ─── recorded_at comes from the database, never before the booking's previous event ─────

    @Test
    void aBookingsFirstEventIsRecordedAtTheDatabaseTime() {
        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_1)));

        final Instant recordedAt = bookingLogRepository.findAll().get(0).getRecordedAt();
        assertTrue(Duration.between(recordedAt, Instant.now()).abs().compareTo(Duration.ofMinutes(1)) < 0,
                recordedAt + " should be close to now");
    }

    @Test
    void aRePicksTwoEventsInOneTransactionAreOrdered() {
        bookingLogRepository.record(REPICK_RELEASED, List.of(hold(BOOKING_A, CS_1)));
        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_2)));

        final List<BookingLog> latest = bookingLogRepository.findLatestEvent(BOOKING_A);
        assertEquals(1, latest.size());
        assertEquals(RESERVED, latest.get(0).getState());
        assertEquals(CS_2, latest.get(0).getCourtScheduleId());
    }

    @Test
    void anEventIsRecordedAfterAPreviousOneWrittenByAPodWhoseClockRunsAhead() {
        final Instant ahead = Instant.now().plus(Duration.ofHours(1));
        bookingLogRepository.saveAndFlush(BookingLog.fromHold(UUID.randomUUID().toString(), RESERVED, hold(BOOKING_A, CS_1), ahead));

        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1)));

        final BookingLog latest = bookingLogRepository.findLatestEvent(BOOKING_A).get(0);
        assertEquals(EXPIRED, latest.getState());
        assertTrue(latest.getRecordedAt().isAfter(ahead), latest.getRecordedAt() + " should be after " + ahead);
    }

    @Test
    void anotherBookingsEventsDoNotHoldBackItsTime() {
        bookingLogRepository.saveAndFlush(BookingLog.fromHold(UUID.randomUUID().toString(), RESERVED,
                hold(BOOKING_B, CS_1), Instant.now().plus(Duration.ofHours(1))));

        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_2)));

        final Instant recordedAt = bookingLogRepository.findLatestEvent(BOOKING_A).get(0).getRecordedAt();
        assertTrue(recordedAt.isBefore(Instant.now().plus(Duration.ofMinutes(1))), recordedAt + " should not follow booking B");
    }

    @Test
    void anEventWithSeveralSessionsIsReadBackWhole() {
        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_1)));
        bookingLogRepository.record(REBOOKED, List.of(hold(BOOKING_A, CS_1), hold(BOOKING_A, CS_2), hold(BOOKING_A, "cs-3")));

        final List<BookingLog> latest = bookingLogRepository.findLatestEvent(BOOKING_A);
        assertEquals(3, latest.size());
        latest.forEach(row -> assertEquals(REBOOKED, row.getState()));
        final List<Instant> times = latest.stream().map(BookingLog::getRecordedAt).toList();
        assertEquals(times.stream().sorted(Comparator.naturalOrder()).toList(), times);
    }

    // ─── the database time is stored exactly like the JVM time it replaces ─────

    @Test
    void recordedAtIsATimestampWithTimeZone() {
        final Object type = entityManager.createNativeQuery(
                "SELECT data_type FROM information_schema.columns WHERE table_name = 'booking_log' AND column_name = 'recorded_at'")
                .getSingleResult();

        assertEquals("timestamp with time zone", type);
    }

    @Test
    void theDatabaseTimeIsTheSameInstantWhateverTheSessionTimeZone() {
        // A timestamp without time zone would take clock_timestamp() in the session's zone and
        // come back hours out; a timestamp with time zone is an instant whatever the zone.
        entityManager.createNativeQuery("SET LOCAL TIME ZONE 'Asia/Tokyo'").executeUpdate();

        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_1)));

        final Instant recordedAt = bookingLogRepository.findLatestEvent(BOOKING_A).get(0).getRecordedAt();
        assertTrue(Duration.between(recordedAt, Instant.now()).abs().compareTo(Duration.ofMinutes(1)) < 0,
                recordedAt + " should be close to " + Instant.now());
    }

    @Test
    void theDatabaseTimeIsStoredLikeTheJvmTimeItReplaces() {
        // The JVM wrote Instant.now() truncated to microseconds; the database now writes clock_timestamp().
        final Instant jvmTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
        bookingLogRepository.saveAndFlush(BookingLog.fromHold(UUID.randomUUID().toString(), RESERVED, hold(BOOKING_B, CS_1), jvmTime));
        bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_1)));

        @SuppressWarnings("unchecked")
        final List<Object[]> rows = entityManager.createNativeQuery(
                "SELECT booking_id, pg_typeof(recorded_at)::text, extract(timezone FROM recorded_at) FROM booking_log ORDER BY booking_id")
                .getResultList();
        assertEquals(2, rows.size());
        rows.forEach(row -> {
            assertEquals("timestamp with time zone", row[1], row[0] + " type");
            assertEquals(0, ((Number) row[2]).intValue(), row[0] + " should be read in UTC");
        });

        final Instant dbTime = bookingLogRepository.findLatestEvent(BOOKING_A).get(0).getRecordedAt();
        assertEquals(dbTime.truncatedTo(ChronoUnit.MICROS), dbTime, "microsecond precision, like the JVM time");
        assertEquals(jvmTime, bookingLogRepository.findLatestEvent(BOOKING_B).get(0).getRecordedAt());
    }

    @Test
    void theFloorAddsExactlyOneMicrosecond() {
        final Instant ahead = Instant.now().plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MICROS);
        bookingLogRepository.saveAndFlush(BookingLog.fromHold(UUID.randomUUID().toString(), RESERVED, hold(BOOKING_A, CS_1), ahead));

        bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1)));

        assertEquals(ahead.plus(1, ChronoUnit.MICROS), bookingLogRepository.findLatestEvent(BOOKING_A).get(0).getRecordedAt());
    }

    @Test
    void anEventCommittedAfterAnotherIsTheLatest() {
        try {
            bookingLogRepository.record(RESERVED, List.of(hold(BOOKING_A, CS_1)));
            commitAndStartAnew();
            bookingLogRepository.record(EXPIRED, List.of(hold(BOOKING_A, CS_1)));
            commitAndStartAnew();

            assertEquals(EXPIRED, bookingLogRepository.findLatestEvent(BOOKING_A).get(0).getState());
        } finally {
            bookingLogRepository.deleteAll();
            TestTransaction.flagForCommit();
        }
    }

    private static void commitAndStartAnew() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private String persistSessionOn(final LocalDate sessionDate) {
        final CourtSchedule session = random(CourtSchedule.class);
        session.setSessionDate(sessionDate);
        courtScheduleRepository.save(session);
        return session.getCourtScheduleId();
    }

    private static Set<String> eventIdsOf(final List<BookingLog> rows, final String bookingId) {
        return rows.stream()
                .filter(row -> bookingId.equals(row.getBookingId()))
                .map(BookingLog::getEventId)
                .collect(Collectors.toSet());
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
