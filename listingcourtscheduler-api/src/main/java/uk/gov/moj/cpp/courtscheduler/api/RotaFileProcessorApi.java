package uk.gov.moj.cpp.courtscheduler.api;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaFileCaptureAndProcessTriggerService;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaRedundantDataCleanerService;
import uk.gov.moj.cpp.courtscheduler.openapi.api.RotaslOpenApi;

/**
 * Spring Boot replacement for the legacy WildFly {@code RotaFileProcessorApi}.
 * Implements the OpenAPI-generated {@link RotaslOpenApi} — triggers rota file
 * processing and clean-up (replaces legacy {@code @Handles
 * courtscheduler.rotasl.process_rota_files} and {@code courtscheduler.rotasl
 * .clean_redundant_rota_data}).
 */
@RestController
public class RotaFileProcessorApi implements RotaslOpenApi {

    private static final Logger LOG = LoggerFactory.getLogger(RotaFileProcessorApi.class);

    private final RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService;
    private final RotaRedundantDataCleanerService rotaRedundantDataCleanerService;

    public RotaFileProcessorApi(final RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService,
                                final RotaRedundantDataCleanerService rotaRedundantDataCleanerService) {
        this.rotaFileCaptureAndProcessTriggerService = rotaFileCaptureAndProcessTriggerService;
        this.rotaRedundantDataCleanerService = rotaRedundantDataCleanerService;
    }

    @Override
    public ResponseEntity<Void> postProcessRotaFiles(final Map<String, Object> body) {
        LOG.info("courtscheduler.rotasl.process_rota_files: {}", body);
        final boolean forItTest = Boolean.TRUE.equals(body == null ? null : body.get("forItTest"));
        // The body's {@code rotaProcess} value picks the processor inside
        // {@link RotaFileCaptureAndProcessTriggerService#captureRotaFilesAndProcessEach}:
        // {@code "old"} routes to {@code rotaFileProcessorService}, anything else (or
        // a random UUID) to {@code newRotaFileProcessor}. Preserve the body value so
        // the legacy IT classes can exercise both branches.
        final Object rawProcess = body == null ? null : body.get("rotaProcess");
        final String rotaProcess = rawProcess == null ? UUID.randomUUID().toString() : rawProcess.toString();
        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(forItTest, rotaProcess);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Override
    public ResponseEntity<Void> postCleanRedundantRotaData(final Map<String, Object> body) {
        LOG.info("courtscheduler.rotasl.clean_redundant_rota_data: {}", body);
        final int months = body == null || body.get("months") == null ? 6 : ((Number) body.get("months")).intValue();
        rotaRedundantDataCleanerService.cleanDataForPreviousMonths(months);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
