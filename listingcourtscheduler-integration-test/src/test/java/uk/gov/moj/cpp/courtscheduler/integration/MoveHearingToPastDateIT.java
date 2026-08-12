package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
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

        final Response response = callMove(centreId, "MAGISTRATES", day, null, 360, hearingId);

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

        final Response response = callMove(centreId, "CROWN", day, null, 360, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"MOVE_TO_PAST_DATE\""));
        assertThat(extractSessionIds(payload), contains(sessionId));
        assertThat("one allocated_listings row booked for the hearing",
                bookedScheduleIds(hearingId), contains(sessionId));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
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
        final Response response = callMove(centreId, "MAGISTRATES", day1, null, 720, hearingId);

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

        final Response response = callMove(centreId, "CROWN", day1, null, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"MOVE_TO_PAST_DATE\""));
        assertThat(extractSessionIds(payload), contains(d1, d2));
        assertThat("both consecutive days booked for the hearing",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2));
        assertThat("persisted allocated_listings.source", bookedSources(hearingId), contains("MOVE_TO_PAST_DATE"));
    }

    // --- helpers ---

    /** POST move-hearing-to-past-date. hearingId travels in the path only; no courtScheduleId anchor. */
    private Response callMove(final String courtCentreId,
                              final String jurisdiction,
                              final LocalDate startDate,
                              final LocalDate endDate,
                              final int durationInMinutes,
                              final String hearingId) {
        final jakarta.json.JsonObjectBuilder b = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId)
                .add("jurisdiction", jurisdiction)
                .add("startDate", startDate.toString())
                .add("durationInMinutes", durationInMinutes);
        if (endDate != null) {
            b.add("endDate", endDate.toString());
        }
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
}
