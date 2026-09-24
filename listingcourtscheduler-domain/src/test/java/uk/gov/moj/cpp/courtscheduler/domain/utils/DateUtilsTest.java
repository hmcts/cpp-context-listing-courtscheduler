package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.normaliseToHourMinute;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.resolveSessionTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toListingSession;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toMeridian;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toSqlDate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;


class DateUtilsTest {

    @Test
    void shouldConvertToDateTimeOffset() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-07-23T09:00:00.000Z");
        assertThat(actual.toString(), is("2020-07-23 09:00:00.0"));
    }

    @Test
    void shouldParseStrictMillisecondZuluFormToOffsetDateTime() {
        final OffsetDateTime actual = DateUtils.toOffsetDateTime("2026-08-06T09:00:00.000Z");
        assertThat(actual, is(OffsetDateTime.of(2026, 8, 6, 9, 0, 0, 0, ZoneOffset.UTC)));
    }

    @Test
    void shouldParseZonedDateTimeToStringFormToOffsetDateTime() {
        // ZonedDateTime.toString() output as sent by listing's crown fallback: zone-id suffix and
        // seconds omitted when zero (SPRDT-1159).
        final OffsetDateTime actual = DateUtils.toOffsetDateTime("2026-08-06T09:00Z[UTC]");
        assertThat(actual, is(OffsetDateTime.of(2026, 8, 6, 9, 0, 0, 0, ZoneOffset.UTC)));
    }

    @Test
    void shouldParseOffsetFormToOffsetDateTimeNormalisedToUtc() {
        final OffsetDateTime actual = DateUtils.toOffsetDateTime("2026-08-06T10:00:00+01:00");
        assertThat(actual, is(OffsetDateTime.of(2026, 8, 6, 9, 0, 0, 0, ZoneOffset.UTC)));
    }

    @Test
    void shouldStillThrowOnUnparseableDateTime() {
        Assertions.assertThrows(java.time.format.DateTimeParseException.class,
                () -> DateUtils.toOffsetDateTime("not-a-date"));
    }

    @Test
    void shouldConvertToLocalDateIfTooLong() {
        final Date actual = toSqlDate("2018-09-28T12:00:00.000000000Z");
        assertThat(actual.toString(), is("2018-09-28"));
    }

    @Test
    void shouldConvertToLocalDate() {
        final Date actual = toSqlDate("2018-09-28");
        assertThat(actual.toString(), is("2018-09-28"));
    }

    @Test
    void shouldConvertToDateTimeOffsetWithRounding() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-07-23T09:59:59.999Z");
        assertThat(actual.toString(), is("2020-07-23 09:00:00.0"));
    }

    @Test
    void shouldConvertToRoundedTimestampWithRoundingNumber() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-06-23T10:00:00.000Z");
        assertThat(actual.getHours(), is(10));
    }

    @Test
    void shouldConvertToRoundedTimestampWithRoundingWinter() {
        final Timestamp actual = DateUtils.toRoundedTimestamp("2020-01-23T10:00:00.000Z");
        assertThat(actual.getHours(), is(10));
    }

    @Test
    void shouldConvertToIsoString() {
        final OffsetDateTime dateTimeOffset = Timestamp.valueOf("2020-01-01 18:05:22").toLocalDateTime().atOffset(ZoneOffset.UTC);
        final String actual = DateUtils.toIsoString(dateTimeOffset);
        assertThat(actual, is("2020-01-01T18:05:22.000Z"));
    }

    @Test
    void shouldConvertToIsoStringSummer() {
        final OffsetDateTime dateTimeOffset = Timestamp.valueOf("2020-09-01 18:05:22").toLocalDateTime().atOffset(ZoneOffset.UTC);
        final String actual = DateUtils.toIsoString(dateTimeOffset);
        assertThat(actual, is("2020-09-01T18:05:22.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeInWinterAM() {
        final String actual = DateUtils.createDefaultHearingStartTime("AM", "2020-01-01");
        assertThat(actual, is("2020-01-01T10:00:00.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeInWinterAD() {
        final String actual = DateUtils.createDefaultHearingStartTime("AD", "2020-01-01");
        assertThat(actual, is("2020-01-01T10:00:00.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeInWinterPM() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-01-01");
        assertThat(actual, is("2020-01-01T14:00:00.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeInWinterPM2() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-01-01");
        assertThat(actual, is("2020-01-01T14:00:00.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeInSummerAM() {
        final String actual = DateUtils.createDefaultHearingStartTime("AM", "2020-08-01");
        assertThat(actual, is("2020-08-01T09:00:00.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeInSummerWinterPM() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-08-01");
        assertThat(actual, is("2020-08-01T13:00:00.000Z"));
    }

    @Test
    void shouldCreateDefaultHearingStartTimeWhenDateHasTime() {
        final String actual = DateUtils.createDefaultHearingStartTime("PM", "2020-08-01T18:08:08.000Z");
        assertThat(actual, is("2020-08-01T13:00:00.000Z"));
    }

    @Test
    void shouldThrowExceptionForCreateDefaultHearingStartTimeWhenSessionIsUnknown() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            DateUtils.createDefaultHearingStartTime("AAA", "2020-08-01T18:08:08.000Z");
        });
    }

    @Test
    void shouldCreateNullDefaultHearingStartTimeWhenSessionIsNull() {
        final String actual = DateUtils.createDefaultHearingStartTime(null, "2020-08-01T18:08:08.000Z");
        assertThat(actual, is(nullValue()));
    }

    @Test
    void shouldConvertToMeridian() {
        assertThat(toMeridian("2020-08-01T16:08:08.000Z"), is("PM"));
    }

    @Test
    void shouldReturnBSTNotApplied(){
        final java.util.Date expectedDate = java.util.Date.from(LocalDateTime.of(2025, 3,15,10, 0).toInstant(ZoneOffset.UTC));
        assertThat(DateUtils.combineDateAndTime(LocalDate.of(2025, 3,15), "10:00"), is(expectedDate));
    }

    @Test
    @Disabled("Disabled because it fails on pipeline and passes locally. This is likely due to the timezone settings of the environment.")
    void shouldReturnBSTApplied(){
        final java.util.Date expectedDate = java.util.Date.from(LocalDateTime.of(2025, 4,15,8,0).toInstant(ZoneOffset.UTC));
        assertThat(DateUtils.combineDateAndTime(LocalDate.of(2025, 4,15), "10:00"), is(expectedDate));
    }

    @Test
    void shouldReturnRandomFutureDateWithinNextYear() {
        LocalDate today = LocalDate.now();
        LocalDate nextYear = today.plusYears(1);
        LocalDate randomDate = DateUtils.getRandomFutureDateWithinNextYear();
        Assertions.assertNotNull(randomDate, "Random date should not be null");
        Assertions.assertFalse(randomDate.isBefore(today), "Random date should not be before today");
        Assertions.assertFalse(randomDate.isAfter(nextYear), "Random date should not be after one year from today");
    }

    // Tests for toExactTimestamp method
    @Test
    void shouldConvertToExactTimestampWithValidIsoDate() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-07-23T09:30:45.123Z");
        assertThat(actual.toString(), is("2020-07-23 09:30:45.123"));
    }

    @Test
    void shouldConvertToExactTimestampPreservingExactTime() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-07-23T09:59:59.999Z");
        assertThat(actual.toString(), is("2020-07-23 09:59:59.999"));
    }

    @Test
    void shouldConvertToExactTimestampWithZeroMillis() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-07-23T09:30:45.000Z");
        assertThat(actual.toString(), is("2020-07-23 09:30:45.0"));
    }

    @Test
    void shouldConvertToExactTimestampWithMidnight() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-07-23T00:00:00.000Z");
        assertThat(actual.toString(), is("2020-07-23 00:00:00.0"));
    }

    @Test
    void shouldConvertToExactTimestampWithEndOfDay() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-07-23T23:59:59.999Z");
        assertThat(actual.toString(), is("2020-07-23 23:59:59.999"));
    }

    @Test
    void shouldConvertToExactTimestampWithLeapYear() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-02-29T12:30:45.500Z");
        assertThat(actual.toString(), is("2020-02-29 12:30:45.5"));
    }

    @Test
    void shouldConvertToExactTimestampWithDifferentMonths() {
        final Timestamp january = DateUtils.toExactTimestamp("2020-01-15T10:15:30.250Z");
        assertThat(january.toString(), is("2020-01-15 10:15:30.25"));

        final Timestamp december = DateUtils.toExactTimestamp("2020-12-25T15:45:20.750Z");
        assertThat(december.toString(), is("2020-12-25 15:45:20.75"));
    }

    @Test
    void shouldReturnNullForNullInput() {
        final Timestamp actual = DateUtils.toExactTimestamp(null);
        assertThat(actual, is(nullValue()));
    }

    @Test
    void shouldReturnNullForEmptyString() {
        final Timestamp actual = DateUtils.toExactTimestamp("");
        assertThat(actual, is(nullValue()));
    }

    @Test
    void shouldReturnNullForBlankString() {
        final Timestamp actual = DateUtils.toExactTimestamp("   ");
        assertThat(actual, is(nullValue()));
    }

    @Test
    void shouldConvertToExactTimestampWithSingleDigitValues() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-01-01T01:01:01.001Z");
        assertThat(actual.toString(), is("2020-01-01 01:01:01.001"));
    }

    @Test
    void shouldConvertToExactTimestampWithDifferentYears() {
        final Timestamp pastYear = DateUtils.toExactTimestamp("1999-12-31T23:59:59.999Z");
        assertThat(pastYear.toString(), is("1999-12-31 23:59:59.999"));

        final Timestamp futureYear = DateUtils.toExactTimestamp("2030-01-01T00:00:00.000Z");
        assertThat(futureYear.toString(), is("2030-01-01 00:00:00.0"));
    }

    @Test
    void shouldConvertToExactTimestampWithDifferentTimezones() {
        // All inputs should be treated as UTC regardless of the 'Z' suffix
        final Timestamp utc = DateUtils.toExactTimestamp("2020-07-23T12:00:00.000Z");
        assertThat(utc.toString(), is("2020-07-23 12:00:00.0"));
    }

    @Test
    void shouldConvertToExactTimestampWithPreciseMillis() {
        final Timestamp actual = DateUtils.toExactTimestamp("2020-07-23T12:34:56.789Z");
        assertThat(actual.toString(), is("2020-07-23 12:34:56.789"));
    }

    // ----- toListingSession -----

    @Test
    void shouldBuildListingSessionForMondayAm() {
        // 2026-04-27 is a Monday
        assertThat(toListingSession(LocalDate.of(2026, 4, 27), "AM"), is("MONAM"));
    }

    @Test
    void shouldBuildListingSessionForFridayPm() {
        // 2026-05-01 is a Friday
        assertThat(toListingSession(LocalDate.of(2026, 5, 1), "PM"), is("FRIPM"));
    }

    @Test
    void shouldBuildListingSessionForWednesdayAllDay() {
        // 2026-04-29 is a Wednesday
        assertThat(toListingSession(LocalDate.of(2026, 4, 29), "AD"), is("WEDAD"));
    }

    @Test
    void shouldReturnNullListingSessionWhenDateIsNull() {
        assertThat(toListingSession(null, "AM"), is(nullValue()));
    }

    @Test
    void shouldReturnNullListingSessionWhenSessionIsBlank() {
        assertThat(toListingSession(LocalDate.of(2026, 4, 27), ""), is(nullValue()));
        assertThat(toListingSession(LocalDate.of(2026, 4, 27), "  "), is(nullValue()));
        assertThat(toListingSession(LocalDate.of(2026, 4, 27), null), is(nullValue()));
    }

    // ----- resolveSessionTime -----

    @Test
    void resolveSessionTimeShouldPreferCustomOverRefdataAndDefault() {
        assertThat(resolveSessionTime("09:30", "10:00", "11:00"), is("09:30"));
    }

    @Test
    void resolveSessionTimeShouldFallBackToRefdataWhenCustomBlank() {
        assertThat(resolveSessionTime(null, "10:15", "11:00"), is("10:15"));
        assertThat(resolveSessionTime("", "10:15", "11:00"), is("10:15"));
        assertThat(resolveSessionTime("   ", "10:15", "11:00"), is("10:15"));
    }

    @Test
    void resolveSessionTimeShouldFallBackToDefaultWhenCustomAndRefdataBlank() {
        assertThat(resolveSessionTime(null, null, "11:00"), is("11:00"));
        assertThat(resolveSessionTime("", "", "11:00"), is("11:00"));
        assertThat(resolveSessionTime("  ", "  ", "11:00"), is("11:00"));
    }

    @Test
    void resolveSessionTimeShouldReturnNullWhenAllBlank() {
        assertThat(resolveSessionTime(null, null, null), is(nullValue()));
    }

    // ----- normaliseToHourMinute -----
    // referencedata-query-api's organisation-unit defaultStartTime has been observed in both
    // "HH:mm" and "HH:mm:ss" form (confirmed live on ns-ste-ccm-22: "10:30:00"). This must accept
    // either without throwing.

    @Test
    void normaliseToHourMinuteShouldPassThroughHourMinuteFormat() {
        assertThat(normaliseToHourMinute("10:30"), is("10:30"));
    }

    @Test
    void normaliseToHourMinuteShouldStripSecondsFromHourMinuteSecondFormat() {
        assertThat(normaliseToHourMinute("10:30:00"), is("10:30"));
    }

    @Test
    void normaliseToHourMinuteShouldTrimWhitespace() {
        assertThat(normaliseToHourMinute("  09:15:00  "), is("09:15"));
    }

    @Test
    void normaliseToHourMinuteShouldReturnNullWhenBlank() {
        assertThat(normaliseToHourMinute(null), is(nullValue()));
        assertThat(normaliseToHourMinute(""), is(nullValue()));
        assertThat(normaliseToHourMinute("   "), is(nullValue()));
    }

    @Test
    void normaliseToHourMinuteShouldReturnNullWhenUnparseable() {
        assertThat(normaliseToHourMinute("not-a-time"), is(nullValue()));
    }
}

