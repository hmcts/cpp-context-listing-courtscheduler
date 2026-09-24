package uk.gov.moj.cpp.courtscheduler.domain.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Utility class for handling timezone conversions.
 * This class provides methods to convert between UTC and local time (BST/GMT).
 * It is designed to be a temporary solution until the frontend can handle timezone conversions.
 */
public class TimezoneUtils {
    
    public static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");
    public static final ZoneOffset UTC_ZONE = ZoneOffset.UTC;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");


    private TimezoneUtils() {
        // Private constructor to prevent instantiation
    }
    
    /**
     * Converts a UTC date to local time (BST/GMT).
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param utcDate The UTC date to convert
     * @return The date in local time (BST/GMT)
     */
    public static Instant utcToLocal(final Instant utcDate) {
        if (utcDate == null) {
            return null;
        }

        // Convert to ZonedDateTime in UTC
        final ZonedDateTime utcZoned = utcDate.atZone(UTC_ZONE);

        // Convert to London time
        final ZonedDateTime londonZoned = utcZoned.withZoneSameInstant(LONDON_ZONE);

        // Convert back to an instant
        return londonZoned.toInstant();
    }
    
    /**
     * Converts a local time (BST/GMT) date to UTC.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param localDate The local date to convert
     * @return The date in UTC
     */
    public static Instant localToUtc(final Instant localDate) {
        if (localDate == null) {
            return null;
        }

        // Convert to ZonedDateTime in London time
        final ZonedDateTime londonZoned = localDate.atZone(LONDON_ZONE);

        // Convert to UTC
        final ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);

        // Convert back to an instant
        return utcZoned.toInstant();
    }
    
    /**
     * Combines a local date and time and converts it to UTC.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param date The local date
     * @param time The local time
     * @return The combined date and time in UTC
     */
    public static Date combineLocalDateAndTimeToUtc(final LocalDate date, final LocalTime time) {
        if (date == null || time == null) {
            return null;
        }
        
        // Create a LocalDateTime in London time
        final LocalDateTime localDateTime = LocalDateTime.of(date, time);

        // Convert to ZonedDateTime in London time
        final ZonedDateTime londonZoned = localDateTime.atZone(LONDON_ZONE).withZoneSameInstant(UTC_ZONE);

        // Convert to UTC
        final ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);

        // Re-interpret the UTC wall-clock fields in the JVM default timezone: this replicates the
        // previous Calendar.Builder-based construction exactly, since Calendar.Builder#setDate/
        // #setTimeOfDay build the Calendar in the default timezone rather than UTC.
        return Date.from(utcZoned.toLocalDateTime().atZone(ZoneId.systemDefault()).toInstant());
    }
    
    /**
     * Converts a UTC date to a local time string in ISO format.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param utcDate The UTC date to convert
     * @return The date in local time as an ISO string
     */
    public static String utcToLocalIsoString(final Instant utcDate) {
        if (utcDate == null) {
            return null;
        }

        // Convert to ZonedDateTime in UTC
        final ZonedDateTime utcZoned = utcDate.atZone(UTC_ZONE);
        
        // Convert to London time
        final ZonedDateTime londonZoned = utcZoned.withZoneSameInstant(LONDON_ZONE);
        
        // Format as ISO string
        return londonZoned.format(DateUtils.ISO_8601_FORMATTER);
    }
    
    /**
     * Converts a local time string in ISO format to a UTC date.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param localIsoString The local time as an ISO string
     * @return The date in UTC
     */
    public static Date localIsoStringToUtc(final String localIsoString) {
        if (localIsoString == null) {
            return null;
        }
        
        // Parse the ISO string to a ZonedDateTime in London time
        final ZonedDateTime londonZoned = ZonedDateTime.parse(localIsoString, DateUtils.ISO_8601_FORMATTER.withZone(LONDON_ZONE));
        
        // Convert to UTC
        final ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);
        
        // Convert to Date
        return Date.from(utcZoned.toInstant());
    }

    /**
     * Calculates the national break time for the given date.
     * During British Summer (British Summer Time), the break time is 12:00 UTC.
     * During British Winter (Greenwich Mean Time), the break time is 13:00 UTC.
     *
     * @param sessionDate The session date to check
     * @return The national break time as a Date object
     */
    public static Date calculateNationalBreakTime(final LocalDate sessionDate) {
        if (sessionDate == null) {
            return null;
        }

        // Create a LocalDateTime at noon on the session date
        final LocalDateTime noonTime = sessionDate.atTime(12, 0);

        // Convert to ZonedDateTime in London time
        final ZonedDateTime londonZoned = noonTime.atZone(LONDON_ZONE);

        // Convert to UTC
        final ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);

        final ZonedDateTime breakZoned = getZonedDateTime(sessionDate, londonZoned, utcZoned);

        return Date.from(breakZoned.toInstant());
    }

    /**
     * Converts the given local date and time to a UTC time string ("HH:mm"), handling DST correctly.
     *
     * @param date   the local date
     * @param hour   the local hour
     * @param minute the local minute
     * @return the UTC time as a formatted string "HH:mm"
     */
    public static String getUtcTimeStringForDate(final LocalDate date, final int hour, final int minute) {
        final LocalTime localTime = LocalTime.of(hour, minute);

        final ZonedDateTime localZdt = ZonedDateTime.of(date, localTime, LONDON_ZONE);
        final ZonedDateTime utcZdt = localZdt.withZoneSameInstant(ZoneOffset.UTC);

        return utcZdt.format(TIME_FORMATTER); // format as "HH:mm"
    }

    private static ZonedDateTime getZonedDateTime(final LocalDate sessionDate, final ZonedDateTime londonZoned, final ZonedDateTime utcZoned) {
        final LocalTime britishSummerNationalBreakTime = LocalTime.of(12, 0);
        final LocalTime britishWinterNationalBreakTime = LocalTime.of(13, 0);

        final boolean isBritishSummerWinterBreakTime = londonZoned.getHour() != utcZoned.getHour();

        final LocalTime nationalBreakTime = isBritishSummerWinterBreakTime ?
                britishSummerNationalBreakTime : britishWinterNationalBreakTime;

        final LocalDateTime breakDateTime = sessionDate.atTime(nationalBreakTime);
        return breakDateTime.atZone(UTC_ZONE);
    }
}