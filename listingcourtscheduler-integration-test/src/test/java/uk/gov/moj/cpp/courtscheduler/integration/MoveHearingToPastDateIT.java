package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.io.StringReader;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@code courtscheduler.move-hearing-to-past-date}, served by
 * {@code POST /hearings/{hearingId}} with media type
 * {@code application/vnd.courtscheduler.move-hearing-to-past-date+json}.
 *
 * <p>These are the first ITs to exercise the no-anchor centre search
 * {@code CourtScheduleRepository.findConsecutiveSessionsForCentre} against a real PostgreSQL — the
 * high-risk native SQL (weekend exclusion, {@code court_house_id} centre mapping, generous window).
 * Both jurisdictions book CONSECUTIVE weekday sessions (one room + business type); CROWN reaches the
 * same centre search when no {@code courtScheduleId} anchor is supplied. Dates are in the past to
 * mirror the "move to past date" intent — the past-only rule itself is owned by the caller (listing),
 * so courtscheduler books whatever consecutive sessions it finds.
 *
 * <p>Cases (e)/(f) additionally arrange an EXISTING future booking (via
 * {@code DatabaseSeeder#insertAllocatedListing}) and assert the prior sessions are paid back —
 * allocated_listings rows released and available_duration_mins restored — for single- and multi-day CROWN.
 *
 * <p>Sister unit tests live in {@code SlotsUpdateServiceTest.MoveHearingToPastDate}.
 */
class MoveHearingToPastDateIT extends AbstractIT {

    private static final String ACCEPT = "application/vnd.courtscheduler.move-hearing-to-past-date+json";

    // --- (a) single-day MAGS ---

    @Test
    void shouldMoveMagsHearingToPastDate_singleDay() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day = pastMonday();

        final String sessionId = seedSession(day, roomId, "NGAP", centreId, "OU-MAG1", "MAGISTRATES");

        final Response response = callMove(centreId, roomId, "MAGISTRATES", day, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"MOVE_TO_PAST_DATE\""));
        assertThat(extractSessionIds(payload), contains(sessionId));
        assertThat("one allocated_listings row booked for the hearing",
                bookedScheduleIds(hearingId), contains(sessionId));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
    }

    // --- (b) single-day CROWN (no anchor → centre search) ---

    @Test
    void shouldMoveCrownHearingToPastDate_singleDay() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day = pastMonday();

        final String sessionId = seedSession(day, roomId, "CR", centreId, "OU-CRN1", "CROWN");

        final Response response = callMove(centreId, roomId, "CROWN", day, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"MOVE_TO_PAST_DATE\""));
        assertThat(extractSessionIds(payload), contains(sessionId));
        assertThat("one allocated_listings row booked for the hearing",
                bookedScheduleIds(hearingId), contains(sessionId));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
    }

    // --- (b2) single-day CROWN, room-scoped: a session in another room on the same day is ignored ---

    @Test
    void shouldMoveCrownHearingToPastDate_scopedToRequestedRoomOnly() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String requestedRoomId = UUID.randomUUID().toString();
        final String otherRoomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day = pastMonday();

        // Another room in the same centre also has a session on this day — main-contract alignment
        // means the search must not wander into it once a courtRoomId is supplied.
        seedSession(day, otherRoomId, "CR", centreId, "OU-CRN1B", "CROWN");
        final String requestedRoomSessionId = seedSession(day, requestedRoomId, "CR", centreId, "OU-CRN1B", "CROWN");

        final Response response = callMove(centreId, requestedRoomId, "CROWN", day, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(body(response)), contains(requestedRoomSessionId));
        assertThat("only the requested room's session is booked",
                bookedScheduleIds(hearingId), contains(requestedRoomSessionId));
    }

    // --- (c) multi-day MAGS (consecutive weekdays) ---

    @Test
    void shouldMoveMagsHearingToPastDate_multiDayConsecutive() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = pastMonday();

        final String d1 = seedSession(day1, roomId, "NGAP", centreId, "OU-MAG2", "MAGISTRATES");
        final String d2 = seedSession(day1.plusDays(1), roomId, "NGAP", centreId, "OU-MAG2", "MAGISTRATES");

        // durationInMinutes 720 => 2 days needed; consecutive Mon+Tue in the same room + business type.
        final Response response = callMove(centreId, roomId, "MAGISTRATES", day1, null, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"MOVE_TO_PAST_DATE\""));
        assertThat(extractSessionIds(payload), contains(d1, d2));
        assertThat("both consecutive days booked for the hearing",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
    }

    // --- (d) multi-day CROWN (no anchor → centre consecutive search) ---

    @Test
    void shouldMoveCrownHearingToPastDate_multiDayConsecutive() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = pastMonday();

        final String d1 = seedSession(day1, roomId, "CR", centreId, "OU-CRN2", "CROWN");
        final String d2 = seedSession(day1.plusDays(1), roomId, "CR", centreId, "OU-CRN2", "CROWN");

        final Response response = callMove(centreId, roomId, "CROWN", day1, null, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"MOVE_TO_PAST_DATE\""));
        assertThat(extractSessionIds(payload), contains(d1, d2));
        assertThat("both consecutive days booked for the hearing",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
    }


    // --- (e) CROWN single-day with an EXISTING future booking → prior session paid back ---

    /**
     * The listing-side flow that matters in production: the hearing is already booked onto a FUTURE
     * session when it is moved to a past date. courtscheduler must release that prior allocation
     * (allocated_listings row removed, the session's available_duration_mins restored) and book the
     * past session, so the future capacity is paid back rather than leaked.
     */
    @Test
    void shouldMoveCrownSingleDayHearingToPastDateAndPayBackPriorFutureSession() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate futureDay = futureMonday();
        final LocalDate pastDay = pastMonday();

        // Currently booked: a future session fully consumed by this hearing (0 mins left).
        final String futureSession = seedSession(futureDay, roomId, "CR", centreId, "OU-CRN3", "CROWN", 0);
        book(hearingId, futureSession, futureDay, 360, "OU-CRN3");
        // Target: a past session with full capacity.
        final String pastSession = seedSession(pastDay, roomId, "CR", centreId, "OU-CRN3", "CROWN", 360);

        final Response response = callMove(centreId, roomId, "CROWN", pastDay, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(body(response)), contains(pastSession));

        assertThat("hearing now booked on the past session only",
                bookedScheduleIds(hearingId), contains(pastSession));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
        assertThat("prior future session's capacity paid back in full",
                databaseReader.courtScheduleById(futureSession).getAvailableDuration(), is(360));
        assertThat("past session's capacity consumed by the moved hearing",
                databaseReader.courtScheduleById(pastSession).getAvailableDuration(), is(0));
    }

    // --- (f) CROWN multi-day with an EXISTING future block → EVERY prior day paid back ---

    /**
     * Multi-day CROWN hearing already holding a 2-day future block. Moving it to a past date must
     * release BOTH prior rows (not just the first — the hearing has one allocated_listings row per
     * day) and restore each future session's capacity, then book the consecutive past run.
     */
    @Test
    void shouldMoveCrownMultiDayHearingToPastDateAndPayBackAllPriorFutureSessions() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate futureDay1 = futureMonday();
        final LocalDate futureDay2 = futureDay1.plusDays(1);
        final LocalDate pastDay1 = pastMonday();
        final LocalDate pastDay2 = pastDay1.plusDays(1);

        // Currently booked: Mon+Tue future block, both days fully consumed by this hearing.
        final String f1 = seedSession(futureDay1, roomId, "CR", centreId, "OU-CRN4", "CROWN", 0);
        final String f2 = seedSession(futureDay2, roomId, "CR", centreId, "OU-CRN4", "CROWN", 0);
        book(hearingId, f1, futureDay1, 360, "OU-CRN4");
        book(hearingId, f2, futureDay2, 360, "OU-CRN4");
        // Target: consecutive past Mon+Tue in the same room + business type.
        final String p1 = seedSession(pastDay1, roomId, "CR", centreId, "OU-CRN4", "CROWN", 360);
        final String p2 = seedSession(pastDay2, roomId, "CR", centreId, "OU-CRN4", "CROWN", 360);

        // durationInMinutes 720 => 2 days needed.
        final Response response = callMove(centreId, roomId, "CROWN", pastDay1, null, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(body(response)), contains(p1, p2));

        assertThat("hearing now booked on the two past sessions only",
                bookedScheduleIds(hearingId), containsInAnyOrder(p1, p2));
        assertThat("no allocation left on either prior future session",
                allocationsOnSchedules(hearingId, f1, f2), is(empty()));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
        assertThat("first future session's capacity paid back in full",
                databaseReader.courtScheduleById(f1).getAvailableDuration(), is(360));
        assertThat("second future session's capacity paid back in full",
                databaseReader.courtScheduleById(f2).getAvailableDuration(), is(360));
        assertThat("first past session's capacity consumed by the moved hearing",
                databaseReader.courtScheduleById(p1).getAvailableDuration(), is(0));
        assertThat("second past session's capacity consumed by the moved hearing",
                databaseReader.courtScheduleById(p2).getAvailableDuration(), is(0));
    }

    // --- helpers ---

    /**
     * POST move-hearing-to-past-date. hearingId travels in the path only; no courtScheduleId anchor.
     * courtRoomId/startTime/endTime mirror main's contract (courtRoomId now mandatory, scoping the
     * search to that room; startTime/endTime are UTC instants whose DATES drive [startDate, endDate] —
     * the same 10:00/17:00 window every seeded session uses).
     */
    private Response callMove(final String courtCentreId,
                              final String courtRoomId,
                              final String jurisdiction,
                              final LocalDate startDate,
                              final LocalDate endDate,
                              final int durationInMinutes,
                              final String hearingId) {
        final LocalDate effectiveEndDate = endDate != null ? endDate : startDate;
        final jakarta.json.JsonObjectBuilder b = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId)
                .add("courtRoomId", courtRoomId)
                .add("jurisdiction", jurisdiction)
                .add("startTime", startDate.atTime(10, 0).toInstant(ZoneOffset.UTC).toString())
                .add("endTime", effectiveEndDate.atTime(17, 0).toInstant(ZoneOffset.UTC).toString())
                .add("durationInMinutes", durationInMinutes);
        return postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID, b.build().toString());
    }

    private List<String> bookedScheduleIds(final String hearingId) {
        return databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
    }

    private List<String> bookedSources(final String hearingId) {
        return databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .map(AllocatedListing::getSource)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> allocationsOnSchedules(final String hearingId, final String... courtScheduleIds) {
        final List<String> ids = List.of(courtScheduleIds);
        return databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()) && ids.contains(al.getCourtScheduleId()))
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
    }

    /**
     * Book {@code hearingId} directly onto {@code courtScheduleId} — the established IT pattern for
     * arranging "hearing already allocated to session X" (see {@code ChangeCourtRoomForMultidayHearingIT},
     * {@code HearingIdIT}). The session's available_duration is NOT adjusted here; seed it explicitly.
     */
    private void book(final String hearingId, final String courtScheduleId, final LocalDate sessionDate,
                      final int durationMinutes, final String ouCode) throws java.sql.SQLException {
        final AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(UUID.randomUUID().toString());
        allocatedListing.setBookingId(UUID.randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setOucode(ouCode);
        allocatedListing.setCourtRoomId(1);
        allocatedListing.setRotaBusinessType("CR");
        allocatedListing.setDuration(durationMinutes);
        allocatedListing.setHearingStartTime(Date.from(sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC)));
        databaseSeeder.insertAllocatedListing(allocatedListing);
    }

    private static String body(final Response response) {
        return response.readEntity(String.class);
    }

    private static List<String> extractSessionIds(final String payload) {
        final JsonObject json = createReader(new StringReader(payload)).readObject();
        if (!json.containsKey("sessions") || json.isNull("sessions")) {
            return List.of();
        }
        final JsonArray arr = json.getJsonArray("sessions");
        return arr.getValuesAs(JsonObject.class).stream()
                .map(o -> o.getString("courtScheduleId"))
                .collect(Collectors.toList());
    }

    /** A Monday comfortably in the past (so Mon+Tue are past weekdays for the multi-day cases). */
    private static LocalDate pastMonday() {
        LocalDate d = LocalDate.now().minusWeeks(4);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** A Monday comfortably in the future - the hearing's CURRENT booking before it is moved to the past. */
    private static LocalDate futureMonday() {
        LocalDate d = LocalDate.now().plusWeeks(4);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /**
     * Insert an {@code court_session=AD}, {@code active=true} court_schedule at the centre and return its id.
     * {@code court_house_id} is set to {@code courtCentreId} — the column the centre search keys on.
     */
    private String seedSession(final LocalDate sessionDate,
                               final String courtRoomId,
                               final String businessType,
                               final String courtCentreId,
                               final String ouCode,
                               final String jurisdiction) throws java.sql.SQLException {
        return seedSession(sessionDate, courtRoomId, businessType, courtCentreId, ouCode, jurisdiction, 360);
    }

    /** As above, with an explicit {@code available_duration_mins} so a session can be seeded as already fully committed (0). */
    private String seedSession(final LocalDate sessionDate,
                               final String courtRoomId,
                               final String businessType,
                               final String courtCentreId,
                               final String ouCode,
                               final String jurisdiction,
                               final int availableDurationMinutes) throws java.sql.SQLException {
        final String id = UUID.randomUUID().toString();
        final Date sessionStart = Date.from(sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC));
        final Date sessionEnd = Date.from(sessionDate.atTime(17, 0).toInstant(ZoneOffset.UTC));

        final CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(id);
        cs.setListingProfileId(UUID.randomUUID().toString());
        cs.setOuCode(ouCode);
        cs.setCourtRoomId(courtRoomId);
        cs.setCourtRoomNumber(1);
        cs.setCourtHouseId(courtCentreId);
        cs.setCourtHouseName("Test Court");
        cs.setCourtRoomName("Room 1");
        cs.setOperationalUnit(ouCode);
        cs.setBusinessType(businessType);
        cs.setPanel("Adult");
        cs.setCourtSession("AD");
        cs.setActive(true);
        cs.setSlotBased(false);
        cs.setSessionDate(sessionDate);
        cs.setMaxSlots(0);
        cs.setMaxDuration(360);
        cs.setAvailableSlots(0);
        cs.setAvailableDuration(availableDurationMinutes);
        cs.setSupportAdSplit(false);
        cs.setMaxAdMorningDuration(180);
        cs.setMaxAdAfternoonDuration(180);
        cs.setSessionStartTime(sessionStart);
        cs.setSessionEndTime(sessionEnd);
        cs.setNationalBreakTime(sessionStart);
        cs.setIsOverbookingAllowed(false);
        cs.setIsDraft(false);
        cs.setJurisdiction(jurisdiction);
        cs.setTotalBookedMorning(0);
        cs.setTotalBookedAfternoon(0);
        cs.setTotalBooked(0);

        databaseSeeder.insertCourtSchedule(cs);
        return id;
    }
}
