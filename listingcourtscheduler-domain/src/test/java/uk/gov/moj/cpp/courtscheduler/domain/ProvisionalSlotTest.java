package uk.gov.moj.cpp.courtscheduler.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalSlot;

import org.junit.jupiter.api.Test;

/**
 * Guards {@code duration} on the booking wire contract.
 *
 * <p>ProvisionalSlot is no longer hand-written — it is generated from
 * {@code courtscheduler-api.openapi.yml}, so these assertions now test that the spec still
 * declares the field. That is the point: when the hand-written POJO was replaced by the
 * generated one, {@code duration} was absent from the schema and would have been dropped
 * silently. Omitting it is the "400 duration is required and must be greater than zero for
 * duration-based session" refusal, which is not visible until a clerk picks such a session.
 */
class ProvisionalSlotTest {

    @Test
    void shouldCarryDuration() {
        final ProvisionalSlot slot = new ProvisionalSlot()
                .courtScheduleId("cs-1")
                .hearingStartTime("2026-10-14T10:00:00.000Z")
                .duration(90);

        assertThat(slot.getDuration(), is(90));
    }

    @Test
    void shouldDefaultDurationToNullWhenOmitted() {
        final ProvisionalSlot slot = new ProvisionalSlot()
                .courtScheduleId("cs-1")
                .hearingStartTime("2026-10-14T10:00:00.000Z");

        assertThat(slot.getDuration(), is(nullValue()));
    }
}
