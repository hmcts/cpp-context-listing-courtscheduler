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
import uk.gov.moj.cpp.courtscheduler.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.converter.RequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.service.SlotsUpdateService;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

import javax.json.JsonObject;
import javax.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtSchedulerApiTest {
    @Mock
    private Enveloper enveloper;
    @Mock
    private SlotsUpdateService slotsUpdateService;
    @Mock
    private SlotsSearchService slotsSearchService;
    @Mock
    private Function<Object, JsonEnvelope> function;
    @InjectMocks
    private CourtSchedulerApi courtSchedulerApi;

    @Test
    void shouldCreateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("create-court-schedule.json"));
        final String requestName = "courtscheduler.create";

        final JsonEnvelope createCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        when(enveloper.withMetadataFrom(createCourtScheduleJsonEnvelope, requestName)).thenReturn(function);

        courtSchedulerApi.createCourtSchedule(createCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(createCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void shouldUpdateHearingSlots() throws IOException {
        String payload = FileUtil.getPayload("courtscheduler.update.hearing.slots.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.update.hearing.slots";

        final JsonEnvelope updateHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(updateHearingSlotsEnvelope, requestName)).thenReturn(function);

        courtSchedulerApi.updateHearingSlots(updateHearingSlotsEnvelope);

        verify(slotsUpdateService, atLeastOnce()).update(new AllocatedSlotConverter().convert(payload).getHearingSlots());
        verify(enveloper, atLeastOnce()).withMetadataFrom(updateHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldRetrieveHearingSlots() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.get.hearing.slots.json"));
        final String requestName = "courtscheduler.get.hearing.slots";
        final JsonEnvelope getHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);
        final RequestParamConverter requestParamConverter = new RequestParamConverter();

        when(enveloper.withMetadataFrom(getHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(slotsSearchService.search(requestParamConverter.convert(jsonObject))).thenReturn(JsonObject.EMPTY_JSON_OBJECT);

        courtSchedulerApi.getHearingSlots(getHearingSlotsEnvelope);

        verify(slotsSearchService, atLeastOnce()).search(requestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(getHearingSlotsEnvelope, requestName);
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
