package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.service.ProvisionalBookingService;

import javax.inject.Inject;
import javax.json.JsonObject;

@CustomServiceComponent("ProvisionalBooking.API")
public class ProvisionalBookingApi {

    @Inject
    private Enveloper enveloper;
    @Inject
    private ProvisionalBookingService provisionalBookingService;
    private ProvisionalSlotConverter provisionalSlotConverter = new ProvisionalSlotConverter();
    private ProvisionalBookingApiValidator provisionalBookingApiValidator = new ProvisionalBookingApiValidator();

    @Handles("courtscheduler.create.provisional.booking")
    public JsonEnvelope createProvisionalBooking(final JsonEnvelope envelope) {
        ProvisionalBookingSlots provisionalBookingSlots = provisionalSlotConverter.convert(envelope.payloadAsJsonObject().toString());
        JsonObject validate = provisionalBookingApiValidator.createProvisionalBookingValidation(provisionalBookingSlots);

        if(!validate.isEmpty()) {
            return envelopeFor(envelope, validate, "error");
        }

        JsonObject responseObject = provisionalBookingService.bookProvisionalSlots(provisionalBookingSlots);
        return envelopeFor(envelope, responseObject, ApiConstants.BOOKING_REFERENCE);
    }

    private JsonEnvelope envelopeFor(final JsonEnvelope originalEnvelope, JsonObject jsonObject, String key) {
        return enveloper.withMetadataFrom(originalEnvelope, "courtscheduler.create.provisional.booking")
                .apply(createObjectBuilder().add(key, jsonObject).build());
    }
}
