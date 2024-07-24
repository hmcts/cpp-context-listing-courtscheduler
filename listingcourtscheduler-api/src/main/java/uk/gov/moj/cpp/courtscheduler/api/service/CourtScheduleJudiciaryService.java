package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import uk.gov.moj.cpp.courtscheduler.api.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class CourtScheduleJudiciaryService {

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    public Map<String, List<CourtScheduleJudiciary>> findRelatedJudiciarySchedules(final List<String> snapshotSlotIds) {
        final Map<String, List<CourtScheduleJudiciary>> result = new HashMap<>();

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
        return result;
    }
}
