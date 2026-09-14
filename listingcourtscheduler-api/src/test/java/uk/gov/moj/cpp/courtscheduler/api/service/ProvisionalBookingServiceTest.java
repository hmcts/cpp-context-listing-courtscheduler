package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
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
import java.util.UUID;

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
        when(reservationService.reserveAll(anyString(), anyList())).thenReturn(List.of(new AllocatedSlot()));

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots("cs-1", "cs-2", "cs-3"));

        assertThat(response.getString("bookingId"), is(notNullValue()));
        final String bookingId = response.getString("bookingId");

        final ArgumentCaptor<List<ProvisionalSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(reservationService, times(1)).reserveAll(eq(bookingId), captor.capture());
        assertThat(captor.getValue().size(), is(3));
        assertThat(captor.getValue().stream().map(ProvisionalSlot::getCourtScheduleId).toList(),
                is(List.of("cs-1", "cs-2", "cs-3")));
        verify(provisionalBookingRepository, never()).saveProvisionalBooking(any(), anyString(), any());
    }

    @Test
    void shouldFailTheWholeBookingWhenAnySlotCannotBeHeld() {
        when(reservationService.reserveAll(anyString(), anyList())).thenThrow(new NoCapacityException("full"));

        assertThrows(NoCapacityException.class,
                () -> provisionalBookingService.bookProvisionalSlots(slots("cs-1", "cs-2")));
    }

    @Test
    void shouldReuseTheSuppliedBookingIdRatherThanMintingANewOne() {
        when(reservationService.reserveAll(anyString(), anyList())).thenReturn(List.of(new AllocatedSlot()));
        final ProvisionalBookingSlots request = slots("cs-1");
        request.setBookingId("existing-booking-1");

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(request);

        assertThat(response.getString("bookingId"), is("existing-booking-1"));
        verify(reservationService).reserveAll(eq("existing-booking-1"), anyList());
    }

    @Test
    void shouldMintABookingIdWhenNoneIsSupplied() {
        when(reservationService.reserveAll(anyString(), anyList())).thenReturn(List.of(new AllocatedSlot()));

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots("cs-1"));

        assertThat(response.getString("bookingId"), is(notNullValue()));
        assertDoesNotThrow(() -> UUID.fromString(response.getString("bookingId")));
    }

    @Test
    void shouldReadSlotsFromReservationRows() {
        final CourtSchedule session = aCourtSchedule("cs-1");
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setHearingId("bk-1");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-1")).thenReturn(List.of(reservation));
        when(courtScheduleRepository.findBy("cs-1")).thenReturn(session);

        final JsonObject response = provisionalBookingService.fetchProvisionalSlots("bk-1");

        final JsonArray slots = response.getJsonArray("provisionalSlots");
        assertThat(slots.size(), is(1));
        assertThat(slots.getJsonObject(0).getString("courtScheduleId"), is("cs-1"));
        assertThat(slots.getJsonObject(0).getString("bookingId"), is("bk-1"));
    }

    @Test
    void shouldFallBackToLegacyProvisionalBookingWhenNoReservationExists() {
        when(allocatedListingRepository.findByHearingId("legacy-bk")).thenReturn(List.of());
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
        reservation.setHearingId("bk-live");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-live")).thenReturn(List.of(reservation));

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-live")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldReportAPurgedBookingAsNone() {
        when(allocatedListingRepository.findByHearingId("bk-gone")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("bk-gone"))).thenReturn(List.of());

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-gone")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("NONE"));
        assertThat(booking.getBoolean("safeToShare"), is(false));
    }

    @Test
    void shouldReportALegacyProvisionalBookingAsLegacy() {
        when(allocatedListingRepository.findByHearingId("legacy-bk")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("legacy-bk")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("legacy-bk")))
                .thenReturn(List.of(aLegacyProvisionalBooking("legacy-bk", "cs-9")));

        final JsonObject booking = provisionalBookingService.getBookingStatus("legacy-bk")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("LEGACY"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldReportAnAlreadySharedBookingAsSharedAndSafeToShare() {
        when(allocatedListingRepository.findByHearingId("bk-shared")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("bk-shared"))
                .thenReturn(List.of(aConfirmedRowFor("bk-shared", "real-hearing-1")));

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-shared")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("SHARED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldPreferTheReservationWhenABookingSomehowHasBoth() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setHearingId("bk-both");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-both")).thenReturn(List.of(reservation));
        // Lenient by design: this stub builds the "both rows exist" world the test name describes.
        // A correct statusOf short-circuits on the reservation and never consumes it, which is the
        // behaviour the never() verification below pins.
        lenient().when(allocatedListingRepository.findByBookingId("bk-both"))
                .thenReturn(List.of(aConfirmedRowFor("bk-both", "real-hearing-2")));

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-both")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
        verify(allocatedListingRepository, never()).findByBookingId("bk-both");
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
                .map(id -> ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                        .withCourtScheduleId(id)
                        .withHearingStartTime("2026-10-14T10:00:00.000Z")
                        .withDuration(60)
                        .build())
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
