package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
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
    public void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        MiFilterCriteria miFilterCriteria = miFilterCriteria();
        CourtSchedule courtSchedule = new CourtSchedule();
        given(courtScheduleRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria)).willReturn(List.of(courtSchedule));

        List<CourtSchedule> courtSchedules = miService.getCourtSchedules(miFilterCriteria);

        assertThat(courtSchedules.contains(courtSchedule), is(true));
    }

    private static MiFilterCriteria miFilterCriteria() {
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        return new MiFilterCriteria(fromDate, toDate);
    }

    @Test
    public void shouldGetCourtScheduleJudiciariesBetweenLastUpdatedOn() {
        // given
        MiFilterCriteria miFilterCriteria = miFilterCriteria();
        CourtScheduleJudiciary courtScheduleJudiciary = new CourtScheduleJudiciary();
        given(courtScheduleJudiciaryRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria)).willReturn(List.of(courtScheduleJudiciary));

        List<CourtScheduleJudiciary> courtScheduleJudiciaries = miService.getCourtSchedulesJudiciary(miFilterCriteria);

        assertThat(courtScheduleJudiciaries.contains(courtScheduleJudiciary), is(true));
    }

    @Test
    public void shouldGetAllocatedListingsBetweenLastUpdatedOn() {
        // given
        MiFilterCriteria miFilterCriteria = miFilterCriteria();
        AllocatedListing allocatedListing = new AllocatedListing();
        given(allocatedListingRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria)).willReturn(List.of(allocatedListing));

        List<AllocatedListing> allocatedListings = miService.getAllocatedListings(miFilterCriteria);

        assertThat(allocatedListings.contains(allocatedListing), is(true));
    }


}