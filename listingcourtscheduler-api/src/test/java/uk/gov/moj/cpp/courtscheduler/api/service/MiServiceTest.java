package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MiServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @InjectMocks
    private MiService miService;

    @Test
    void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        final MiFilterCriteria miFilterCriteria = miFilterCriteria();
        final uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule courtSchedule = new uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule();
        given(courtScheduleRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria)).willReturn(List.of(courtSchedule));

        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> courtSchedules = miService.getCourtSchedules(miFilterCriteria);

        assertThat(courtSchedules.contains(courtSchedule), is(true));
    }

    private static MiFilterCriteria miFilterCriteria() {
        final LocalDate fromDate = LocalDate.now().minusDays(1);
        final LocalDate toDate = LocalDate.now().plusDays(1);
        return new MiFilterCriteria(fromDate, toDate);
    }

    @Test
    void shouldGetCourtScheduleJudiciariesBetweenLastUpdatedOn() {
        // given
        final MiFilterCriteria miFilterCriteria = miFilterCriteria();
        final uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary courtScheduleJudiciary = new uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary();
        given(courtScheduleJudiciaryRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria)).willReturn(List.of(courtScheduleJudiciary));

        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> courtScheduleJudiciaries = miService.getCourtSchedulesJudiciary(miFilterCriteria);

        assertThat(courtScheduleJudiciaries.contains(courtScheduleJudiciary), is(true));
    }

    @Test
    void shouldGetAllocatedListingsBetweenLastUpdatedOn() {
        // given
        final MiFilterCriteria miFilterCriteria = miFilterCriteria();
        final uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing allocatedListing = new uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing();
        given(allocatedListingRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria)).willReturn(List.of(allocatedListing));

        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> allocatedListings = miService.getAllocatedListings(miFilterCriteria);

        assertThat(allocatedListings.contains(allocatedListing), is(true));
    }


}