package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.io.StringReader;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for POST /hearings/{hearingId}, media type
 * application/vnd.courtscheduler.move-hearing-to-past-date+json.
 *
 * <p>The endpoint books a past-dated session per sitting day, supporting both jurisdictions, a
 * mandatory room-scoped search ({@code courtRoomId}), a mandatory hearingStartTime (range-containment
 * session selection), and a multi-day [startDate, endDate] span returned as a {@code bookedSlots}
 * array. Sister unit tests live in {@code SlotsUpdateServiceTest}, {@code MoveHearingToPastDateApiTest}
 * and {@code MoveHearingToPastDateRepositoryTest}.
 */
class MoveHearingToPastDateIT extends AbstractIT {

    private static final String URL = "/hearings";
    private static final String ACCEPT = "application/vnd.courtscheduler.move-hearing-to-past-date+json";

    @Test
    void shouldBookSessionAndReturnSlotDetailsForPastDate() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        final String sessionId = seedMagistratesSession(pastDate, roomId, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject slot = firstBookedSlot(response.readEntity(String.class));
        assertThat(slot.getString("courtScheduleId"), is(sessionId));
        assertThat(slot.getString("source"), is("MOVE_TO_PAST_DATE"));
        assertThat(slot.getBoolean("isDraft"), is(false));

        assertThat("allocated_listings row written", bookedScheduleIds(hearingId).size(), is(1));
        assertThat(bookedScheduleIds(hearingId).get(0), is(sessionId));
    }

