package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.io.StringReader;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for the CROWN multi-day search-and-book path, now served by
 * {@code POST /hearings/{hearingId}} with media type
 * {@code application/vnd.courtscheduler.crown.search.and.book+json} (was the retired
 * {@code GET /multidaysearchandbook/hearingslots}).
 *
 * <p>The request carries an anchor {@code courtScheduleId}, a total {@code durationInMinutes}
 * (&gt;360 selects the multi-day path), {@code courtCentreId}, {@code hearingDate} and the
 * {@code hearingId}. The engine discovers consecutive weekday sessions in the same court room +
 * business type starting from the anchor's date and BOOKS them (allocated_listings rows). The
 * response returns the booked {@code sessions} (empty when no qualifying consecutive run exists —
 * the multi-day path returns 200 with an empty list, it does not 404).
 *
 * <p>Sister unit tests live in {@code SlotsUpdateServiceTest} / {@code CourtSchedulerApiTest};
 * these ITs cover concerns that need a real database — same-room/centre/businessType constraints,
 * the effect of pre-existing allocations on per-day availability, idempotency, and historic dates.
 */
class MultiDaySearchAndBookIT extends AbstractIT {

    private static final String ACCEPT = "application/vnd.courtscheduler.crown.search.and.book+json";

    /** Default SQL anchor date — a Monday well into the future to avoid past-date filtering. */
    private static final LocalDate ANCHOR_MONDAY = LocalDate.now().plusDays(daysUntilNextMonday()).plusDays(7);

    private static final String CENTRE_A = "centre-A";

    // --- Gap #1 + #14: end-to-end happy path returns N courtScheduleIds INCLUDING the anchor ---

    @Test
    void shouldReturnAndBookConsecutiveWeekdaySessionsIncludingAnchor() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        // Three consecutive weekday sessions in the same room + businessType.
        final String day1Id = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day2Id = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day3Id = seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        final Response response = callCrown(day1Id, CENTRE_A, ANCHOR_MONDAY, 1080, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final List<String> returnedIds = extractCourtScheduleIds(body(response));
        assertThat("response includes the anchor courtScheduleId and the two subsequent days, in order",
                returnedIds, contains(day1Id, day2Id, day3Id));

        // Verify that allocated_listings rows were created for all three sessions for this hearing.
        final List<AllocatedListing> bookedRows = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        final List<String> bookedIds = bookedRows.stream()
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
        assertThat("all three days are booked in allocated_listings", bookedIds,
                containsInAnyOrder(day1Id, day2Id, day3Id));
        final List<String> bookedBusinessTypes = bookedRows.stream()
                .map(AllocatedListing::getRotaBusinessType)
                .collect(Collectors.toList());
        assertThat("rotaBusinessType is persisted on each allocated_listings row",
                bookedBusinessTypes, containsInAnyOrder("BTX", "BTX", "BTX"));
    }

    // --- Gap #2: same-room constraint ---

