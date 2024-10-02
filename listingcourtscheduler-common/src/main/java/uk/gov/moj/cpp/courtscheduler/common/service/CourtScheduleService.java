package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class CourtScheduleService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return courtScheduleRepository.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);
    }
}
