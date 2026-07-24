package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

@Service
@org.springframework.transaction.annotation.Transactional
public class MiService {


    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private AllocatedListingRepository allocatedListingRepository;


    public List<CourtSchedule> getCourtSchedules(MiFilterCriteria miFilterCriteria) {
        return courtScheduleRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
    }

    public List<CourtScheduleJudiciary> getCourtSchedulesJudiciary(MiFilterCriteria miFilterCriteria) {
        return courtScheduleJudiciaryRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
    }

    public List<AllocatedListing> getAllocatedListings(MiFilterCriteria miFilterCriteria) {
        return allocatedListingRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
    }


}
