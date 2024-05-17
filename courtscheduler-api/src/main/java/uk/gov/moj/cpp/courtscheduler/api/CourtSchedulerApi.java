package uk.gov.moj.cpp.courtscheduler.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.service.SlotsUpdateService;

import javax.inject.Inject;
import java.util.List;

import static javax.json.Json.createObjectBuilder;

@CustomServiceComponent("Courtscheduler.API")
public class CourtSchedulerApi {

    @Inject
    private Enveloper enveloper;

    @Inject
    private SlotsUpdateService slotsUpdateService;

    private final AllocatedSlotConverter converter = new AllocatedSlotConverter();

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
}