    @Test
    void shouldReturnEmptyWhenSubsequentSessionsAreInDifferentCourtRoom() throws Exception {
        final String roomA = UUID.randomUUID().toString();
        final String roomB = UUID.randomUUID().toString();

        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomA, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        // Day 2 + 3 exist on the right dates but in a DIFFERENT room — must be excluded.
        seedSession(ANCHOR_MONDAY.plusDays(1), roomB, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        seedSession(ANCHOR_MONDAY.plusDays(2), roomB, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        final Response response = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, UUID.randomUUID().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat("multiday must not cross court-room boundaries",
                extractCourtScheduleIds(body(response)), is(empty()));
    }

    // --- Gap #3: same-centre (ouCode) constraint ---

    @Test
    void shouldReturnEmptyWhenSubsequentSessionsAreInDifferentOuCode() throws Exception {
        final String roomId = UUID.randomUUID().toString();

        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        // Same room id but different ouCode (i.e. different operational unit) → excluded by SQL filter.
        seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", "centre-B", "OU-B", false, false, 360, 0);
        seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTX", "centre-B", "OU-B", false, false, 360, 0);

        final Response response = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, UUID.randomUUID().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat("multiday must not cross ouCode boundaries",
                extractCourtScheduleIds(body(response)), is(empty()));
    }

    // --- Gap #11: businessType consistency ---

    @Test
    void shouldReturnEmptyWhenSubsequentSessionsHaveDifferentBusinessType() throws Exception {
        final String roomId = UUID.randomUUID().toString();

        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        // Different businessType → excluded by SQL filter even though room + centre match.
        seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTY", CENTRE_A, "OU-A", false, false, 360, 0);
        seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTY", CENTRE_A, "OU-A", false, false, 360, 0);

        final Response response = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, UUID.randomUUID().toString());

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat("multiday must not cross businessType boundaries",
                extractCourtScheduleIds(body(response)), is(empty()));
    }

    // --- Gap #4 (rewritten for F1, court-calendar always-assign rule): pre-existing
    // allocated_listings on a later day no longer block — the short day is overbooked. ---

    @Test
    void shouldBookAllDaysWhenLaterDayHasPreExistingAllocationsThatReduceAvailability() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day2Id = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day3Id = seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        // Pre-existing allocation from another hearing on day 2 consumes 200 of 360 minutes.
        // F1: the shortfall is advisory — the booking must proceed and overbook day 2.
        databaseSeeder.insertAllocatedListing(allocatedListing(day2Id, UUID.randomUUID().toString(), 200));

        final Response response = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat("books all three days despite day 2's reduced availability (always-assign rule)",
                extractCourtScheduleIds(body(response)), contains(day1Anchor, day2Id, day3Id));

        assertThat("three new allocated_listings written alongside the pre-existing one",
                databaseReader.allocatedListings().size(), is(4));
    }

    // --- F1 (court-calendar always-assign rule): the reported defect scenario — a day with NO
    // availability and is_overbooking_allowed=false must not block the multiday assignment. ---

    @Test
    void shouldBookAllDaysWhenADayIsFullyBookedAndOverbookingDisallowed() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day2Id = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        // Day 2 is FULLY booked by another hearing and overbooking is NOT allowed — previously the
        // whole 2-day block was rejected and the hearing stayed unassigned (RC-1).
        databaseSeeder.insertAllocatedListing(allocatedListing(day2Id, UUID.randomUUID().toString(), 360));

        final Response response = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat("the hearing is assigned to both days, overbooking the full day",
                extractCourtScheduleIds(body(response)), contains(day1Anchor, day2Id));

