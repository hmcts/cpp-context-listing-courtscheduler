package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaFileCaptureAndProcessTriggerService;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaRedundantDataCleanerService;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFilePartialProcessor;

import java.util.UUID;
import java.util.function.Function;

import javax.ejb.AsyncResult;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

@ExtendWith(MockitoExtension.class)
class RotaFileProcessorApiTest {

    @Mock
    private Enveloper enveloper;

    @Mock
    private Requester requester;

    @Mock
    private Function<Object, JsonEnvelope> function;

    @Mock
    private RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService;

    @Mock
    private RotaRedundantDataCleanerService rotaRedundantDataCleanerService;

    @Mock
    private RotaFilePartialProcessor rotaFilePartialProcessor;

    @InjectMocks
    private RotaFileProcessorApi rotaFileProcessorApi;

    @Mock
    private Logger LOGGER;

    @Test
    void shouldProcessRotaFiles() {
        final String requestName = "courtscheduler.rotasl.process_rota_files";

        final JsonObject payloadAsJsonObject = createObjectBuilder().build();
        final JsonEnvelope processRotaFilesJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);
        when(enveloper.withMetadataFrom(processRotaFilesJsonEnvelope, requestName)).thenReturn(function);
        when(rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(eq(requester), eq(false))).thenReturn(new AsyncResult<>("SUCCESS"));

        rotaFileProcessorApi.processRotaFiles(processRotaFilesJsonEnvelope);

