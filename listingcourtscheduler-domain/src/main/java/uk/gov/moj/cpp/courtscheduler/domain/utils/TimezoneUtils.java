package uk.gov.moj.cpp.courtscheduler.domain.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

/**
 * Utility class for handling timezone conversions.
 * This class provides methods to convert between UTC and local time (BST/GMT).
 * It is designed to be a temporary solution until the frontend can handle timezone conversions.
 */
public class TimezoneUtils {
    
    public static final ZoneId LONDON_ZONE = ZoneId.of("Europe/London");
    public static final ZoneOffset UTC_ZONE = ZoneOffset.UTC;
    
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
    public static Date utcToLocal(Date utcDate) {
        if (utcDate == null) {
            return null;
        }

        // Convert to ZonedDateTime in UTC
        ZonedDateTime utcZoned = utcDate.toInstant().atZone(UTC_ZONE);
        
        // Convert to London time
        ZonedDateTime londonZoned = utcZoned.withZoneSameInstant(LONDON_ZONE);
        
        // Convert back to Date
        return Date.from(londonZoned.toInstant());
    }
    
    /**
     * Converts a local time (BST/GMT) date to UTC.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param localDate The local date to convert
     * @return The date in UTC
     */
    public static Date localToUtc(Date localDate) {
        if (localDate == null) {
            return null;
        }
        
        // Convert to ZonedDateTime in London time
        ZonedDateTime londonZoned = localDate.toInstant().atZone(LONDON_ZONE);
        
        // Convert to UTC
        ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);
        
        // Convert back to Date
        return Date.from(utcZoned.toInstant());
    }
    
    /**
     * Combines a local date and time and converts it to UTC.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param date The local date
     * @param time The local time
     * @return The combined date and time in UTC
     */
    public static Date combineLocalDateAndTimeToUtc(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return null;
        }
        
        // Create a LocalDateTime in London time
        LocalDateTime localDateTime = LocalDateTime.of(date, time);
        
        // Convert to ZonedDateTime in London time
        ZonedDateTime londonZoned = localDateTime.atZone(LONDON_ZONE);
        
        // Convert to UTC
        ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);
        
        // Convert to Date
        return Date.from(utcZoned.toInstant());
    }
    
    /**
     * Converts a UTC date to a local time string in ISO format.
     * This is a temporary solution until the frontend can handle timezone conversions.
     * 
     * @param utcDate The UTC date to convert
     * @return The date in local time as an ISO string
     */
    public static String utcToLocalIsoString(Date utcDate) {
        if (utcDate == null) {
            return null;
        }
        
        // Convert to ZonedDateTime in UTC
        ZonedDateTime utcZoned = utcDate.toInstant().atZone(UTC_ZONE);
        
        // Convert to London time
        ZonedDateTime londonZoned = utcZoned.withZoneSameInstant(LONDON_ZONE);
        
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
    public static Date localIsoStringToUtc(String localIsoString) {
        if (localIsoString == null) {
            return null;
        }
        
        // Parse the ISO string to a ZonedDateTime in London time
        ZonedDateTime londonZoned = ZonedDateTime.parse(localIsoString, DateUtils.ISO_8601_FORMATTER.withZone(LONDON_ZONE));
        
        // Convert to UTC
        ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);
        
        // Convert to Date
        return Date.from(utcZoned.toInstant());
    }
    
    /**
     * Calculates the national break time based on whether the date is in BST or not.
     * During BST (British Summer Time), the break time is 13:00 UTC.
     * During GMT (Greenwich Mean Time), the break time is 12:00 UTC.
     * 
     * @param sessionDate The session date to check
     * @return The national break time as a Date object
     */
    public static Date calculateNationalBreakTime(LocalDate sessionDate) {
        if (sessionDate == null) {
            return null;
        }
        
        // Create a LocalDateTime at noon on the session date
        LocalDateTime noonTime = sessionDate.atTime(12, 0);
        
        // Convert to ZonedDateTime in London time
        ZonedDateTime londonZoned = noonTime.atZone(LONDON_ZONE);
        
        // Convert to UTC
        ZonedDateTime utcZoned = londonZoned.withZoneSameInstant(UTC_ZONE);
        
        // Check if the date is in BST by comparing the hour in London time vs UTC
        // If the hour is different, it means we're in BST
        boolean isBST = londonZoned.getHour() != utcZoned.getHour();
        
        // Set the break time based on whether we're in BST or not
        // During BST, the break time is 13:00 UTC
        // During GMT, the break time is 12:00 UTC
        LocalTime breakTime = isBST ? LocalTime.of(13, 0) : LocalTime.of(12, 0);
        
        // Create the final break time
        LocalDateTime breakDateTime = sessionDate.atTime(breakTime);
        ZonedDateTime breakZoned = breakDateTime.atZone(UTC_ZONE);
        
        // Convert to Date
        return Date.from(breakZoned.toInstant());
    }
} 