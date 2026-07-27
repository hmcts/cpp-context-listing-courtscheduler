package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HearingSlotSearchRequestToAllocatedSlotConverterTest {

    @InjectMocks
    HearingSlotSearchRequestToAllocatedSlotConverter hearingSlotSearchRequestToAllocatedSlotConverter;

    @Test
    public void shouldConvertJsonObjectToRequestParam() {
        HearingSlotSearchRequest hearingSlotSearchRequest  = new HearingSlotSearchRequest("5771a96b-1c5a-45d1-b647-1bec5212cafc", "B01LY00", "2025-05-13",
                null, null, null, null, true, null);
        AllocatedSlot allocatedSlot = hearingSlotSearchRequestToAllocatedSlotConverter.convert(hearingSlotSearchRequest);

        assertNotNull(allocatedSlot);
        assertEquals("5771a96b-1c5a-45d1-b647-1bec5212cafc", allocatedSlot.getHearingId());
    }

    @Test
    public void shouldCarryBusinessTypeThroughToAllocatedSlot() {
        HearingSlotSearchRequest hearingSlotSearchRequest = new HearingSlotSearchRequest("5771a96b-1c5a-45d1-b647-1bec5212cafc", "B01LY00", "2025-05-13",
                null, null, null, null, false, "ENF_AUTO");
        AllocatedSlot allocatedSlot = hearingSlotSearchRequestToAllocatedSlotConverter.convert(hearingSlotSearchRequest);

        assertEquals("ENF_AUTO", allocatedSlot.getBusinessType());
    }

    @Test
    public void shouldLeaveBusinessTypeNullWhenNotProvided() {
        HearingSlotSearchRequest hearingSlotSearchRequest = new HearingSlotSearchRequest("5771a96b-1c5a-45d1-b647-1bec5212cafc", "B01LY00", "2025-05-13",
                null, null, null, null, false, null);
        AllocatedSlot allocatedSlot = hearingSlotSearchRequestToAllocatedSlotConverter.convert(hearingSlotSearchRequest);

        assertEquals(null, allocatedSlot.getBusinessType());
    }
}