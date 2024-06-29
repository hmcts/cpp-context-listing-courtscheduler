package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProvisionalSlotConverterTest {

    @InjectMocks
    private ProvisionalSlotConverter provisionalSlotConverter;


    @Test
    public void shouldConvertProvisionalSlot() {
        final String payload = fileToString("/test-data/courtscheduler.book.provisional.hearing.slots.json");

        final ProvisionalBookingSlots provisionalBookingSlots = provisionalSlotConverter.convert(payload);

        assertThat(provisionalBookingSlots.getProvisionalSlots().size(),is(2));
        final ProvisionalSlot provisionalSlot1 = provisionalBookingSlots.getProvisionalSlots().get(0);
        assertThat(provisionalSlot1.getCourtScheduleId().toString(), is("000f36bc-f33a-42ea-8a6c-8103636c5341"));

        final ProvisionalSlot provisionalSlot2 = provisionalBookingSlots.getProvisionalSlots().get(1);
        assertThat(provisionalSlot2.getCourtScheduleId().toString(), is("001b1891-cbe9-45fe-a0b9-2168d50a25a2"));
    }

    @Test
    public void shouldConvertProvisionalSlotWithEmptyArray() {
        final String payload = fileToString("/test-data/courtscheduler.book.provisional.hearing.slots-empty-array-payload.json");

        final ProvisionalBookingSlots provisionalBookingSlots = provisionalSlotConverter.convert(payload);

        assertNotNull(provisionalBookingSlots);
        assertThat(isEmpty(provisionalBookingSlots.getProvisionalSlots()), is(true));
    }

    @Test
    public void shouldThrowJsonProcessingException() {
        Assertions.assertThrows(ConverterException.class, () -> {
            provisionalSlotConverter.convert("nonJson");
        });
    }

}
