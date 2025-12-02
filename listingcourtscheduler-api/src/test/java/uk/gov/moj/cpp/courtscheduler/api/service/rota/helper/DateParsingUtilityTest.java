package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DateParsingUtilityTest {

    @InjectMocks
    private DateParsingUtility dateParsingUtility;

    @Test
    void shouldParseValidDate() {
        // given
        String validDateStr = "2024-01-15";

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(validDateStr);

        // then
        assertNotNull(result);
        assertThat(result.getYear(), is(2024));
        assertThat(result.getMonthValue(), is(1));
        assertThat(result.getDayOfMonth(), is(15));
    }

    @Test
    void shouldReturnNull_WhenDateIsInvalid() {
        // given
        String invalidDateStr = "2024-13-45"; // Invalid month and day

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(invalidDateStr);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldReturnNull_WhenDateIsInWrongFormat() {
        // given
        String wrongFormatDateStr = "15/01/2024"; // Wrong format

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(wrongFormatDateStr);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldReturnNull_WhenDateIsNull() {
        // when
        LocalDate result = dateParsingUtility.parseSessionDate(null);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldReturnNull_WhenDateIsEmpty() {
        // given
        String emptyDateStr = "";

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(emptyDateStr);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldParseLeapYearDate() {
        // given
        String leapYearDateStr = "2024-02-29"; // 2024 is a leap year

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(leapYearDateStr);

        // then
        assertNotNull(result);
        assertThat(result.getYear(), is(2024));
        assertThat(result.getMonthValue(), is(2));
        assertThat(result.getDayOfMonth(), is(29));
    }

    @Test
    void shouldReturnNull_WhenDateIsNotLeapYear() {
        // given
        String nonLeapYearDateStr = "2023-02-29"; // 2023 is not a leap year

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(nonLeapYearDateStr);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldParseDateWithSingleDigitMonthAndDay() {
        // given
        String dateStr = "2024-1-5"; // Single digit month and day

        // when
        LocalDate result = dateParsingUtility.parseSessionDate(dateStr);

        // then
        // The formatter expects "yyyy-MM-dd" format, so this should fail
        assertThat(result, is(nullValue()));
    }
}

