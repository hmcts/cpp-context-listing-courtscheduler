package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaFileCaptureAndProcessTriggerService;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaRedundantDataCleanerService;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFilePartialProcessor;

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

    @Inject
    private RotaRedundantDataCleanerService rotaRedundantDataCleanerService;

    @Inject
    private RotaFilePartialProcessor rotaFilePartialProcessor;

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

    @Handles("courtscheduler.rotasl.clean_redundant_rota_data")
    public JsonEnvelope cleanRedundantRotaData(final JsonEnvelope envelope) {
        LOGGER.info("cleanRedundantRotaData api called - courtscheduler.rotasl.clean_redundant_rota_data");
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("calling rotaRedundantDataCleanerService.cleanDataForPreviousMonths asynchronously");
        final int numberOfPreviousMonthsAndOlder = payload.getInt("numberOfPreviousMonthsAndOlder", 6);
        rotaRedundantDataCleanerService.cleanDataForPreviousMonths(numberOfPreviousMonthsAndOlder);
        LOGGER.info("successfully called and completed - rotaRedundantDataCleanerService.cleanDataForPreviousMonths asynchronously");

        return enveloper.withMetadataFrom(envelope, "courtscheduler.rotasl.clean_redundant_rota_data").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.rotasl.unassign.judiciary")
    public JsonEnvelope unassignJudiciary(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.rotasl.unassign.judiciary requested : {}", payload);

        final String courtScheduleId = payload.getString("courtScheduleId", "");
        final String judiciaryId = payload.getString("judiciaryId", "");

        if (courtScheduleId.isEmpty()) {
            throw new BadRequestException("courtScheduleId is required");
        }

        if (judiciaryId.isEmpty()) {
            throw new BadRequestException("judiciaryId is required");
        }

        try {
            rotaFilePartialProcessor.unassignJudiciary(courtScheduleId, judiciaryId);
            LOGGER.info("courtscheduler.rotasl.unassign.judiciary: successfully unassigned judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);
        } catch (IllegalStateException e) {
            LOGGER.warn("courtscheduler.rotasl.unassign.judiciary: cannot unassign - {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("courtscheduler.rotasl.unassign.judiciary: not found - {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
        }

        return enveloper.withMetadataFrom(envelope, "courtscheduler.rotasl.unassign.judiciary").apply(createObjectBuilder().build());
    }
}
