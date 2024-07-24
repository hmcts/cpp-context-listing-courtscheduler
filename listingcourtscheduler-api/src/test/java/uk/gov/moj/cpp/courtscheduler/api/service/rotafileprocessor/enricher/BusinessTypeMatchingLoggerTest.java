package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.MissingDataErrorMessages.BUSINESS_TYPES_NOT_FOUND_MSG;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class BusinessTypeMatchingLoggerTest {

    @InjectMocks
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Mock
    private Logger logger;

    private static final String MISSING_BUSINESS_TYPE = "CJU";

    @Test
    void shouldLogMissingBusinessType() {
        final List<String> missingBusinessTypes = List.of(MISSING_BUSINESS_TYPE);
        businessTypeMatchingLogger.logMissingBusinessType(missingBusinessTypes);
        verify(logger, times(2)).info("------------------------------------------------------------------------------------");

        final String missingBusinessTypesAsStr = String.join(",", missingBusinessTypes);
        verify(logger, atLeastOnce()).warn(BUSINESS_TYPES_NOT_FOUND_MSG, missingBusinessTypesAsStr);
    }
}
