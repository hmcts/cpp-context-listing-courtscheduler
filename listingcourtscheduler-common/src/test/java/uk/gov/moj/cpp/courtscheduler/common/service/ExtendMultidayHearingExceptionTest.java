package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException;
import uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException.ErrorCode;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExtendMultidayHearingExceptionTest {

    @Test
    void twoArgConstructorShouldDefaultUnavailableDatesToEmpty() {
        final ExtendMultidayHearingException e = new ExtendMultidayHearingException(
                ErrorCode.INVALID_DATE_RANGE, "bad range");

        assertEquals(ErrorCode.INVALID_DATE_RANGE, e.getErrorCode());
        assertEquals("bad range", e.getMessage());
        assertTrue(e.getUnavailableDates().isEmpty());
    }

    @Test
    void threeArgConstructorShouldCopyUnavailableDates() {
        final ExtendMultidayHearingException e = new ExtendMultidayHearingException(
                ErrorCode.NO_AVAILABILITY, "blocked",
                List.of(LocalDate.of(2026, 3, 6)));

        assertEquals(ErrorCode.NO_AVAILABILITY, e.getErrorCode());
        assertEquals(List.of(LocalDate.of(2026, 3, 6)), e.getUnavailableDates());
    }

    @Test
    void threeArgConstructorShouldTreatNullUnavailableDatesAsEmpty() {
        final ExtendMultidayHearingException e = new ExtendMultidayHearingException(
                ErrorCode.NO_EXISTING_ALLOCATION, "missing", null);

        assertTrue(e.getUnavailableDates().isEmpty());
    }
}
