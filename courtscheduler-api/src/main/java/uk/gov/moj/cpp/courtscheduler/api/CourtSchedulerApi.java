package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.converter.RequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.service.SlotsUpdateService;

import java.util.List;

import javax.inject.Inject;
import javax.json.JsonObject;

@CustomServiceComponent("Courtscheduler.API")
public class CourtSchedulerApi {

    @Inject
    private Enveloper enveloper;
    @Inject
    private SlotsUpdateService slotsUpdateService;
    @Inject
    private SlotsSearchService slotsSearchService;
    private final AllocatedSlotConverter converter = new AllocatedSlotConverter();
    private final HearingSlotsApiValidator validator = new HearingSlotsApiValidator();
    private final RequestParamConverter requestParamConverter = new RequestParamConverter();

    @Handles("courtscheduler.create")
    public JsonEnvelope createCourtSchedule(final JsonEnvelope envelope) {
        return enveloper.withMetadataFrom(envelope, "courtscheduler.create").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.update.hearing.slots")
    public JsonEnvelope updateHearingSlots(final JsonEnvelope envelope) {

        List<AllocatedSlot> allocatedSlots = converter.convert(envelope.payloadAsJsonObject().toString()).getHearingSlots();

        slotsUpdateService.update(allocatedSlots);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.update.hearing.slots").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.get.hearing.slots")
    public JsonEnvelope getHearingSlots(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        HearingSlotRequestParam hearingSlotRequestParam = requestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = validator.getHearingSlotsValidation(hearingSlotRequestParam);

        if(!validate.isEmpty()) {
            return envelopeFor(envelope, validate, "error");
        }

        JsonObject responseObject = slotsSearchService.search(hearingSlotRequestParam);
        return envelopeFor(envelope, responseObject, "hearingSlots");
    }

    private JsonEnvelope envelopeFor(final JsonEnvelope originalEnvelope, JsonObject jsonObject, String key) {
        return enveloper.withMetadataFrom(originalEnvelope, "courtscheduler.get.hearing.slots")
                .apply(createObjectBuilder().add(key, jsonObject).build());
    }
}
