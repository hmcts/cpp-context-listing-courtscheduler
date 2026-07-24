package uk.gov.moj.cpp.courtscheduler.common.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VenueNameComparatorTest {

    @Test
    void shouldMatchWhenStringsAreIdentical() {
        assertTrue(VenueNameComparator.matches("Court Room 1", "Court Room 1"));
        assertTrue(VenueNameComparator.matches("Court 8", "Court 8"));
    }

    @Test
    void shouldMatchWhenStringsDifferOnlyByCase() {
        assertTrue(VenueNameComparator.matches("Court Room 1", "COURT ROOM 1"));
        assertTrue(VenueNameComparator.matches("court room 1", "Court Room 1"));
    }

    @Test
    void shouldMatchWhenStringsDifferOnlyByWhitespace() {
        assertTrue(VenueNameComparator.matches("Court Room 1", "  Court Room 1  "));
        assertTrue(VenueNameComparator.matches("  Court Room 1  ", "Court Room 1"));
    }

    @Test
    void shouldMatchWhenNumericPartsHaveLeadingZeros() {
        assertTrue(VenueNameComparator.matches("Court Room 001", "Court Room 1"));
        assertTrue(VenueNameComparator.matches("Court Room 1", "Court Room 001"));
        assertTrue(VenueNameComparator.matches("Court 08", "Court 8"));
        assertTrue(VenueNameComparator.matches("Court 000123", "Court 123"));
    }

    @Test
    void shouldMatchWhenNumericPartsAreZero() {
        assertTrue(VenueNameComparator.matches("Court Room 0", "Court Room 0"));
        assertTrue(VenueNameComparator.matches("Court Room 00", "Court Room 0"));
        assertTrue(VenueNameComparator.matches("Court Room 000", "Court Room 0"));
    }

    @Test
    void shouldMatchWhenAlphabeticPartsHavePartialMatch() {
        assertTrue(VenueNameComparator.matches("Court Room 1", "Court 1"));
        assertTrue(VenueNameComparator.matches("Court 1", "Court Room 1"));
        assertTrue(VenueNameComparator.matches("Main Court Room 1", "Court Room 1"));
        assertTrue(VenueNameComparator.matches("Court Room 1", "Main Court Room 1"));
    }

    @Test
    void shouldMatchWhenBothAlphabeticAndNumericPartsMatch() {
        assertTrue(VenueNameComparator.matches("Court Room 001", "Court Room 1"));
        assertTrue(VenueNameComparator.matches("Main Court 08", "Court 8"));
        assertTrue(VenueNameComparator.matches("Court 0005", "Court Room 5"));
    }

    @Test
    void shouldNotMatchWhenNumericPartsDiffer() {
        assertFalse(VenueNameComparator.matches("Court Room 1", "Court Room 2"));
        assertFalse(VenueNameComparator.matches("Court Room 001", "Court Room 10"));
        assertFalse(VenueNameComparator.matches("Court 8", "Court 9"));
    }

    @Test
    void shouldNotMatchWhenNumericPartsCountDiffer() {
        assertFalse(VenueNameComparator.matches("Court Room 1", "Court Room 1 2"));
        assertFalse(VenueNameComparator.matches("Court 1 2", "Court 1"));
    }

    @Test
    void shouldNotMatchWhenAlphabeticPartsDoNotHavePartialMatch() {
        assertFalse(VenueNameComparator.matches("Court Room 1", "Hall 1"));
        assertFalse(VenueNameComparator.matches("Court 1", "Room 1"));
    }

    @Test
    void shouldMatchWhenOnlyAlphabeticPartsPresent() {
        assertTrue(VenueNameComparator.matches("Court Room", "Court"));
        assertTrue(VenueNameComparator.matches("Court", "Court Room"));
        assertTrue(VenueNameComparator.matches("Main Court Room", "Court Room"));
    }

    @Test
    void shouldMatchWhenOnlyNumericPartsPresent() {
        assertTrue(VenueNameComparator.matches("001", "1"));
        assertTrue(VenueNameComparator.matches("123", "123"));
        assertTrue(VenueNameComparator.matches("0005", "5"));
    }

    @Test
    void shouldMatchWhenBothStringsAreEmpty() {
        assertTrue(VenueNameComparator.matches("", ""));
        assertTrue(VenueNameComparator.matches("   ", "   "));
    }

    @Test
    void shouldMatchWhenBothStringsAreNull() {
        assertTrue(VenueNameComparator.matches(null, null));
    }

    @Test
    void shouldNotMatchWhenOneStringIsNull() {
        assertFalse(VenueNameComparator.matches(null, "Court Room 1"));
        assertFalse(VenueNameComparator.matches("Court Room 1", null));
    }

    @Test
    void shouldNotMatchWhenOneStringIsEmpty() {
        assertFalse(VenueNameComparator.matches("", "Court Room 1"));
        assertFalse(VenueNameComparator.matches("Court Room 1", ""));
    }

    @Test
    void shouldMatchComplexVenueNames() {
        assertTrue(VenueNameComparator.matches("Main Court Room 001", "Main Court Room 1"));
        assertTrue(VenueNameComparator.matches("Court Building A Room 005", "Court Building A Room 5"));
        assertTrue(VenueNameComparator.matches("Family Court Room 012", "Family Court Room 12"));
    }

    @Test
    void shouldMatchWhenAlphabeticPartsAreSubset() {
        // Partial match when one string has additional alphabetic parts
        assertTrue(VenueNameComparator.matches("Main Court Room 1", "Court Room 1"));
        assertTrue(VenueNameComparator.matches("Court Room 1", "Main Court Room 1"));
    }

    @Test
    void shouldNotMatchWhenNumericPartsAreInDifferentOrder() {
        assertFalse(VenueNameComparator.matches("Court 1 Room 2", "Court 2 Room 1"));
    }

    @Test
    void shouldMatchWhenStringsHaveMultipleNumericParts() {
        assertTrue(VenueNameComparator.matches("Court 001 Room 002", "Court 1 Room 2"));
        assertTrue(VenueNameComparator.matches("Building 05 Floor 10", "Building 5 Floor 10"));
    }

    @Test
    void shouldNotMatchWhenMultipleNumericPartsDiffer() {
        assertFalse(VenueNameComparator.matches("Court 001 Room 002", "Court 1 Room 3"));
        assertFalse(VenueNameComparator.matches("Building 05 Floor 10", "Building 5 Floor 11"));
    }

    @Test
    void shouldHandleSpecialCharacters() {
        assertTrue(VenueNameComparator.matches("Court-Room-001", "Court-Room-1"));
        assertTrue(VenueNameComparator.matches("Court_Room_001", "Court_Room_1"));
        assertTrue(VenueNameComparator.matches("Court Room #001", "Court Room #1"));
    }

    @Test
    void shouldMatchRealWorldExamples() {
        // Examples that might occur in practice
        assertTrue(VenueNameComparator.matches("Court 8", "Court 08"));
        assertTrue(VenueNameComparator.matches("Court Room 1", "Court Room 001"));
        assertTrue(VenueNameComparator.matches("Main Court 12", "Main Court 012"));
    }
}

