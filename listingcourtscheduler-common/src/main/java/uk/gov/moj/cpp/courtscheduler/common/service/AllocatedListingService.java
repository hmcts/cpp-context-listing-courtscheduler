package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.stream.Collectors.toMap;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;

import java.util.List;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class AllocatedListingService {

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Transactional
    public Map<String, Integer> getAllocatedListingsByCourtScheduleId(final List<String> courtScheduleIdList) {
        final List<AllocatedListingTotalBooked> allocatedListingTotalBookeds = allocatedListingRepository.getAllocatedListingsByCourtScheduleId(courtScheduleIdList);

        return allocatedListingTotalBookeds.stream()
                .collect(toMap(AllocatedListingTotalBooked::getCourtScheduleId, AllocatedListingTotalBooked::getTotalBooked));
    }

    @Transactional
    public int deleteRedundantRotaData(final int numberOfPreviousMonths) {
        return allocatedListingRepository.deleteRedundantRotaData(numberOfPreviousMonths * 30);
    }
}
