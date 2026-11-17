package uk.gov.moj.cpp.courtscheduler.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeekCommencingRequestParamTest {

    private static final String CROWN = "CROWN";
    private static final String MULTI_DAY_MINUTES = "1080";

    @Test
    void courtScheduleRequestParamShouldIncludeTheFlagInEqualsAndHashCode() {
        final CourtScheduleRequestParam withoutFlag = courtScheduleRequestParam(null);

        assertEquals(courtScheduleRequestParam(true), courtScheduleRequestParam(true));
        assertEquals(courtScheduleRequestParam(true).hashCode(), courtScheduleRequestParam(true).hashCode());
        assertNotEquals(courtScheduleRequestParam(false), courtScheduleRequestParam(true));
        assertNotEquals(withoutFlag, courtScheduleRequestParam(true));
    }

    @Test
    void courtScheduleRequestParamShortConstructorShouldLeaveTheFlagUnset() {
        assertNull(new CourtScheduleRequestParam("centre", null, null, "2027-01-04", "2027-01-10", null, "10", "1").isWeekCommencing());
    }

    @Test
    void hearingSlotRequestParamShouldIncludeTheFlagInEqualsAndHashCode() {
        assertEquals(hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES), hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES));
        assertEquals(hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES).hashCode(),
                hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES).hashCode());
        assertNotEquals(hearingSlotRequestParam(false, CROWN, MULTI_DAY_MINUTES), hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES));
        assertNotEquals(hearingSlotRequestParam(null, CROWN, MULTI_DAY_MINUTES), hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES));
    }

    @Test
    void weekCommencingOnlyShouldBeTrueOnlyForAnExplicitTrue() {
        assertTrue(hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES).weekCommencingOnly());
        assertFalse(hearingSlotRequestParam(false, CROWN, MULTI_DAY_MINUTES).weekCommencingOnly());
        assertFalse(hearingSlotRequestParam(null, CROWN, MULTI_DAY_MINUTES).weekCommencingOnly());
    }

    @Test
    void aWeekCommencingSearchShouldNeverBeAMultiDaySearch() {
        assertFalse(hearingSlotRequestParam(true, CROWN, MULTI_DAY_MINUTES).isCrownMultiDaySearch());
        assertTrue(hearingSlotRequestParam(false, CROWN, MULTI_DAY_MINUTES).isCrownMultiDaySearch());
        assertTrue(hearingSlotRequestParam(null, CROWN, MULTI_DAY_MINUTES).isCrownMultiDaySearch());
    }

    private static CourtScheduleRequestParam courtScheduleRequestParam(final Boolean isWeekCommencing) {
        return new CourtScheduleRequestParam("centre", null, null, "2027-01-04", "2027-01-10", null, "10", "1", isWeekCommencing);
    }

    private static HearingSlotRequestParam hearingSlotRequestParam(final Boolean isWeekCommencing, final String jurisdiction,
                                                                   final String duration) {
        return new HearingSlotRequestParam("ADULT", "2027-01-04", "2027-01-29", null, null, "OU1", "10", "1", null, null, null,
                null, null, null, false, duration, null, jurisdiction, isWeekCommencing);
    }
}
