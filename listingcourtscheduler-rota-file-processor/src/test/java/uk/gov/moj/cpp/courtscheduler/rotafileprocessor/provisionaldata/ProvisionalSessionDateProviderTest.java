package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalSessionDateProviderTest {

    @Mock
    private ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateInfoProvider;

    @Mock
    private ProvisionalDataDateInfoProvider provisionalDataDateInfoProvider;

    @Test
    void populatePopulateDataLookUp() {
        when(provisionalDataDateInfoProvider.getCyclesToPopulate()).thenReturn(9);
        final LocalDate provisionalStartDate = LocalDate.of(2020, 01, 01);
        when(provisionalDataDateInfoProvider.getProvisionalDataStartDate()).thenReturn(provisionalStartDate);
        when(provisionalDataDateInfoProvider.getProvisionalDataEndDate()).thenReturn(LocalDate.of(2020, 10, 01));
        when(provisionalDataDateInfoProvider.getCyclesToPopulate()).thenReturn(10);
        final LocalDate extractStartDate = LocalDate.of(2019, 10, 01);
        when(provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDate()).thenReturn(extractStartDate);

        final ProvisionalSessionDateProvider provisionalSessionDateProvider =
                new ProvisionalSessionDateProvider(provisionalDataExtractDateInfoProvider, provisionalDataDateInfoProvider, 28);

        LocalDate cycleDate = provisionalStartDate;
        assertThat(provisionalSessionDateProvider.provisionalDate(0, extractStartDate), is(provisionalStartDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(0, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(1, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(1, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(2, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(2, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(3, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(3, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(4, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(4, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(5, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(5, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(6, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(6, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(7, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(7, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(8, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(27);
        assertThat(provisionalSessionDateProvider.provisionalDate(8, extractStartDate.plusDays(27)), is(cycleDate));

        cycleDate = cycleDate.plusDays(1);
        assertThat(provisionalSessionDateProvider.provisionalDate(9, extractStartDate), is(cycleDate));

        cycleDate = cycleDate.plusDays(22);
        assertThat(provisionalSessionDateProvider.provisionalDate(9, extractStartDate.plusDays(22)), is(cycleDate));
    }
}
