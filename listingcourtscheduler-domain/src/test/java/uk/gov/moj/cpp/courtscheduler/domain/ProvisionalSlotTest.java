package uk.gov.moj.cpp.courtscheduler.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

class ProvisionalSlotTest {

    @Test
    void shouldCarryDuration() {
        final ProvisionalSlot slot = ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                .withCourtScheduleId("cs-1")
                .withHearingStartTime("2026-10-14T10:00:00.000Z")
                .withDuration(90)
                .build();

        assertThat(slot.getDuration(), is(90));
    }

    @Test
    void shouldDefaultDurationToNullWhenOmitted() {
        final ProvisionalSlot slot = ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                .withCourtScheduleId("cs-1")
                .withHearingStartTime("2026-10-14T10:00:00.000Z")
                .build();

        assertThat(slot.getDuration(), is(nullValue()));
    }
}
