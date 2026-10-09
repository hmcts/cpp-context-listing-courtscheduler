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
import java.time.Instant;
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
    private static final String CROWN_2 = "CROWN";
    private static final String MAGISTRATES_2 = "MAGISTRATES";
    private static final String MOVE_TO_PAST_DATE_2 = "MOVE_TO_PAST_DATE";
    private static final String OU_CRN4 = "OU-CRN4";
    private static final String NGAP = "NGAP";
    private static final String C01CY00 = "C01CY00";
    private static final String OU_MAG5 = "OU-MAG5";
    private static final String OU_MAG7 = "OU-MAG7";
    private static final String NOON = "12:00";
    private static final String HALF_PAST_MIDNIGHT = "00:30";
    private static final String NOTHING_BOOKED = "nothing booked";
    private static final String CR = "CR";
    private static final String OU_CRN6 = "OU-CRN6";
    private static final String TEN_AM = "10:00";
    private static final String T_TEN_THIRTY = "T10:30:00.000Z";
    private static final String COURT_CENTRE_ID = "courtCentreId";
    private static final String SESSIONS = "sessions";
    private static final String COURT_SCHEDULE_ID = "courtScheduleId";
    private static final String COURT_ROOM_ID = "courtRoomId";
    private static final String JURISDICTION = "jurisdiction";
    private static final String START_TIME = "startTime";
    private static final String END_TIME = "endTime";
    private static final String SESSION_START_TIME = "sessionStartTime";
    private static final String SESSION_END_TIME = "sessionEndTime";
    private static final String DURATION_IN_MINUTES = "durationInMinutes";
    private static final String NO_SESSION_FOUND = "NO_SESSION_FOUND";
    private static final String FUTURE_DATE_NOT_ALLOWED = "FUTURE_DATE_NOT_ALLOWED";
    private static final int UNPROCESSABLE = 422;
    private static final int BAD_REQUEST = 400;
    private static final String SOURCE_MOVE_TO_PAST_DATE = "\"source\":\"MOVE_TO_PAST_DATE\"";
    private static final String PERSISTED_ALLOCATED_LISTINGS_SOURCE = "persisted allocated_listings.source";


    private static final String ACCEPT = "application/vnd.courtscheduler.move-hearing-to-past-date+json";

    // --- (a) single-day MAGS ---

    @Test
    void shouldMoveMagsHearingToPastDate_singleDay() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day = pastMonday();

        final String sessionId = seedSession(day, roomId, NGAP, centreId, "OU-MAG1", MAGISTRATES_2);

        final Response response = callMove(centreId, roomId, MAGISTRATES_2, day, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString(SOURCE_MOVE_TO_PAST_DATE));
        assertThat(extractSessionIds(payload), contains(sessionId));
        assertThat("one allocated_listings row booked for the hearing",
                bookedScheduleIds(hearingId), contains(sessionId));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    // --- (b) single-day CROWN (no anchor → centre search) ---

    @Test
    void shouldMoveCrownHearingToPastDate_singleDay() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day = pastMonday();

        final String sessionId = seedSession(day, roomId, "CR", centreId, "OU-CRN1", CROWN_2);

        final Response response = callMove(centreId, roomId, CROWN_2, day, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString(SOURCE_MOVE_TO_PAST_DATE));
        assertThat(extractSessionIds(payload), contains(sessionId));
        assertThat("one allocated_listings row booked for the hearing",
                bookedScheduleIds(hearingId), contains(sessionId));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
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
        seedSession(day, otherRoomId, "CR", centreId, "OU-CRN1B", CROWN_2);
        final String requestedRoomSessionId = seedSession(day, requestedRoomId, "CR", centreId, "OU-CRN1B", CROWN_2);

        final Response response = callMove(centreId, requestedRoomId, CROWN_2, day, null, 360, hearingId);

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

        final String d1 = seedSession(day1, roomId, NGAP, centreId, "OU-MAG2", MAGISTRATES_2);
        final String d2 = seedSession(day1.plusDays(1), roomId, NGAP, centreId, "OU-MAG2", MAGISTRATES_2);

        // A genuine date range (endDate after startDate) => 2 days needed; consecutive Mon+Tue in the
        // same room + business type. durationInMinutes alone no longer drives multi-day sizing here —
        // it's the hearing's own overall estimate (SPRDT-1361), unrelated to this move's day count.
        final Response response = callMove(centreId, roomId, MAGISTRATES_2, day1, day1.plusDays(1), 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString(SOURCE_MOVE_TO_PAST_DATE));
        assertThat(extractSessionIds(payload), contains(d1, d2));
        assertThat("both consecutive days booked for the hearing",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    // --- (d) multi-day CROWN (no anchor → centre consecutive search) ---

    @Test
    void shouldMoveCrownHearingToPastDate_multiDayConsecutive() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = pastMonday();

        final String d1 = seedSession(day1, roomId, "CR", centreId, "OU-CRN2", CROWN_2);
        final String d2 = seedSession(day1.plusDays(1), roomId, "CR", centreId, "OU-CRN2", CROWN_2);

        // A genuine date range (endDate after startDate) drives the 2-day search; see the MAGS case above.
        final Response response = callMove(centreId, roomId, CROWN_2, day1, day1.plusDays(1), 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString(SOURCE_MOVE_TO_PAST_DATE));
        assertThat(extractSessionIds(payload), contains(d1, d2));
        assertThat("both consecutive days booked for the hearing",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
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
        final String futureSession = seedSession(futureDay, roomId, "CR", centreId, "OU-CRN3", CROWN_2, 0);
        book(hearingId, futureSession, futureDay, 360, "OU-CRN3");
        // Target: a past session with full capacity.
        final String pastSession = seedSession(pastDay, roomId, "CR", centreId, "OU-CRN3", CROWN_2, 360);

        final Response response = callMove(centreId, roomId, CROWN_2, pastDay, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(body(response)), contains(pastSession));

        assertThat("hearing now booked on the past session only",
                bookedScheduleIds(hearingId), contains(pastSession));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
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
        final String f1 = seedSession(futureDay1, roomId, "CR", centreId, OU_CRN4, CROWN_2, 0);
        final String f2 = seedSession(futureDay2, roomId, "CR", centreId, OU_CRN4, CROWN_2, 0);
        book(hearingId, f1, futureDay1, 360, OU_CRN4);
        book(hearingId, f2, futureDay2, 360, OU_CRN4);
        // Target: consecutive past Mon+Tue in the same room + business type.
        final String p1 = seedSession(pastDay1, roomId, "CR", centreId, OU_CRN4, CROWN_2, 360);
        final String p2 = seedSession(pastDay2, roomId, "CR", centreId, OU_CRN4, CROWN_2, 360);

        // A genuine date range (endDate after startDate) => 2 days needed; see case (c)/(d) above.
        final Response response = callMove(centreId, roomId, CROWN_2, pastDay1, pastDay2, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(body(response)), contains(p1, p2));

        assertThat("hearing now booked on the two past sessions only",
                bookedScheduleIds(hearingId), containsInAnyOrder(p1, p2));
        assertThat("no allocation left on either prior future session",
                allocationsOnSchedules(hearingId, f1, f2), is(empty()));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
        assertThat("first future session's capacity paid back in full",
                databaseReader.courtScheduleById(f1).getAvailableDuration(), is(360));
        assertThat("second future session's capacity paid back in full",
                databaseReader.courtScheduleById(f2).getAvailableDuration(), is(360));
        assertThat("first past session's capacity consumed by the moved hearing",
                databaseReader.courtScheduleById(p1).getAvailableDuration(), is(0));
        assertThat("second past session's capacity consumed by the moved hearing",
                databaseReader.courtScheduleById(p2).getAvailableDuration(), is(0));
    }

    // ===== SPRDT-1447: MAGISTRATES = main's behaviour (ported from main's MoveHearingToPastDateIT) =====

    @Test
    void shouldBookSessionAndReturnSlotDetailsForPastDate() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        final String sessionId = seedMagistratesSession(pastDate, roomId, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, pastDate, null, NOON, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        final JsonObject slot = firstSession(response.body());
        assertThat(slot.getString(COURT_SCHEDULE_ID), is(sessionId));
        assertThat(slot.getString("source"), is(MOVE_TO_PAST_DATE_2));
        assertThat(slot.getBoolean("isDraft"), is(false));
        assertThat("allocated_listings row written", bookedScheduleIds(hearingId), contains(sessionId));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    @Test
    void shouldBookCrownSessionForPastDate() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        final String sessionId = seedTimedSession(pastDate, roomId, NGAP, centreId, C01CY00, CROWN_2, "AD", 10, 17);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, CROWN_2, pastDate, null, NOON, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat(firstSession(response.body()).getString(COURT_SCHEDULE_ID), is(sessionId));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    /** Weekend sessions are real (magistrates remand courts sit Saturdays). */
    @Test
    void shouldBookSaturdaySessionForPastDate() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate saturday = mostRecentSaturday();

        final String sessionId = seedMagistratesSession(saturday, roomId, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, saturday, null, NOON, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat(firstSession(response.body()).getString(COURT_SCHEDULE_ID), is(sessionId));
        assertThat("allocated_listings row written", bookedScheduleIds(hearingId), contains(sessionId));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    @Test
    void shouldBookOnlyWorkingDaysForAMultiDayRange() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate startDate = pastWorkingDay(2);
        final LocalDate endDate = pastWorkingDay(1);

        final String session1 = seedMagistratesSession(startDate, roomId, NGAP, centreId, C01CY00);
        final String session2 = seedMagistratesSession(endDate, roomId, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, startDate, endDate, NOON, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat(parse(response.body()).getJsonArray(SESSIONS).size(), is(2));
        assertThat("both sitting days booked (weekend skipped)", bookedScheduleIds(hearingId), containsInAnyOrder(session1, session2));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    @Test
    void shouldStampSubmittedTimesAndSingleDayWindowDurationOnBookedSlot() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();
        seedMagistratesSession(pastDate, roomId, NGAP, centreId, C01CY00); // session window 10:00-17:00

        // submitted 10:30 -> 10:50 = a 20-minute single-day window
        final MoveResult response = postMove(hearingId,
                moveWindowPayload(centreId, roomId, MAGISTRATES_2, pastDate + T_TEN_THIRTY, pastDate + "T10:50:00.000Z"));

        assertThat(response.status(), is(OK.getStatusCode()));
        final JsonObject slot = firstSession(response.body());
        // the SUBMITTED times (not the session's 10:00-17:00 window) + the computed window duration
        assertThat(slot.getString(SESSION_START_TIME), is(pastDate + T_TEN_THIRTY));
        assertThat(slot.getString(SESSION_END_TIME), is(pastDate + "T10:50:00.000Z"));
        assertThat(slot.getInt(DURATION_IN_MINUTES), is(20));
    }

    @Test
    void shouldStampSubmittedTimesAndFullCourtDayDurationPerDayForMultiDayMove() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate startDate = pastWorkingDay(2);
        final LocalDate endDate = pastWorkingDay(1);
        seedMagistratesSession(startDate, roomId, NGAP, centreId, C01CY00);
        seedMagistratesSession(endDate, roomId, NGAP, centreId, C01CY00);

        // multi-day 10:30 -> 17:00 : every sitting day is booked at a full court day (360 min), 10:30-17:00
        final MoveResult response = postMove(hearingId,
                moveWindowPayload(centreId, roomId, MAGISTRATES_2, startDate + T_TEN_THIRTY, endDate + "T17:00:00.000Z"));

        assertThat(response.status(), is(OK.getStatusCode()));
        final JsonArray slots = parse(response.body()).getJsonArray(SESSIONS);
        assertThat(slots.size(), is(2));
        for (int i = 0; i < slots.size(); i++) {
            final JsonObject slot = slots.getJsonObject(i);
            final String date = slot.getString("sessionDate");
            assertThat(slot.getString(SESSION_START_TIME), is(date + T_TEN_THIRTY));
            assertThat(slot.getString(SESSION_END_TIME), is(date + "T17:00:00.000Z"));
            assertThat(slot.getInt(DURATION_IN_MINUTES), is(360));
        }
    }

    @Test
    void shouldBookWithinTheRequestedRoom() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String requestedRoom = UUID.randomUUID().toString();
        final String otherRoom = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        seedMagistratesSession(pastDate, otherRoom, NGAP, centreId, C01CY00);
        final String wanted = seedMagistratesSession(pastDate, requestedRoom, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, requestedRoom, MAGISTRATES_2, pastDate, null, NOON, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat(firstSession(response.body()).getString(COURT_SCHEDULE_ID), is(wanted));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    @Test
    void shouldReturn422WhenSessionExistsButInADifferentRoom() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String requestedRoom = UUID.randomUUID().toString();
        final String otherRoom = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // a session exists on the date/centre but only in a DIFFERENT room than the one requested
        seedMagistratesSession(pastDate, otherRoom, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, requestedRoom, MAGISTRATES_2, pastDate, null, NOON, 30));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(NO_SESSION_FOUND));
        assertThat(NOTHING_BOOKED, bookedScheduleIds(hearingId), is(empty()));
    }

    @Test
    void shouldSelectSessionByHearingStartTime() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // same room; range-containment on the start time-of-day discriminates AM from PM
        final String amSession = seedTimedSession(pastDate, roomId, NGAP, centreId, C01CY00, MAGISTRATES_2, "AM", 9, 12);
        final String pmSession = seedTimedSession(pastDate, roomId, NGAP, centreId, C01CY00, MAGISTRATES_2, "PM", 13, 17);

        // 10:00 lands the AM window, never the PM window
        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, pastDate, null, TEN_AM, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        final String booked = firstSession(response.body()).getString(COURT_SCHEDULE_ID);
        assertThat(booked, is(amSession));
        assertThat(booked, is(org.hamcrest.Matchers.not(pmSession)));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    @Test
    void shouldReleasePriorAllocationAndRebookOnPastDate() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();
        final LocalDate futureDate = LocalDate.now().plusDays(14);

        final String oldSessionId = seedMagistratesSession(futureDate, roomId, NGAP, centreId, C01CY00);
        book(hearingId, oldSessionId, futureDate, 30, C01CY00);
        final String newSessionId = seedMagistratesSession(pastDate, roomId, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, pastDate, null, NOON, 30));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat("prior allocation released, hearing rebooked onto the past-date session only",
                bookedScheduleIds(hearingId), contains(newSessionId));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    /** Past dates only - a future date is a 422 FUTURE_DATE_NOT_ALLOWED even when a session exists. */
    @Test
    void shouldReturn422WhenStartDateIsAfterToday() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate futureDate = nextWorkingDayAfterToday();

        seedMagistratesSession(futureDate, roomId, NGAP, centreId, C01CY00);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, futureDate, null, NOON, 30));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(FUTURE_DATE_NOT_ALLOWED));
        assertThat(NOTHING_BOOKED, bookedScheduleIds(hearingId), is(empty()));
    }

    @Test
    void shouldReturn422WhenNoSessionAtCourtCentreOnDate() throws Exception {
        final String hearingId = UUID.randomUUID().toString();

        final MoveResult response = postMove(hearingId, movePayload(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                MAGISTRATES_2, lastWorkingDayBeforeToday(), null, NOON, 30));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(NO_SESSION_FOUND));
    }

    @Test
    void shouldReturn400WhenCourtCentreIdIsMissing() throws Exception {
        final LocalDate pastDate = lastWorkingDayBeforeToday();
        // courtRoomId/startTime/endTime present so the 400 is unambiguously the missing courtCentreId
        final String payload = Json.createObjectBuilder()
                .add(COURT_ROOM_ID, UUID.randomUUID().toString())
                .add(JURISDICTION, MAGISTRATES_2)
                .add(START_TIME, pastDate + "T12:00:00.000Z")
                .add(END_TIME, pastDate + "T12:30:00.000Z")
                .build().toString();

        assertThat(postMove(UUID.randomUUID().toString(), payload).status(), is(BAD_REQUEST));
    }

    @Test
    void shouldReturn400WhenCourtRoomIdIsMissing() throws Exception {
        // courtRoomId omitted (mandatory); movePayload skips it when null
        final MoveResult response = postMove(UUID.randomUUID().toString(),
                movePayload(UUID.randomUUID().toString(), null, MAGISTRATES_2, lastWorkingDayBeforeToday(), null, NOON, 30));

        assertThat(response.status(), is(BAD_REQUEST));
    }

    @Test
    void shouldReturn400WhenStartTimeIsMissing() throws Exception {
        // every other mandatory field present so the 400 is unambiguously the missing startTime
        final String payload = Json.createObjectBuilder()
                .add(COURT_CENTRE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, UUID.randomUUID().toString())
                .add(JURISDICTION, MAGISTRATES_2)
                .add(END_TIME, lastWorkingDayBeforeToday() + "T12:30:00.000Z")
                .build().toString();

        assertThat(postMove(UUID.randomUUID().toString(), payload).status(), is(BAD_REQUEST));
    }

    // ===== SPRDT-1447: AM/PM-only rooms (the reported defect) =====

    @Test
    void shouldMoveMagsHearingIntoAmSessionOfAnAmPmOnlyRoom() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // no AD session that day - only AM (09:00-13:00) and PM (13:00-17:00), as in the reported rota
        final String amSession = seedTimedSession(pastDate, roomId, NGAP, centreId, OU_MAG5, MAGISTRATES_2, "AM", 9, 13);
        seedTimedSession(pastDate, roomId, NGAP, centreId, OU_MAG5, MAGISTRATES_2, "PM", 13, 17);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, pastDate, null, TEN_AM, 60));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(response.body()), contains(amSession));
        assertThat("the AM session containing 10:00 is booked", bookedScheduleIds(hearingId), contains(amSession));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    @Test
    void shouldReturn422WhenStartTimeFallsOutsideEverySessionWindow() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // PM-only room; a 10:00 start falls in no session window
        seedTimedSession(pastDate, roomId, NGAP, centreId, "OU-MAG6", MAGISTRATES_2, "PM", 13, 17);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, pastDate, null, TEN_AM, 60));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(NO_SESSION_FOUND));
        assertThat(NOTHING_BOOKED, bookedScheduleIds(hearingId), is(empty()));
    }

    @Test
    void shouldMoveMagsHearingIntoAmSessionAndPayBackPriorFutureSession() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate futureDay = futureMonday();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // currently booked: 60 of a future AM session's 240 minutes (180 left)
        final String futureSession = seedTimedSession(futureDay, roomId, NGAP, centreId, OU_MAG7, MAGISTRATES_2, "AM", 9, 13, 180);
        book(hearingId, futureSession, futureDay, 60, OU_MAG7);
        final String pastAm = seedTimedSession(pastDate, roomId, NGAP, centreId, OU_MAG7, MAGISTRATES_2, "AM", 9, 13, 240);
        final String pastPm = seedTimedSession(pastDate, roomId, NGAP, centreId, OU_MAG7, MAGISTRATES_2, "PM", 13, 17, 240);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, pastDate, null, TEN_AM, 60));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat("hearing now booked on the past AM session only", bookedScheduleIds(hearingId), contains(pastAm));
        assertThat("prior future AM session's 60 minutes paid back",
                databaseReader.courtScheduleById(futureSession).getAvailableDuration(), is(240));
        assertThat("past AM session's capacity consumed by the moved hearing (submitted 60-minute window)",
                databaseReader.courtScheduleById(pastAm).getAvailableDuration(), is(180));
        assertThat("past PM session untouched", databaseReader.courtScheduleById(pastPm).getAvailableDuration(), is(240));
    }

    // ===== SPRDT-1447: CROWN rulings of 2026-10-08 =====

    /** Requested room + date + time: a 14:00 start in an AM/PM-only Crown room books the PM session. */
    @Test
    void shouldMoveCrownHearingIntoPmSessionOfAnAmPmOnlyRoom() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        seedTimedSession(pastDate, roomId, CR, centreId, OU_CRN6, CROWN_2, "AM", 9, 12);
        final String pmSession = seedTimedSession(pastDate, roomId, CR, centreId, OU_CRN6, CROWN_2, "PM", 13, 16);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, CROWN_2, pastDate, null, "14:00", 60));

        assertThat(response.status(), is(OK.getStatusCode()));
        assertThat(extractSessionIds(response.body()), contains(pmSession));
        assertThat("the PM session containing 14:00 is booked", bookedScheduleIds(hearingId), contains(pmSession));
        assertThat(PERSISTED_ALLOCATED_LISTINGS_SOURCE, bookedSources(hearingId), contains(MOVE_TO_PAST_DATE_2));
    }

    /** No fallback: a session elsewhere in the centre is never used when the requested room has none. */
    @Test
    void shouldReturn422ForCrownWhenOnlyAnotherRoomHasASession() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String requestedRoom = UUID.randomUUID().toString();
        final String otherRoom = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        seedTimedSession(pastDate, otherRoom, CR, centreId, OU_CRN6, CROWN_2, "AD", 10, 17);

        final MoveResult response = postMove(hearingId, movePayload(centreId, requestedRoom, CROWN_2, pastDate, null, NOON, 60));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(NO_SESSION_FOUND));
        assertThat(NOTHING_BOOKED, bookedScheduleIds(hearingId), is(empty()));
    }

    /** Past dates only: today is rejected for MAGISTRATES too, even when a session exists. */
    @Test
    void shouldReturn422ForMagistratesWhenTheMoveIsForToday() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);

        seedTimedSession(today, roomId, NGAP, centreId, "OU-MAG8", MAGISTRATES_2, "AD", 0, 23);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, MAGISTRATES_2, today, null, HALF_PAST_MIDNIGHT, 30));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(FUTURE_DATE_NOT_ALLOWED));
        assertThat(NOTHING_BOOKED, bookedScheduleIds(hearingId), is(empty()));
    }

    /** Past dates only: today is rejected for CROWN even when a session exists. */
    @Test
    void shouldReturn422ForCrownWhenTheMoveIsForToday() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate today = LocalDate.now(ZoneOffset.UTC);

        seedTimedSession(today, roomId, CR, centreId, OU_CRN6, CROWN_2, "AD", 0, 23);

        final MoveResult response = postMove(hearingId, movePayload(centreId, roomId, CROWN_2, today, null, HALF_PAST_MIDNIGHT, 30));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(FUTURE_DATE_NOT_ALLOWED));
        assertThat(NOTHING_BOOKED, bookedScheduleIds(hearingId), is(empty()));
    }

    /** No past session is always a 422: a Crown range that matches nothing no longer returns an empty 200. */
    @Test
    void shouldReturn422ForCrownRangeWithNoPastSessionsAndKeepTheCurrentBooking() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate futureDay = futureMonday();

        final String futureSession = seedSession(futureDay, roomId, CR, centreId, OU_CRN6, CROWN_2, 0);
        book(hearingId, futureSession, futureDay, 360, OU_CRN6);

        final MoveResult response = postMove(hearingId,
                movePayload(centreId, roomId, CROWN_2, pastWorkingDay(2), pastWorkingDay(1), NOON, 360));

        assertThat(response.status(), is(UNPROCESSABLE));
        assertThat(response.body(), containsString(NO_SESSION_FOUND));
        assertThat("the hearing keeps its current booking", bookedScheduleIds(hearingId), contains(futureSession));
        assertThat("current session's capacity untouched", databaseReader.courtScheduleById(futureSession).getAvailableDuration(), is(0));
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
        allocatedListing.setHearingStartTime(sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC));
        databaseSeeder.insertAllocatedListing(allocatedListing);
    }

    private static String body(final Response response) {
        return response.readEntity(String.class);
    }

    private static List<String> extractSessionIds(final String payload) {
        final JsonObject json = createReader(new StringReader(payload)).readObject();
        if (!json.containsKey(SESSIONS) || json.isNull(SESSIONS)) {
            return List.of();
        }
        final JsonArray arr = json.getJsonArray(SESSIONS);
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
        final Instant sessionStart = sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC);
        final Instant sessionEnd = sessionDate.atTime(17, 0).toInstant(ZoneOffset.UTC);

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

    // ----- helpers for the SPRDT-1447 / main-ported tests -----

    /** Status + body of a move POST, read eagerly so the Response is always closed. */
    private record MoveResult(int status, String body) { }

    private MoveResult postMove(final String hearingId, final String payload) {
        try (Response response = postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID, payload)) {
            return new MoveResult(response.getStatus(), response.readEntity(String.class));
        }
    }

    private static LocalDate lastWorkingDayBeforeToday() {
        return pastWorkingDay(1);
    }

    /** most recent Saturday strictly before today - always past; proves weekend dates are bookable. */
    private static LocalDate mostRecentSaturday() {
        LocalDate day = LocalDate.now().minusDays(1);
        while (day.getDayOfWeek() != DayOfWeek.SATURDAY) {
            day = day.minusDays(1);
        }
        return day;
    }

    /** n-th working (Mon-Fri) day strictly before today. */
    private static LocalDate pastWorkingDay(final int n) {
        LocalDate day = LocalDate.now();
        int found = 0;
        while (found < n) {
            day = day.minusDays(1);
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                found++;
            }
        }
        return day;
    }

    /** next working (Mon-Fri) day strictly after today. */
    private static LocalDate nextWorkingDayAfterToday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(1);
        }
        return day;
    }

    /**
     * main's payload builder. hearingId travels only in the URL path. The request schema requires
     * endTime, so a single-day move (endDate null) ends {@code durationInMinutes} after the start;
     * a multi-day move ends at the same time-of-day on endDate (main's shape).
     */
    private static String movePayload(final String courtCentreId,
                                      final String courtRoomId,
                                      final String jurisdiction,
                                      final LocalDate startDate,
                                      final LocalDate endDate,
                                      final String timeHhmm,
                                      final int durationInMinutes) {
        final java.time.LocalDateTime start = startDate.atTime(java.time.LocalTime.parse(timeHhmm));
        final java.time.LocalDateTime end = endDate != null
                ? endDate.atTime(java.time.LocalTime.parse(timeHhmm))
                : start.plusMinutes(durationInMinutes);
        final jakarta.json.JsonObjectBuilder builder = Json.createObjectBuilder()
                .add(COURT_CENTRE_ID, courtCentreId)
                .add(JURISDICTION, jurisdiction)
                .add(START_TIME, start.toInstant(ZoneOffset.UTC).toString())
                .add(END_TIME, end.toInstant(ZoneOffset.UTC).toString())
                .add(DURATION_IN_MINUTES, durationInMinutes);
        if (courtRoomId != null) {
            builder.add(COURT_ROOM_ID, courtRoomId);
        }
        return builder.build().toString();
    }

    /** explicit start/end instants, so the stamped time-of-day and computed duration can be asserted. */
    private static String moveWindowPayload(final String courtCentreId, final String courtRoomId,
                                            final String jurisdiction, final String startInstant, final String endInstant) {
        return Json.createObjectBuilder()
                .add(COURT_CENTRE_ID, courtCentreId)
                .add(COURT_ROOM_ID, courtRoomId)
                .add(JURISDICTION, jurisdiction)
                .add(START_TIME, startInstant)
                .add(END_TIME, endInstant)
                .build().toString();
    }

    private static JsonObject parse(final String payload) {
        return createReader(new StringReader(payload)).readObject();
    }

    private static JsonObject firstSession(final String payload) {
        return parse(payload).getJsonArray(SESSIONS).getJsonObject(0);
    }

    private String seedMagistratesSession(final LocalDate sessionDate, final String courtRoomId, final String businessType,
                                          final String courtHouseId, final String ouCode) throws java.sql.SQLException {
        return seedTimedSession(sessionDate, courtRoomId, businessType, courtHouseId, ouCode, MAGISTRATES_2, "AD", 10, 17);
    }

    @SuppressWarnings("java:S107")
    private String seedTimedSession(final LocalDate sessionDate, final String courtRoomId, final String businessType,
                                    final String courtHouseId, final String ouCode, final String jurisdiction,
                                    final String courtSession, final int startHourUtc, final int endHourUtc) throws java.sql.SQLException {
        return seedTimedSession(sessionDate, courtRoomId, businessType, courtHouseId, ouCode, jurisdiction,
                courtSession, startHourUtc, endHourUtc, 360);
    }

    /** As {@link #seedSession}, with an explicit {@code court_session} and UTC session window. */
    @SuppressWarnings("java:S107")
    private String seedTimedSession(final LocalDate sessionDate, final String courtRoomId, final String businessType,
                                    final String courtHouseId, final String ouCode, final String jurisdiction,
                                    final String courtSession, final int startHourUtc, final int endHourUtc,
                                    final int availableDurationMinutes) throws java.sql.SQLException {
        final String id = UUID.randomUUID().toString();
        final Instant sessionStart = sessionDate.atTime(startHourUtc, 0).toInstant(ZoneOffset.UTC);
        final Instant sessionEnd = sessionDate.atTime(endHourUtc, 0).toInstant(ZoneOffset.UTC);

        final CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(id);
        cs.setListingProfileId(UUID.randomUUID().toString());
        cs.setOuCode(ouCode);
        cs.setCourtRoomId(courtRoomId);
        cs.setCourtRoomNumber(1);
        cs.setCourtHouseId(courtHouseId);
        cs.setCourtHouseName("Test Court");
        cs.setCourtRoomName("Court 1");
        cs.setOperationalUnit(ouCode);
        cs.setBusinessType(businessType);
        cs.setPanel("Adult");
        cs.setCourtSession(courtSession);
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
