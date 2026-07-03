package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotsUpdateServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private ProvisionalBookingRepository provisionalBookingRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private SlotsUpdateService service;

    @BeforeEach
    void setup() {
        setField(service, "courtScheduleRepository", courtScheduleRepository);
        setField(service, "provisionalBookingRepository", provisionalBookingRepository);
    }

    @Test
    void shouldUpdateAllocatedSlots() {

        final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();
        when(courtScheduleRepository.saveBookedSlots(any(), anyBoolean(), anyBoolean())).thenReturn(new Result("", true));
        service.update(allocatedSlots);

        verify(courtScheduleRepository).saveBookedSlots(allocatedSlots, false, false);
    }

    @Test
    void shouldUpdateAllocatedSlotsWithBookingId() throws JsonProcessingException {

        final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

        final String provisionalBookingPayload = fileToString("/test-data/courtscheduler.get.provisional.hearing.slots.json");
        final List<ProvisionalBookingInfo> provisionalBookingInfos = new ObjectMapper().readValue(provisionalBookingPayload, new TypeReference<>() {
        });

        final Map<String, Date> courtScheduleInfo = provisionalBookingInfos
                .stream()
                .collect(Collectors.toMap(ProvisionalBookingInfo::getCourtScheduleId, ProvisionalBookingInfo::getHearingStartTime));

        when(provisionalBookingRepository.getCourtScheduleInfo(any())).thenReturn(courtScheduleInfo);
        when(courtScheduleRepository.saveBookedSlots(any(), anyBoolean(), anyBoolean())).thenReturn(new Result("", true));
        service.update(allocatedSlots);

        verify(courtScheduleRepository, atLeastOnce()).saveBookedSlots(any(), eq(true), eq(false));
    }

    @Test
    void shouldThrowProvisionalSlotNotFoundExceptionWhenUpdateAllocatedSlotsWithBookingId() {

        Assertions.assertThrows(uk.gov.moj.cpp.courtscheduler.exception.ProvisionalSlotNotFoundException.class, () -> {
            final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid.json");
            final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

            when(provisionalBookingRepository.getCourtScheduleInfo(any())).thenReturn(Collections.emptyMap());

            service.update(allocatedSlots);
        });
    }

    @Test
    void shouldThrowCourtScheduleIdNotMatchingExceptionWhenUpdateAllocatedSlotsWithBookingId() {

        Assertions.assertThrows(uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException.class, () -> {
            final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid-wrong-csId.json");
            final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

            final String provisionalBookingPayload = fileToString("/test-data/courtscheduler.get.provisional.hearing.slots.json");
            final List<ProvisionalBookingInfo> provisionalBookingInfos = new ObjectMapper().readValue(provisionalBookingPayload, new TypeReference<List<ProvisionalBookingInfo>>() {
            });

            final Map<String, Date> courtScheduleInfo = provisionalBookingInfos
                    .stream()
                    .collect(Collectors.toMap(ProvisionalBookingInfo::getCourtScheduleId, ProvisionalBookingInfo::getHearingStartTime));


            when(provisionalBookingRepository.getCourtScheduleInfo(any())).thenReturn(courtScheduleInfo);

            service.update(allocatedSlots);
        });
    }

    @Test
    void shouldSearchUpdateAllocatedSlots() {

        final String payload = fileToString("/test-data/courtscheduler.search.update.available.hearing.slots-police.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();

        when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(true))).thenReturn(new Result("", true));

        service.searchUpdate(allocatedSlots);

        verify(courtScheduleRepository).saveBookedSlots(allocatedSlots, false, true);
    }

    @Test
    void shouldSearchUpdateAllocatedSlots_NonPolice() {

        final String payload = fileToString("/test-data/courtscheduler.search.update.available.hearing.slots-non-police.json");
        final List<AllocatedSlot> allocatedSlots = new AllocatedSlotConverter().convert(payload).getHearingSlots();
        when(courtScheduleRepository.saveBookedSlots(any(), eq(false), eq(false))).thenReturn(new Result("", true));
        service.searchUpdate(allocatedSlots);

        verify(courtScheduleRepository).saveBookedSlots(allocatedSlots, false, false);
    }

    @Test
    void shouldListHearingSlotsAndReturnResponse() {
        final RequestedSlots wrapper = new RequestedSlots();

        final Hearing hearing1 = new Hearing();
        final String hearingId1 = UUID.randomUUID().toString();
        hearing1.setHearingId(hearingId1);
        final Hearing hearing2 = new Hearing();
        final String hearingId2 = UUID.randomUUID().toString();
        hearing2.setHearingId(hearingId2);
        final List<Hearing> hearings = List.of(hearing1, hearing2);

        when(courtScheduleRepository.updateListHearingSlots(wrapper)).thenReturn(hearings);

        ListHearingSlotsResponse response = service.listHearingSlots(wrapper);

        assertNotNull(response);
        assertEquals(2, response.getHearings().size());
        assertEquals(hearingId1, response.getHearings().get(0).getHearingId());
        verify(courtScheduleRepository).updateListHearingSlots(wrapper);
        verifyNoMoreInteractions(courtScheduleRepository);
    }
}