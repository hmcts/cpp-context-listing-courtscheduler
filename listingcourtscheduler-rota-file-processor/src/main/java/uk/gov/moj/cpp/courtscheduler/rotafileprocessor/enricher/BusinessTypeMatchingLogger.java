package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.BUSINESS_TYPES_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;

import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BusinessTypeMatchingLogger {

    public static final Logger LOGGER = LoggerFactory.getLogger(BusinessTypeMatchingLogger.class);
    private static final String NEW_LINE = "------------------------------------------------------------------------------------";

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    public void logMissingBusinessType(final List<String> missingBusinessTypes, final String executionId) {
        logMissingBusinessTypeMessage(missingBusinessTypes, executionId);
    }

    private void logMissingBusinessTypeMessage(final List<String> missingBusinessTypes, final String executionId) {
        LOGGER.info(NEW_LINE);
        if (isNotEmpty(missingBusinessTypes)) {
            final String missingBusinessTypesAsStr = String.join(",", missingBusinessTypes);
            LOGGER.warn(BUSINESS_TYPES_NOT_FOUND.template(), missingBusinessTypesAsStr);

            final String msg = BUSINESS_TYPES_NOT_FOUND
                    .template()
                    .replace("{}", missingBusinessTypesAsStr);
            rotaProcessLogService.saveRotaProcessLog(
                    RotaProcessLogBuilder.rotaProcessLog()
                            .withExecutionId(executionId)
                            .withErrorCode(BUSINESS_TYPES_NOT_FOUND.code())
                            .withErrorText(msg)
                            .build()
            );
        }
        LOGGER.info(NEW_LINE);
    }
}
