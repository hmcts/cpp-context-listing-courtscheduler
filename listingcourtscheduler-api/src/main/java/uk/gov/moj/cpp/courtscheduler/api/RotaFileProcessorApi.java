package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.RotaFileProcessorService;

import javax.inject.Inject;

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
    private RotaFileProcessorService rotaFileProcessorService;

    @Handles("courtscheduler.rotasl.process_rota_files")
    public JsonEnvelope processRotaFiles(final JsonEnvelope envelope) {
        LOGGER.info("processRotaFiles api called - courtscheduler.rotasl.process_rota_files");

        rotaFileProcessorService.captureRotaFilesAndProcessEach(requester);
        return enveloper.withMetadataFrom(envelope, "courtscheduler.rotasl.process_rota_files").apply(createObjectBuilder().build());
    }
}
