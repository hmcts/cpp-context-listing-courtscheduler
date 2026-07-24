package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@org.springframework.transaction.annotation.Transactional
public class RotaRedundantDataCleanerService {

    private static final Logger logger = LoggerFactory.getLogger(RotaRedundantDataCleanerService.class);

    @Inject
    private CourtScheduleService courtScheduleService;

    @Inject
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @Async
    @Transactional
    public void cleanDataForPreviousMonths(final int numberOfPreviousMonthsAndOlder) {
        final int numberOfDeletedAllocatedListingsForRedundancy = allocatedListingService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        logger.info("numberOfDeletedAllocatedListingsForRedundancy: {}", numberOfDeletedAllocatedListingsForRedundancy);
        final int numberOfDeletedJudiciariesForRedundancy = courtScheduleJudiciaryService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        logger.info("numberOfDeletedJudiciariesForRedundancy: {}", numberOfDeletedJudiciariesForRedundancy);
        final int numberOfDeletedCourtSchedulesForRedundancy = courtScheduleService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        logger.info("numberOfDeletedCourtSchedulesForRedundancy: {}", numberOfDeletedCourtSchedulesForRedundancy);
        final int numberOfDeletedRotaProcessLogsForRedundancy = rotaProcessLogService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        logger.info("numberOfDeletedRotaProcessLogsForRedundancy: {}", numberOfDeletedRotaProcessLogsForRedundancy);
    }
}
