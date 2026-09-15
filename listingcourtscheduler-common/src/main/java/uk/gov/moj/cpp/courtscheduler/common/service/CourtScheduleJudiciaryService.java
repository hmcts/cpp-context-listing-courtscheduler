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

    /**
     * Builds a map keyed by courtScheduleId of the unallocated court schedule judiciaries that
     * {@link #deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod} would delete for the
     * given rota period and OU codes. Each value holds the row data: courtScheduleId, judiciaryId,
     * courtListingProfileId, rotaJudiciaryId, title, forenames, surname, email, judiciaryType,
     * isBenchChairman, isDeputy, position and active.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Map<String, List<CourtScheduleJudiciary>> getUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
            final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        final Map<String, List<CourtScheduleJudiciary>> courtScheduleJudiciaryMap = new HashMap<>();

        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary> courtScheduleJudiciaryEntities =
                courtScheduleJudiciaryRepository.findUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, ouCodes);

        if (isNotEmpty(courtScheduleJudiciaryEntities)) {
            courtScheduleJudiciaryEntities.forEach(courtScheduleJudiciaryEntity ->
                    courtScheduleJudiciaryMap
                            .computeIfAbsent(courtScheduleJudiciaryEntity.getId().getCourtScheduleId(), key -> new ArrayList<>())
                            .add(CourtScheduleJudiciaryMapper.toDomain(courtScheduleJudiciaryEntity)));
        }

        return courtScheduleJudiciaryMap;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        return courtScheduleJudiciaryRepository.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, ouCodes);
    }

    /**
     * Queries court_schedule, court_schedule_judiciary and allocated_listings for the supplied
     * court schedule IDs. Returns one row per (hearing, judiciary) combination:
     * court_schedule_id, hearing_id, judiciary_id, judiciary_type, is_bench_chairman, is_deputy.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<Object[]> getJudiciaryHearingInfoForCourtSchedules(final List<String> courtScheduleIds) {
        if (!isNotEmpty(courtScheduleIds)) {
            return new ArrayList<>();
        }
        return courtScheduleJudiciaryRepository.findJudiciaryHearingInfoByCourtScheduleIds(courtScheduleIds);
    }

    public List<Object[]> getAllocatedScheduleJudiciaryInfo(final LocalDate startDate, final LocalDate endDate, final List<String> ouCodes) {
        final LocalDate hearingStartTimeEndBoundary = endDate.plusDays(1);
        return courtScheduleJudiciaryRepository.getAllocatedScheduleJudiciaryInfo(startDate, hearingStartTimeEndBoundary, ouCodes);
    }

    public int deleteRedundantRotaData(final int numberOfPreviousMonthsAndOlder) {
        return courtScheduleJudiciaryRepository.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder * 30);
    }
}
