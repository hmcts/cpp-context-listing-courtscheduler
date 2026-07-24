package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalDataExtractDateInfoProviderTest {

    @Test
    void shouldReturnDayOfWeek() {
        final int rotaCycleLength = 28;

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 01);
        final ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateAnalyzer
                = new ProvisionalDataExtractDateInfoProvider(rotaPeriodStartDate, DayOfWeek.WEDNESDAY, rotaCycleLength);

        final LocalDate provisionalDataExtractStartDate = provisionalDataExtractDateAnalyzer.getProvisionalDataExtractStartDate();
        assertThat(provisionalDataExtractStartDate.toString(), is("2019-10-02"));

        final DayOfWeek provisionalDataExtractStartDay = provisionalDataExtractDateAnalyzer.getProvisionalDataExtractStartDay();
        assertThat(provisionalDataExtractStartDay.toString(), is("WEDNESDAY"));

        final LocalDate provisionalDataExtractEndDate = provisionalDataExtractDateAnalyzer.getProvisionalDataExtractEndDate();
        assertThat(provisionalDataExtractEndDate.toString(), is("2019-10-29"));

        final DayOfWeek provisionalDataExtractEndDay = provisionalDataExtractDateAnalyzer.getProvisionalDataExtractEndDay();
        assertThat(provisionalDataExtractEndDay.toString(), is("TUESDAY"));

    }
}
