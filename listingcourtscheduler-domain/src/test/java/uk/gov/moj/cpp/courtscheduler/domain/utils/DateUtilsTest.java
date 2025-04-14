package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toMeridian;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toSqlDate;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.LONDON_ZONE;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class DateUtilsTest {

    private static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    @Test
    public void shouldConvertToDateTimeOffset() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-07-23T09:00:00.000Z");
        assertThat(actual.toString(), is("2020-07-23 09:00:00.0"));
    }

    @Test
    public void shouldConvertToLocalDateIfTooLong() {
        final Date actual = toSqlDate("2018-09-28T12:00:00.000000000Z");
        assertThat(actual.toString(), is("2018-09-28"));
    }

    @Test
    public void shouldConvertToLocalDate() {
        final Date actual = toSqlDate("2018-09-28");
        assertThat(actual.toString(), is("2018-09-28"));
    }

    @Test
    public void shouldConvertToDateTimeOffsetWithRounding() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-07-23T09:59:59.999Z");
        assertThat(actual.toString(), is("2020-07-23 09:00:00.0"));
    }

    @Test
    public void shouldConvertToRoundedTimestampWithRoundingSummer() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-06-23T10:00:00.000Z");
        assertThat(actual.getHours(), is(10));
    }

    @Test
    public void shouldConvertToRoundedTimestampWithRoundingWinter() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-01-23T10:00:00.000Z");
        assertThat(actual.getHours(), is(10));
    }

    @Test
    void testToIsoStringWithLocalDateTime() {
        // Create a local date time in London timezone
        LocalDateTime localDateTime = LocalDateTime.of(2023, 7, 1, 10, 0);
        
        // Get the ISO string
        String isoString = DateUtils.toIsoString(localDateTime);
        
        // The ISO string should represent UTC time
        ZonedDateTime utcTime = localDateTime.atZone(ZoneOffset.UTC);
        assertThat(utcTime.format(ISO_8601_FORMATTER), is(isoString));
    }

    @Test
    void testToIsoStringWithOffsetDateTime() {
        // Create an offset date time in UTC
        OffsetDateTime utcDateTime = OffsetDateTime.of(2023, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        
        // Get the ISO string
        String isoString = DateUtils.toIsoString(utcDateTime);
        
        // The ISO string should represent UTC time
        assertThat(utcDateTime.format(ISO_8601_FORMATTER), is(isoString));
    }

    @Test
    void testToIsoStringWithTimestamp() {
        // Create a timestamp in UTC
        Timestamp timestamp = Timestamp.valueOf("2023-07-01 10:00:00");
        
        // Get the ISO string
        String isoString = DateUtils.toIsoString(timestamp);
        
        // The ISO string should represent UTC time
        ZonedDateTime utcTime = timestamp.toLocalDateTime().atZone(ZoneOffset.UTC);
        assertThat(utcTime.format(ISO_8601_FORMATTER), is(isoString));
    }

    @Test
    void testToIsoStringWithDate() {
        // Create a date in UTC (2023-07-01 10:00:00 UTC)
        LocalDateTime localDateTime = LocalDateTime.of(2023, 7, 1, 10, 0, 0);
        ZonedDateTime utcDateTime = localDateTime.atZone(ZoneOffset.UTC);
        java.util.Date date = java.util.Date.from(utcDateTime.toInstant());
        
        // Get the ISO string
        String isoString = DateUtils.toIsoString(date);
        
        // The ISO string should represent UTC time
        assertThat(isoString, is("2023-07-01T10:00:00.000Z"));
    }

    @Test
    void testCombineDateAndTime() {
        // Create a local date and time
        LocalDate date = LocalDate.of(2023, 7, 1);
        String time = "10:00";
        
        // Combine the date and time
        java.util.Date result = DateUtils.combineDateAndTime(date, time);
        
        // The result should be in UTC
        ZonedDateTime expectedUtc = LocalDateTime.of(date, LocalTime.parse(time))
            .atZone(LONDON_ZONE)
            .withZoneSameInstant(ZoneOffset.UTC);
        
        assertThat(expectedUtc.toInstant().toEpochMilli(), is(result.getTime()));
    }

    @Test
    void testLocalDateToDateWithTime() {
        // Create a local date and time
        LocalDate date = LocalDate.of(2023, 7, 1);
        int hour = 10;
        int minute = 0;
        
        // Convert to date with time
        java.util.Date result = DateUtils.localDateToDateWithTime(date, hour, minute);
        
        // The result should be in UTC
        ZonedDateTime expectedUtc = LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(LONDON_ZONE)
            .withZoneSameInstant(ZoneOffset.UTC);
        
        assertThat(expectedUtc.toInstant().toEpochMilli(), is(result.getTime()));
    }

    @Test
    void testCreateDefaultHearingStartTime() {
        // Test AM session
        String amResult = DateUtils.createDefaultHearingStartTime("AM", "2023-07-01");
        ZonedDateTime expectedAm = LocalDateTime.of(2023, 7, 1, 10, 0)
            .atZone(LONDON_ZONE)
            .withZoneSameInstant(ZoneOffset.UTC);
        assertThat(expectedAm.format(ISO_8601_FORMATTER), is(amResult));
        
        // Test PM session
        String pmResult = DateUtils.createDefaultHearingStartTime("PM", "2023-07-01");
        ZonedDateTime expectedPm = LocalDateTime.of(2023, 7, 1, 14, 0)
            .atZone(LONDON_ZONE)
            .withZoneSameInstant(ZoneOffset.UTC);
        assertThat(expectedPm.format(ISO_8601_FORMATTER), is(pmResult));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeInWinterAM() {
        final String actual = DateUtils.createDefaultHearingStartTime("AM", "2020-01-01");
        assertThat(actual, is("2020-01-01T10:00:00.000Z"));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeInWinterAD() {
        final String actual = DateUtils.createDefaultHearingStartTime("AD", "2020-01-01");
        assertThat(actual, is("2020-01-01T10:00:00.000Z"));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeInWinterPM() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-01-01");
        assertThat(actual, is("2020-01-01T14:00:00.000Z"));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeInWinterPM2() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-01-01");
        assertThat(actual, is("2020-01-01T14:00:00.000Z"));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeInSummerAM() {
        final String actual = DateUtils.createDefaultHearingStartTime("AM", "2020-08-01");
        assertThat(actual, is("2020-08-01T09:00:00.000Z"));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeInSummerWinterPM() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-08-01");
        assertThat(actual, is("2020-08-01T13:00:00.000Z"));
    }

    @Test
    public void shouldCreateDefaultHearingStartTimeWhenDateHasTime() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-08-01T18:08:08.000Z");
        assertThat(actual, is("2020-08-01T13:00:00.000Z"));
    }

    @Test
    public void shouldThrowExceptionForCreateDefaultHearingStartTimeWhenSessionIsUnknown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DateUtils.createDefaultHearingStartTime("AAA", "2020-08-01T18:08:08.000Z");
        });
    }

    @Test
    public void shouldCreateNullDefaultHearingStartTimeWhenSessionIsNull() {
        final String actual = DateUtils.createDefaultHearingStartTime(null, "2020-08-01T18:08:08.000Z");
        assertThat(actual, is(nullValue()));
    }

    @Test
    public void shouldConvertToMeridian() {
        assertThat(toMeridian("2020-08-01T16:08:08.000Z"), is("PM"));
    }

    @Test
    public void shouldReturnBSTNotApplied(){
        final java.util.Date expectedDate = java.util.Date.from(LocalDateTime.of(2025,03,15,10,00).toInstant(ZoneOffset.UTC));
        assertThat(DateUtils.combineDateAndTime(LocalDate.of(2025,03,15), "10:00"), is(expectedDate));;
    }

    @Test
    public void shouldReturnBSTApplied(){
        // During BST (July), 10:00 London time is 09:00 UTC
        final java.util.Date expectedDate = java.util.Date.from(LocalDateTime.of(2025,07,15,9,00).toInstant(ZoneOffset.UTC));
        assertThat(DateUtils.combineDateAndTime(LocalDate.of(2025,07,15), "10:00"), is(expectedDate));
    }
}

