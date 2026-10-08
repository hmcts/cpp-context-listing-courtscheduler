package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Repository tests for {@code findSessionForMoveToPastDate}. Covers exact date + jurisdiction match,
 * optional room scoping, and hearingStartTime range-containment (a 10:00 start lands an AM/AD session
 * whose window contains 10:00, a 14:00 start lands a PM/AD session, never the other).
 */
class MoveHearingToPastDateRepositoryTest extends AbstractRepositoryTest {

    private static final String MAGISTRATES = "MAGISTRATES";

    @Autowired
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    void shouldFindMagistratesSessionForExactCourtHouseAndDate() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        final CourtSchedule schedule = createMoveToPastDateSchedule(courtCentreId, sessionDate, MAGISTRATES);

        final Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate, null, MAGISTRATES);

        assertTrue(result.isPresent());
        assertEquals(schedule.getCourtScheduleId(), result.get().getCourtScheduleId());
    }

    @Test
    void shouldReturnEmpty_whenNoSessionMatchesExactDate() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        createMoveToPastDateSchedule(courtCentreId, sessionDate, MAGISTRATES);

        final Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate.plusDays(1), null, MAGISTRATES);

        assertFalse(result.isPresent());
    }

    @Test
    void shouldReturnEmpty_whenJurisdictionDoesNotMatch() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        createMoveToPastDateSchedule(courtCentreId, sessionDate, "CROWN");

        final Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate, null, MAGISTRATES);

        assertFalse(result.isPresent());
    }

    @Test
    void shouldFindCrownSession_whenJurisdictionIsCrown() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        final CourtSchedule schedule = createMoveToPastDateSchedule(courtCentreId, sessionDate, "CROWN");

        final Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate, null, "CROWN");

        assertTrue(result.isPresent());
        assertEquals(schedule.getCourtScheduleId(), result.get().getCourtScheduleId());
    }

    @Test
    void shouldScopeToRequestedCourtRoom() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);
        final String room2 = randomUUID().toString();

        createTimedSchedule(courtCentreId, sessionDate, MAGISTRATES, randomUUID().toString(), 8, 17, 1);
        final CourtSchedule inRoom2 = createTimedSchedule(courtCentreId, sessionDate, MAGISTRATES, room2, 8, 17, 2);

        final Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, room2, sessionDate, null, MAGISTRATES);

        assertTrue(result.isPresent());
        assertEquals(inRoom2.getCourtScheduleId(), result.get().getCourtScheduleId());
    }

    @Test
    void shouldReturnEmpty_whenSessionExistsOnDateButInADifferentRoom() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);
        final String requestedRoom = randomUUID().toString();
        final String otherRoom = randomUUID().toString();

        // a session exists on the date/centre, but only in a DIFFERENT room than the one requested
        createTimedSchedule(courtCentreId, sessionDate, MAGISTRATES, otherRoom, 8, 17, 1);

        final Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(
                courtCentreId, requestedRoom, sessionDate, null, MAGISTRATES);

        // room-scoped search must not fall back to another room -> caller surfaces NO_SESSION_FOUND
        assertFalse(result.isPresent());
    }

    @Test
    void shouldSelectSessionWhoseWindowContainsHearingStartTime() {
        final String courtCentreId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        final CourtSchedule morningSession = createTimedSchedule(courtCentreId, sessionDate, MAGISTRATES, null, 8, 12, 1);
        final CourtSchedule afternoonSession = createTimedSchedule(courtCentreId, sessionDate, MAGISTRATES, null, 13, 17, 2);

        // 10:00 lands the AM window (08:00-12:00), never the PM window
        final Optional<CourtSchedule> morning = courtScheduleRepository.findSessionForMoveToPastDate(
                courtCentreId, null, sessionDate, LocalDateTime.of(sessionDate, LocalTime.of(10, 0)), MAGISTRATES);
        assertTrue(morning.isPresent());
        assertEquals(morningSession.getCourtScheduleId(), morning.get().getCourtScheduleId());

        // 14:00 lands the PM window (13:00-17:00), never the AM window
        final Optional<CourtSchedule> afternoon = courtScheduleRepository.findSessionForMoveToPastDate(
                courtCentreId, null, sessionDate, LocalDateTime.of(sessionDate, LocalTime.of(14, 0)), MAGISTRATES);
        assertTrue(afternoon.isPresent());
        assertEquals(afternoonSession.getCourtScheduleId(), afternoon.get().getCourtScheduleId());
    }

    private CourtSchedule createMoveToPastDateSchedule(final String courtCentreId, final LocalDate sessionDate, final String jurisdiction) {
        final CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId(randomUUID().toString());
        schedule.setSessionDate(sessionDate);
        // oucode is VARCHAR(10) in the production schema — keep the random ≤10-char value
        // instead of the original branch's UUID; the query matches on court_house_id only.
        schedule.setCourtHouseId(courtCentreId);
        schedule.setJurisdiction(jurisdiction);
        schedule.setIsDraft(false);
        schedule.setActive(true);
        courtScheduleRepository.save(schedule);
        return schedule;
    }

    private CourtSchedule createTimedSchedule(final String courtCentreId, final LocalDate sessionDate, final String jurisdiction, final String courtRoomId, final int startHour, final int endHour, final int roomNumber) {
        final CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId(randomUUID().toString());
        schedule.setSessionDate(sessionDate);
        schedule.setCourtHouseId(courtCentreId);
        schedule.setJurisdiction(jurisdiction);
        if (courtRoomId != null) {
            schedule.setCourtRoomId(courtRoomId);
        }
        schedule.setCourtRoomNumber(roomNumber);
        schedule.setSessionStartTime(sessionDate.atTime(startHour, 0).atZone(ZoneOffset.UTC).toInstant());
        schedule.setSessionEndTime(sessionDate.atTime(endHour, 0).atZone(ZoneOffset.UTC).toInstant());
        schedule.setIsDraft(false);
        schedule.setActive(true);
        courtScheduleRepository.save(schedule);
        return schedule;
    }
}
