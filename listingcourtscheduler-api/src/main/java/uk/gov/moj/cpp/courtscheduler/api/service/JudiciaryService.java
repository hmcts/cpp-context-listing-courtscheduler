package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.Collections.singletonList;

import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class JudiciaryService {

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryService.class);

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Transactional
    public void unassignJudiciary(final String courtScheduleId, final String judiciaryId) {
        logger.info("unassignJudiciary: attempting to unassign judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);

        // Check if there are allocated listings for this court schedule
        final Map<String, Integer> allocatedListings = allocatedListingService.getAllocatedListingsByCourtScheduleId(singletonList(courtScheduleId));
        if (allocatedListings.containsKey(courtScheduleId) && allocatedListings.get(courtScheduleId) > 0) {
            final String errorMessage = String.format("Cannot unassign judiciary %s from courtSchedule %s: court schedule has allocated listings", judiciaryId, courtScheduleId);
            logger.warn("unassignJudiciary: {}", errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        // Find the CourtScheduleJudiciary entity
        final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId);
        final CourtScheduleJudiciary courtScheduleJudiciary = courtScheduleJudiciaryRepository.findBy(key);

        if (courtScheduleJudiciary == null) {
            final String errorMessage = String.format("Judiciary %s not found for courtSchedule %s", judiciaryId, courtScheduleId);
            logger.warn("unassignJudiciary: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        // Remove the judiciary assignment
        courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary);
        logger.info("unassignJudiciary: successfully unassigned judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);
    }
}

