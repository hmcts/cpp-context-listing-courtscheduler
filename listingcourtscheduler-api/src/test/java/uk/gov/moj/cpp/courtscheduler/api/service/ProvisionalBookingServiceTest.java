package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException;
import uk.gov.moj.cpp.courtscheduler.exception.SlotsBookException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.ArrayList;
import java.util.List;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalBookingServiceTest {

    private static final String JUDICIARIES = "judiciaries";

    @InjectMocks
    private ProvisionalBookingService provisionalBookingService;
    @Mock
    private ProvisionalBookingRepository provisionalBookingRepository;
    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    void shouldProcessProvisionalBookingRequestSuccessfully() {
        ProvisionalBookingSlots provisionalBookingSlots = new ProvisionalBookingSlots();
        List<ProvisionalSlot> provisionalSlotList = new ArrayList<>();
        ProvisionalSlot provisionalSlot = new ProvisionalSlot("2523432432");
        provisionalSlotList.add(provisionalSlot);
        provisionalBookingSlots.setProvisionalSlots(provisionalSlotList);

        when(courtScheduleRepository.findBy(anyString())).thenReturn(new CourtSchedule());
        doNothing().when(provisionalBookingRepository).saveProvisionalBooking(any(), anyString(), any());

        JsonObject response = provisionalBookingService.bookProvisionalSlots(provisionalBookingSlots);

        assertNotNull(response);
        assertNotNull(response.getString("bookingId"));
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

    @Test
    void shouldThrowSlotsBookExceptionWhenSaveProvisionalBookingFails() {
        ProvisionalBookingSlots provisionalBookingSlots = new ProvisionalBookingSlots();
        ProvisionalSlot provisionalSlot = new ProvisionalSlot("2523432432");
        provisionalBookingSlots.setProvisionalSlots(List.of(provisionalSlot));

        when(courtScheduleRepository.findBy(anyString())).thenReturn(new CourtSchedule());
        doThrow(new PersistenceStoreException("db error")).when(provisionalBookingRepository)
                .saveProvisionalBooking(any(), anyString(), any());

        assertThrows(SlotsBookException.class, () -> provisionalBookingService.bookProvisionalSlots(provisionalBookingSlots));
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