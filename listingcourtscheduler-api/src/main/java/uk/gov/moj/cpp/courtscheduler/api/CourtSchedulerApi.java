package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_.HEARING_ID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.converter.*;
import uk.gov.moj.cpp.courtscheduler.api.service.*;
import uk.gov.moj.cpp.courtscheduler.api.validator.*;
import uk.gov.moj.cpp.courtscheduler.domain.*;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.List;

@CustomServiceComponent("Courtscheduler.API")
public class CourtSchedulerApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(CourtSchedulerApi.class.getName());
    private static final String ALLOCATED_LISTINGS = "allocatedListings";
    private static final String COURT_SCHEDULES = "courtSchedules";
    protected static final String RESULTS = "results";
    private static final String COURT_SCHEDULE_JUDICIARIES = "courtScheduleJudiciaries";
    @Inject
    private Enveloper enveloper;
    @Inject
    private SessionsService sessionsService;
    @Inject
    private Requester requester;
    @Inject
    private SlotsUpdateService slotsUpdateService;
    @Inject
    private SlotsSearchService slotsSearchService;
    @Inject
    private SlotsRemoveService slotsRemoveService;
    @Inject
    private ProvisionalBookingService provisionalBookingService;
    @Inject
    private MiService miService;
    @Inject
    private SessionsApiValidator sessionsApiValidator;
    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Inject
    private AllocatedSlotConverter converter;
    @Inject
    private HearingSlotsApiValidator hearingSlotsApiValidator;
    @Inject
    private CourtScheduleApiValidator courtScheduleApiValidator;
    @Inject
    private HearingSlotRequestParamConverter hearingSlotRequestParamConverter;
    @Inject
    private CourtScheduleRequestParamConverter courtScheduleRequestParamConverter;
    @Inject
    private MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter;
    @Inject
    private ProvisionalSlotConverter provisionalSlotConverter;
    @Inject
    private ProvisionalBookingApiValidator provisionalBookingApiValidator;
    @Inject
    private SessionsConverter sessionsConverter;
    @Inject
    private UpdateCourtScheduleConverter updateCourtScheduleConverter;
    @Inject
    private CreateSessionsRequestParamConverter createSessionsRequestParamConverter;
    @Inject
    private OuCodeMigrateConverter ouCodeMigrateConverter;


    @Handles("courtscheduler.create")
    public JsonEnvelope createCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        CreateSessionRequestParam createSessionRequestParam = createSessionsRequestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        sessionsService.create(createSessionRequestParam, requester);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.create").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.validate.create")
    public JsonEnvelope validateCreateCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        CreateSessionRequestParam createSessionRequestParam = createSessionsRequestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        return enveloper.withMetadataFrom(envelope, "courtscheduler.validate.create").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.delete")
    public JsonEnvelope deleteCourtSchedule(final JsonEnvelope envelope) {
        SessionsParam sessions = sessionsConverter.convert(envelope.payloadAsJsonObject().toString());

        JsonObject responseObject = sessionsService.deleteCourtScheduleSessions(sessions, requester);

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.get.court_schedule")
    public JsonEnvelope getCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParamConverter.convert(requestFromApiJsonObject);

        JsonObject validate = courtScheduleApiValidator.getCourtSchedulesValidation(courtScheduleRequestParam);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        List<CourtSchedule> courtSchedules = sessionsService.getCourtSchedules(courtScheduleRequestParam, requester);

        List<CourtSessionsView> courtSessionsViewList = CourtScheduleToViewConverter.getCourtSessionsViews(courtSchedules);

        return envelopeFor(envelope, new ListToJsonArrayConverter<CourtSessionsView>().convert(courtSessionsViewList), COURT_SCHEDULES);
    }

    @Handles("courtscheduler.update")
    public JsonEnvelope updateCourtSchedule(final JsonEnvelope envelope) {
        UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(envelope.payloadAsJsonObject());
        Result result = sessionsService.update(updateCourtSchedule, requester);
        if (!result.isSuccess()) {
            throw new BadRequestException(result.getMsg());
        }
        JsonObject responseObject = createObjectBuilder()
                .add(RESULTS, objectToJsonObjectConverter.convert(result))
                .build();
        return envelopeFor(envelope, responseObject, RESULTS);
    }

    @Handles("courtscheduler.update.hearing.slots")
    public JsonEnvelope updateHearingSlots(final JsonEnvelope envelope) {
        final String payloadAsJsonString = envelope.payloadAsJsonObject().toString();
        LOGGER.info("CHECK: courtscheduler.update.hearing.slots:{}", payloadAsJsonString);
        List<AllocatedSlot> allocatedSlots = converter.convert(payloadAsJsonString).getHearingSlots();

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
        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.remove.hearing.slots")
    public JsonEnvelope removeHearingSlots(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        final String hearingId = payload.getString(HEARING_ID);

        slotsRemoveService.remove(hearingId);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.remove.hearing.slots").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.export.court_schedule")
    public JsonEnvelope exportCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);


        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> courtSchedules = miService.getCourtSchedules(miFilterCriteria);
        final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(courtSchedules), COURT_SCHEDULES);
    }


    @Handles("courtscheduler.export.court_schedule_judiciary")
    public JsonEnvelope exportCourtScheduleJudiciary(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> courtScheduleJudiciaries = miService.getCourtSchedulesJudiciary(miFilterCriteria);
        final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(courtScheduleJudiciaries), COURT_SCHEDULE_JUDICIARIES);
    }

    @Handles("courtscheduler.export.allocated_listings")
    public JsonEnvelope exportAlloctedListings(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> allocatedListings = miService.getAllocatedListings(miFilterCriteria);
        final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(allocatedListings), ALLOCATED_LISTINGS);
    }

    @Handles("courtscheduler.create.provisional.booking")
    public JsonEnvelope createProvisionalBooking(final JsonEnvelope envelope) {
        ProvisionalBookingSlots provisionalBookingSlots = provisionalSlotConverter.convert(envelope.payloadAsJsonObject().toString());
        LOGGER.info("Converted JsonEnvelope with ProvisionalBookingSlots : {}", provisionalBookingSlots);
        JsonObject validate = provisionalBookingApiValidator.createProvisionalBookingValidation(provisionalBookingSlots);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = provisionalBookingService.bookProvisionalSlots(provisionalBookingSlots);
        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.get.provisional.booking")
    public JsonEnvelope getProvisionalBooking(final JsonEnvelope envelope) {
        final String bookingIds = envelope.payloadAsJsonObject().getString(RequestParameterConstant.BOOKING_IDS.getLabel());
        LOGGER.info("BookingIds to retrieve Provisional Booking : {}", bookingIds);
        JsonObject validate = provisionalBookingApiValidator.getProvisionalBookingValidation(bookingIds);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = provisionalBookingService.fetchProvisionalSlots(bookingIds);
        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.oucode.migrate")
    public JsonEnvelope migrateOuCode(final JsonEnvelope envelope) {

        OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(envelope.payloadAsJsonObject().toString());

        Result result = sessionsService.migrateOuCodes(ouCodeMigrateRequest);

        if (!result.isSuccess()) {
            throw new BadRequestException(result.getMsg());
        }

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(createObjectBuilder().build());
    }

    private JsonEnvelope envelopeFor(final JsonEnvelope originalEnvelope, JsonValue jsonValue, String key) {
        JsonObject build = createObjectBuilder().add(key, jsonValue).build();
        String name = originalEnvelope.metadata().name();
        return enveloper.withMetadataFrom(originalEnvelope, name).apply(build);
    }
}
