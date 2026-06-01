package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.DELIMITER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.MISSING_COURT_SESSION;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MissingReferenceDataMappingLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MissingReferenceDataMappingLogger.class);
    private static final String NEW_LINE = "------------------------------------------------------------------------------------";

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    public void logMissingVenueMessages(final Map<String, String> missingReferenceDataMappingMap, final String executionId) {
        if (!isNotEmpty(executionId) || missingReferenceDataMappingMap.isEmpty()) {
            return;
        }
        LOGGER.info(NEW_LINE);
        final String venueDetails = missingReferenceDataMappingMap.entrySet()
                .stream()
                .filter(e -> REF_DATA_VENUE_NOT_FOUND.code().equals(e.getValue()))
                .map(Map.Entry::getKey)
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .distinct()
                .collect(joining(format(DELIMITER)));
        if (isNotEmpty(venueDetails)) {
            final String msg = REF_DATA_VENUE_NOT_FOUND.format(venueDetails);
            LOGGER.warn(msg);
            rotaProcessLogService.saveRotaProcessLog(
                    rotaProcessLog()
                            .withExecutionId(executionId)
                            .withErrorCode(REF_DATA_VENUE_NOT_FOUND.code())
                            .withErrorText(msg)
                            .build()
            );
        }
        LOGGER.info(NEW_LINE);
    }

    public void logJudiciaryMissingMessage(final Collection<String> messages, final String executionId) {
        if (!isNotEmpty(executionId) || messages.isEmpty()) {
            return;
        }
        LOGGER.warn(NEW_LINE);
        final String judiciaryMissingMessages = messages
                .stream()
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .distinct()
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

    public void logMissingCourtSessions(final Map<String, List<String>> missingSessionsByOuCode, final String executionId) {
        if (!isNotEmpty(executionId) || missingSessionsByOuCode.isEmpty()) {
            return;
        }
        missingSessionsByOuCode.forEach((ouCode, sessions) -> {
            final String sessionDetails = sessions.stream()
                    .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                    .distinct()
                    .collect(joining(format(DELIMITER)));
            if (isNotEmpty(sessionDetails)) {
                final String msg = MISSING_COURT_SESSION.format(ouCode, sessionDetails);
                LOGGER.warn(msg);
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(MISSING_COURT_SESSION.code())
                                .withErrorText(msg)
                                .build()
                );
            }
        });
    }
}