        verify(rotaFileCaptureAndProcessTriggerService, timeout(1000).atLeastOnce()).captureRotaFilesAndProcessEach(eq(requester), eq(false));
        verify(LOGGER, atLeastOnce()).info("processRotaFiles api called - courtscheduler.rotasl.process_rota_files");
        verify(enveloper, atLeastOnce()).withMetadataFrom(processRotaFilesJsonEnvelope, requestName);
    }

    @Test
    void shouldCleanRedundantRotaData() {
        final String requestName = "courtscheduler.rotasl.clean_redundant_rota_data";

        final JsonObject payloadAsJsonObject = createObjectBuilder().build();
        final JsonEnvelope cleanRedundantRotaDataJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);
        when(enveloper.withMetadataFrom(cleanRedundantRotaDataJsonEnvelope, requestName)).thenReturn(function);
        doNothing().when(rotaRedundantDataCleanerService).cleanDataForPreviousMonths(anyInt());

        rotaFileProcessorApi.cleanRedundantRotaData(cleanRedundantRotaDataJsonEnvelope);

        verify(rotaRedundantDataCleanerService, timeout(1000).atLeastOnce()).cleanDataForPreviousMonths(anyInt());
        verify(LOGGER, atLeastOnce()).info("cleanRedundantRotaData api called - courtscheduler.rotasl.clean_redundant_rota_data");
        verify(LOGGER, atLeastOnce()).info("successfully called and completed - rotaRedundantDataCleanerService.cleanDataForPreviousMonths asynchronously");
        verify(enveloper, atLeastOnce()).withMetadataFrom(cleanRedundantRotaDataJsonEnvelope, requestName);
    }

    @Test
    void shouldUnassignJudiciarySuccessfully() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";
        final String courtScheduleId = "schedule-123";
        final String judiciaryId = "judge-456";

        final JsonObject assignment = createObjectBuilder()
                .add("courtScheduleId", courtScheduleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .add(assignment)
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);
        when(enveloper.withMetadataFrom(unassignJudiciaryJsonEnvelope, requestName)).thenReturn(function);
        doNothing().when(rotaFilePartialProcessor).unassignJudiciary(eq(courtScheduleId), eq(judiciaryId));

        rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope);

        verify(rotaFilePartialProcessor, atLeastOnce()).unassignJudiciary(eq(courtScheduleId), eq(judiciaryId));
        verify(LOGGER, atLeastOnce()).info("courtscheduler.rotasl.unassign.judiciary requested : {}", payloadAsJsonObject);
        verify(LOGGER, atLeastOnce()).info("courtscheduler.rotasl.unassign.judiciary: successfully unassigned judiciary {} from courtSchedule {}", judiciaryId, courtScheduleId);
        verify(enveloper, atLeastOnce()).withMetadataFrom(unassignJudiciaryJsonEnvelope, requestName);
    }

    @Test
    void shouldThrowBadRequestExceptionWhenCourtScheduleIdIsMissing() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";

        final JsonObject assignment = createObjectBuilder()
                .add("judiciaryId", "judge-456")
                .build();
        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .add(assignment)
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope));

        assertEquals("courtScheduleId is required in assignments[0]", exception.getMessage());
        verify(rotaFilePartialProcessor, org.mockito.Mockito.never()).unassignJudiciary(anyString(), anyString());
    }

    @Test
    void shouldThrowBadRequestExceptionWhenJudiciaryIdIsMissing() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";

        final JsonObject assignment = createObjectBuilder()
                .add("courtScheduleId", "schedule-123")
                .build();
        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .add(assignment)
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope));

        assertEquals("judiciaryId is required in assignments[0]", exception.getMessage());
        verify(rotaFilePartialProcessor, org.mockito.Mockito.never()).unassignJudiciary(anyString(), anyString());
    }

    @Test
    void shouldThrowBadRequestExceptionWhenCourtScheduleHasAllocatedListings() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";
        final String courtScheduleId = "schedule-123";
        final String judiciaryId = "judge-456";
        final String errorMessage = "Cannot unassign judiciary judge-456 from courtSchedule schedule-123: court schedule has allocated listings";

        final JsonObject assignment = createObjectBuilder()
                .add("courtScheduleId", courtScheduleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .add(assignment)
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);
        doThrow(new IllegalStateException(errorMessage))
                .when(rotaFilePartialProcessor).unassignJudiciary(eq(courtScheduleId), eq(judiciaryId));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope));

        assertEquals(errorMessage, exception.getMessage());
        verify(rotaFilePartialProcessor, atLeastOnce()).unassignJudiciary(eq(courtScheduleId), eq(judiciaryId));
        verify(LOGGER, atLeastOnce()).warn("courtscheduler.rotasl.unassign.judiciary: cannot unassign - {}", errorMessage);
    }

    @Test
    void shouldThrowBadRequestExceptionWhenJudiciaryNotFound() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";
        final String courtScheduleId = "schedule-123";
        final String judiciaryId = "judge-456";
        final String errorMessage = "Judiciary judge-456 not found for courtSchedule schedule-123";

        final JsonObject assignment = createObjectBuilder()
                .add("courtScheduleId", courtScheduleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .add(assignment)
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);
        doThrow(new IllegalArgumentException(errorMessage))
                .when(rotaFilePartialProcessor).unassignJudiciary(eq(courtScheduleId), eq(judiciaryId));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope));

        assertEquals(errorMessage, exception.getMessage());
        verify(rotaFilePartialProcessor, atLeastOnce()).unassignJudiciary(eq(courtScheduleId), eq(judiciaryId));
        verify(LOGGER, atLeastOnce()).warn("courtscheduler.rotasl.unassign.judiciary: not found - {}", errorMessage);
    }

    @Test
    void shouldThrowBadRequestExceptionWhenAssignmentsArrayIsMissing() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";

        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope));

        assertEquals("assignments array is required", exception.getMessage());
        verify(rotaFilePartialProcessor, org.mockito.Mockito.never()).unassignJudiciary(anyString(), anyString());
    }

    @Test
    void shouldThrowBadRequestExceptionWhenAssignmentsArrayIsEmpty() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";

        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope));

        assertEquals("assignments array must contain at least one item", exception.getMessage());
        verify(rotaFilePartialProcessor, org.mockito.Mockito.never()).unassignJudiciary(anyString(), anyString());
    }

    @Test
    void shouldUnassignMultipleJudiciariesSuccessfully() {
        final String requestName = "courtscheduler.rotasl.unassign.judiciary";
        final String courtScheduleId1 = "schedule-123";
        final String judiciaryId1 = "judge-456";
        final String courtScheduleId2 = "schedule-789";
        final String judiciaryId2 = "judge-012";

        final JsonObject assignment1 = createObjectBuilder()
                .add("courtScheduleId", courtScheduleId1)
                .add("judiciaryId", judiciaryId1)
                .build();
        final JsonObject assignment2 = createObjectBuilder()
                .add("courtScheduleId", courtScheduleId2)
                .add("judiciaryId", judiciaryId2)
                .build();
        final javax.json.JsonArray assignmentsArray = createArrayBuilder()
                .add(assignment1)
                .add(assignment2)
                .build();
        final JsonObject payloadAsJsonObject = createObjectBuilder()
                .add("assignments", assignmentsArray)
                .build();
        final JsonEnvelope unassignJudiciaryJsonEnvelope = createEnvelope(requestName, payloadAsJsonObject);
        when(enveloper.withMetadataFrom(unassignJudiciaryJsonEnvelope, requestName)).thenReturn(function);
        doNothing().when(rotaFilePartialProcessor).unassignJudiciary(eq(courtScheduleId1), eq(judiciaryId1));
        doNothing().when(rotaFilePartialProcessor).unassignJudiciary(eq(courtScheduleId2), eq(judiciaryId2));

        rotaFileProcessorApi.unassignJudiciary(unassignJudiciaryJsonEnvelope);

        verify(rotaFilePartialProcessor, atLeastOnce()).unassignJudiciary(eq(courtScheduleId1), eq(judiciaryId1));
        verify(rotaFilePartialProcessor, atLeastOnce()).unassignJudiciary(eq(courtScheduleId2), eq(judiciaryId2));
        verify(enveloper, atLeastOnce()).withMetadataFrom(unassignJudiciaryJsonEnvelope, requestName);
    }

    private JsonEnvelope createEnvelope(final String name, final JsonValue payload) {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();

        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName(name)
                .withId(uuid)
                .withUserId(userId.toString())
                .build();
        return new DefaultJsonEnvelopeProvider().envelopeFrom(metadata, payload);
    }
}
