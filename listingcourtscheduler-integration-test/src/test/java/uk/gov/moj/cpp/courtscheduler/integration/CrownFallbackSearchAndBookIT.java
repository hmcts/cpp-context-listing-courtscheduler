package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for the CROWN single-day fallback search-and-book path, now served by
 * {@code POST /hearings/{hearingId}} with media type
 * {@code application/vnd.courtscheduler.crown.search.and.book+json} (was the retired
 * {@code GET /crownfallbacksearchandbook/hearingslots}).
 *
 * <p>The request carries a {@code courtCentreId}, a {@code hearingDate}, a small
 * {@code durationInMinutes} (≤360 selects the single-day path), and optionally a
 * {@code courtRoomId}. No {@code courtScheduleId} anchor is supplied — the engine picks any
 * available session at the centre+date, relaxing MAGS rota matching (no businessType filter).
 *
 * <p>Sister unit tests live in {@code SlotsUpdateServiceTest.CrownFallbackSearchAndBook}; these ITs
 * cover behaviours that need the real DB: native SQL predicate correctness against
 * {@code court_schedule.court_house_id}, availability-driven tier selection, and idempotency via
 * {@code allocated_listings} inspection.
 */
class CrownFallbackSearchAndBookIT extends AbstractIT {

    private static final String ACCEPT = "application/vnd.courtscheduler.crown.search.and.book+json";

    @Test
    void shouldBookNonDraftSessionWhenCourtRoomIdSuppliedAndSessionAvailable() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        final String sessionId = seedSession(date, roomId, "CR", centreId, "C01CY00", false, false, 360);

        final Response response = callFallback(hearingId, centreId, roomId, date, 10, "CROWN_FB_LIST");

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        assertThat(body.getString("courtScheduleId"), is(sessionId));
        assertThat(body.getString("source"), is("CROWN_FB_LIST"));

