package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.payloadToObject;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtSchedulerApiTest {

    @InjectMocks
    private CourtSchedulerApi courtSchedulerApi;

    @Mock
    private Enveloper enveloper;

    @Mock
    private Function<Object, JsonEnvelope> function;

    @Test
    void shouldCreateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("create-court-schedule.json"));
        final JsonEnvelope createCourtScheduleJsonEnvelope = createCourtScheduleApiEnvelope(jsonPayloadObject);

        when(enveloper.withMetadataFrom(createCourtScheduleJsonEnvelope, "courtscheduler.create")).thenReturn(function);

        courtSchedulerApi.createCourtSchedule(createCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(createCourtScheduleJsonEnvelope, "courtscheduler.create");
    }

    private JsonEnvelope createCourtScheduleApiEnvelope(final JsonObject payload) {
        final UUID uuid = randomUUID();
        final UUID userId = randomUUID();

        final Metadata metadata = Envelope
                .metadataBuilder()
                .withName("courtscheduler.create")
                .withId(uuid)
                .withUserId(userId.toString())
                .build();
        return new DefaultJsonEnvelopeProvider().envelopeFrom(metadata, payload);
    }
}
