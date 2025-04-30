package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toMeridian;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toSqlDate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class DateUtilsTest {

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
    public void shouldConvertToIsoString() {
        final OffsetDateTime dateTimeOffset = Timestamp.valueOf("2020-01-01 18:05:22").toLocalDateTime().atOffset(ZoneOffset.UTC);
        final String actual = DateUtils.toIsoString(dateTimeOffset);
        assertThat(actual, is("2020-01-01T18:05:22.000Z"));
    }

    @Test
    public void shouldConvertToIsoStringSummer() {
        final OffsetDateTime dateTimeOffset = Timestamp.valueOf("2020-09-01 18:05:22").toLocalDateTime().atOffset(ZoneOffset.UTC);
        final String actual = DateUtils.toIsoString(dateTimeOffset);
        assertThat(actual, is("2020-09-01T18:05:22.000Z"));
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
        final java.util.Date expectedDate = java.util.Date.from(LocalDateTime.of(2025,04,15,9,0).toInstant(ZoneOffset.UTC));
        assertThat(DateUtils.combineDateAndTime(LocalDate.of(2025,04,15), "10:00"), is(expectedDate));;
    }
}