        final List<String> booked = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
        assertThat("allocated_listings row written", booked.size(), is(1));
        assertThat(booked.get(0), is(sessionId));
    }

    @Test
    void shouldBookDraftSessionWhenCourtRoomIdOmitted() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        final String draftId = seedSession(date, roomId, "CR", centreId, "C01CY00", true, false, 360);

        final Response response = callFallback(hearingId, centreId, null, date, 10, "CROWN_FB_LIST");

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        assertThat("draft session booked when no courtRoomId supplied",
                body.getString("courtScheduleId"), is(draftId));
    }

    @Test
    void shouldReturn422WhenNoSessionAtCourtCentreOnDate() {
        final String centreId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        // no seeded sessions → fallback exhausts all tiers
        final Response response = callFallback(hearingId, centreId, null, date, 10, "CROWN_FB_LIST");

        assertThat(response.getStatus(), is(422));
        assertThat(response.readEntity(String.class), containsString("NO_SESSION_FOUND"));
    }

    @Test
    void shouldReturnExistingAllocationOnIdempotentReplay() throws Exception {
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        final String sessionId = seedSession(date, roomId, "CR", centreId, "C01CY00", false, false, 360);

        final Response first = callFallback(hearingId, centreId, roomId, date, 10, "CROWN_FB_LIST");
        assertThat(first.getStatus(), is(OK.getStatusCode()));
        final JsonObject firstBody = parse(body(first));
        assertThat("first call returns a courtScheduleId", firstBody.getString("courtScheduleId"), is(sessionId));
        assertThat("first call writes one allocated_listings row",
                databaseReader.allocatedListings().stream()
                        .filter(al -> hearingId.equals(al.getHearingId())).count(), is(1L));

        final Response second = callFallback(hearingId, centreId, roomId, date, 10, "CROWN_FB_LIST");
        assertThat(second.getStatus(), is(OK.getStatusCode()));
        final JsonObject secondBody = parse(body(second));
        assertThat("idempotent replay returns same courtScheduleId",
                secondBody.getString("courtScheduleId"), is(sessionId));
        assertThat("no additional row written on replay",
                databaseReader.allocatedListings().stream()
                        .filter(al -> hearingId.equals(al.getHearingId())).count(), is(1L));
    }

    @Test
    void shouldIgnoreBusinessTypeAndPickAnySessionOnCentrePlusDate() throws Exception {
        // Relaxed matching: a CR session at the centre+date should be picked even when the caller
        // doesn't mention businessType at all. The MAGS search-and-book would have a tighter predicate.
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        final String id = seedSession(date, roomId, "UNUSUAL_BT", centreId, "C01CY00", false, false, 360);

        final Response response = callFallback(hearingId, centreId, roomId, date, 10, "CROWN_FB_ADJOURN");

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        assertThat(body.getString("courtScheduleId"), is(id));
        assertThat(body.getString("source"), is("CROWN_FB_ADJOURN"));
    }

    // NOTE: source-label IT coverage intentionally omitted.
    //
    // End-to-end verification that the caller's CROWN_FB_LIST/ADJOURN/UPDATE label lands on
    // allocated_listings.source requires the Crown fallback path's follow-up UPDATE to run within
    // the command-handler's JTA transaction — which the current plumbing (save-based override
    // through AllocatedListingRepository.updateSourceByHearingId) doesn't reliably guarantee
    // outside the command-dispatch boundary. The label IS correctly set on the AllocatedSlot
    // before the save pipeline and is covered at the unit-test level by
    // SlotsUpdateServiceTest.CrownFallbackSearchAndBook.shouldPropagateSourceLabelToAllocatedSlot.
    //
    // Tracked as a follow-up: the label-persistence flow needs either (a) a dedicated
    // @Transactional wrapper service method, or (b) the existing saveAllocatedListing pipeline
    // amended to not null out source between AllocatedSlot and AllocatedListing. Both options
    // are non-trivial and best handled in a dedicated ticket.

    // --- helpers ---

    private Response callFallback(final String hearingId,
                                   final String courtCentreId,
                                   final String courtRoomId,
                                   final LocalDate hearingDate,
                                   final int durationInMinutes,
                                   final String source) {
        final jakarta.json.JsonObjectBuilder b = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId)
                .add("hearingDate", hearingDate.toString())
                .add("durationInMinutes", durationInMinutes)
                .add("source", source);
        if (courtRoomId != null) {
            b.add("courtRoomId", courtRoomId);
        }
        return postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID, b.build().toString());
    }

    private static String body(final Response response) {
        return response.readEntity(String.class);
    }

    private static JsonObject parse(final String payload) {
        return createReader(new StringReader(payload)).readObject();
    }

    private String seedSession(final LocalDate sessionDate,
                               final String courtRoomId,
                               final String businessType,
                               final String courtHouseId,
                               final String ouCode,
                               final boolean isDraft,
                               final boolean overbookingAllowed,
                               final int maxDuration) throws java.sql.SQLException {
        final String id = UUID.randomUUID().toString();
        final Date sessionStart = Date.from(sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC));
        final Date sessionEnd = Date.from(sessionDate.atTime(17, 0).toInstant(ZoneOffset.UTC));

        final CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(id);
        cs.setListingProfileId(UUID.randomUUID().toString());
        cs.setOuCode(ouCode);
        cs.setCourtRoomId(courtRoomId);
        cs.setCourtRoomNumber(1);
        cs.setCourtHouseId(courtHouseId);
        cs.setCourtHouseName("Test Crown Court");
        cs.setCourtRoomName("Court 1");
        cs.setOperationalUnit(ouCode);
        cs.setBusinessType(businessType);
        cs.setPanel("Adult");
        cs.setCourtSession("AD");
        cs.setActive(true);
        cs.setSlotBased(false);
        cs.setSessionDate(sessionDate);
        cs.setMaxSlots(0);
        cs.setMaxDuration(maxDuration);
        cs.setAvailableSlots(0);
        cs.setAvailableDuration(maxDuration);
        cs.setSupportAdSplit(false);
        cs.setMaxAdMorningDuration(maxDuration / 2);
        cs.setMaxAdAfternoonDuration(maxDuration / 2);
        cs.setSessionStartTime(sessionStart);
        cs.setSessionEndTime(sessionEnd);
        cs.setNationalBreakTime(sessionStart);
        cs.setIsOverbookingAllowed(overbookingAllowed);
        cs.setIsDraft(isDraft);
        cs.setJurisdiction("CROWN");
        cs.setTotalBookedMorning(0);
        cs.setTotalBookedAfternoon(0);
        cs.setTotalBooked(0);

        databaseSeeder.insertCourtSchedule(cs);
        return id;
    }
}
