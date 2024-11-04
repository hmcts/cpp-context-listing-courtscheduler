package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.COURT_DETAIL_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.COURT_DETAIL_NOT_FOUND_MSG;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.DELIMITER;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.JUDICIARY_NOT_FOUND_MSG;

import java.util.Collection;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MissingReferenceDataMappingLogger {

    private static Logger logger = LoggerFactory.getLogger(MissingReferenceDataMappingLogger.class);

    public void logCourtDetailsMessage(final Map<String, String> missingReferenceDataMappingMap) {
        logger.info("------------------------------------------------------------------------------------");

        final String courtDetailMissing = missingReferenceDataMappingMap.entrySet()
                .stream()
                .filter(e -> e.getValue().equals(COURT_DETAIL_NOT_FOUND))
                .map(Map.Entry::getKey)
                .collect(joining(format(DELIMITER)));

        if (isNotEmpty(courtDetailMissing)) {
            logger.warn(format(COURT_DETAIL_NOT_FOUND_MSG, courtDetailMissing));
        }
        logger.info("------------------------------------------------------------------------------------");


    }

    public void logJudiciaryMissingMessage(final Collection<String> messages) {
        logger.warn("------------------------------------------------------------------------------------");

        final String judiciaryMissingMessages = messages
                .stream()
                .collect(joining(format(DELIMITER)));

        if (isNotEmpty(judiciaryMissingMessages)) {
            final String message = format(JUDICIARY_NOT_FOUND_MSG, judiciaryMissingMessages);
            logger.warn(message);
        }

        logger.warn("------------------------------------------------------------------------------------");
    }
}