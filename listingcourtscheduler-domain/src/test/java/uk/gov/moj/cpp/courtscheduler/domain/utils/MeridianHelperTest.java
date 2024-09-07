package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.MeridianHelper.getMeridian;

import java.time.ZonedDateTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


public class MeridianHelperTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "2019-12-02T11:15:30-05:00",
            "2019-12-02T09:15:30-05:00"

    })
    public void shouldGetAmMeridian(String zonedDataTimeStr) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.parse(zonedDataTimeStr);

        final String meridian = getMeridian(zonedDateTime);

        assertThat(meridian, is("AM"));
    }

    @ParameterizedTest
    @ValueSource(strings = {

            "2019-12-02T14:00:30-05:00", // At2pm
            "2019-12-02T14:15:30-05:00", // PmMeridian
            "2019-12-02T13:15:30-05:00", // Between1PmAnd2Pm
    })
    public void shouldGetPmMeridianForPM(String zonedDataTimeStr) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.parse(zonedDataTimeStr);

        final String meridian = getMeridian(zonedDateTime);

        assertThat(meridian, is("PM"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2019-12-02T19:15:30-05:00", // BeforeAm
            "2019-12-02T08:15:30-05:00", // Before9am
            "2019-12-02T02:15:30-05:00" // AfterPm

    })
    public void shouldGetAdMeridianForAD(String zonedDataTimeStr) {
        final ZonedDateTime zonedDateTime = ZonedDateTime.parse(zonedDataTimeStr);

        final String meridian = getMeridian(zonedDateTime);

        assertThat(meridian, is("AD"));
    }
}