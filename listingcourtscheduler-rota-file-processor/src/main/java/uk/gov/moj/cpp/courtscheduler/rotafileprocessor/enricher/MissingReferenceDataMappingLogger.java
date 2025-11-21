package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.COURT_DETAIL_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.DELIMITER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.util.Collection;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class MissingReferenceDataMappingLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissingReferenceDataMappingLogger.class);
    private static final String NEW_LINE = "------------------------------------------------------------------------------------";

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    public void logCourtDetailsMessage(final Map<String, String> missingReferenceDataMappingMap, final String executionId) {
        LOGGER.info(NEW_LINE);
        final String courtDetailMissing = missingReferenceDataMappingMap.entrySet()
                .stream()
                .filter(e -> e.getValue().equals(COURT_DETAIL_NOT_FOUND.code()))
                .map(Map.Entry::getKey)
                .collect(joining(format(DELIMITER)));
        if (isNotEmpty(courtDetailMissing)) {
            final String msg = COURT_DETAIL_NOT_FOUND.format(courtDetailMissing);
            LOGGER.warn(msg);
            rotaProcessLogService.saveRotaProcessLog(
                    rotaProcessLog()
                            .withExecutionId(executionId)
                            .withErrorCode(COURT_DETAIL_NOT_FOUND.code())
                            .withErrorText(msg)
                            .build()
            );
        }
        LOGGER.info(NEW_LINE);
    }

    public void logJudiciaryMissingMessage(final Collection<String> messages, final String executionId) {
        LOGGER.warn(NEW_LINE);
        final String judiciaryMissingMessages = messages
                .stream()
                .collect(joining(format(DELIMITER)));
        if (isNotEmpty(judiciaryMissingMessages)) {
            final String msg = JUDICIARY_NOT_FOUND.format(judiciaryMissingMessages);
            LOGGER.warn(msg);
            rotaProcessLogService.saveRotaProcessLog(
                    RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog()
                            .withExecutionId(executionId)
                            .withErrorCode(JUDICIARY_NOT_FOUND.code())
                            .withErrorText(msg)
                            .build()
            );
        }
        LOGGER.warn(NEW_LINE);
    }
}