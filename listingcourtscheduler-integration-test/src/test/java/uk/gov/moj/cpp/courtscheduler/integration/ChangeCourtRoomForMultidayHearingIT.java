package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@code courtscheduler.change-court-room-for-multiday-hearing}, served by
 * {@code POST /hearings/{hearingId}} with media type
 * {@code application/vnd.courtscheduler.change-court-room-for-multiday-hearing+json}.
 *
 * <p>Mirrors {@link MoveHearingToPastDateIT}'s helper style (session seeding, {@code bookedScheduleIds}
 * assertions via a direct DB read) but books the hearing's ORIGINAL allocations directly via
 * {@link uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder#insertAllocatedListing}
 * (the established pattern in this module for arranging "hearing already booked onto session X" —
 * see {@code HearingIdIT}), since the feature under test re-allocates EXISTING day(s) rather than
 * searching-and-booking new ones.
 *
 * <p>Sister unit tests live in {@code SlotsUpdateServiceTest} and {@code CourtSchedulerApiTest}
 * (handler-level day parsing / error mapping) and {@code CourtScheduleRepositoryTest}
 * (date-scoped release semantics).
 */
class ChangeCourtRoomForMultidayHearingIT extends AbstractIT {

    private static final String ACCEPT = "application/vnd.courtscheduler.change-court-room-for-multiday-hearing+json";
    private static final int DURATION_MINUTES = 360;

    @Test
    void shouldChangeCourtRoomForSelectedDaysLeavingUntouchedDayAndRestoringAvailability() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String room1 = UUID.randomUUID().toString();
        final String room2 = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = LocalDate.now().plusDays(30);
        final LocalDate day2 = day1.plusDays(1);
        final LocalDate day3 = day1.plusDays(2);

        // room1: the hearing's current 3-day booking, day2/day3 shown as fully committed (0 available).
        final String d1 = seedSession(day1, room1, "CR", centreId, "OU-CRN10", "CROWN", DURATION_MINUTES);
        final String d2 = seedSession(day2, room1, "CR", centreId, "OU-CRN10", "CROWN", 0);
        final String d3 = seedSession(day3, room1, "CR", centreId, "OU-CRN10", "CROWN", 0);

        // room2: the new target sessions for day2/day3, with capacity available.
        final String d2b = seedSession(day2, room2, "CR", centreId, "OU-CRN10", "CROWN", DURATION_MINUTES);
        final String d3b = seedSession(day3, room2, "CR", centreId, "OU-CRN10", "CROWN", DURATION_MINUTES);

        book(hearingId, d1, day1, DURATION_MINUTES);
        book(hearingId, d2, day2, DURATION_MINUTES);
        book(hearingId, d3, day3, DURATION_MINUTES);

        final Response response = callChangeCourtRoom(hearingId,
                dayEntry(day2, d2b, DURATION_MINUTES),
                dayEntry(day3, d3b, DURATION_MINUTES));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"CHANGE_COURT_ROOM_MULTIDAY\""));
        assertThat(extractAllocatedScheduleIds(payload), containsInAnyOrder(d2b, d3b));

        assertThat("day1 untouched, day2/day3 moved to room2",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2b, d3b));

        assertThat("room1 day2 availability restored to full",
                databaseReader.courtScheduleById(d2).getAvailableDuration(), is(DURATION_MINUTES));
        assertThat("room1 day3 availability restored to full",
                databaseReader.courtScheduleById(d3).getAvailableDuration(), is(DURATION_MINUTES));
    }

    @Test
    void shouldRejectWhenNoAllocationOnDate() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String room1 = UUID.randomUUID().toString();
        final String room2 = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = LocalDate.now().plusDays(40);
        final LocalDate day2 = day1.plusDays(1);

        final String d1 = seedSession(day1, room1, "CR", centreId, "OU-CRN11", "CROWN", DURATION_MINUTES);
        final String d2b = seedSession(day2, room2, "CR", centreId, "OU-CRN11", "CROWN", DURATION_MINUTES);

        book(hearingId, d1, day1, DURATION_MINUTES);

        final Response response = callChangeCourtRoom(hearingId, dayEntry(day2, d2b, DURATION_MINUTES));

        assertThat(response.getStatus(), is(422));
        assertThat(body(response), containsString("\"errorCode\":\"NO_ALLOCATION_ON_DATE\""));
        assertThat("unchanged - the hearing is still only booked on day1",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1));
    }

    @Test
    void shouldRejectUnknownTargetSessionWithZeroMutations() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String room1 = UUID.randomUUID().toString();
        final String room2 = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = LocalDate.now().plusDays(50);
        final LocalDate day2 = day1.plusDays(1);
        final LocalDate day3 = day1.plusDays(2);

        final String d1 = seedSession(day1, room1, "CR", centreId, "OU-CRN12", "CROWN", DURATION_MINUTES);
        final String d2 = seedSession(day2, room1, "CR", centreId, "OU-CRN12", "CROWN", 0);
        final String d3 = seedSession(day3, room1, "CR", centreId, "OU-CRN12", "CROWN", 0);
        final String d3b = seedSession(day3, room2, "CR", centreId, "OU-CRN12", "CROWN", DURATION_MINUTES);

        book(hearingId, d1, day1, DURATION_MINUTES);
        book(hearingId, d2, day2, DURATION_MINUTES);
        book(hearingId, d3, day3, DURATION_MINUTES);

        final String unknownTarget = UUID.randomUUID().toString();
        final Response response = callChangeCourtRoom(hearingId,
                dayEntry(day2, unknownTarget, DURATION_MINUTES),
                dayEntry(day3, d3b, DURATION_MINUTES));

        assertThat(response.getStatus(), is(422));
        assertThat(body(response), containsString("\"errorCode\":\"NO_SESSION_FOUND\""));
        assertThat("zero mutations - even the valid day3->d3b in the same request is untouched",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2, d3));
    }

    @Test
    void shouldTreatSameSessionAsNoop() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String room1 = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate day1 = LocalDate.now().plusDays(60);
        final LocalDate day2 = day1.plusDays(1);

        final String d1 = seedSession(day1, room1, "CR", centreId, "OU-CRN13", "CROWN", DURATION_MINUTES);
        final String d2 = seedSession(day2, room1, "CR", centreId, "OU-CRN13", "CROWN", DURATION_MINUTES);

        book(hearingId, d1, day1, DURATION_MINUTES);
        book(hearingId, d2, day2, DURATION_MINUTES);

        final Response response = callChangeCourtRoom(hearingId, dayEntry(day2, d2, DURATION_MINUTES));

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String payload = body(response);
        assertThat(payload, containsString("\"source\":\"CHANGE_COURT_ROOM_MULTIDAY\""));
        assertThat(extractAllocatedScheduleIds(payload), hasItem(d2));

        assertThat("no-op leaves both allocations exactly as they were",
                bookedScheduleIds(hearingId), containsInAnyOrder(d1, d2));
        assertThat("no-op does not touch availability",
                databaseReader.courtScheduleById(d2).getAvailableDuration(), is(DURATION_MINUTES));
    }

    // --- helpers ---

    private Response callChangeCourtRoom(final String hearingId, final JsonObjectBuilder... days) {
        final JsonArrayBuilder daysArray = Json.createArrayBuilder();
        for (final JsonObjectBuilder day : days) {
            daysArray.add(day);
        }
        final String requestPayload = Json.createObjectBuilder().add("days", daysArray).build().toString();
        return postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID, requestPayload);
    }

    private static JsonObjectBuilder dayEntry(final LocalDate sessionDate, final String courtScheduleId, final int durationInMinutes) {
        return Json.createObjectBuilder()
                .add("sessionDate", sessionDate.toString())
                .add("courtScheduleId", courtScheduleId)
                .add("durationInMinutes", durationInMinutes);
    }

    /** Book {@code hearingId} directly onto {@code courtScheduleId} - the established IT pattern for arranging an existing allocation (see {@code HearingIdIT}). */
    private void book(final String hearingId, final String courtScheduleId, final LocalDate sessionDate, final int durationMinutes) throws java.sql.SQLException {
        final AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(UUID.randomUUID().toString());
        allocatedListing.setBookingId(UUID.randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setOucode("OU-CRN10");
        allocatedListing.setCourtRoomId(1);
        allocatedListing.setRotaBusinessType("CR");
        allocatedListing.setDuration(durationMinutes);
        allocatedListing.setHearingStartTime(Date.from(sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC)));
        databaseSeeder.insertAllocatedListing(allocatedListing);
    }

    private List<String> bookedScheduleIds(final String hearingId) {
        return databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
    }

    private static String body(final Response response) {
        return response.readEntity(String.class);
    }

    private static List<String> extractAllocatedScheduleIds(final String payload) {
        final JsonObject json = createReader(new StringReader(payload)).readObject();
        if (!json.containsKey("allocatedSchedules") || json.isNull("allocatedSchedules")) {
            return List.of();
        }
        final JsonArray arr = json.getJsonArray("allocatedSchedules");
        return arr.getValuesAs(JsonObject.class).stream()
                .map(o -> o.getString("courtScheduleId"))
                .collect(Collectors.toList());
    }

    /**
     * Insert an {@code court_session=AD}, {@code active=true} court_schedule at the centre and return its id.
     * {@code court_house_id} is set to {@code courtCentreId} - the column the centre search keys on.
     * Copied from {@link MoveHearingToPastDateIT#seedSession} with an added {@code availableDurationMinutes}
     * parameter, so tests can seed a session as already fully committed (0) to exercise the
     * availability-restoration assertion.
     */
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
        cs.setMaxDuration(DURATION_MINUTES);
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
