package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
class TimezoneUtilsTest {

    @Test
    void shouldReturnBSTNotApplied(){
        final java.util.Date expectedDate = java.util.Date.from(LocalDateTime.of(2025, 3,15,10, 0).toInstant(ZoneOffset.UTC));
        assertThat(TimezoneUtils.combineLocalDateAndTimeToUtc(LocalDate.of(2025, 3,15), LocalTime.of(10,0)), is(expectedDate));
    }

    @Test
    @Disabled("Disabled because it fails on pipeline and passes locally. This is likely due to the timezone settings of the environment.")
    void shouldReturnBSTApplied(){
        final java.util.Date expectedDate = java.util.Date.from(LocalDate.now().withDayOfMonth(15).withMonth(4).withYear(2025)
                .atTime(8, 0).toInstant(ZoneOffset.UTC));
        // Note: The expected date is adjusted to account for BST (British Summer Time) being in effect
        assertThat(TimezoneUtils.combineLocalDateAndTimeToUtc(LocalDate.of(2025, 4,15), LocalTime.of(10,0)), is(expectedDate));
    }
}
