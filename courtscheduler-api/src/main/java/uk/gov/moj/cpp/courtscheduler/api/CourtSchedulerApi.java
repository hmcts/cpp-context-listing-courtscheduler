package uk.gov.moj.cpp.courtscheduler.api;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.domain.*;
import uk.gov.moj.cpp.courtscheduler.service.MiService;
import uk.gov.moj.cpp.courtscheduler.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.service.SlotsUpdateService;

import javax.inject.Inject;
import javax.json.JsonObject;
import java.util.List;

import static javax.json.Json.createObjectBuilder;

@CustomServiceComponent("Courtscheduler.API")
public class CourtSchedulerApi {

    private static final String ALLOCATED_LISTINGS = "allocatedListings";
    private static final String COURT_SCHEDULES = "courtSchedules";
    private static final String COURT_SCHEDULE_JUDICIARIES = "courtScheduleJudiciaries";
    @Inject
    private Enveloper enveloper;
    @Inject
    private SlotsUpdateService slotsUpdateService;
    @Inject
    private SlotsSearchService slotsSearchService;

    @Inject
    private MiService miService;
    private final AllocatedSlotConverter converter = new AllocatedSlotConverter();
    private final HearingSlotsApiValidator validator = new HearingSlotsApiValidator();
    private final HearingSlotRequestParamConverter hearingSlotRequestParamConverter = new HearingSlotRequestParamConverter();
    private final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter = new MiFilterCriteriaRequestParamConverter();

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
        HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = validator.getHearingSlotsValidation(hearingSlotRequestParam);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, "error");
        }

        JsonObject responseObject = slotsSearchService.search(hearingSlotRequestParam);
        return envelopeFor(envelope, responseObject, "hearingSlots");
    }

    @Handles("courtscheduler.export.court_schedule")
    public JsonEnvelope exportCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);


        List<CourtSchedule> courtSchedules = miService.getCourtSchedules(miFilterCriteria);
        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        JsonObject responseObject = createObjectBuilder()
                .add(COURT_SCHEDULES, listToJsonArrayConverter.convert(courtSchedules))
                .build();
        return envelopeFor(envelope, responseObject, COURT_SCHEDULES);
    }


    @Handles("courtscheduler.export.court_schedule_judiciary")
    public JsonEnvelope exportCourtScheduleJudiciary(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);


        List<CourtScheduleJudiciary> courtScheduleJudiciaries = miService.getCourtSchedulesJudiciary(miFilterCriteria);
        final ListToJsonArrayConverter<CourtScheduleJudiciary> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        JsonObject responseObject = createObjectBuilder()
                .add(COURT_SCHEDULE_JUDICIARIES, listToJsonArrayConverter.convert(courtScheduleJudiciaries))
                .build();
        return envelopeFor(envelope, responseObject, COURT_SCHEDULE_JUDICIARIES);
    }

    @Handles("courtscheduler.export.allocated_listings")
    public JsonEnvelope exportAlloctedListings(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);


        List<AllocatedListing> allocatedListings = miService.getAllocatedListings(miFilterCriteria);
        final ListToJsonArrayConverter<AllocatedListing> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        JsonObject responseObject = createObjectBuilder()
                .add(ALLOCATED_LISTINGS, listToJsonArrayConverter.convert(allocatedListings))
                .build();
        return envelopeFor(envelope, responseObject, ALLOCATED_LISTINGS);
    }


    private JsonEnvelope envelopeFor(final JsonEnvelope originalEnvelope, JsonObject jsonObject, String key) {
        return enveloper.withMetadataFrom(originalEnvelope, originalEnvelope.metadata().name())
                .apply(createObjectBuilder().add(key, jsonObject).build());
    }
}
