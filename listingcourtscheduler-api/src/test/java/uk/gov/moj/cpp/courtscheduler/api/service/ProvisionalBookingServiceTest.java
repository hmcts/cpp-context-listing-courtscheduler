package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalBookingServiceTest {

    private static final String JUDICIARIES = "judiciaries";

    @InjectMocks
    private ProvisionalBookingService provisionalBookingService;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ProvisionalBookingRepository provisionalBookingRepository;

    // Used from Task 6 onwards; declared here so the class is stable across tasks.
    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    @SuppressWarnings("unchecked")
    void shouldReserveEverySlotUnderOneBookingIdInASingleReservationCall() {
        // CRITICAL 1: every slot of a booking shares the minted bookingId as its hearing_id, and
        // saveBookedSlots opens with a hearing-wide release on that key. Reserving slot by slot
        // therefore released the booking's own earlier slots — a 3-day pick held 1 day. The whole
        // booking must go to the reservation service in ONE list.
        when(reservationService.reserveAllUnderBooking(any(), anyList())).thenReturn("bk-used");

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots("cs-1", "cs-2", "cs-3"));

        assertThat(response.getString("bookingId"), is("bk-used"));

        final ArgumentCaptor<List<ProvisionalSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationService, times(1)).reserveAllUnderBooking(any(), captor.capture());
        assertThat(captor.getValue().size(), is(3));
        assertThat(captor.getValue().stream().map(ProvisionalSlot::getCourtScheduleId).toList(),
                is(List.of("cs-1", "cs-2", "cs-3")));
        verify(provisionalBookingRepository, never()).saveProvisionalBooking(any(), anyString(), any());
    }

    /** POST /unconfirmedBooking/release: the release itself belongs to the reservation service. */
    @Test
    void shouldHandTheBookingIdsToReleaseToTheReservationService() {
        provisionalBookingService.releaseUnconfirmedBookings(List.of("bk-1", "bk-2"), null);

        verify(reservationService).releaseUnconfirmedBookings(List.of("bk-1", "bk-2"), BookingState.RELEASED);
    }

    @Test
    void shouldRecordADroppedLineAsDraftReleased() {
        provisionalBookingService.releaseUnconfirmedBookings(List.of("bk-1"), "DROPPED_FROM_DRAFT");

        verify(reservationService).releaseUnconfirmedBookings(List.of("bk-1"), BookingState.DRAFT_RELEASED);
    }

    @Test
    void shouldRecordASurplusHoldAsSurplusReleased() {
        provisionalBookingService.releaseUnconfirmedBookings(List.of("bk-1"), "SURPLUS");

        verify(reservationService).releaseUnconfirmedBookings(List.of("bk-1"), BookingState.SURPLUS_RELEASED);
    }

    @Test
    void shouldFailTheWholeBookingWhenAnySlotCannotBeHeld() {
        when(reservationService.reserveAllUnderBooking(any(), anyList())).thenThrow(new NoCapacityException("full"));

        assertThrows(NoCapacityException.class,
                () -> provisionalBookingService.bookProvisionalSlots(slots("cs-1", "cs-2")));
    }

    /**
     * LPT-2592: the UI always sends the booking id the line already holds, and the reservation
     * decides whether it can be kept. The response must carry the id actually used - which may be
     * a fresh one - because the UI writes exactly that back onto the result line.
     */
    @Test
    void shouldPassTheSuppliedBookingIdOnAndReturnTheOneTheReservationUsed() {
        when(reservationService.reserveAllUnderBooking(eq("existing-booking-1"), anyList())).thenReturn("fresh-booking-2");
        final ProvisionalBookingSlots request = slots("cs-1");
        request.setBookingId("existing-booking-1");

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(request);

        assertThat(response.getString("bookingId"), is("fresh-booking-2"));
        verify(reservationService).reserveAllUnderBooking(eq("existing-booking-1"), anyList());
    }

    @Test
    void shouldLeaveMintingToTheReservationWhenNoBookingIdIsSupplied() {
        when(reservationService.reserveAllUnderBooking(isNull(), anyList())).thenReturn("minted-1");

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots("cs-1"));

        assertThat(response.getString("bookingId"), is("minted-1"));
    }

    @Test
    void shouldReadSlotsFromReservationRows() {
        final CourtSchedule session = aCourtSchedule("cs-1");
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setBookingId("bk-1");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByBookingId("bk-1")).thenReturn(List.of(reservation));
        when(courtScheduleRepository.findBy("cs-1")).thenReturn(session);

        final JsonObject response = provisionalBookingService.fetchProvisionalSlots("bk-1");

        final JsonArray slots = response.getJsonArray("provisionalSlots");
        assertThat(slots.size(), is(1));
        assertThat(slots.getJsonObject(0).getString("courtScheduleId"), is("cs-1"));
        assertThat(slots.getJsonObject(0).getString("bookingId"), is("bk-1"));
    }

    @Test
    void shouldFallBackToLegacyProvisionalBookingWhenNoReservationExists() {
        when(allocatedListingRepository.findByBookingId("legacy-bk")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("legacy-bk")))
                .thenReturn(List.of(aLegacyProvisionalBooking("legacy-bk", "cs-9")));

        final JsonObject response = provisionalBookingService.fetchProvisionalSlots("legacy-bk");

        assertThat(response.getJsonArray("provisionalSlots").size(), is(1));
        assertThat(response.getJsonArray("provisionalSlots").getJsonObject(0).getString("courtScheduleId"), is("cs-9"));
    }

    @Test
    void shouldReportABookingWithAReservationAsReserved() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setBookingId("bk-live");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByBookingId("bk-live")).thenReturn(List.of(reservation));

        final JsonObject booking = provisionalBookingService.getBookingStatus(List.of("bk-live"), false)
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldReportAPurgedBookingAsNotFound() {
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());

        final JsonObject booking = provisionalBookingService.getBookingStatus(List.of("bk-gone"), false)
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("NOT_FOUND"));
        assertThat(booking.getBoolean("safeToShare"), is(false));
    }

    /**
     * Inverted along with the fallback it guarded: a legacy provisional_booking row no longer
     * makes a booking safe to share.
     *
     * <p>That row proves only that a booking was once recorded — never that a session is still
     * held. Reporting it as safe therefore waved through precisely the drafts most likely to be
     * stale, the oldest ones, which is the opposite of what the gate exists to do. Absence of
     * an unconfirmed booking now means what it says: the hold is gone, so the clerk re-picks.
     *
     * <p>The legacy table is NOT consulted at all any more, which is why no stub for it
     * appears here; Mockito's strict stubbing would fail the test if one did.
     */
    @Test
    void shouldReportABookingWithOnlyALegacyRowAsNotFound() {
        when(allocatedListingRepository.findByBookingId("legacy-bk")).thenReturn(List.of());

        final JsonObject booking = provisionalBookingService.getBookingStatus(List.of("legacy-bk"), false)
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("NOT_FOUND"));
        assertThat(booking.getBoolean("safeToShare"), is(false));
    }

    @Test
    void shouldReportAnAlreadySharedBookingAsSharedAndSafeToShare() {
        when(allocatedListingRepository.findByBookingId("bk-shared"))
                .thenReturn(List.of(aConfirmedRowFor("bk-shared", "real-hearing-1")));

        final JsonObject booking = provisionalBookingService.getBookingStatus(List.of("bk-shared"), false)
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("SHARED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldPreferTheReservationWhenABookingSomehowHasBoth() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setBookingId("bk-both");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        // Both rows share one booking_id now - the confirmed listing from the first share, and a
        // fresh hold from a re-pick during the amendment - so a single lookup returns both and
        // expiresAt is the only thing that separates them.
        when(allocatedListingRepository.findByBookingId("bk-both"))
                .thenReturn(List.of(aConfirmedRowFor("bk-both", "real-hearing-2"), reservation));

        final JsonObject booking = provisionalBookingService.getBookingStatus(List.of("bk-both"), false)
                .getJsonArray("bookings").getJsonObject(0);

        // RESERVED wins even though the confirmed row is listed first: while the clerk holds
        // capacity, reporting SHARED would wave a second share through against a live hold.
        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    // -----------------------------------------------------------------------
    // LPT-2611: with reserveIfExpired, a NOT_FOUND booking is re-booked if its session still has room
    // -----------------------------------------------------------------------

    @Test
    void withoutTheFlagAPurgedBookingStaysNotFoundAndIsNeverRebooked() {
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());

        final JsonObject booking = statusOf("bk-gone", false);

        assertThat(booking.getString("status"), is("NOT_FOUND"));
        verify(reservationService, never()).rebookExpired(anyString());
    }

    @Test
    void withTheFlagARebookedBookingIsReportedRebookedAndSafeToShare() {
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());
        when(reservationService.rebookExpired("bk-gone")).thenReturn(true);

        final JsonObject booking = statusOf("bk-gone", true);

        assertThat(booking.getString("status"), is("REBOOKED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void withTheFlagABookingThatCannotBeRebookedStaysNotFound() {
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());
        when(reservationService.rebookExpired("bk-gone")).thenReturn(false);

        final JsonObject booking = statusOf("bk-gone", true);

        assertThat(booking.getString("status"), is("NOT_FOUND"));
        assertThat(booking.getBoolean("safeToShare"), is(false));
    }

    @Test
    void withTheFlagARefusedRebookStaysNotFound() {
        when(allocatedListingRepository.findByBookingId("bk-full")).thenReturn(List.of());
        when(reservationService.rebookExpired("bk-full")).thenThrow(new NoCapacityException("full"));

        final JsonObject booking = statusOf("bk-full", true);

        assertThat(booking.getString("status"), is("NOT_FOUND"));
        assertThat(booking.getBoolean("safeToShare"), is(false));
    }

    // A double click: the other request re-booked it first, so this one's insert hits the unique
    // reservation index. The honest answer is whatever the booking is now.
    @Test
    void withTheFlagARebookThatLosesARaceReportsTheBookingAsItNowIs() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setBookingId("bk-race");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByBookingId("bk-race")).thenReturn(List.of(), List.of(reservation));
        when(reservationService.rebookExpired("bk-race"))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("allocated_listings_reservation_idx"));

        final JsonObject booking = statusOf("bk-race", true);

        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    // An unexpected failure re-booking one booking must not fail the whole call: listing would
    // then answer UNKNOWN (safe to share) for every booking, including one that is really gone.
    @Test
    void withTheFlagAnUnexpectedRebookFailureStillAnswersThatBookingsRealStatus() {
        when(allocatedListingRepository.findByBookingId("bk-npe")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());
        when(reservationService.rebookExpired("bk-npe")).thenThrow(new IllegalStateException("max_slot is null"));
        when(reservationService.rebookExpired("bk-gone")).thenReturn(false);

        final JsonArray bookings = provisionalBookingService.getBookingStatus(List.of("bk-npe", "bk-gone"), true)
                .getJsonArray("bookings");

        assertThat(bookings.getJsonObject(0).getString("status"), is("NOT_FOUND"));
        assertThat(bookings.getJsonObject(1).getString("status"), is("NOT_FOUND"));
        assertThat(bookings.getJsonObject(1).getBoolean("safeToShare"), is(false));
    }

    // The other click's re-book committed between this request's two reads: it no longer qualifies
    // (its latest event is REBOOKED), and the honest answer is that it is held.
    @Test
    void withTheFlagABookingAnotherRequestJustRebookedIsReportedAsItNowIs() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setBookingId("bk-race");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByBookingId("bk-race")).thenReturn(List.of(), List.of(reservation));
        when(reservationService.rebookExpired("bk-race")).thenReturn(false);

        final JsonObject booking = statusOf("bk-race", true);

        assertThat(booking.getString("status"), is("RESERVED"));
    }

    @Test
    void withTheFlagAReservedOrSharedBookingIsNeverRebooked() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setBookingId("bk-live");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByBookingId("bk-live")).thenReturn(List.of(reservation));
        when(allocatedListingRepository.findByBookingId("bk-shared"))
                .thenReturn(List.of(aConfirmedRowFor("bk-shared", "real-hearing-1")));

        final JsonArray bookings = provisionalBookingService.getBookingStatus(List.of("bk-live", "bk-shared"), true)
                .getJsonArray("bookings");

        assertThat(bookings.getJsonObject(0).getString("status"), is("RESERVED"));
        assertThat(bookings.getJsonObject(1).getString("status"), is("SHARED"));
        verify(reservationService, never()).rebookExpired(anyString());
    }

    private JsonObject statusOf(final String bookingId, final boolean reserveIfExpired) {
        return provisionalBookingService.getBookingStatus(List.of(bookingId), reserveIfExpired)
                .getJsonArray("bookings").getJsonObject(0);
    }

    private CourtSchedule aCourtSchedule(final String courtScheduleId) {
        final CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCourtScheduleId(courtScheduleId);
        return courtSchedule;
    }

    private AllocatedListing aConfirmedRowFor(final String bookingId, final String realHearingId) {
        final AllocatedListing confirmed = new AllocatedListing();
        confirmed.setCourtScheduleId("cs-1");
        confirmed.setHearingId(realHearingId);
        confirmed.setBookingId(bookingId);
        confirmed.setExpiresAt(null);
        return confirmed;
    }

    private ProvisionalBooking aLegacyProvisionalBooking(final String bookingId, final String courtScheduleId) {
        return prepareProvisionalBooking(bookingId, courtScheduleId);
    }

    private ProvisionalBookingSlots slots(final String... courtScheduleIds) {
        final ProvisionalBookingSlots booking = new ProvisionalBookingSlots();
        booking.setProvisionalSlots(java.util.Arrays.stream(courtScheduleIds)
                .map(id -> new ProvisionalSlot()
                        .courtScheduleId(id)
                        .hearingStartTime("2026-10-14T10:00:00.000Z")
                        .duration(60))
                .toList());
        return booking;
    }

    @Test
     void shouldFetchProvisionalSlotsForMultipleBookingIdsWithSameCourtScheduleId() {
        final String bookingId1 = randomUUID().toString();
        final String bookingId2 = randomUUID().toString();
        final String courtScheduleId = randomUUID().toString();
        final String bookedSlots = bookingId1+","+bookingId2;
        ProvisionalBooking provisionalBooking1 = prepareProvisionalBooking(bookingId1, courtScheduleId);
        ProvisionalBooking provisionalBooking2 = prepareProvisionalBooking(bookingId2, courtScheduleId);
        List<ProvisionalBooking> provisionalBookingList = new ArrayList<>();
        provisionalBookingList.add(provisionalBooking1);
        provisionalBookingList.add(provisionalBooking2);

        doReturn(provisionalBookingList).when(provisionalBookingRepository).findByBookingIdIn(anyList());

        final JsonObject actualProvisionalSlots = provisionalBookingService.fetchProvisionalSlots(bookedSlots);

        assertThat(actualProvisionalSlots.getJsonArray("provisionalSlots"), notNullValue());
        assertThat(actualProvisionalSlots.getJsonArray("provisionalSlots").size(), is(2));

        final JsonValue provisionalSlot1 = actualProvisionalSlots.getJsonArray("provisionalSlots").get(0);

        assertThat(provisionalSlot1, notNullValue());
        assertThat(provisionalSlot1.asJsonObject().getString("bookingId"), is(bookingId1));
        assertThat(provisionalSlot1.asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
        assertThat(provisionalSlot1.asJsonObject().get(JUDICIARIES).asJsonArray().size(), is(0));

        final JsonValue provisionalSlot2 = actualProvisionalSlots.getJsonArray("provisionalSlots").get(1);

        assertThat(provisionalSlot2, notNullValue());
        assertThat(provisionalSlot2.asJsonObject().getString("bookingId"), is(bookingId2));
        assertThat(provisionalSlot2.asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
        assertThat(provisionalSlot2.asJsonObject().get(JUDICIARIES).asJsonArray().size(), is(0));
    }

    @Test
     void shouldFetchProvisionalSlots() {
        final String bookingId = randomUUID().toString();
        final String courtScheduleId = randomUUID().toString();
        ProvisionalBooking provisionalBooking = prepareProvisionalBooking(bookingId, courtScheduleId);
        List<ProvisionalBooking> provisionalBookingList = new ArrayList<>();
        provisionalBookingList.add(provisionalBooking);

        doReturn(provisionalBookingList).when(provisionalBookingRepository).findByBookingIdIn(anyList());

        final JsonObject actualProvisionalSlots = provisionalBookingService.fetchProvisionalSlots(bookingId);

        final JsonValue provisionalSlot = actualProvisionalSlots.getJsonArray("provisionalSlots").get(0);

        assertThat(actualProvisionalSlots.getJsonArray("provisionalSlots"), notNullValue());
        assertThat(provisionalSlot, notNullValue());
        assertThat(provisionalSlot.asJsonObject().getString("bookingId"), is(bookingId));
        assertThat(provisionalSlot.asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
    }

    @Test
    void shouldLoadJudiciaryDataWhenListingProfilePresent() {
        final String bookingId = randomUUID().toString();
        final String courtScheduleId = randomUUID().toString();
        final String listingProfileId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String expectedEmail = "judge@example.com";

        ProvisionalBooking provisionalBooking =
                prepareProvisionalBookingWithListingProfile(bookingId, courtScheduleId, listingProfileId);
        CourtScheduleJudiciary judiciaryEntity =
                judiciaryEntity(courtScheduleId, judiciaryId, listingProfileId, expectedEmail);

        doReturn(List.of(provisionalBooking)).when(provisionalBookingRepository).findByBookingIdIn(anyList());
        doReturn(List.of(judiciaryEntity)).when(courtScheduleRepository)
                .getCourtScheduleJudiciariesForProvisionalBooking(anyList());

        JsonObject actual = provisionalBookingService.fetchProvisionalSlots(bookingId);

        verify(courtScheduleRepository).getCourtScheduleJudiciariesForProvisionalBooking(anyList());
        assertThat(actual.getJsonArray("provisionalSlots").size(), is(1));
        JsonObject slot = actual.getJsonArray("provisionalSlots").get(0).asJsonObject();
        assertThat(slot.getString("bookingId"), is(bookingId));
        assertThat(slot.getJsonArray(JUDICIARIES).size(), is(1));
        assertThat(slot.getJsonArray(JUDICIARIES).get(0).asJsonObject().getString("emailAddress"), is(expectedEmail));
    }

    @Test
    void shouldMapEachJudiciaryWhenRepositoryReturnsSeveral() {
        final String bookingId = randomUUID().toString();
        final String courtScheduleId = randomUUID().toString();
        final String listingProfileId = randomUUID().toString();

        ProvisionalBooking provisionalBooking =
                prepareProvisionalBookingWithListingProfile(bookingId, courtScheduleId, listingProfileId);
        CourtScheduleJudiciary first =
                judiciaryEntity(courtScheduleId, randomUUID().toString(), listingProfileId, "first@example.com");
        CourtScheduleJudiciary second =
                judiciaryEntity(courtScheduleId, randomUUID().toString(), listingProfileId, "second@example.com");

        doReturn(List.of(provisionalBooking)).when(provisionalBookingRepository).findByBookingIdIn(anyList());
        doReturn(List.of(first, second)).when(courtScheduleRepository)
                .getCourtScheduleJudiciariesForProvisionalBooking(anyList());

        JsonObject actual = provisionalBookingService.fetchProvisionalSlots(bookingId);

        JsonObject slot = actual.getJsonArray("provisionalSlots").get(0).asJsonObject();
        assertThat(slot.getJsonArray(JUDICIARIES).size(), is(2));
    }

    private static CourtScheduleJudiciary judiciaryEntity(
            final String courtScheduleId,
            final String judiciaryId,
            final String listingProfileId,
            final String email) {
        CourtScheduleJudiciary entity = new CourtScheduleJudiciary();
        entity.setId(new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId));
        entity.setCourtListingProfileId(listingProfileId);
        entity.setEmail(email);
        entity.setRotaJudiciaryId(randomUUID().toString());
        entity.setTitle("Mr");
        entity.setForenames("Test");
        entity.setSurname("Judge");
        entity.setJudiciaryType("CIRCUIT_JUDGE");
        entity.setBenchChairman(false);
        entity.setDeputy(false);
        entity.setPosition("1");
        entity.setActive(true);
        return entity;
    }

    private ProvisionalBooking prepareProvisionalBookingWithListingProfile(
            String bookingId, String courtScheduleId, String listingProfileId) {
        ProvisionalBooking provisionalBooking = new ProvisionalBooking();
        CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setListingProfileId(listingProfileId);
        ProvisionalBookingKey provisionalBookingKey = new ProvisionalBookingKey();
        provisionalBookingKey.setBookingId(bookingId);
        provisionalBookingKey.setCourtSchedule(courtSchedule);
        provisionalBooking.setProvisionalBookingKey(provisionalBookingKey);
        return provisionalBooking;
    }

    private ProvisionalBooking prepareProvisionalBooking(String bookingId, String courtScheduleId) {
        ProvisionalBooking provisionalBooking = new ProvisionalBooking();
        CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCourtScheduleId(courtScheduleId);
        ProvisionalBookingKey provisionalBookingKey = new ProvisionalBookingKey();
        provisionalBookingKey.setBookingId(bookingId);
        provisionalBookingKey.setCourtSchedule(courtSchedule);
        provisionalBooking.setProvisionalBookingKey(provisionalBookingKey);
        return provisionalBooking;
    }
}
