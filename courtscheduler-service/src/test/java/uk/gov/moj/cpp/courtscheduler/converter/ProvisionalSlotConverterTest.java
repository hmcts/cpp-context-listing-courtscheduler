package uk.gov.moj.cpp.courtscheduler.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;

import java.util.List;

import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

@ExtendWith(MockitoExtension.class)
public class ProvisionalSlotConverterTest {

    @InjectMocks
    private ProvisionalSlotConverter provisionalSlotConverter;


    @Test
    public void shouldConvertProvisionalSlot() {
        final String payload = fileToString("/test-data/courtscheduler.book.provisional.hearing.slots.json");

        final List<ProvisionalSlot> provisionalSlots = provisionalSlotConverter.convert(payload);

        assertThat(provisionalSlots.size(),is(2));
        final ProvisionalSlot provisionalSlot1 = provisionalSlots.get(0);
        assertThat(provisionalSlot1.getCourtScheduleId(), is("000f36bc-f33a-42ea-8a6c-8103636c5341"));

        final ProvisionalSlot provisionalSlot2 = provisionalSlots.get(1);
        assertThat(provisionalSlot2.getCourtScheduleId(), is("001b1891-cbe9-45fe-a0b9-2168d50a25a2"));
    }

    @Test
    public void shouldConvertProvisionalSlotWithEmptyArray() {
        final String payload = fileToString("/test-data/courtscheduler.book.provisional.hearing.slots-empty-array-payload.json");

        final List<ProvisionalSlot> provisionalSlots = provisionalSlotConverter.convert(payload);

        assertNotNull(provisionalSlots);
        assertThat(isEmpty(provisionalSlots), is(true));
    }

    @Test
    public void shouldThrowJsonProcessingException() {
        Assertions.assertThrows(ConverterException.class, () -> {
            provisionalSlotConverter.convert("nonJson");
        });
    }

}
