package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class ProvisionalBookingIT extends AbstractIT {

    private final String RELATIVE_PATH = "/provisionalBooking";

    @Test
    void shouldCreateProvisionalHearingSlot() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);


        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json");
        provisionalBookingPayload = provisionalBookingPayload.replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommand(RELATIVE_PATH, "application/vnd.courtscheduler.create.provisional.booking+json", SYSTEM_USER_ID, provisionalBookingPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        String responseString = response.readEntity(String.class); // Ensure to read the entity as String
        JSONObject responseJson = new JSONObject(responseString);
        assertThat(responseJson.get("bookingId"), notNullValue());
    }

    /**
     * Backward-compatibility guard: a legacy WildFly client posted to this endpoint with
     * {@code Accept: application/json} (the legacy default response type — the RAML declared no
     * response media type for provisional booking). The migrated OpenAPI declares
     * {@code produces: application/vnd.courtscheduler.create.provisional.booking.response+json},
     * so with strict content negotiation a specific {@code Accept: application/json} must NOT be
     * rejected with 406. (The happy-path test above doesn't catch this — it sends no Accept, so
     * RestTemplate uses {@code *}/{@code *}, which matches any produces.)
     */
    @Test
    void shouldAcceptLegacyApplicationJsonAcceptHeader() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json")
                .replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommandWithAccept(
                RELATIVE_PATH,
                "application/vnd.courtscheduler.create.provisional.booking+json",
                "application/json",
                SYSTEM_USER_ID,
                provisionalBookingPayload);

        assertThat("legacy Accept: application/json must not be rejected with 406 Not Acceptable",
                response.getStatus(), is(not(NOT_ACCEPTABLE.getStatusCode())));
        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    /**
     * Mirrors the real production caller — cpp-context-hearing's
     * {@code ProvisionalBookingService.bookSlots} (Apache HttpClient) sends
     * {@code Content-Type: …create.provisional.booking+json}, {@code CJSCPPUID}, and
     * NO {@code Accept} header. This is the traffic that actually hits the endpoint, so it
     * must return 200 (an absent Accept is treated as {@code *}/{@code *}).
     */
    @Test
    void shouldAcceptHearingCallerWithNoAcceptHeader() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json")
                .replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommandWithoutAccept(
                RELATIVE_PATH,
                "application/vnd.courtscheduler.create.provisional.booking+json",
                SYSTEM_USER_ID,
                provisionalBookingPayload);

        assertThat("real hearing caller sends no Accept header and must not be rejected",
                response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldRetrieveProvisionalBooking() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();


        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);


        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        final CourtScheduleJudiciaryKey courtScheduleJudiciaryKey = random(CourtScheduleJudiciaryKey.class);
        courtScheduleJudiciaryKey.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciaryKey.setJudiciaryId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciary.setId(courtScheduleJudiciaryKey);
        courtScheduleJudiciary.setCourtListingProfileId(courtScheduleJudiciary.getCourtListingProfileId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final ProvisionalSlot provisionalSlot = new ProvisionalSlot(courtSchedule.getCourtScheduleId(), "2020-01-01T11:00:00.000Z");
        databaseSeeder.bookSlots(List.of(provisionalSlot), bookingId);

        String provisionalBooking = getPayload("courtscheduler.get.provisional.booking.json");
        provisionalBooking = provisionalBooking.replace("BOOKING_ID", bookingId);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(provisionalBooking, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_PATH, "application/vnd.courtscheduler.get.provisional.booking+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
    }


    /**
     * End-to-end proof for the two defects a slot-by-slot reservation loop and a legacy-only
     * confirm lookup produced. Everything here needs <b>two</b> sessions — the single-slot fixture
     * cannot see either bug.
     *
     * <p><b>C1.</b> Every slot of a booking shares the minted bookingId, and reserving opens with
     * a release keyed on that id. Reserving one slot at a time therefore made slot 2 release
     * slot 1: a two-day pick ended holding one day on the last session. Asserting two held rows
     * and two decremented sessions pins that.
     *
     * <p><b>C2.</b> Confirming through the real {@code PUT /hearingslots} path with the bookingId
     * used to look the booking up in legacy {@code provisional_booking}, which reserve-a-slot no
     * longer writes — so the lookup came back empty and the confirm threw
     * {@code ProvisionalSlotNotFoundException}. Asserting a 200, the hearing booked on both
     * sessions, and no surviving reservation pins that.
     *
     * <p><b>No double decrement.</b> Each session starts with 5 free slots. The reservation takes
     * it to 4; the confirm decrements to 3 and then releases the bookingId-keyed hold back to 4.
     * Anything other than 4 at the end means the hold leaked (3) or the reservation never held
     * anything (5).
     */
    @Test
    void shouldHoldEverySessionOfAMultiSlotBookingAndDecrementEachExactlyOnceOnConfirm() throws Exception {
        final String courtScheduleId1 = UUID.randomUUID().toString();
        final String courtScheduleId2 = UUID.randomUUID().toString();
        final CourtSchedule session1 = bookableCourtSchedule(courtScheduleId1);
        final CourtSchedule session2 = bookableCourtSchedule(courtScheduleId2);
        databaseSeeder.insertCourtSchedule(session1);
        databaseSeeder.insertCourtSchedule(session2);

        // ---------- pick: POST /provisionalBooking with TWO sessions ----------
        final String payload = getPayload("courtscheduler.create.provisional.booking.two-slots.json")
                .replace("COURTSCHEDULER_ID_1", courtScheduleId1)
                .replace("COURTSCHEDULER_ID_2", courtScheduleId2);

        final Response bookResponse = postCommand(RELATIVE_PATH,
                "application/vnd.courtscheduler.create.provisional.booking+json", SYSTEM_USER_ID, payload);

        assertThat(bookResponse.getStatus(), is(OK.getStatusCode()));
        final String bookingId = new JSONObject(bookResponse.readEntity(String.class)).getString("bookingId");
        assertThat(bookingId, notNullValue());

        // BOTH sessions are held — one reservation row each, both carrying the expiry that is the
        // reservation discriminator, both keyed on the same bookingId.
        final List<AllocatedListing> reservations = reservationsForBookingId(bookingId);
        assertThat("both picked sessions must be held — one row means slot 2 released slot 1",
                reservations, hasSize(2));
        reservations.forEach(row -> assertThat("a reservation must carry expires_at",
                row.getExpiresAt(), is(LocalDate.now(ZoneOffset.UTC))));
        assertThat(reservations.stream().map(AllocatedListing::getCourtScheduleId).sorted().toList(),
                is(List.of(courtScheduleId1, courtScheduleId2).stream().sorted().toList()));

        // BOTH sessions actually lost capacity: 5 -> 4.
        assertThat(availableSlots(courtScheduleId1), is(4));
        assertThat(availableSlots(courtScheduleId2), is(4));

        // ---------- confirm: PUT /hearingslots carrying the bookingId ----------
        final String hearingId = UUID.randomUUID().toString();
        final Response confirmResponse = putCommand("/hearingslots",
                "application/vnd.courtscheduler.update.hearing.slots+json", SYSTEM_USER_ID,
                confirmPayload(hearingId, bookingId, session1, session2));

        // Listing's real share path is courtscheduler.list.hearings-in-sessions (POST /hearings),
        // covered by shouldReleaseTheHoldWhenListingConfirmsThroughListHearingsInSessions below.
        assertThat("a reservation-backed booking must be confirmable through the update.hearing.slots path: "
                        + confirmResponse.readEntity(String.class),
                confirmResponse.getStatus(), is(OK.getStatusCode()));

        // The hearing is booked on both sessions under its REAL id ...
        final List<AllocatedListing> booked = allocatedListingsForHearingId(hearingId);
        assertThat(booked, hasSize(2));
        booked.forEach(row -> assertThat("a confirmed booking must not carry expires_at",
                row.getExpiresAt(), is(nullValue())));

        // ... and no reservation survives under the bookingId.
        assertThat("the bookingId-keyed hold must be released on confirm",
                reservationsForBookingId(bookingId), hasSize(0));

        // Net effect per session is exactly one decrement: 5 -> 4 (hold) -> 3 (book) -> 4 (release).
        assertThat("session decremented twice — the reservation was never released",
                availableSlots(courtScheduleId1), is(4));
        assertThat("session decremented twice — the reservation was never released",
                availableSlots(courtScheduleId2), is(4));
    }

    /**
     * BUG-3. The existing multi-slot test above confirms through {@code PUT /hearingslots}
     * ({@code update.hearing.slots}). Listing does not use that path at share: it posts
     * {@code courtscheduler.list.hearings-in-sessions}, which lands on
     * {@code updateListHearingSlots}. Until BUG-3 that method released only by the real hearing id,
     * so the bookingId-keyed hold survived the share and the session stayed decremented twice until
     * the 01:00 purge. This is the same arithmetic as the test above, on the path production takes.
     */
    @Test
    void shouldReleaseTheHoldWhenListingConfirmsThroughListHearingsInSessions() throws Exception {
        final String courtScheduleId = UUID.randomUUID().toString();
        final CourtSchedule session = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(session);

        final String bookingId = reserveOneSession(courtScheduleId);
        assertThat("the pick must hold a slot", availableSlots(courtScheduleId), is(4));

        final String hearingId = UUID.randomUUID().toString();
        final Response listResponse = postCommand("/hearings",
                "application/vnd.courtscheduler.list.hearings-in-sessions+json", SYSTEM_USER_ID,
                listHearingsPayload(hearingId, bookingId, session));

        assertThat("listing's real share path must accept a bookingId: "
                + listResponse.readEntity(String.class), listResponse.getStatus(), is(OK.getStatusCode()));

        assertThat("the bookingId-keyed hold must be released on the list path",
                reservationsForBookingId(bookingId), hasSize(0));

        final List<AllocatedListing> booked = allocatedListingsForHearingId(hearingId);
        assertThat(booked, hasSize(1));
        assertThat("the confirmed row must carry booking_id — it is what makes an already-shared "
                + "booking distinguishable from an expired one", booked.get(0).getBookingId(), is(bookingId));
        assertThat(booked.get(0).getExpiresAt(), is(nullValue()));

        assertThat("net effect must be exactly one decrement: 5 -> 4 (hold) -> 3 (list) -> 4 (release)",
                availableSlots(courtScheduleId), is(4));
    }

    /**
     * NEW-15. A clerk who re-picks must not hold both sessions. The re-pick comes back under the
     * same bookingId, and reserveAll opens by releasing every unconfirmed row already held under
     * it, so the previous pick is given back before the new one is taken.
     */
    @Test
    void shouldReleaseTheFirstSessionWhenRePickingUnderTheSameBookingId() throws Exception {
        final String sessionAId = UUID.randomUUID().toString();
        final String sessionBId = UUID.randomUUID().toString();
        databaseSeeder.insertCourtSchedule(bookableCourtSchedule(sessionAId));
        databaseSeeder.insertCourtSchedule(bookableCourtSchedule(sessionBId));

        final String bookingId = reserveOneSession(sessionAId);
        assertThat(availableSlots(sessionAId), is(4));

        reserveOneSession(sessionBId, bookingId);

        assertThat("the abandoned session must get its slot back", availableSlots(sessionAId), is(5));
        assertThat("the newly picked session must be held", availableSlots(sessionBId), is(4));

        final List<AllocatedListing> held = reservationsForBookingId(bookingId);
        assertThat("only the new pick may survive", held, hasSize(1));
        assertThat(held.get(0).getCourtScheduleId(), is(sessionBId));
    }

    /**
     * NEW-7a. The pre-share gate must tell an expired hold from an already-shared booking. Only an
     * end-to-end run can show the transition, because RESERVED and SHARED are produced by two
     * different rows written by two different code paths.
     */
    @Test
    void shouldReportReservedBeforeShareAndSharedAfterIt() throws Exception {
        final String courtScheduleId = UUID.randomUUID().toString();
        final CourtSchedule session = bookableCourtSchedule(courtScheduleId);
        databaseSeeder.insertCourtSchedule(session);

        final String bookingId = reserveOneSession(courtScheduleId);

        assertThat(bookingStatusOf(bookingId), is("RESERVED"));

        final String hearingId = UUID.randomUUID().toString();
        postCommand("/hearings", "application/vnd.courtscheduler.list.hearings-in-sessions+json",
                SYSTEM_USER_ID, listHearingsPayload(hearingId, bookingId, session));

        assertThat("after the share the hold is gone but the booking is not expired — reporting "
                + "anything but SHARED here is what would block a legitimate re-share",
                bookingStatusOf(bookingId), is("SHARED"));

        assertThat(bookingStatusOf(UUID.randomUUID().toString()), is("NONE"));
    }

    /** Confirmed rows, which are the only ones carrying a real {@code hearing_id}. */
    private List<AllocatedListing> allocatedListingsForHearingId(final String hearingId) {
        return databaseReader.allocatedListings().stream()
                .filter(row -> hearingId.equals(row.getHearingId()))
                .toList();
    }

    /**
     * The UNCONFIRMED rows held under a bookingId. The expires_at filter is what makes this
     * meaningful: a confirmed listing carries the same booking_id as the reservation it grew from,
     * so filtering on booking_id alone would count the confirmed row as a surviving hold and let
     * "the hold was released" assertions pass while it had not been.
     */
    private List<AllocatedListing> reservationsForBookingId(final String bookingId) {
        return databaseReader.allocatedListings().stream()
                .filter(row -> bookingId.equals(row.getBookingId()))
                .filter(row -> row.getExpiresAt() != null)
                .toList();
    }

    private int availableSlots(final String courtScheduleId) {
        return databaseReader.courtScheduleById(courtScheduleId).getAvailableSlots();
    }

    /**
     * The real share payload: one hearingSlots entry per picked session, each carrying the
     * bookingId (which is what puts {@code SlotsUpdateService.update} on the booking-based branch)
     * alongside the real hearingId the hearing is booked under.
     *
     * <p>{@code source} is included because {@code allocated_listings.source} is NOT NULL and the
     * courtScheduleId-anchored book path copies it straight off the request (unlike the
     * search-and-book path, which derives POLICE/NONPOLICE itself). Omitting it makes the insert
     * fail with a constraint violation — pre-existing behaviour of this endpoint, unrelated to
     * reserve-a-slot, but it means a realistic payload has to carry one.
     */
    private static String confirmPayload(final String hearingId, final String bookingId,
                                         final CourtSchedule... sessions) {
        final StringBuilder entries = new StringBuilder();
        for (final CourtSchedule session : sessions) {
            if (entries.length() > 0) {
                entries.append(",");
            }
            entries.append(String.format(
                    "{\"hearingId\":\"%s\",\"bookingId\":\"%s\",\"courtScheduleId\":\"%s\","
                            + "\"hearingStartTime\":\"%sT10:00:00.000Z\",\"sessionDate\":\"%s\","
                            + "\"session\":\"AM\",\"ouCode\":\"%s\",\"courtRoomId\":%d,\"duration\":60,"
                            + "\"source\":\"NONPOLICE\"}",
                    hearingId, bookingId, session.getCourtScheduleId(),
                    session.getSessionDate(), session.getSessionDate(),
                    session.getOuCode(), session.getCourtRoomNumber()));
        }
        return "{\"hearingSlots\":[" + entries + "]}";
    }

    /**
     * Task 5: {@code POST /provisionalBooking} now goes through {@code ReservationService.reserve},
     * which (a) filters sessions on {@code isActive} — 404s otherwise, (b) calls
     * {@code session.getSessionDate().toString()} directly — NPEs on a null session date, and
     * (c) runs the real capacity-decrementing {@code saveBookedSlots} pipeline. A plain
     * {@code RANDOM.nextObject(CourtSchedule.class)} leaves all three to chance, which used to be
     * harmless when this endpoint only ever inserted an unconditional {@code provisional_booking}
     * row. Pin exactly the fields the reservation path depends on; leave everything else to RANDOM.
     */
    private static CourtSchedule bookableCourtSchedule(final String courtScheduleId) {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setActive(true);
        courtSchedule.setSessionDate(LocalDate.now().plusDays(7));
        courtSchedule.setSlotBased(true);
        courtSchedule.setAvailableSlots(5);
        courtSchedule.setMaxSlots(5);
        courtSchedule.setAvailableDuration(360);
        courtSchedule.setMaxDuration(360);
        return courtSchedule;
    }

    /**
     * Picks one session for the first time: {@code POST /provisionalBooking} with a single
     * {@code provisionalSlots} entry and no {@code bookingId}, so the server mints one. Reuses the
     * single-session fixture as-is (it already fits one session).
     */
    private String reserveOneSession(final String courtScheduleId) {
        final String payload = getPayload("courtscheduler.create.provisional.booking.json")
                .replace("COURTSCHEDULER_ID", courtScheduleId);
        return postReserveOneSession(payload);
    }

    /**
     * Re-picks under an EXISTING bookingId (NEW-15): same single-session fixture, with
     * {@code bookingId} added at the top level so the server reuses the booking rather than
     * minting a new one. The fixture carries no bookingId placeholder to replace, so the id is
     * added onto the parsed JSON instead of hand-building the whole payload.
     */
    private String reserveOneSession(final String courtScheduleId, final String bookingId) {
        final String payload = new JSONObject(getPayload("courtscheduler.create.provisional.booking.json")
                .replace("COURTSCHEDULER_ID", courtScheduleId))
                .put("bookingId", bookingId)
                .toString();
        return postReserveOneSession(payload);
    }

    private String postReserveOneSession(final String payload) {
        final Response response = postCommand(RELATIVE_PATH,
                "application/vnd.courtscheduler.create.provisional.booking+json", SYSTEM_USER_ID, payload);
        final String body = response.readEntity(String.class);
        assertThat("the pick must succeed: " + body, response.getStatus(), is(OK.getStatusCode()));
        return new JSONObject(body).getString("bookingId");
    }

    /**
     * The real listing share payload ({@code courtscheduler.list.hearings-in-sessions}, POST
     * {@code /hearings}): one {@code hearingSlots} entry, with {@code bookingId} a SIBLING of
     * {@code hearingId} — the schema nests it at the hearingSlot level, never inside
     * {@code courtScheduleIds[]} (see {@code courtscheduler.list.hearings-in-sessions.json}). No
     * {@code source} is sent: unlike {@link #confirmPayload}'s path, {@code updateListHearingSlots}
     * defaults an absent source to {@code "DEFAULT"} itself
     * (CourtScheduleRepositoryImpl#resolveAllocatedListingSource), so this path never needs one
     * supplied to satisfy {@code allocated_listings.source} NOT NULL.
     */
    private static String listHearingsPayload(final String hearingId, final String bookingId,
                                              final CourtSchedule session) {
        return String.format(
                "{\"hearingSlots\":[{\"hearingId\":\"%s\",\"bookingId\":\"%s\",\"courtScheduleIds\":"
                        + "[{\"courtScheduleId\":\"%s\",\"hearingStartTime\":\"%sT10:00:00.000Z\"}]}]}",
                hearingId, bookingId, session.getCourtScheduleId(), session.getSessionDate());
    }

    /**
     * {@code GET /provisionalBooking/status?bookingIds=<id>} (NEW-7a pre-share gate). Returns the
     * single {@code bookings[0].status} for the one id queried.
     */
    private String bookingStatusOf(final String bookingId) {
        final RequestParams params = getRequestParams("/provisionalBooking/status",
                "application/vnd.courtscheduler.get.booking-status+json", SYSTEM_USER_ID,
                Map.of("bookingIds", bookingId));
        final Response response = getCommand(params);
        final String body = response.readEntity(String.class);
        assertThat("booking-status query must succeed: " + body, response.getStatus(), is(OK.getStatusCode()));
        return new JSONObject(body).getJSONArray("bookings").getJSONObject(0).getString("status");
    }

}
