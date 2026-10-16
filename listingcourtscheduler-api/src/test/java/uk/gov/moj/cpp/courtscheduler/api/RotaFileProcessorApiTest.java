package uk.gov.moj.cpp.courtscheduler.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaFileCaptureAndProcessTriggerService;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaRedundantDataCleanerService;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Re-platformed onto the Spring Boot {@link RotaFileProcessorApi} (was a CDI
 * {@code @CustomServiceComponent} that took {@code JsonEnvelope}s and called the
 * trigger service with a {@code Requester}). The current controller takes a
 * plain {@code Map<String,Object>} body and returns {@link ResponseEntity}; it
 * dispatches to the same downstream services with their migrated signatures.
 */
@ExtendWith(MockitoExtension.class)
class RotaFileProcessorApiTest {

    @Mock
    private RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService;

    @Mock
    private RotaRedundantDataCleanerService rotaRedundantDataCleanerService;

    @InjectMocks
    private RotaFileProcessorApi rotaFileProcessorApi;

    @ParameterizedTest(name = "rotaProcess = {0}")
    @NullSource
    @ValueSource(strings = {"old", "new", "anything"})
    void shouldProcessRotaFilesTheSameWayWhateverTheLegacyRotaProcessValue(final String rotaProcess) {
        final Map<String, Object> body = new HashMap<>();
        if (rotaProcess != null) {
            body.put("rotaProcess", rotaProcess);
        }
        body.put("forItTest", false);

        final ResponseEntity<Void> response = rotaFileProcessorApi.postProcessRotaFiles(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaFileCaptureAndProcessTriggerService).captureRotaFilesAndProcessEach(false);
        verifyNoMoreInteractions(rotaFileCaptureAndProcessTriggerService);
    }

    @Test
    void shouldProcessRotaFilesWithEmptyBody() {
        final ResponseEntity<Void> response = rotaFileProcessorApi.postProcessRotaFiles(new HashMap<>());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaFileCaptureAndProcessTriggerService).captureRotaFilesAndProcessEach(false);
    }

    @Test
    void shouldProcessRotaFilesPassingForItTestFlagThrough() {
        final Map<String, Object> body = new HashMap<>();
        body.put("forItTest", true);

        final ResponseEntity<Void> response = rotaFileProcessorApi.postProcessRotaFiles(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaFileCaptureAndProcessTriggerService).captureRotaFilesAndProcessEach(true);
    }

    @Test
    void shouldCleanRedundantRotaData() {
        final Map<String, Object> body = new HashMap<>();
        body.put("numberOfPreviousMonthsAndOlder", 36);

        final ResponseEntity<Void> response = rotaFileProcessorApi.postCleanRedundantRotaData(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaRedundantDataCleanerService).cleanDataForPreviousMonths(36);
    }

    @Test
    void shouldCleanRedundantRotaDataDefaultsToSixMonthsWhenBodyEmpty() {
        final ResponseEntity<Void> response = rotaFileProcessorApi.postCleanRedundantRotaData(new HashMap<>());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaRedundantDataCleanerService).cleanDataForPreviousMonths(6);
    }
}
