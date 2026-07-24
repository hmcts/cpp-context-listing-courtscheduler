package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.time.LocalDate.of;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalDataDateInfoProviderTest {

    @Test
    void shouldProvideProvisionalDataDates() {
        final LocalDate masterRotaPeriodCutOffDate = of(2019, 12, 17);
        final LocalDate rotaPeriodEndDate = of(2019, 12, 31);

        final ProvisionalDataDateInfoProvider provisionalDataDateAnalyzer = new ProvisionalDataDateInfoProvider(rotaPeriodEndDate, masterRotaPeriodCutOffDate,9L, 28);

        final LocalDate provisionalDataStartDate = provisionalDataDateAnalyzer.getProvisionalDataStartDate();
        assertThat(provisionalDataStartDate.toString(), is(masterRotaPeriodCutOffDate.toString()));

        final DayOfWeek provisionalDataStartDay = provisionalDataDateAnalyzer.getProvisionalDataStartDay();
        assertThat(provisionalDataStartDay.toString(), is("TUESDAY"));

        final LocalDate provisionalDataEndDate = provisionalDataDateAnalyzer.getProvisionalDataEndDate();
        assertThat(provisionalDataEndDate.toString(), is("2020-09-30"));

        final DayOfWeek provisionalDataEndDay = provisionalDataDateAnalyzer.getProvisionalDataEndDay();
        assertThat(provisionalDataEndDay.toString(), is("WEDNESDAY"));

        final int provisionalDataDaysCountToPopulate = provisionalDataDateAnalyzer.getProvisionalDataDaysCountToPopulate();
        assertThat(provisionalDataDaysCountToPopulate, is(289));

        final int cyclesToPopulate = provisionalDataDateAnalyzer.getCyclesToPopulate();
        assertThat(cyclesToPopulate, is(11));

        final int remainingDaysExtractToBePopulated = provisionalDataDateAnalyzer.getProvisionalDaysInCycleRemaining();
        assertThat(remainingDaysExtractToBePopulated, is(9));

        final ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateAnalyzer = new ProvisionalDataExtractDateInfoProvider(rotaPeriodEndDate, provisionalDataStartDay, 28);
        final DayOfWeek provisionalDataExtractStartDay = provisionalDataExtractDateAnalyzer.getProvisionalDataExtractStartDay();
        assertThat(provisionalDataStartDay,is(provisionalDataExtractStartDay));
    }
}
