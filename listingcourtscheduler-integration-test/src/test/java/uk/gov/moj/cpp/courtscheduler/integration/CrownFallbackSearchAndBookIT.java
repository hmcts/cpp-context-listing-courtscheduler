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
 * session at the centre+date, relaxing MAGS rota matching (no businessType filter) and ignoring
 * remaining capacity entirely (search-and-book is overbooking-exempt, SPRDT-1159).
 *
 * <p>Sister unit tests live in {@code SlotsUpdateServiceTest.CrownFallbackSearchAndBook}; these ITs
 * cover behaviours that need the real DB: native SQL predicate correctness against
 * {@code court_schedule.court_house_id}, capacity-exempt selection, on-the-fly session creation
 * attributes, and idempotency via {@code allocated_listings} inspection.
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

        // no seeded sessions AND no ouCode on the request → auto-create has neither a template nor
        // request metadata to build from (SPRDT-1283), so the fallback still exhausts all tiers
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

    @Test
    void shouldBookSessionRegardlessOfRemainingCapacity() throws Exception {
        // SPRDT-1159: search-and-book is overbooking-exempt — a session whose remaining capacity is
        // below the requested duration (and which does NOT allow overbooking) still gets booked.
        // Before this change the strict pass skipped it and the relaxed pass required
        // is_overbooking_allowed=true, so this request would have found no session.
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        final String sessionId = seedSession(date, roomId, "CR", centreId, "C01CY00", false, false, 5);

        final Response response = callFallback(hearingId, centreId, roomId, date, 10, "CROWN_FB_LIST");

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        assertThat(body.getString("courtScheduleId"), is(sessionId));
        assertThat("overbooked flag reports the capacity shortfall", body.getBoolean("overbooked"), is(true));
    }

    @Test
    void shouldAutoCreateFinalLngAdSessionWhenRoomSuppliedAndNoSessionOnDate() throws Exception {
        // SPRDT-1159 auto-create shape (room pinned): duration-based AD session, businessType LNG,
        // fixed 360-minute capacity, overbooking disallowed, FINAL (is_draft=false).
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        // Template session at the same centre+room but on a different date: the target date has no
        // session, so the engine must create one, copying only residual metadata from this template.
        seedSession(date.plusDays(7), roomId, "CR", centreId, "C01CY00", false, false, 300);

        // An afternoon hearing time: the created session must still end at 17:00 (SPRDT-1324), not
        // at start + the 360-minute capacity, which would have put it at 18:30.
        final Response response = postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                Json.createObjectBuilder()
                        .add("courtCentreId", centreId)
                        .add("courtRoomId", roomId)
                        .add("hearingDate", date.toString())
                        .add("durationInMinutes", 10)
                        .add("earliestHearingTime", date + "T12:30:00Z")
                        .add("source", "CROWN_FB_LIST")
                        .build().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        final String createdId = body.getString("courtScheduleId");
        assertThat(body.getBoolean("isDraft"), is(false));

        final CourtSchedule created = databaseReader.courtScheduleById(createdId);
        assertThat(created.getBusinessType(), is("LNG"));
        assertThat(created.getCourtSession(), is("AD"));
        assertThat(created.getMaxDuration(), is(360));
        assertThat(created.getIsOverbookingAllowed(), is(false));
        assertThat(created.getIsDraft(), is(false));
        assertThat(created.isSlotBased(), is(false));
        assertThat(created.getSupportAdSplit(), is(false));
        assertThat(created.getCourtRoomId(), is(roomId));
        assertThat(created.getSessionDate(), is(date));
        assertThat(new java.sql.Timestamp(created.getSessionStartTime().getTime()).toLocalDateTime(),
                is(date.atTime(12, 30)));
        assertThat(new java.sql.Timestamp(created.getSessionEndTime().getTime()).toLocalDateTime(),
                is(date.atTime(17, 0)));
    }

    @Test
    void shouldAutoCreateDraftGencAdSessionWhenRoomOmittedAndNoSessionOnDate() throws Exception {
        // SPRDT-1159 auto-create shape (no room): duration-based AD session, businessType GENC,
        // fixed 360-minute capacity, overbooking disallowed, DRAFT (is_draft=true).
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        seedSession(date.plusDays(7), roomId, "CR", centreId, "C01CY00", false, false, 300);

        final Response response = callFallback(hearingId, centreId, null, date, 10, "CROWN_FB_LIST");

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        final String createdId = body.getString("courtScheduleId");
        assertThat(body.getBoolean("isDraft"), is(true));

        final CourtSchedule created = databaseReader.courtScheduleById(createdId);
        assertThat(created.getBusinessType(), is("GENC"));
        assertThat(created.getCourtSession(), is("AD"));
        assertThat(created.getMaxDuration(), is(360));
        assertThat(created.getIsOverbookingAllowed(), is(false));
        assertThat(created.getIsDraft(), is(true));
        assertThat(created.isSlotBased(), is(false));
        assertThat(created.getSupportAdSplit(), is(false));
        // SPRDT-1324: the DRAFT variant ends at the same fixed all-day default
        assertThat(new java.sql.Timestamp(created.getSessionEndTime().getTime()).toLocalDateTime(),
                is(date.atTime(17, 0)));
    }

    @Test
    void shouldAutoCreateSessionAtNeverSeededCentreWhenOuCodeSupplied() throws Exception {
        // SPRDT-1283 part 1: a centre with NO session at all to copy metadata from no longer blocks
        // auto-creation — the session is built from the request's own metadata (ouCode/names) and
        // starts at the requested hearing time.
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);
        final String hearingTime = date + "T11:30:00Z";

        // deliberately NO seeded session anywhere at this centre
        final Response response = postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                Json.createObjectBuilder()
                        .add("courtCentreId", centreId)
                        .add("courtRoomId", roomId)
                        .add("hearingDate", date.toString())
                        .add("durationInMinutes", 60)
                        .add("earliestHearingTime", hearingTime)
                        .add("source", "CROWN_FB_LIST")
                        .add("ouCode", "C99XX00")
                        .add("courtCentreName", "Never Seeded Crown Court")
                        .add("courtRoomName", "Courtroom 7")
                        .build().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        final String createdId = body.getString("courtScheduleId");
        assertThat(body.getBoolean("isDraft"), is(false));

        final CourtSchedule created = databaseReader.courtScheduleById(createdId);
        assertThat(created.getOuCode(), is("C99XX00"));
        assertThat(created.getCourtHouseId(), is(centreId));
        assertThat(created.getCourtHouseName(), is("Never Seeded Crown Court"));
        assertThat(created.getCourtRoomId(), is(roomId));
        assertThat(created.getCourtRoomName(), is("Courtroom 7"));
        assertThat(created.getListingProfileId(), is("CROWN-FB-AUTO"));
        assertThat(created.getBusinessType(), is("LNG"));
        assertThat(created.getCourtSession(), is("AD"));
        assertThat(created.getIsDraft(), is(false));
        assertThat(created.getMaxDuration(), is(360));
        assertThat(created.getSessionDate(), is(date));
        // session starts at the requested hearing time — compared as the stored UTC wall-clock
        // (the app runs in UTC; asserting instants would break under a non-UTC test JVM)
        assertThat(new java.sql.Timestamp(created.getSessionStartTime().getTime()).toLocalDateTime(),
                is(date.atTime(11, 30)));
        // SPRDT-1324: an AD session ends at the fixed all-day default, never start + capacity
        assertThat(new java.sql.Timestamp(created.getSessionEndTime().getTime()).toLocalDateTime(),
                is(date.atTime(17, 0)));

        final List<AllocatedListing> booked = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        assertThat("hearing booked onto the auto-created session", booked.size(), is(1));
        assertThat(booked.get(0).getCourtScheduleId(), is(createdId));
    }

    @Test
    void shouldAutoCreateDraftSessionAtNeverSeededCentreWhenRoomOmitted() throws Exception {
        // SPRDT-1283 part 1 (no-room variant): with no template session and no courtRoomId the
        // session is created DRAFT/GENC on a stable per-centre virtual room — proving every
        // NOT NULL column is satisfiable without a template.
        final String centreId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        final Response response = postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                Json.createObjectBuilder()
                        .add("courtCentreId", centreId)
                        .add("hearingDate", date.toString())
                        .add("durationInMinutes", 60)
                        .add("source", "CROWN_FB_LIST")
                        .add("ouCode", "C99XX00")
                        .build().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject body = parse(body(response));
        final String createdId = body.getString("courtScheduleId");
        assertThat(body.getBoolean("isDraft"), is(true));

        final CourtSchedule created = databaseReader.courtScheduleById(createdId);
        assertThat(created.getOuCode(), is("C99XX00"));
        assertThat(created.getBusinessType(), is("GENC"));
        assertThat(created.getIsDraft(), is(true));
        // no name supplied -> ouCode stands in for display metadata; room is the virtual room
        assertThat(created.getCourtHouseName(), is("C99XX00"));
        assertThat(created.getCourtRoomId(), is(UUID.nameUUIDFromBytes(
                ("CROWN-FB-ROOM:" + centreId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()));
    }

    @Test
    void shouldClampAllocatedListingStartTimeToSessionStartWhenRequestedTimeOutsideSessionWindow() throws Exception {
        // SPRDT-1283 part 2: allocated_listings reflects the SESSION — a requested hearing time
        // outside the booked session's window lands as the session's start time on the row (the
        // listing viewstore keeps the user time; the divergence is accepted by design).
        final String centreId = UUID.randomUUID().toString();
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();
        final LocalDate date = LocalDate.now().plusDays(14);

        // seeded session runs 10:00-17:00; the hearing asks for 23:00 — far enough outside the
        // window that no test-JVM-vs-app timezone offset can pull it back inside
        final String sessionId = seedSession(date, roomId, "CR", centreId, "C01CY00", false, false, 360);

        final Response response = postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID,
                Json.createObjectBuilder()
                        .add("courtCentreId", centreId)
                        .add("courtRoomId", roomId)
                        .add("hearingDate", date.toString())
                        .add("durationInMinutes", 60)
                        .add("earliestHearingTime", date + "T23:00:00Z")
                        .add("source", "CROWN_FB_LIST")
                        .build().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(parse(body(response)).getString("courtScheduleId"), is(sessionId));

        final List<AllocatedListing> booked = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        assertThat(booked.size(), is(1));
        // Compare DB wall-clocks: the stored value must equal what the seeder stored for the
        // session's start (both round-trip through the same JDBC rendering, so the comparison is
        // timezone-robust); the out-of-window 23:00 user time must NOT survive.
        final Date seededSessionStart = Date.from(date.atTime(10, 0).toInstant(ZoneOffset.UTC));
        assertThat("row carries the SESSION start, not the out-of-window user time",
                new java.sql.Timestamp(booked.get(0).getHearingStartTime().getTime()).toLocalDateTime(),
                is(new java.sql.Timestamp(seededSessionStart.getTime()).toLocalDateTime()));
    }

    // NOTE: allocated_listings.source IT coverage intentionally omitted.
    //
    // End-to-end verification that the EXEMPT_SAB / AUTO_CREATE_SAB marker lands on
    // allocated_listings.source requires the Crown fallback path's follow-up UPDATE to run within
    // the command-handler's JTA transaction — which the current plumbing (save-based override
    // through AllocatedListingRepository.updateSourceByHearingId) doesn't reliably guarantee
    // outside the command-dispatch boundary. The marker IS correctly set on the AllocatedSlot
    // before the save pipeline and is covered at the unit-test level by
    // SlotsUpdateServiceTest.CrownFallbackSearchAndBook.shouldStampExemptSabSourceOnAllocatedSlot
    // and .shouldAutoCreateSessionAndBookWhenSearchReturnsEmpty.
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
