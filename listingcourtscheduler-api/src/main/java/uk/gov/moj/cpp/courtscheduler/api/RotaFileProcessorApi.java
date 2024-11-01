package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaFileCaptureAndProcessTriggerService;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CustomServiceComponent("Courtscheduler.API")
public class RotaFileProcessorApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotaFileProcessorApi.class.getName());

    @Inject
    private Enveloper enveloper;

    @Inject
    private Requester requester;

    @Inject
    private RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService;

    @Handles("courtscheduler.rotasl.process_rota_files")
    public JsonEnvelope processRotaFiles(final JsonEnvelope envelope) {
        LOGGER.info("processRotaFiles api called - courtscheduler.rotasl.process_rota_files");
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("calling rotaFileProcessorService.captureRotaFilesAndProcessEach asynchronously");
        final boolean isForItTest = payload.getBoolean("forItTest", false);
        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(requester, isForItTest);
        LOGGER.info("successfully called and completed - rotaFileProcessorService.captureRotaFilesAndProcessEach asynchronously");

        return enveloper.withMetadataFrom(envelope, "courtscheduler.rotasl.process_rota_files").apply(createObjectBuilder().build());
    }
}
