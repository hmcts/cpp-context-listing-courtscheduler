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
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Repository tests for {@code findSessionForMoveToPastDate}. Covers exact date + jurisdiction match,
 * optional room scoping, and hearingStartTime range-containment (a 10:00 start lands an AM/AD session
 * whose window contains 10:00, a 14:00 start lands a PM/AD session, never the other).
 */
class MoveHearingToPastDateRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    public void shouldFindMagistratesSessionForExactCourtHouseAndDate() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        CourtSchedule schedule = createMoveToPastDateSchedule(courtCentreId, sessionDate, "MAGISTRATES");

        Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate, null, "MAGISTRATES");

        assertTrue(result.isPresent());
        assertEquals(schedule.getCourtScheduleId(), result.get().getCourtScheduleId());
    }

    @Test
    public void shouldReturnEmpty_whenNoSessionMatchesExactDate() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        createMoveToPastDateSchedule(courtCentreId, sessionDate, "MAGISTRATES");

        Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate.plusDays(1), null, "MAGISTRATES");

        assertFalse(result.isPresent());
    }

    @Test
    public void shouldReturnEmpty_whenJurisdictionDoesNotMatch() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        createMoveToPastDateSchedule(courtCentreId, sessionDate, "CROWN");

        Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate, null, "MAGISTRATES");

        assertFalse(result.isPresent());
    }

    @Test
    public void shouldFindCrownSession_whenJurisdictionIsCrown() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        CourtSchedule schedule = createMoveToPastDateSchedule(courtCentreId, sessionDate, "CROWN");

        Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, null, sessionDate, null, "CROWN");

        assertTrue(result.isPresent());
        assertEquals(schedule.getCourtScheduleId(), result.get().getCourtScheduleId());
    }

    @Test
    public void shouldScopeToRequestedCourtRoom() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);
        String room2 = randomUUID().toString();

        createTimedSchedule(courtCentreId, sessionDate, "MAGISTRATES", randomUUID().toString(), 8, 17, 1);
        CourtSchedule inRoom2 = createTimedSchedule(courtCentreId, sessionDate, "MAGISTRATES", room2, 8, 17, 2);

        Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(courtCentreId, room2, sessionDate, null, "MAGISTRATES");

        assertTrue(result.isPresent());
        assertEquals(inRoom2.getCourtScheduleId(), result.get().getCourtScheduleId());
    }

    @Test
    public void shouldReturnEmpty_whenSessionExistsOnDateButInADifferentRoom() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);
        String requestedRoom = randomUUID().toString();
        String otherRoom = randomUUID().toString();

        // a session exists on the date/centre, but only in a DIFFERENT room than the one requested
        createTimedSchedule(courtCentreId, sessionDate, "MAGISTRATES", otherRoom, 8, 17, 1);

        Optional<CourtSchedule> result = courtScheduleRepository.findSessionForMoveToPastDate(
                courtCentreId, requestedRoom, sessionDate, null, "MAGISTRATES");

        // room-scoped search must not fall back to another room -> caller surfaces NO_SESSION_FOUND
        assertFalse(result.isPresent());
    }

    @Test
    public void shouldSelectSessionWhoseWindowContainsHearingStartTime() {
        String courtCentreId = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2024, 3, 4);

        CourtSchedule morningSession = createTimedSchedule(courtCentreId, sessionDate, "MAGISTRATES", null, 8, 12, 1);
        CourtSchedule afternoonSession = createTimedSchedule(courtCentreId, sessionDate, "MAGISTRATES", null, 13, 17, 2);

        // 10:00 lands the AM window (08:00-12:00), never the PM window
        Optional<CourtSchedule> morning = courtScheduleRepository.findSessionForMoveToPastDate(
                courtCentreId, null, sessionDate, LocalDateTime.of(sessionDate, LocalTime.of(10, 0)), "MAGISTRATES");
        assertTrue(morning.isPresent());
        assertEquals(morningSession.getCourtScheduleId(), morning.get().getCourtScheduleId());

        // 14:00 lands the PM window (13:00-17:00), never the AM window
        Optional<CourtSchedule> afternoon = courtScheduleRepository.findSessionForMoveToPastDate(
                courtCentreId, null, sessionDate, LocalDateTime.of(sessionDate, LocalTime.of(14, 0)), "MAGISTRATES");
        assertTrue(afternoon.isPresent());
        assertEquals(afternoonSession.getCourtScheduleId(), afternoon.get().getCourtScheduleId());
    }

    private CourtSchedule createMoveToPastDateSchedule(String courtCentreId, LocalDate sessionDate, String jurisdiction) {
        CourtSchedule schedule = random(CourtSchedule.class);
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

    private CourtSchedule createTimedSchedule(String courtCentreId, LocalDate sessionDate, String jurisdiction,
                                              String courtRoomId, int startHour, int endHour, int roomNumber) {
        CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId(randomUUID().toString());
        schedule.setSessionDate(sessionDate);
        schedule.setCourtHouseId(courtCentreId);
        schedule.setJurisdiction(jurisdiction);
        if (courtRoomId != null) {
            schedule.setCourtRoomId(courtRoomId);
        }
        schedule.setCourtRoomNumber(roomNumber);
        schedule.setSessionStartTime(Date.from(sessionDate.atTime(startHour, 0).atZone(ZoneOffset.UTC).toInstant()));
        schedule.setSessionEndTime(Date.from(sessionDate.atTime(endHour, 0).atZone(ZoneOffset.UTC).toInstant()));
        schedule.setIsDraft(false);
        schedule.setActive(true);
        courtScheduleRepository.save(schedule);
        return schedule;
    }
}