        final List<AllocatedListing> hearingRows = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        assertThat("both days booked for this hearing, 360 minutes each", hearingRows.size(), is(2));
        assertThat(hearingRows.stream().map(AllocatedListing::getDuration).collect(Collectors.toList()),
                containsInAnyOrder(360, 360));
    }

    // --- Gap #16: historic / past-date anchor ---

    @Test
    void shouldReturnEmptyWhenAnchorIsInThePast() throws Exception {
        final String roomId = UUID.randomUUID().toString();

        // Anchor in the past — even though the SQL doesn't explicitly exclude past dates,
        // the only "consecutive" sessions seeded here ARE in the past, so this is effectively
        // a smoke test that historic anchors don't blow up. If product later requires explicit
        // rejection of past anchors, this test will need to be tightened.
        final LocalDate pastMonday = LocalDate.now().minusDays(daysUntilNextMonday()).minusDays(28);
        final String day1Anchor = seedSession(pastMonday, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        final Response response = callCrown(day1Anchor, CENTRE_A, pastMonday, 1080, UUID.randomUUID().toString());

        // Only 1 candidate exists (the anchor) — service needs 3 → returns empty.
        // The endpoint must respond 200 with an empty array, not error.
        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractCourtScheduleIds(body(response)), is(empty()));
    }

    // --- Gap #6: idempotency — repeated call must not double-book ---

    @Test
    void shouldNotDoubleBookWhenSameRequestRepeatedAndOverbookingDisallowed() throws Exception {
        // The first call books all three days. A repeat with the SAME hearingId AND the SAME anchor
        // is a genuine retry: the idempotency guard short-circuits (no re-search, no re-book) but
        // returns the EXISTING booked sessions (SPRDT-1089 STE ns-ste-ccm-34 fix) rather than an
        // empty list, so a caller's enrichment doesn't collapse on a harmless retry.
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day2Id = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day3Id = seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        // First call books all three days.
        final Response first = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, hearingId);
        assertThat(first.getStatus(), is(OK.getStatusCode()));
        assertThat(extractCourtScheduleIds(body(first)).size(), is(3));
        assertThat("first call writes 3 allocated_listings",
                databaseReader.allocatedListings().size(), is(3));

        // Second call with the same hearingId AND the same anchor: idempotency guard short-circuits
        // the search/book, but the EXISTING three sessions are returned (not emptyList).
        final Response second = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, hearingId);
        assertThat(second.getStatus(), is(OK.getStatusCode()));
        assertThat("second call (same anchor) returns the existing booked sessions, not empty",
                extractCourtScheduleIds(body(second)), containsInAnyOrder(day1Anchor, day2Id, day3Id));
        assertThat("no additional allocated_listings written by retry",
                databaseReader.allocatedListings().size(), is(3));
    }

    // --- SPRDT-1089 STE ns-ste-ccm-34 fix: a multi-day MOVE when the hearing already has
    // allocated_listings rows but is re-searched with a DIFFERENT anchor (e.g. update-hearing-for-
    // listing picked a different FINAL session). Must release the old rows and book the new run,
    // tagging source=MOVE, instead of short-circuiting with an empty sessions list. ---

    @Test
    void shouldMoveHearingWhenReSearchedWithDifferentAnchorAfterExistingAllocation() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String newRoomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        // Original three-day allocation (as if booked unallocated, one row per day).
        final String oldDay1 = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String oldDay2 = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String oldDay3 = seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final Response first = callCrown(oldDay1, CENTRE_A, ANCHOR_MONDAY, 1080, hearingId);
        assertThat(first.getStatus(), is(OK.getStatusCode()));
        assertThat("first call writes 3 allocated_listings", databaseReader.allocatedListings().size(), is(3));

        // A NEW consecutive run in a different room, one week later — mirrors update-hearing-for-listing
        // supplying a different FINAL session anchor.
        final LocalDate newMonday = ANCHOR_MONDAY.plusWeeks(1);
        final String newDay1 = seedSession(newMonday, newRoomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String newDay2 = seedSession(newMonday.plusDays(1), newRoomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String newDay3 = seedSession(newMonday.plusDays(2), newRoomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        final Response moveResponse = callCrown(newDay1, CENTRE_A, newMonday, 1080, hearingId);

        assertThat(moveResponse.getStatus(), is(OK.getStatusCode()));
        // Read the entity exactly once — a second readEntity on the same Response throws
        // RESTEASY003765 (Response is closed).
        final String moveBody = body(moveResponse);
        assertThat("move books the new consecutive run",
                extractCourtScheduleIds(moveBody), containsInAnyOrder(newDay1, newDay2, newDay3));
        assertThat(moveBody, containsString("\"source\":\"MOVE\""));

        // Old rows released, new rows booked — total stays at 3, all pointing at the new run.
        final List<AllocatedListing> rowsAfterMove = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        assertThat("old allocation released, new allocation booked — 3 rows total", rowsAfterMove.size(), is(3));
        final List<String> scheduleIdsAfterMove = rowsAfterMove.stream()
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
        assertThat("rows now point at the NEW run, not the old one",
                scheduleIdsAfterMove, containsInAnyOrder(newDay1, newDay2, newDay3));
        assertThat("old sessions no longer carry this hearing's allocation",
                scheduleIdsAfterMove, not(containsInAnyOrder(oldDay1, oldDay2, oldDay3)));
        assertThat("moved rows are tagged source=MOVE",
                rowsAfterMove.stream().map(AllocatedListing::getSource).collect(Collectors.toList()),
                containsInAnyOrder("MOVE", "MOVE", "MOVE"));
    }

    // --- SPRDT-1089: live STE bug (hearing 00b7b8bd/072b7512) — a MOVE re-anchored on a
    // CONTINUATION day of the hearing's OWN existing block (not the block's first/earliest day).
    // The retry-idempotency guard used to match on "anchor is ANY existing row", so this case was
    // wrongly treated as a replay and the old block was returned unchanged. Anchor-strict detection
    // (idempotent ONLY when the anchor equals the block's first session) must recognise this as a
    // genuine move: release the old block and book a fresh consecutive run starting at the anchor's
    // date, even though the new run overlaps two of the hearing's own currently-held days. ---

    @Test
    void shouldMoveHearingWhenReAnchoredAtLaterDayOfExistingBlock() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        // Four consecutive weekday sessions in the same room — enough for the original 3-day block
        // AND the new 3-day block that starts one day later (re-using day2/day3, adding day4).
        final String day1Id = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day2Id = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day3Id = seedSession(ANCHOR_MONDAY.plusDays(2), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day4Id = seedSession(ANCHOR_MONDAY.plusDays(3), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        // Book the original 3-day block: [day1, day2, day3].
        final Response first = callCrown(day1Id, CENTRE_A, ANCHOR_MONDAY, 1080, hearingId);
        assertThat(first.getStatus(), is(OK.getStatusCode()));
        assertThat("first call writes 3 allocated_listings", databaseReader.allocatedListings().size(), is(3));

        // Re-anchor the SAME hearing at day2 — a continuation day of its own existing block, not the
        // block's first day. This must be treated as a genuine MOVE to [day2, day3, day4], not an
        // idempotent replay of [day1, day2, day3].
        final Response moveResponse = callCrown(day2Id, CENTRE_A, ANCHOR_MONDAY.plusDays(1), 1080, hearingId);

        assertThat(moveResponse.getStatus(), is(OK.getStatusCode()));
        final String moveBody = body(moveResponse);
        assertThat("new block starts at day2 and runs 3 consecutive business days",
                extractCourtScheduleIds(moveBody), contains(day2Id, day3Id, day4Id));
        assertThat(moveBody, containsString("\"source\":\"MOVE\""));

        // Old day1 booking released, new block booked — total stays at 3 rows for this hearing.
        final List<AllocatedListing> rowsAfterMove = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        assertThat("old day1 released, new 3-day block booked — 3 rows total", rowsAfterMove.size(), is(3));
        final List<String> scheduleIdsAfterMove = rowsAfterMove.stream()
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toList());
        assertThat("rows now point at [day2, day3, day4]",
                scheduleIdsAfterMove, containsInAnyOrder(day2Id, day3Id, day4Id));
        assertThat("day1 no longer carries this hearing's allocation",
                scheduleIdsAfterMove, not(org.hamcrest.Matchers.hasItem(day1Id)));
        assertThat("moved rows are tagged source=MOVE",
                rowsAfterMove.stream().map(AllocatedListing::getSource).collect(Collectors.toList()),
                containsInAnyOrder("MOVE", "MOVE", "MOVE"));

        // Counters restored on the released day1 — back to its full 360-minute capacity.
        final CourtSchedule day1AfterMove = databaseReader.courtScheduleById(day1Id);
        assertThat("day1's availableDuration is restored after release",
                day1AfterMove.getAvailableDuration(), is(360));
    }

    // --- Single→multi-day conversion (update-hearing-for-listing): a hearing already booked on
    // ONE day is extended to two days, anchored on its OWN booked session. Two traps must both be
    // avoided: (1) the idempotency guard must not misread the request as a replay of the existing
    // 1-day block — the anchor matches the block's first day but the day count differs, so it is a
    // RESIZE; and (2) the availability check must not count the hearing's own not-yet-released 360
    // minutes against the anchor day (reclaimHearingsOwnCapacity). Before the fix this returned the
    // existing single session untouched and day 2 was never booked. ---

    @Test
    void shouldExtendSingleDayHearingToMultiDayWhenAnchoredOnItsOwnBookedSession() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        final String day1Id = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        final String day2Id = seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        // The hearing's existing single-day booking fully consumes day 1's capacity.
        databaseSeeder.insertAllocatedListing(allocatedListing(day1Id, hearingId, 360));

        // Convert to 2 days (720 mins), anchored on the hearing's own day-1 session.
        final Response response = callCrown(day1Id, CENTRE_A, ANCHOR_MONDAY, 720, hearingId);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseBody = body(response);
        assertThat("conversion books BOTH days, not just the pre-existing one",
                extractCourtScheduleIds(responseBody), contains(day1Id, day2Id));

        // The old single-day row is replaced by the 2-day block — exactly 2 rows for this hearing,
        // no duplicate allocation left behind on day 1.
        final List<AllocatedListing> rowsAfterConversion = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .collect(Collectors.toList());
        assertThat("single-day booking replaced by a 2-day block", rowsAfterConversion.size(), is(2));
        assertThat(rowsAfterConversion.stream()
                        .map(AllocatedListing::getCourtScheduleId)
                        .collect(Collectors.toList()),
                containsInAnyOrder(day1Id, day2Id));
        assertThat("the 720-minute total is split evenly across the two booked days",
                rowsAfterConversion.stream().map(AllocatedListing::getDuration).collect(Collectors.toList()),
                containsInAnyOrder(360, 360));
    }

    /**
     * Idempotency holds even when sessions allow overbooking: the (court_schedule_id, hearing_id)
     * guard means repeating the same call with the same hearingId does NOT create duplicate
     * allocated_listings rows.
     */
    @Test
    void shouldNotDoubleBookWhenOverbookingAllowedAndSameRequestRepeated() throws Exception {
        final String roomId = UUID.randomUUID().toString();
        final String hearingId = UUID.randomUUID().toString();

        // Sessions allow overbooking → availability check is bypassed.
        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, true, 360, 0);
        seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, true, 360, 0);

        final Response first = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 720, hearingId);
        assertThat(first.getStatus(), is(OK.getStatusCode()));
        assertThat("first call writes 2 allocated_listings",
                databaseReader.allocatedListings().size(), is(2));

        final Response second = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 720, hearingId);
        assertThat(second.getStatus(), is(OK.getStatusCode()));
        // Idempotent on (hearingId, courtScheduleId) — no new rows created.
        assertThat("second call with same hearing must NOT create duplicate allocated_listings even under overbooking",
                databaseReader.allocatedListings().size(), is(2));
    }

    // --- Gap #5 (rewritten for F1): no partial state on STRUCTURAL failure. Availability no
    // longer rejects (always-assign rule), but a run that is structurally incomplete — a missing
    // session day — still returns empty and must not leave partial allocated_listings behind. ---

    @Test
    void shouldNotWritePartialAllocatedListingsWhenRunIsStructurallyIncomplete() throws Exception {
        final String roomId = UUID.randomUUID().toString();

        // Only 2 of the 3 required consecutive days exist in the anchor's room — day 3 is missing.
        final String day1Anchor = seedSession(ANCHOR_MONDAY, roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);
        seedSession(ANCHOR_MONDAY.plusDays(1), roomId, "BTX", CENTRE_A, "OU-A", false, false, 360, 0);

        final int allocatedListingsBefore = databaseReader.allocatedListings().size();

        final Response response = callCrown(day1Anchor, CENTRE_A, ANCHOR_MONDAY, 1080, UUID.randomUUID().toString());
        assertThat(response.getStatus(), is(OK.getStatusCode()));
        assertThat(extractCourtScheduleIds(body(response)), is(empty()));

        assertThat("no partial allocated_listings written when the run is structurally incomplete — count unchanged",
                databaseReader.allocatedListings().size(), is(allocatedListingsBefore));
    }

    // --- Gap #8: required-field validation enforced at the RAML/schema layer ---

    @ParameterizedTest(name = "shouldReturn400 when required field {0} is missing")
    @ValueSource(strings = {"courtCentreId", "hearingDate"})
    void shouldReturn400WhenRequiredFieldIsMissing(final String fieldToOmit) {
        final String hearingId = UUID.randomUUID().toString();
        final JsonObject full = Json.createObjectBuilder()
                .add("courtCentreId", CENTRE_A)
                .add("hearingDate", ANCHOR_MONDAY.toString())
                .add("durationInMinutes", 1080)
                .add("courtScheduleId", UUID.randomUUID().toString())
                .build();
        final JsonObjectBuilder b = Json.createObjectBuilder();
        full.forEach((k, v) -> {
            if (!k.equals(fieldToOmit)) {
                b.add(k, v);
            }
        });

        final Response response = postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID, b.build().toString());

        assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
        assertThat("error payload should mention the missing field",
                response.readEntity(String.class), containsString(fieldToOmit));
    }

    // --- helpers ---

    private Response callCrown(final String anchorCourtScheduleId,
                               final String courtCentreId,
                               final LocalDate hearingDate,
                               final int durationInMinutes,
                               final String hearingId) {
        final String payload = Json.createObjectBuilder()
                .add("courtCentreId", courtCentreId)
                .add("hearingDate", hearingDate.toString())
                .add("durationInMinutes", durationInMinutes)
                .add("courtScheduleId", anchorCourtScheduleId)
                .build()
                .toString();
        return postCommand("/hearings/" + hearingId, ACCEPT, SYSTEM_USER_ID, payload);
    }

    private static String body(final Response response) {
        return response.readEntity(String.class);
    }

    private static List<String> extractCourtScheduleIds(final String payload) {
        final JsonObject json = createReader(new StringReader(payload)).readObject();
        if (!json.containsKey("sessions") || json.isNull("sessions")) {
            return List.of();
        }
        final JsonArray arr = json.getJsonArray("sessions");
        return arr.getValuesAs(JsonObject.class).stream()
                .map(o -> o.getString("courtScheduleId"))
                .collect(Collectors.toList());
    }

    /**
     * Insert a court_schedule row and return its id. Sessions default to {@code court_session=AD}
     * (the value the multiday SQL filters on) and {@code active=true}.
     */
    private String seedSession(final LocalDate sessionDate,
                               final String courtRoomId,
                               final String businessType,
                               final String courtHouseId,
                               final String ouCode,
                               final boolean isDraft,
                               final boolean overbookingAllowed,
                               final int maxDuration,
                               final int totalBookedIgnored) throws java.sql.SQLException {
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

    private static AllocatedListing allocatedListing(final String courtScheduleId,
                                                     final String hearingId,
                                                     final int duration) {
        final AllocatedListing al = new AllocatedListing();
        al.setId(UUID.randomUUID().toString());
        al.setCourtScheduleId(courtScheduleId);
        al.setBookingId(UUID.randomUUID().toString());
        al.setHearingId(hearingId);
        al.setOucode("OU-A");
        al.setCourtRoomId(1);
        al.setRotaBusinessType("BTX");
        al.setDuration(duration);
        al.setHearingStartTime(new Timestamp(System.currentTimeMillis()));
        return al;
    }

    /** Days from today (inclusive of today if today is Monday) until the next Monday. */
    private static long daysUntilNextMonday() {
        final int dow = LocalDate.now().getDayOfWeek().getValue(); // 1=Mon..7=Sun
        return (8 - dow) % 7;
    }
}
