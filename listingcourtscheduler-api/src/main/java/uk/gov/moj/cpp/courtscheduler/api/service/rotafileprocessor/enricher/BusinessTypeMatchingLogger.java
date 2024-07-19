package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.MissingDataErrorMessages.BUSINESS_TYPES_NOT_FOUND_MSG;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class BusinessTypeMatchingLogger {

    public static final Logger logger = LoggerFactory.getLogger(BusinessTypeMatchingLogger.class);

    public void logMissingBusinessType(final List<String> missingBusinessTypes) {
        logMissingBusinessTypeMessage(missingBusinessTypes);

    }

    private void logMissingBusinessTypeMessage(final List<String> missingBusinessTypes) {
        logger.info("------------------------------------------------------------------------------------");

        if (isNotEmpty(missingBusinessTypes)) {
            final String missingBusinessTypesAsStr = String.join(",", missingBusinessTypes);
            logger.warn(BUSINESS_TYPES_NOT_FOUND_MSG, missingBusinessTypesAsStr);
        }
        logger.info("------------------------------------------------------------------------------------");
    }


}
