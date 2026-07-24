package uk.gov.moj.cpp.courtscheduler.common.service;


import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtScheduleService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return courtScheduleRepository.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CourtSchedule saveSlot(final CourtSchedule courtSchedule) {
        return courtScheduleRepository.save(courtSchedule);
    }

    public int deleteRedundantRotaData(final int cleanDataForPreviousMonths) {
        return courtScheduleRepository.deleteRedundantRotaData(cleanDataForPreviousMonths * 30);
    }
}
