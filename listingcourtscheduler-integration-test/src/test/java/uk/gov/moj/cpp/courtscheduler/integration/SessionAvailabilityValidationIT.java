package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

class SessionAvailabilityValidationIT extends AbstractIT {

    private static final String VALIDATE_URL = "/validate-session-availability";
    private static final String CONTENT_TYPE =
            "application/vnd.courtscheduler.validate.session.availability+json";

    @Test
    void shouldReturn400WhenCourtScheduleIdNotFound() {
        final String payload =
                "{\"courtScheduleIdList\":[{\"courtScheduleId\":\"00000000-0000-0000-0000-000000000001\"}],\"duration\":30}";

        Response response = postCommand(VALIDATE_URL, CONTENT_TYPE, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String body = response.readEntity(String.class);
        assertThat(body, containsString("not found"));
    }

    @Test
    void shouldReturn400WhenCourtScheduleIdListIsMissing() {
        final String payload = "{\"duration\":30}";

        Response response = postCommand(VALIDATE_URL, CONTENT_TYPE, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturn400WhenMultipleSchedulesHaveDifferentJurisdictions() throws Exception {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        databaseSeeder.insertCourtSchedule(buildCourtSchedule(id1, true, 10, false, "CROWN", "centre-1"));
        databaseSeeder.insertCourtSchedule(buildCourtSchedule(id2, true, 10, false, "MAGISTRATES", "centre-1"));

        final String payload = "{\"courtScheduleIdList\":["
                + "{\"courtScheduleId\":\"" + id1 + "\"},"
                + "{\"courtScheduleId\":\"" + id2 + "\"}"
                + "],\"duration\":30}";

        Response response = postCommand(VALIDATE_URL, CONTENT_TYPE, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String body = response.readEntity(String.class);
        assertThat(body, containsString("same jurisdiction"));
    }

    @Test
    void shouldReturn400WhenSlotBasedScheduleIsFullyBookedIgnoringDuration() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule cs = buildCourtSchedule(courtScheduleId, true, 2, false, "MAGISTRATES", "centre-1");
        databaseSeeder.insertCourtSchedule(cs);

        for (int i = 0; i < 2; i++) {
            databaseSeeder.insertAllocatedListing(buildAllocatedListing(courtScheduleId));
        }

        // Large duration value — must be ignored for slot-based sessions (SPRDT-725 bug)
        final String payload = "{\"courtScheduleIdList\":["
                + "{\"courtScheduleId\":\"" + courtScheduleId + "\"}"
                + "],\"duration\":9999}";

        Response response = postCommand(VALIDATE_URL, CONTENT_TYPE, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String body = response.readEntity(String.class);
        assertThat(body, containsString("no longer available"));
    }

    @Test
    void shouldReturn400WhenSlotBasedScheduleHasZeroMaxSlotsAndNoAllocations() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        databaseSeeder.insertCourtSchedule(buildCourtSchedule(courtScheduleId, true, 0, false, "MAGISTRATES", "centre-1"));

        final String payload = "{\"courtScheduleIdList\":["
                + "{\"courtScheduleId\":\"" + courtScheduleId + "\"}"
                + "],\"duration\":100}";

        Response response = postCommand(VALIDATE_URL, CONTENT_TYPE, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        final String body = response.readEntity(String.class);
        assertThat(body, containsString("no longer available"));
    }

    @Test
    void shouldReturn200WhenSlotBasedScheduleHasAvailableSlots() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        databaseSeeder.insertCourtSchedule(buildCourtSchedule(courtScheduleId, true, 5, false, "CROWN", "centre-1"));

        for (int i = 0; i < 2; i++) {
            databaseSeeder.insertAllocatedListing(buildAllocatedListing(courtScheduleId));
        }

        final String payload = "{\"courtScheduleIdList\":["
                + "{\"courtScheduleId\":\"" + courtScheduleId + "\"}"
                + "],\"duration\":999}";

        Response response = postCommand(VALIDATE_URL, CONTENT_TYPE, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    /**
     * Backward-compatibility guard: cpp-context-listing's
     * {@code HearingSlotsService.validateSessionAvailability()} (the real production caller)
     * sends {@code Accept: application/json} (the legacy default — the RAML declared no response
     * media type here either). The migrated OpenAPI declares
     * {@code produces: application/vnd.courtscheduler.validate.session.availability.response+json},
     * so with strict content negotiation that Accept header must NOT be rejected with 406. (The
     * happy-path test above doesn't catch this — it sends no Accept, so RestTemplate uses
     * {@code *}/{@code *}, which matches any produces.) Same fix as ProvisionalBookingIT.
     */
    @Test
    void shouldAcceptLegacyApplicationJsonAcceptHeader() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        databaseSeeder.insertCourtSchedule(buildCourtSchedule(courtScheduleId, true, 5, false, "CROWN", "centre-1"));

        final String payload = "{\"courtScheduleIdList\":["
                + "{\"courtScheduleId\":\"" + courtScheduleId + "\"}"
                + "],\"duration\":30}";

        final Response response = postCommandWithAccept(VALIDATE_URL, CONTENT_TYPE, "application/json", SYSTEM_USER_ID, payload);

        assertThat("legacy Accept: application/json must not be rejected with 406 Not Acceptable",
                response.getStatus(), is(not(NOT_ACCEPTABLE.getStatusCode())));
        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    private static CourtSchedule buildCourtSchedule(String id, boolean slotBased, int maxSlots,
                                                     boolean overbookingAllowed, String jurisdiction, String courtHouseId) {
        Date now = new Date();
        CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(id);
        cs.setListingProfileId(UUID.randomUUID().toString());
        cs.setOuCode("B01LY");
        cs.setCourtRoomId(UUID.randomUUID().toString());
        cs.setCourtRoomNumber(1);
        cs.setCourtHouseId(courtHouseId);
        cs.setCourtHouseName("Test Court");
        cs.setCourtRoomName("Room 1");
        cs.setOperationalUnit("OU1");
        cs.setBusinessType("BT1");
        cs.setPanel("Adult");
        cs.setCourtSession("AD");
        cs.setActive(true);
        cs.setSlotBased(slotBased);
        cs.setSessionDate(LocalDate.now().plusDays(7));
        cs.setMaxSlots(maxSlots);
        cs.setMaxDuration(360);
        cs.setAvailableSlots(maxSlots);
        cs.setAvailableDuration(360);
        cs.setSupportAdSplit(false);
        cs.setMaxAdMorningDuration(180);
        cs.setMaxAdAfternoonDuration(180);
        cs.setSessionStartTime(now);
        cs.setSessionEndTime(now);
        cs.setNationalBreakTime(now);
        cs.setIsOverbookingAllowed(overbookingAllowed);
        cs.setIsDraft(false);
        cs.setJurisdiction(jurisdiction);
        return cs;
    }

    private static AllocatedListing buildAllocatedListing(String courtScheduleId) {
        AllocatedListing al = new AllocatedListing();
        al.setId(UUID.randomUUID().toString());
        al.setCourtScheduleId(courtScheduleId);
        al.setBookingId(UUID.randomUUID().toString());
        al.setHearingId(UUID.randomUUID().toString());
        al.setOucode("B01LY");
        al.setCourtRoomId(1);
        al.setRotaBusinessType("BT1");
        al.setDuration(30);
        return al;
    }
}
