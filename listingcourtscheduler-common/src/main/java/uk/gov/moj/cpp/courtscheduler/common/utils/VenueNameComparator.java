package uk.gov.moj.cpp.courtscheduler.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VenueNameComparator {

    private static final Pattern ALPHABETIC_PATTERN = Pattern.compile("[a-zA-Z]+");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("\\d+");

    private VenueNameComparator() {
        // Private constructor to prevent instantiation
    }

    /**
     * Compares two venue names with the following rules:
     * 1. Normalizes strings (trim and lowercase)
     * 2. Removes preceding zeroes in numeric digits
     * 3. Checks for partial match on alphabetic parts
     * 4. Checks for exact match on numeric parts
     *
     * @param venueName1 First venue name to compare
     * @param venueName2 Second venue name to compare
     * @return true if the venue names match according to the rules, false otherwise
     */
    public static boolean matches(final String venueName1, final String venueName2) {
        if (venueName1 == null && venueName2 == null) {
            return true;
        }
        if (venueName1 == null || venueName2 == null) {
            return false;
        }

        // Normalize strings (trim and lowercase)
        final String normalized1 = venueName1.trim().toLowerCase();
        final String normalized2 = venueName2.trim().toLowerCase();

        // Extract alphabetic and numeric parts
        final List<String> alphabeticParts1 = extractAlphabeticParts(normalized1);
        final List<String> numericParts1 = extractNumericParts(normalized1);
        final List<String> alphabeticParts2 = extractAlphabeticParts(normalized2);
        final List<String> numericParts2 = extractNumericParts(normalized2);

        // Check if alphabetic parts have partial match
        if (!hasPartialMatch(alphabeticParts1, alphabeticParts2)) {
            return false;
        }

        // Check if numeric parts match exactly (after removing leading zeros)
        return hasExactNumericMatch(numericParts1, numericParts2);
    }

    /**
     * Extracts all alphabetic sequences from a string.
     */
    private static List<String> extractAlphabeticParts(final String str) {
        final List<String> parts = new ArrayList<>();
        final Matcher matcher = ALPHABETIC_PATTERN.matcher(str);
        while (matcher.find()) {
            parts.add(matcher.group());
        }
        return parts;
    }

    /**
     * Extracts all numeric sequences from a string and removes leading zeros.
     */
    private static List<String> extractNumericParts(final String str) {
        final List<String> parts = new ArrayList<>();
        final Matcher matcher = NUMERIC_PATTERN.matcher(str);
        while (matcher.find()) {
            final String numericPart = matcher.group();
            // Remove leading zeros
            final String normalizedNumeric = removeLeadingZeros(numericPart);
            parts.add(normalizedNumeric);
        }
        return parts;
    }

    /**
     * Removes leading zeros from a numeric string.
     * Returns "0" if the string is all zeros.
     */
    private static String removeLeadingZeros(final String numeric) {
        if (numeric == null || numeric.isEmpty()) {
            return numeric;
        }
        // Remove leading zeros, but keep at least one digit
        final String result = numeric.replaceFirst("^0+", "");
        return result.isEmpty() ? "0" : result;
    }

    /**
     * Checks if alphabetic parts have a partial match.
     * A partial match means that all alphabetic parts from one string
     * are contained in the other string (or vice versa).
     */
    private static boolean hasPartialMatch(final List<String> parts1, final List<String> parts2) {
        if (parts1.isEmpty() && parts2.isEmpty()) {
            return true;
        }
        if (parts1.isEmpty() || parts2.isEmpty()) {
            return false;
        }

        // Combine all alphabetic parts into single strings for comparison
        final String combined1 = String.join("", parts1);
        final String combined2 = String.join("", parts2);

        // Check if one contains the other (partial match)
        return combined1.contains(combined2) || combined2.contains(combined1);
    }

    /**
     * Checks if numeric parts match exactly (after normalization).
     */
    private static boolean hasExactNumericMatch(final List<String> parts1, final List<String> parts2) {
        if (parts1.size() != parts2.size()) {
            return false;
        }
        if (parts1.isEmpty() && parts2.isEmpty()) {
            return true;
        }

        // Check each numeric part matches exactly
        for (int i = 0; i < parts1.size(); i++) {
            if (!parts1.get(i).equals(parts2.get(i))) {
                return false;
            }
        }
        return true;
    }
}

