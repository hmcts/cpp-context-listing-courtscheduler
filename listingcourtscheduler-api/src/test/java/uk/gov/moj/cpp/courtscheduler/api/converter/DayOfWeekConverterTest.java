package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DayOfWeekConverterTest {

    @Test
    void convertStringToListShouldReturnCorrectDaysOfWeek() {
        final Set<DayOfWeek> expectedDays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        final Set<DayOfWeek> actualDays = DayOfWeekConverter.convert("MONDAY,TUESDAY");
        assertEquals(expectedDays, actualDays);
    }

    @Test
    void convertStringToListShouldHandleExtraSpaces() {
        final Set<DayOfWeek> expectedDays = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        final Set<DayOfWeek> actualDays = DayOfWeekConverter.convert(" MONDAY , TUESDAY ");
        assertEquals(expectedDays, actualDays);
    }

    @Test
    void convertStringToListShouldReturnEmptySetForEmptyString() {
        final Set<DayOfWeek> actualDays = DayOfWeekConverter.convert("");
        assertTrue(actualDays.isEmpty());
    }

    @Test
    void convertListToStringShouldReturnCorrectString() {
        final Set<DayOfWeek> daysOfWeek = EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        final String actualString = DayOfWeekConverter.convert(new ArrayList<>(daysOfWeek));
        assertTrue(actualString.contains("MONDAY"));
        assertTrue(actualString.contains("TUESDAY"));
        assertTrue(actualString.contains(","));
    }

    @Test
    void convertListToStringShouldReturnEmptyStringForEmptyList() {
        final String actualString = DayOfWeekConverter.convert(new ArrayList<>());
        assertEquals("", actualString);
    }

}