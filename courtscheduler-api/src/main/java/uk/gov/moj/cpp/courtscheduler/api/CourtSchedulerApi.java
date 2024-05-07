package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.inject.Inject;

@CustomServiceComponent("Courtscheduler.API")
public class CourtSchedulerApi {

    @Inject
    private Enveloper enveloper;

    @Inject
    private PayloadExtractor payloadExtractor;

    @Handles("courtscheduler.create")
    public JsonEnvelope createCourtSchedule(final JsonEnvelope envelope) {
        //validate request by checking the payload
        return enveloper.withMetadataFrom(envelope, "courtscheduler.create").apply(createObjectBuilder().build());
    }
}
