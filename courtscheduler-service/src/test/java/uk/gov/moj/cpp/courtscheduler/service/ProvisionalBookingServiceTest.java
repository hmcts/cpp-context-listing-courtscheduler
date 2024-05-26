package uk.gov.moj.cpp.courtscheduler.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.ArrayList;
import java.util.List;

import javax.json.JsonObject;

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
        assertNotNull(response.getString("bookingIds"));
    }
}