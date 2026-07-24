package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlots;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
 class AllocatedSlotConverterTest {

    private AllocatedSlotConverter converter = new AllocatedSlotConverter();

    @Test
     void shouldConvertAllocatedSlot() {
        final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots.json");

        AllocatedSlots allocatedSlots = converter.convert(payload);
        final List<AllocatedSlot> allocatedSlotsList = allocatedSlots.getHearingSlots();

        assertThat(allocatedSlotsList.size(), is(2));
        final AllocatedSlot firstSlot = allocatedSlotsList.get(0);
        assertThat(firstSlot.getDuration(), is(20));
        assertThat(firstSlot.getSessionDate(), is("2018-09-28"));
        assertThat(firstSlot.getSession(), is("AM"));
        assertThat(firstSlot.getCourtRoomId(), is("7"));
        assertThat(firstSlot.getOuCode(), is("B01LY00"));
        assertThat(firstSlot.getHearingId().toString(), is("2669bdd9-2620-42e2-8310-43b66a91b4cf"));
        assertThat(firstSlot.getCourtScheduleId().toString(), is("3669bdd9-2620-42e2-8310-43b66a91b5bf"));
        assertThat(firstSlot.getHearingStartTime(), is("2018-09-28T09:00:00.000Z"));

        final AllocatedSlot secondSlot = allocatedSlotsList.get(1);
        assertThat(secondSlot.getDuration(), is(21));
        assertThat(secondSlot.getSessionDate(), is("2018-09-30"));
        assertThat(secondSlot.getSession(), is("AD"));
        assertThat(secondSlot.getCourtRoomId(), is("23"));
        assertThat(secondSlot.getOuCode(), is("B01LY00"));
        assertThat(secondSlot.getHearingId().toString(), is("1d5f0dd6-0c6c-42e7-b0b8-7314f81ddc72"));
        assertThat(secondSlot.getCourtScheduleId().toString(), is("2e5f0dd6-0c6c-42e7-b0b8-7314f81ddc72"));
        assertThat(secondSlot.getHearingStartTime(), is("2018-09-30T09:00:00.000Z"));

    }

    @Test
     void shouldConvertAllocatedSlotWithBookingId() {
        final String payload = fileToString("/test-data/courtscheduler.update.available.hearing.slots-with-bookingid.json");

        AllocatedSlots allocatedSlots = converter.convert(payload);
        final List<AllocatedSlot> allocatedSlotsList = allocatedSlots.getHearingSlots();

        assertThat(allocatedSlotsList.size(), is(1));
        final AllocatedSlot bookingSlot = allocatedSlotsList.get(0);
        assertThat(bookingSlot.getDuration(), is(20));
        assertThat(bookingSlot.getHearingId().toString(), is("2669bdd9-2620-42e2-8310-43b66a91b4cf"));
        assertThat(bookingSlot.getBookingId().toString(), is("2262a5cc-621a-40a5-ae98-a6cacc299432"));
        assertThat(bookingSlot.getHearingStartTime(), is(nullValue()));
    }

}