    @Test
    void shouldBookCrownSessionForPastDate() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        final String sessionId = seedSession(pastDate, roomId, "NGAP", centreId, "C01CY00", "CROWN", 10, 17);

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "CROWN", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(firstBookedSlot(response.readEntity(String.class)).getString("courtScheduleId"), is(sessionId));
    }

    /** Weekend sessions are real (magistrates remand courts sit Saturdays): a Saturday target books
     * like any other past day when a Saturday session exists - the calendar no longer rejects it. */
    @Test
    void shouldBookSaturdaySessionForPastDate() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate saturday = mostRecentSaturday();

        final String sessionId = seedMagistratesSession(saturday, roomId, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", saturday, null, "12:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(firstBookedSlot(response.readEntity(String.class)).getString("courtScheduleId"), is(sessionId));
        assertThat("allocated_listings row written", bookedScheduleIds(hearingId).size(), is(1));
    }

    @Test
    void shouldBookOnlyWorkingDaysForAMultiDayRange() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate startDate = pastWorkingDay(2);
        final LocalDate endDate = pastWorkingDay(1);

        final String session1 = seedMagistratesSession(startDate, roomId, "NGAP", centreId, "C01CY00");
        final String session2 = seedMagistratesSession(endDate, roomId, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", startDate, endDate, "12:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(response.readEntity(String.class));
        assertThat(body.getJsonArray("bookedSlots").size(), is(2));
        assertThat("both sitting days booked (weekend skipped)", bookedScheduleIds(hearingId).size(), is(2));
        assertThat(bookedScheduleIds(hearingId).contains(session1), is(true));
        assertThat(bookedScheduleIds(hearingId).contains(session2), is(true));
    }

    @Test
    void shouldStampSubmittedTimesAndSingleDayWindowDurationOnBookedSlot() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();
        seedMagistratesSession(pastDate, roomId, "NGAP", centreId, "C01CY00"); // session window 10:00-17:00

        // submitted 10:30 -> 10:50 = a 20-minute single-day window
        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                moveWindowPayload(centreId, roomId, "MAGISTRATES", pastDate + "T10:30:00.000Z", pastDate + "T10:50:00.000Z"));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject slot = firstBookedSlot(response.readEntity(String.class));
        // booked slot carries the SUBMITTED times (not the session's 10:00-17:00 window) + the computed window duration
        assertThat(slot.getString("sessionStartTime"), is(pastDate + "T10:30:00.000Z"));
        assertThat(slot.getString("sessionEndTime"), is(pastDate + "T10:50:00.000Z"));
        assertThat(slot.getInt("durationInMinutes"), is(20));
    }

    @Test
    void shouldStampSubmittedTimesAndFullCourtDayDurationPerDayForMultiDayMove() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate startDate = pastWorkingDay(2);
        final LocalDate endDate = pastWorkingDay(1);
        seedMagistratesSession(startDate, roomId, "NGAP", centreId, "C01CY00");
        seedMagistratesSession(endDate, roomId, "NGAP", centreId, "C01CY00");

        // multi-day 10:30 -> 17:00 : every sitting day is booked at a full court day (360 min), 10:30-17:00
        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                moveWindowPayload(centreId, roomId, "MAGISTRATES", startDate + "T10:30:00.000Z", endDate + "T17:00:00.000Z"));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final jakarta.json.JsonArray slots = parse(response.readEntity(String.class)).getJsonArray("bookedSlots");
        assertThat(slots.size(), is(2));
        for (int i = 0; i < slots.size(); i++) {
            final JsonObject slot = slots.getJsonObject(i);
            final String date = slot.getString("sessionDate");
            assertThat(slot.getString("sessionStartTime"), is(date + "T10:30:00.000Z"));
            assertThat(slot.getString("sessionEndTime"), is(date + "T17:00:00.000Z"));
            assertThat(slot.getInt("durationInMinutes"), is(360));
        }
    }

    @Test
    void shouldBookWithinTheRequestedRoom() throws Exception {
        final String centreId = randomUUID().toString();
        final String requestedRoom = randomUUID().toString();
        final String otherRoom = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        seedMagistratesSession(pastDate, otherRoom, "NGAP", centreId, "C01CY00");
        final String wanted = seedMagistratesSession(pastDate, requestedRoom, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, requestedRoom, "MAGISTRATES", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(firstBookedSlot(response.readEntity(String.class)).getString("courtScheduleId"), is(wanted));
    }

    @Test
    void shouldReturn422WhenSessionExistsButInADifferentRoom() throws Exception {
        final String centreId = randomUUID().toString();
        final String requestedRoom = randomUUID().toString();
        final String otherRoom = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // a session exists on the date/centre but only in a DIFFERENT room than the one requested
        seedMagistratesSession(pastDate, otherRoom, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, requestedRoom, "MAGISTRATES", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(422));
        assertThat(response.readEntity(String.class), containsString("NO_SESSION_FOUND"));
        assertThat("nothing booked", bookedScheduleIds(hearingId).size(), is(0));
    }

    @Test
    void shouldSelectSessionByHearingStartTime() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // both sessions share the requested room so the room-scoped search returns both and
        // range-containment on the startTime time-of-day is what discriminates AM from PM. They use
        // distinct businessTypes so the (oucode,room,businessType,date,session) unique index doesn't clash.
        final String amSession = seedSession(pastDate, roomId, "NGAP", centreId, "C01CY00", "MAGISTRATES", 9, 12);
        final String pmSession = seedSession(pastDate, roomId, "SESI", centreId, "C01CY00", "MAGISTRATES", 13, 17);

        // 10:00 lands the AM window, never the PM window
        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", pastDate, null, "10:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String booked = firstBookedSlot(response.readEntity(String.class)).getString("courtScheduleId");
        assertThat(booked, is(amSession));
        assertThat(booked, is(org.hamcrest.Matchers.not(pmSession)));
    }

    @Test
    void shouldReleasePriorAllocationAndRebookOnPastDate() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        final String oldSessionId = seedMagistratesSession(LocalDate.now().plusDays(14), roomId, "NGAP", centreId, "C01CY00");
        databaseSeeder.insertAllocatedListing(allocatedListing(oldSessionId, hearingId, 30));
        final String newSessionId = seedMagistratesSession(pastDate, roomId, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat("prior allocation released, single row remains", bookedScheduleIds(hearingId).size(), is(1));
        assertThat("hearing rebooked onto the past-date session", bookedScheduleIds(hearingId).get(0), is(newSessionId));
    }

    /** Moves are to past dates only - a future date is rejected with 422 FUTURE_DATE_NOT_ALLOWED
     * even when a matching session exists, and nothing is booked. */
    @Test
    void shouldReturn422WhenStartDateIsAfterToday() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate futureDate = nextWorkingDayAfterToday();

        seedMagistratesSession(futureDate, roomId, "NGAP", centreId, "C01CY00");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", futureDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(422));
        assertThat(response.readEntity(String.class), containsString("FUTURE_DATE_NOT_ALLOWED"));
        assertThat("nothing booked", bookedScheduleIds(hearingId).size(), is(0));
    }

    @Test
    void shouldReturn422WhenNoSessionAtCourtCentreOnDate() throws Exception {
        final String centreId = randomUUID().toString();
        final String roomId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, roomId, "MAGISTRATES", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(422));
        assertThat(response.readEntity(String.class), containsString("NO_SESSION_FOUND"));
    }

    @Test
    void shouldReturn400WhenCourtCentreIdIsMissing() throws Exception {
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // courtRoomId/startTime present so the 400 is unambiguously the missing courtCentreId
        final JsonObjectBuilder builder = createObjectBuilder()
                .add("courtRoomId", randomUUID().toString())
                .add("jurisdiction", "MAGISTRATES")
                .add("startTime", pastDate + "T12:00:00.000Z");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID, builder.build().toString());

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenCourtRoomIdIsMissing() throws Exception {
        final String centreId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate pastDate = lastWorkingDayBeforeToday();

        // courtRoomId omitted (now mandatory); movePayload skips it when null
        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                movePayload(centreId, null, "MAGISTRATES", pastDate, null, "12:00", 30));

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenStartTimeIsMissing() throws Exception {
        final String centreId = randomUUID().toString();
        final String hearingId = randomUUID().toString();

        // every other mandatory field present so the 400 is unambiguously the missing startTime
        final JsonObjectBuilder builder = createObjectBuilder()
                .add("courtCentreId", centreId)
                .add("courtRoomId", randomUUID().toString())
                .add("jurisdiction", "MAGISTRATES");

        final Response response = postCommand(URL + "/" + hearingId, ACCEPT, SYSTEM_USER_ID, builder.build().toString());

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    // --- helpers ---

    private List<String> bookedScheduleIds(final String hearingId) {
        return databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
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

    /** hearingId travels only in the URL path — the REST adapter injects it into the payload. */
    private static String movePayload(final String courtCentreId,
                                      final String courtRoomId,
                                      final String jurisdiction,
                                      final LocalDate startDate,
                                      final LocalDate endDate,
                                      final String timeHhmm,
                                      final Integer durationInMinutes) {
        final JsonObjectBuilder builder = createObjectBuilder()
                .add("courtCentreId", courtCentreId)
                .add("jurisdiction", jurisdiction)
                .add("startTime", startDate + "T" + timeHhmm + ":00.000Z");
        if (courtRoomId != null) {
            builder.add("courtRoomId", courtRoomId);
        }
        if (endDate != null) {
            builder.add("endTime", endDate + "T" + timeHhmm + ":00.000Z");
        }
        if (durationInMinutes != null) {
            builder.add("durationInMinutes", durationInMinutes);
        }
        return builder.build().toString();
    }

    /** hearingId rides in the URL path; explicit start/end instants (distinct times) so the booked slot's
     * submitted-time-of-day and computed duration can be asserted. */
    private static String moveWindowPayload(final String courtCentreId, final String courtRoomId,
                                            final String jurisdiction, final String startInstant, final String endInstant) {
        return createObjectBuilder()
                .add("courtCentreId", courtCentreId)
                .add("courtRoomId", courtRoomId)
                .add("jurisdiction", jurisdiction)
                .add("startTime", startInstant)
                .add("endTime", endInstant)
                .build().toString();
    }

    private static JsonObject parse(final String payload) {
        return createReader(new StringReader(payload)).readObject();
    }

    private static JsonObject firstBookedSlot(final String payload) {
        return parse(payload).getJsonArray("bookedSlots").getJsonObject(0);
    }

    private String seedMagistratesSession(final LocalDate sessionDate,
                                          final String courtRoomId,
                                          final String businessType,
                                          final String courtHouseId,
                                          final String ouCode) throws java.sql.SQLException {
        return seedSession(sessionDate, courtRoomId, businessType, courtHouseId, ouCode, "MAGISTRATES", 10, 17);
    }

    private String seedSession(final LocalDate sessionDate,
                               final String courtRoomId,
                               final String businessType,
                               final String courtHouseId,
                               final String ouCode,
                               final String jurisdiction,
                               final int startHour,
                               final int endHour) throws java.sql.SQLException {
        final String id = randomUUID().toString();
        final Date sessionStart = Date.from(sessionDate.atTime(startHour, 0).toInstant(ZoneOffset.UTC));
        final Date sessionEnd = Date.from(sessionDate.atTime(endHour, 0).toInstant(ZoneOffset.UTC));

        final CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(id);
        cs.setListingProfileId(randomUUID().toString());
        cs.setOuCode(ouCode);
        cs.setCourtRoomId(courtRoomId);
        cs.setCourtRoomNumber(1);
        cs.setCourtHouseId(courtHouseId);
        cs.setCourtHouseName("Test Court");
        cs.setCourtRoomName("Court 1");
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
        cs.setAvailableDuration(360);
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

    private static AllocatedListing allocatedListing(final String courtScheduleId,
                                                     final String hearingId,
                                                     final int duration) {
        final AllocatedListing al = new AllocatedListing();
        al.setId(randomUUID().toString());
        al.setCourtScheduleId(courtScheduleId);
        al.setBookingId(randomUUID().toString());
        al.setHearingId(hearingId);
        al.setOucode("C01CY00");
        al.setCourtRoomId(1);
        al.setRotaBusinessType("NGAP");
        al.setDuration(duration);
        al.setHearingStartTime(new Timestamp(System.currentTimeMillis()));
        al.setSource("MOVE_TO_PAST_DATE");
        return al;
    }
}
