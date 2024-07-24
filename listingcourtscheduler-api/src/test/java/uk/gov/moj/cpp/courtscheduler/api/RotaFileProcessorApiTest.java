package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.RotaFileProcessorService;

import java.util.UUID;
import java.util.function.Function;

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
    private RotaFileProcessorService rotaFileProcessorService;

    @InjectMocks
    private RotaFileProcessorApi rotaFileProcessorApi;

    @Mock
    private Logger LOGGER;

    @Test
    void shouldProcessRotaFiles() throws InterruptedException {
        final String requestName = "courtscheduler.rotasl.process_rota_files";

        final JsonEnvelope processRotaFilesJsonEnvelope = createEnvelope(requestName, JsonValue.EMPTY_JSON_OBJECT);

        when(enveloper.withMetadataFrom(processRotaFilesJsonEnvelope, requestName)).thenReturn(function);
        doNothing().when(rotaFileProcessorService).captureRotaFilesAndProcessEach(eq(requester));

        rotaFileProcessorApi.processRotaFiles(processRotaFilesJsonEnvelope);

        verify(rotaFileProcessorService, timeout(1000).atLeastOnce()).captureRotaFilesAndProcessEach(eq(requester));
        verify(LOGGER, atLeastOnce()).info("processRotaFiles api called - courtscheduler.rotasl.process_rota_files");
        verify(enveloper, atLeastOnce()).withMetadataFrom(processRotaFilesJsonEnvelope, requestName);
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
