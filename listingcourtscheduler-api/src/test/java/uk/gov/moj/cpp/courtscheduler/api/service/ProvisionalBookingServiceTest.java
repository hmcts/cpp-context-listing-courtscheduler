package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
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
        assertThat(provisionalSlot1.asJsonObject().get("judiciaries").asJsonArray().size(), is(0));

        final JsonValue provisionalSlot2 = actualProvisionalSlots.getJsonArray("provisionalSlots").get(1);

        assertThat(provisionalSlot2, notNullValue());
        assertThat(provisionalSlot2.asJsonObject().getString("bookingId"), is(bookingId2));
        assertThat(provisionalSlot2.asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
        assertThat(provisionalSlot2.asJsonObject().get("judiciaries").asJsonArray().size(), is(0));
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