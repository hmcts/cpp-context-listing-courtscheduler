package uk.gov.moj.cpp.courtscheduler.service;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class SlotsRemoveService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public void remove(final String hearingId) {
        courtScheduleRepository.releaseOldAllocatedListings(hearingId);
    }
}
