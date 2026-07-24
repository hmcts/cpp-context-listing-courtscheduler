package uk.gov.moj.cpp.courtscheduler.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaFileCaptureAndProcessTriggerService;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaRedundantDataCleanerService;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Test
    void shouldProcessRotaFiles() {
        final Map<String, Object> body = new HashMap<>();
        body.put("rotaProcess", "new");
        body.put("forItTest", false);

        final ResponseEntity<Void> response = rotaFileProcessorApi.postProcessRotaFiles(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaFileCaptureAndProcessTriggerService).captureRotaFilesAndProcessEach(eq(false), eq("new"));
    }

    @Test
    void shouldProcessRotaFilesWithMissingRotaProcessUsingRandomUuid() {
        // When body has no rotaProcess key, the controller passes a random UUID through —
        // the trigger service then routes to the "new" rota processor branch.
        final ResponseEntity<Void> response = rotaFileProcessorApi.postProcessRotaFiles(new HashMap<>());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaFileCaptureAndProcessTriggerService).captureRotaFilesAndProcessEach(eq(false), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldProcessRotaFilesPassingForItTestFlagThrough() {
        final Map<String, Object> body = new HashMap<>();
        body.put("rotaProcess", "old");
        body.put("forItTest", true);

        final ResponseEntity<Void> response = rotaFileProcessorApi.postProcessRotaFiles(body);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        verify(rotaFileCaptureAndProcessTriggerService).captureRotaFilesAndProcessEach(eq(true), eq("old"));
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
