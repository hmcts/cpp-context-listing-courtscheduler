package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

@Service
@org.springframework.transaction.annotation.Transactional
public class SlotsRemoveService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public void remove(final String hearingId) {
        courtScheduleRepository.releaseOldAllocatedListings(hearingId);
    }
}
