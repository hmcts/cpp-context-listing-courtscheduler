package uk.gov.moj.cpp.courtscheduler.common.service;

import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
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

    @Transactional(REQUIRES_NEW)
    public int deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return courtScheduleRepository.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);
    }

    @Transactional(REQUIRES_NEW)
    public CourtSchedule saveSlot(final CourtSchedule courtSchedule) {
        return courtScheduleRepository.save(courtSchedule);
    }

    public int deleteRedundantRotaData(final int cleanDataForPreviousMonths) {
        return courtScheduleRepository.deleteRedundantRotaData(cleanDataForPreviousMonths * 30);
    }
}
