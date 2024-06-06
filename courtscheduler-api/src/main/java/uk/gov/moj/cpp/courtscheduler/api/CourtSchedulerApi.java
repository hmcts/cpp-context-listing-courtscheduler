package uk.gov.moj.cpp.courtscheduler.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.converter.*;
import uk.gov.moj.cpp.courtscheduler.domain.*;
import uk.gov.moj.cpp.courtscheduler.service.*;

import javax.inject.Inject;
import javax.json.JsonObject;
import java.util.List;

import static javax.json.Json.createObjectBuilder;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.HEARING_SLOTS;

@CustomServiceComponent("Courtscheduler.API")
public class CourtSchedulerApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(CourtSchedulerApi.class.getName());
    private static final String ALLOCATED_LISTINGS = "allocatedListings";
    private static final String COURT_SCHEDULES = "courtSchedules";
    private static final String SESSIONS = "sessions";
    private static final String COURT_SCHEDULE_JUDICIARIES = "courtScheduleJudiciaries";
    @Inject
    private Enveloper enveloper;
    @Inject
    private SlotsUpdateService slotsUpdateService;
    @Inject
    private SlotsSearchService slotsSearchService;
    @Inject
    private ProvisionalBookingService provisionalBookingService;
    @Inject
    private MiService miService;
    @Inject
    private CourtScheduleService courtScheduleService;

    private final AllocatedSlotConverter converter = new AllocatedSlotConverter();
    private final HearingSlotsApiValidator hearingSlotsApiValidator = new HearingSlotsApiValidator();
    private final CourtScheduleApiValidator courtScheduleApiValidator = new CourtScheduleApiValidator();
    private final HearingSlotRequestParamConverter hearingSlotRequestParamConverter = new HearingSlotRequestParamConverter();
    private final CourtScheduleRequestParamConverter courtScheduleRequestParamConverter = new CourtScheduleRequestParamConverter();
    private final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter = new MiFilterCriteriaRequestParamConverter();
    private final ProvisionalSlotConverter provisionalSlotConverter = new ProvisionalSlotConverter();
    private final ProvisionalBookingApiValidator provisionalBookingApiValidator = new ProvisionalBookingApiValidator();
    private final SessionsConverter sessionsConverter = new SessionsConverter();

    @Handles("courtscheduler.create")
    public JsonEnvelope createCourtSchedule(final JsonEnvelope envelope) {
        return enveloper.withMetadataFrom(envelope, "courtscheduler.create").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.delete")
    public JsonEnvelope deleteCourtSchedule(final JsonEnvelope envelope) {
        SessionsParam sessions = sessionsConverter.convert(envelope.payloadAsJsonObject().toString());

        JsonObject responseObject = courtScheduleService.deleteCourtScheduleSessions(sessions);

        return envelopeFor(envelope, responseObject, SESSIONS);
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
        JsonObject validate = hearingSlotsApiValidator.getHearingSlotsValidation(hearingSlotRequestParam);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = slotsSearchService.search(hearingSlotRequestParam);
        return envelopeFor(envelope, responseObject, HEARING_SLOTS);
    }

    @Handles("courtscheduler.get.court_schedule")
    public JsonEnvelope getCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParamConverter.convert(requestFromApiJsonObject);


        JsonObject validate = courtScheduleApiValidator.getCourtSchedulesValidation(courtScheduleRequestParam);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        List<CourtSchedule> courtSchedules = courtScheduleService.getCourtSchedules(courtScheduleRequestParam);
        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        JsonObject responseObject = createObjectBuilder()
                .add(COURT_SCHEDULES, listToJsonArrayConverter.convert(courtSchedules))
                .build();
        return envelopeFor(envelope, responseObject, COURT_SCHEDULES);
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

    @Handles("courtscheduler.create.provisional.booking")
    public JsonEnvelope createProvisionalBooking(final JsonEnvelope envelope) {
        ProvisionalBookingSlots provisionalBookingSlots = provisionalSlotConverter.convert(envelope.payloadAsJsonObject().toString());
        LOGGER.info("Converted JsonEnvelope with ProvisionalBookingSlots : {}", provisionalBookingSlots);
        JsonObject validate = provisionalBookingApiValidator.createProvisionalBookingValidation(provisionalBookingSlots);

        if(!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = provisionalBookingService.bookProvisionalSlots(provisionalBookingSlots);
        return envelopeFor(envelope, responseObject, ApiConstants.BOOKING_REFERENCE);
    }

    @Handles("courtscheduler.get.provisional.booking")
    public JsonEnvelope getProvisionalBooking(final JsonEnvelope envelope) {
        final String bookingIds = envelope.payloadAsJsonObject().getString(RequestParameterConstant.BOOKING_IDS.getLabel());
        LOGGER.info("BookingIds to retrieve Provisional Booking : {}", bookingIds);
        JsonObject validate = provisionalBookingApiValidator.getProvisionalBookingValidation(bookingIds);

        if(!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = provisionalBookingService.fetchProvisionalSlots(bookingIds);
        return envelopeFor(envelope, responseObject, ApiConstants.BOOKING_REFERENCE);
    }

    private JsonEnvelope envelopeFor(final JsonEnvelope originalEnvelope, JsonObject jsonObject, String key) {
        return enveloper.withMetadataFrom(originalEnvelope, originalEnvelope.metadata().name())
                .apply(createObjectBuilder().add(key, jsonObject).build());
    }
}
