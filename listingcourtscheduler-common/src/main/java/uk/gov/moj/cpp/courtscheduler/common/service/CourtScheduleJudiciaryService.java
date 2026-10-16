package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtScheduleJudiciaryService {

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return courtScheduleJudiciaryRepository.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, ouCodes);
    }

    public List<Object[]> getAllocatedScheduleJudiciaryInfo(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        final LocalDate hearingStartTimeEndBoundary = endDate.plusDays(1);
        return courtScheduleJudiciaryRepository.getAllocatedScheduleJudiciaryInfo(startDate, hearingStartTimeEndBoundary, ouCodes);
    }

    public int deleteRedundantRotaData(final int numberOfPreviousMonthsAndOlder) {
        return courtScheduleJudiciaryRepository.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder * 30);
    }
}
