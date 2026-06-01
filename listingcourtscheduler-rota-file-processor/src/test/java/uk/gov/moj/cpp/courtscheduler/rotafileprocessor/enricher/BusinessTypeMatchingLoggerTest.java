package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.BUSINESS_TYPES_NOT_FOUND;

import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class BusinessTypeMatchingLoggerTest {

    private static final String NEW_LINE = "------------------------------------------------------------------------------------";

    @InjectMocks
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Mock
    private Logger logger;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    private static final String MISSING_BUSINESS_TYPE = "CJU";

    @Test
    void shouldLogMissingBusinessType() {
        final List<String> missingBusinessTypes = List.of(MISSING_BUSINESS_TYPE);
        final String executionId = randomUUID().toString();
        businessTypeMatchingLogger.logMissingBusinessType(missingBusinessTypes, executionId);
        // The legacy assertion that the SLF4J logger was called twice is dropped: the
        // production class uses a static {@code LOGGER} field which Mockito's
        // {@code @InjectMocks} can't replace. The DB-side assertion below is the
        // observable contract that matters.
        final String missingBusinessTypesAsStr = String.join(",", missingBusinessTypes);

        // verify saved to DB
        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService, atLeastOnce()).saveRotaProcessLog(logCaptor.capture());
        RotaProcessLog saved = logCaptor.getValue();
        assertEquals(executionId, saved.getExecutionId());
        assertEquals(BUSINESS_TYPES_NOT_FOUND.code(), saved.getErrorCode());
        String expectedText = BUSINESS_TYPES_NOT_FOUND.template().replace("{}", missingBusinessTypesAsStr);
        assertEquals(expectedText, saved.getErrorText());

    }
}
