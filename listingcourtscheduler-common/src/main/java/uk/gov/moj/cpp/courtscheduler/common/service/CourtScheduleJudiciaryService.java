package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourtScheduleJudiciaryService {

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Map<String, List<CourtScheduleJudiciary>> findRelatedJudiciarySchedules(final List<String> snapshotSlotIds) {
        final Map<String, List<CourtScheduleJudiciary>> result = new HashMap<>();

        if (isNotEmpty(snapshotSlotIds)) {
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary> courtScheduleJudiciaryEntities = courtScheduleJudiciaryRepository.findInCourtScheduleIds(snapshotSlotIds);

            if (isNotEmpty(courtScheduleJudiciaryEntities)) {
                courtScheduleJudiciaryEntities.forEach(
                        courtScheduleJudiciaryEntity -> {
                            if (result.containsKey(courtScheduleJudiciaryEntity.getCourtListingProfileId())) {
                                final List<CourtScheduleJudiciary> existingCourtScheduleJudiciaries = result.get(courtScheduleJudiciaryEntity.getCourtListingProfileId());
                                existingCourtScheduleJudiciaries.add(CourtScheduleJudiciaryMapper.toDomain(courtScheduleJudiciaryEntity));
                                result.put(courtScheduleJudiciaryEntity.getCourtListingProfileId(), existingCourtScheduleJudiciaries);
                            } else {
                                final List<CourtScheduleJudiciary> courtScheduleJudiciaries = new ArrayList<>();
                                courtScheduleJudiciaries.add(CourtScheduleJudiciaryMapper.toDomain(courtScheduleJudiciaryEntity));
                                result.put(courtScheduleJudiciaryEntity.getCourtListingProfileId(), courtScheduleJudiciaries);
                            }
                        }
                );

            }
        }

        return result;
    }

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
