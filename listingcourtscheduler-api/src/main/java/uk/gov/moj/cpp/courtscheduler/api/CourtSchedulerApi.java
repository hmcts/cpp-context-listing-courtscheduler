package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_.HEARING_ID;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.converter.*;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.*;

import java.text.ParseException;
import java.util.List;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private HearingSlotsApiValidator hearingIdsApiValidator;
    @Inject
    private CourtScheduleApiValidator courtScheduleApiValidator;
    @Inject
    private HearingSlotRequestParamConverter hearingSlotRequestParamConverter;
    @Inject
    private ListHearingSlotConverter listHearingSlotConverter;
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
    @Inject
    private AllocatedListingService allocatedListingService;


    @Handles("courtscheduler.create")
    public JsonEnvelope createCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.create requested : {}", requestFromApiJsonObject);
        CreateSessionRequestParam createSessionRequestParam = createSessionsRequestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        sessionsService.create(createSessionRequestParam, requester);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.create").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.validate.create")
    public JsonEnvelope validateCreateCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.validate.create requested : {}", requestFromApiJsonObject);
        CreateSessionRequestParam createSessionRequestParam = createSessionsRequestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam, requester);

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        return enveloper.withMetadataFrom(envelope, "courtscheduler.validate.create").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.delete")
    public JsonEnvelope deleteCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.delete requested : {}", payload);
        SessionsParam sessions = sessionsConverter.convert(envelope.payloadAsJsonObject().toString());

        JsonObject responseObject = sessionsService.deleteCourtScheduleSessions(sessions, requester);

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.get.court_schedule")
    public JsonEnvelope getCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.get.court_schedule requested : {}", requestFromApiJsonObject);

        final CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParamConverter.convert(requestFromApiJsonObject);

        final JsonObject validate = courtScheduleApiValidator.getCourtSchedulesValidation(courtScheduleRequestParam);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        final List<CourtSchedule> courtSchedules = sessionsService.getCourtSchedules(courtScheduleRequestParam, requester);

        final List<CourtSessionsView> courtSessionsViewList = CourtScheduleToViewConverter.getCourtSessionsViews(courtSchedules);

        return envelopeFor(envelope, new ListToJsonArrayConverter<CourtSessionsView>().convert(courtSessionsViewList), COURT_SCHEDULES);
    }

    @Handles("courtscheduler.update")
    public JsonEnvelope updateCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.update requested : {}", payload);
        UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(envelope.payloadAsJsonObject());

        JsonObject validate = sessionsApiValidator.getSessionsUpdateValidation(updateCourtSchedule, requester);

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        Result result = sessionsService.update(updateCourtSchedule, requester);
        if (!result.isSuccess()) {
            throw new ValidationException(createObjectBuilder().add(ERROR_MESSAGE, result.getMsg()).build());
        }
        JsonObject responseObject = createObjectBuilder()
                .add(RESULTS, objectToJsonObjectConverter.convert(result))
                .build();
        return envelopeFor(envelope, responseObject, RESULTS);
    }

    @Handles("courtscheduler.update.hearing.slots")
    public JsonEnvelope updateHearingSlots(final JsonEnvelope envelope) {
        final String payloadAsJsonString = envelope.payloadAsJsonObject().toString();
        LOGGER.info("courtscheduler.update.hearing.slots:{}", payloadAsJsonString);
        List<AllocatedSlot> allocatedSlots = converter.convert(payloadAsJsonString).getHearingSlots();

        slotsUpdateService.update(allocatedSlots);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.update.hearing.slots").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.search.list.hearings-in-court-schedules")
    public JsonEnvelope searchListHearingSlotsInCourtSchedules(final JsonEnvelope envelope) {
        final String payloadAsJsonString = envelope.payloadAsJsonObject().toString();
        LOGGER.info("courtscheduler.search.list.hearing-in-court-schedules:{}", payloadAsJsonString);
        HearingSlotWrapper hearingSlotWrapper = listHearingSlotConverter.convert(payloadAsJsonString);

        JsonObject validate = hearingIdsApiValidator.listHearingSlotsValidation(hearingSlotWrapper.getHearingSlots());


        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        final ListHearingSlotsResponse listHearingSlotsResponse = slotsUpdateService.updateSearchListHearingSlots(hearingSlotWrapper);

        JsonObject responseObject =  Json.createObjectBuilder()
                .add(RequestParameterConstant.HEARINGS.getLabel(),
                        objectToJsonObjectConverter.convert(listHearingSlotsResponse))
                .build();

        return enveloper.withMetadataFrom(envelope, "courtscheduler.search.list.hearings-in-court-schedules.response").apply(responseObject);
    }

    @Handles("courtscheduler.search.update.hearing.slots")
    public JsonEnvelope searchUpdateHearingSlots(final JsonEnvelope envelope) {
        final String payloadAsJsonString = envelope.payloadAsJsonObject().toString();
        LOGGER.info("courtscheduler.search.update.hearing.slots:{}", payloadAsJsonString);
        List<AllocatedSlot> allocatedSlots = converter.convert(payloadAsJsonString).getHearingSlots();

        Result result = slotsUpdateService.searchUpdate(allocatedSlots);

        JsonObject responseObject = createObjectBuilder()
                .add(RESULTS, objectToJsonObjectConverter.convert(result))
                .build();

        return enveloper.withMetadataFrom(envelope, "courtscheduler.search.update.hearing.slots").apply(responseObject);
    }

    @Handles("courtscheduler.get.hearing.slots")
    public JsonEnvelope getHearingSlots(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.get.hearing.slots requested : {}", requestFromApiJsonObject);
        HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(requestFromApiJsonObject);
        final long validatestart = System.nanoTime();
        JsonObject validate = hearingIdsApiValidator.getHearingSlotsValidation(hearingSlotRequestParam);
        final long validateEnd = System.nanoTime();

        LOGGER.info("BRS: Time taken for validation : {}", (validateEnd - validatestart) / 1000000);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        final JsonObject responseObject = slotsSearchService.search(hearingSlotRequestParam);
        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.remove.hearing.slots")
    public JsonEnvelope removeHearingSlots(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.remove.hearing.slots requested  : {}", payload);

        final String hearingId = payload.getString(HEARING_ID);

        slotsRemoveService.remove(hearingId);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.remove.hearing.slots").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.get.hearing.ids")
    public JsonEnvelope getHearingIds(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.get.hearing.ids requested : {}", requestFromApiJsonObject);
        HearingSlotRequestParam hearingIdsRequest = hearingSlotRequestParamConverter.convert(requestFromApiJsonObject);
        final long validateStart = System.nanoTime();
        JsonObject validate = hearingIdsApiValidator.getHearingSlotsValidation(hearingIdsRequest);
        final long validateEnd = System.nanoTime();

        LOGGER.info("Time taken for allocated hearing ids validation : {}", (validateEnd - validateStart) / 1000000);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = allocatedListingService.getHearingIds(hearingIdsRequest);

        LOGGER.info("courtscheduler.get.hearing.ids returned : {}", responseObject);

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.export.court_schedule")
    public JsonEnvelope exportCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.export.court_schedule requested : {}", requestFromApiJsonObject);

        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);


        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> courtSchedules = miService.getCourtSchedules(miFilterCriteria);
        final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(courtSchedules), COURT_SCHEDULES);
    }


    @Handles("courtscheduler.export.court_schedule_judiciary")
    public JsonEnvelope exportCourtScheduleJudiciary(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.export.court_schedule_judiciary requested : {}", requestFromApiJsonObject);

        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> courtScheduleJudiciaries = miService.getCourtSchedulesJudiciary(miFilterCriteria);
        final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(courtScheduleJudiciaries), COURT_SCHEDULE_JUDICIARIES);
    }

    @Handles("courtscheduler.export.allocated_listings")
    public JsonEnvelope exportAlloctedListings(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.export.allocated_listings requested : {}", requestFromApiJsonObject);

        MiFilterCriteria miFilterCriteria = miFilterCriteriaRequestParamConverter.convert(requestFromApiJsonObject);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> allocatedListings = miService.getAllocatedListings(miFilterCriteria);
        final ListToJsonArrayConverter<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(allocatedListings), ALLOCATED_LISTINGS);
    }

    @Handles("courtscheduler.create.provisional.booking")
    public JsonEnvelope createProvisionalBooking(final JsonEnvelope envelope) {
        ProvisionalBookingSlots provisionalBookingSlots = provisionalSlotConverter.convert(envelope.payloadAsJsonObject().toString());
        LOGGER.info("courtscheduler.create.provisional.booking : {}", provisionalBookingSlots);
        JsonObject validate = provisionalBookingApiValidator.createProvisionalBookingValidation(provisionalBookingSlots);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = provisionalBookingService.bookProvisionalSlots(provisionalBookingSlots);
        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.get.provisional.booking")
    public JsonEnvelope getProvisionalBooking(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        final String bookingIds = payload.getString(RequestParameterConstant.BOOKING_IDS.getLabel());
        LOGGER.info("courtscheduler.get.provisional.booking requested : {}", payload);
        JsonObject validate = provisionalBookingApiValidator.getProvisionalBookingValidation(bookingIds);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        JsonObject responseObject = provisionalBookingService.fetchProvisionalSlots(bookingIds);
        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.oucode.migrate")
    public JsonEnvelope migrateOuCode(final JsonEnvelope envelope) {

        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.oucode.migrate requested : {}", payload);

        OuCodeMigrateRequest ouCodeMigrateRequest = ouCodeMigrateConverter.convert(payload.toString());

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
