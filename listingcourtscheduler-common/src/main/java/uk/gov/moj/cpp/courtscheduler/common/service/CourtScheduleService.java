package uk.gov.moj.cpp.courtscheduler.common.service;

import static javax.transaction.Transactional.TxType.REQUIRES_NEW;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.Collections;
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

    public List<String> getAllocatedCourtSchedulesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        if (isNotEmpty(ouCodes)) {
            final LocalDate hearingStartTimeEndBoundary = endDate.plusDays(1);
            return courtScheduleRepository.getAllocatedCourtSchedules(startDate, endDate, hearingStartTimeEndBoundary, ouCodes)
                    .stream()
                    .map(CourtSchedule::getCourtScheduleId)
                    .toList();
        }
        return Collections.emptyList();
    }
}
