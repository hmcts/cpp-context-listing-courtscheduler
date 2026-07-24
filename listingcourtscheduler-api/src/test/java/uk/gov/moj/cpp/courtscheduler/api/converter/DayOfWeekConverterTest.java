package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DayOfWeekConverterTest {

    @Test
    void convertStringToListShouldReturnCorrectDaysOfWeek() {
        Set<DayOfWeek> expectedDays = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        Set<DayOfWeek> actualDays = DayOfWeekConverter.convert("MONDAY,TUESDAY");
        assertEquals(expectedDays, actualDays);
    }

    @Test
    void convertStringToListShouldHandleExtraSpaces() {
        Set<DayOfWeek> expectedDays = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        Set<DayOfWeek> actualDays = DayOfWeekConverter.convert(" MONDAY , TUESDAY ");
        assertEquals(expectedDays, actualDays);
    }

    @Test
    void convertStringToListShouldReturnEmptySetForEmptyString() {
        Set<DayOfWeek> actualDays = DayOfWeekConverter.convert("");
        assertTrue(actualDays.isEmpty());
    }

    @Test
    void convertListToStringShouldReturnCorrectString() {
        Set<DayOfWeek> daysOfWeek = new HashSet<>(Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY));
        String actualString = DayOfWeekConverter.convert(new ArrayList<>(daysOfWeek));
        assertTrue(actualString.contains("MONDAY"));
        assertTrue(actualString.contains("TUESDAY"));
        assertTrue(actualString.contains(","));
    }

    @Test
    void convertListToStringShouldReturnEmptyStringForEmptyList() {
        String actualString = DayOfWeekConverter.convert(new ArrayList<>());
        assertEquals("", actualString);
    }

}