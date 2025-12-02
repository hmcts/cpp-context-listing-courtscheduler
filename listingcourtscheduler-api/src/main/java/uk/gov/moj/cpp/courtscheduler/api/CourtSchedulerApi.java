package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.Arrays.stream;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_.HEARING_ID;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_.OUCODE;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignJudiciariesRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleToViewConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CreateSessionsRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.OuCodeRecalculateAvailabilityConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ValidateSessionAvailabilityRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.OrganisationUnitHMIStatusService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.validator.AssignJudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnitHMIStatus;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnitHMIStatusList;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeRecalculateAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SearchCourtSchedulesByIdRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private static final String ORGANISATION_UNIT_HMI_STATUS = "organisationUnitHMIStatus";
    private static final String JUDICIARIES = "judiciaries";
    private static final String SESSIONIDS = "sessionIds";
    private static final String JUDICIARY_ID = "judiciaryId";
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
    private OrganisationUnitHMIStatusService organisationUnitHMIStatusService;
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
    private HearingSlotSearchRequestConverter hearingSlotSearchRequestConverter;
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
    private OuCodeRecalculateAvailabilityConverter ouCodeRecalculateAvailabilityConverter;
    @Inject
    private AllocatedListingService allocatedListingService;
    @Inject
    private ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter;
    @Inject
    private JudiciaryUnassignmentService judiciaryService;
    @Inject
    private JudiciariesApiValidator judiciariesApiValidator;

    @Inject
    private AssignJudiciariesRequestConverter assignJudiciariesRequestConverter;

    @Inject
    private AssignJudiciariesApiValidator assignJudiciariesApiValidator;

    @Inject
    private JudiciaryAssignmentService judiciaryAssignmentService;


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

    @Handles("courtscheduler.validate.session.availability")
    public JsonEnvelope validateSessionAvailabilityCourtSchedule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.validate.session.availability requested : {}", requestFromApiJsonObject);
        ValidateSessionAvailabilityRequestParam validateSessionAvailabilityRequestParam = validateSessionAvailabilityRequestParamConverter.convert(requestFromApiJsonObject);
        JsonObject validate = sessionsApiValidator.getSessionsAvailabilityValidation(validateSessionAvailabilityRequestParam);

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        return enveloper.withMetadataFrom(envelope, "courtscheduler.validate.session.availability").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.assign-judiciary")
    public JsonEnvelope assignJudiciary(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.assign-judiciary requested : {}", payload);

        final AssignJudiciariesRequest requestDto = assignJudiciariesRequestConverter.convert(payload);
        final JsonObject validation = assignJudiciariesApiValidator.validate(requestDto);

        if (!validation.isEmpty()) {
            throw new ValidationException(validation);
        }

        judiciaryAssignmentService.assignJudiciaries(
                requestDto,
                requester,
                envelope.metadata().id().toString());

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(createObjectBuilder().build());
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

    @Handles("courtscheduler.search.court-schedules-by-id")
    public JsonEnvelope searchCourtSchedulesById(final JsonEnvelope envelope) {
        final JsonObject queryParams = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.search.court-schedules-by-id  : {}", queryParams);

        final String idsParam = queryParams.getString("courtScheduleIds", "");
        final List<String> courtScheduleIds = stream(idsParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        final SearchCourtSchedulesByIdRequestParam param =
                SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder
                        .searchCourtSchedulesByIdRequestParamBuilder()
                        .withCourtScheduleIds(courtScheduleIds)
                        .build();

        List<CourtSchedule> courtSchedules = sessionsService.getCourtSchedulesById(param);

        final JsonValue result = new ListToJsonArrayConverter<CourtSchedule>().convert(courtSchedules);

        return enveloper
                .withMetadataFrom(envelope, "courtscheduler.search.court-schedules-by-id")
                .apply(createObjectBuilder().add(COURT_SCHEDULES, result).build());
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

        final JsonObject schedulesJsonObj = slotsUpdateService.update(allocatedSlots);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.update.hearing.slots").apply(schedulesJsonObj);
    }

    @Handles("courtscheduler.list.hearings-in-court-sessions")
    public JsonEnvelope listHearingSlotsInCourtSchedules(final JsonEnvelope envelope) {
        final String payloadAsJsonString = envelope.payloadAsJsonObject().toString();
        LOGGER.info("courtscheduler.list.hearings-in-court-sessions:{}", payloadAsJsonString);
        RequestedSlots requestedSlots = listHearingSlotConverter.convert(payloadAsJsonString);

        JsonObject validate = hearingIdsApiValidator.listHearingSlotsValidation(requestedSlots.getHearingSlots());

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        final ListHearingSlotsResponse listHearingSlotsResponse = slotsUpdateService.listHearingSlots(requestedSlots);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.list.hearings-in-court-sessions.response")
                .apply(objectToJsonObjectConverter.convert(listHearingSlotsResponse));
    }

    @Handles("courtscheduler.search.book.hearing.slots")
    public JsonEnvelope searchBookHearingSlots(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.search.book.hearing.slots requested : {}", requestFromApiJsonObject);
        HearingSlotSearchRequest hearingSlotSearchRequest = hearingSlotSearchRequestConverter.convert(requestFromApiJsonObject);

        final long validateStart = System.nanoTime();
        JsonObject validate = hearingIdsApiValidator.searchAndBookRequestValidation(hearingSlotSearchRequest);
        final long validateEnd = System.nanoTime();

        LOGGER.info("Search Book: Time taken for validation : {}", (validateEnd - validateStart) / 1000000);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        final HearingSlotSearchAndBookResponse hearingSlotSearchAndBookResponse = slotsUpdateService.searchAndBook(hearingSlotSearchRequest);

        JsonObject responseObject =  Json.createObjectBuilder()
                .add(RequestParameterConstant.HEARING_SLOTS.getLabel(),
                        objectToJsonObjectConverter.convert(hearingSlotSearchAndBookResponse))
                .build();

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(responseObject);
    }

    @Handles("courtscheduler.get.hearing.slots")
    public JsonEnvelope getHearingSlots(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.get.hearing.slots requested : {}", requestFromApiJsonObject);
        HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(requestFromApiJsonObject);
        final long validatestart = System.nanoTime();
        JsonObject validate = hearingIdsApiValidator.getHearingSlotsValidation(hearingSlotRequestParam);
        final long validateEnd = System.nanoTime();

        LOGGER.info("PRF: Time taken for validation : {}", (validateEnd - validatestart) / 1000000);

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

    @Handles("courtscheduler.oucode.recalculate.availability")
    public JsonEnvelope ouCodeRecalculateAvailability(final JsonEnvelope envelope) {

        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.oucode.recalculate.availability requested : {}", payload);

        OuCodeRecalculateAvailabilityRequest ouCodeRequest = ouCodeRecalculateAvailabilityConverter.convert(payload.toString());

        Result result = sessionsService.ouCodesRecalculateAvailability(ouCodeRequest);

        if (!result.isSuccess()) {
            throw new BadRequestException(result.getMsg());
        }

        return enveloper.withMetadataFrom(envelope, envelope.metadata().name()).apply(createObjectBuilder().build());
    }

    @Handles("listingcourtscheduler.query.organisation-units-hmi-status")
    public JsonEnvelope getOrganisationUnitsHmiStatus(final JsonEnvelope envelope) {
        final JsonObject reqJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("listingcourtscheduler.query.organisation-units-hmi-status requested : {}", reqJsonObject);

        final OrganisationUnitHMIStatusList organisationUnitHMIStatus = organisationUnitHMIStatusService.getAllOrganisationUnitsHMIStatus();
        final ListToJsonArrayConverter<OrganisationUnitHMIStatus> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return envelopeFor(envelope, listToJsonArrayConverter.convert(organisationUnitHMIStatus.getOrganisationUnitHMIStatus()), ORGANISATION_UNIT_HMI_STATUS);
    }

    @Handles("listingcourtscheduler.query.organisation-unit-hmi-status")
    public JsonEnvelope getOrganisationUnitHmiStatus(final JsonEnvelope envelope) {
        final JsonObject reqJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("listingcourtscheduler.query.organisation-unit-hmi-status requested : {}", reqJsonObject);

        final String oucode = reqJsonObject.getString(OUCODE, "");
        final Optional<OrganisationUnitHMIStatus> orgUnitHMIStatusOpt = organisationUnitHMIStatusService.getOrganisationUnitHMIStatus(oucode);
        final JsonObject resJsonObj = !orgUnitHMIStatusOpt.isEmpty() ? createObjectBuilder().add("oucode", orgUnitHMIStatusOpt.get().getOucode())
                .add("isHMIListingEnabled", orgUnitHMIStatusOpt.get().getIsHMIListingEnabled())
                .add("isHMISchedulingEnabled", orgUnitHMIStatusOpt.get().getIsHMISchedulingEnabled())
                .add("isHMIPubHubEnabled", orgUnitHMIStatusOpt.get().getIsHMIPubHubEnabled())
                .add("courtCentreId", orgUnitHMIStatusOpt.get().getCourtCentreId())
                .add("courtId", orgUnitHMIStatusOpt.get().getCourtId())
                .build()
                : EMPTY_JSON_OBJECT;
        return envelopeFor(envelope, resJsonObj, ORGANISATION_UNIT_HMI_STATUS);
    }

    @Handles("courtscheduler.unassign.judiciary")
    public JsonEnvelope unassignJudiciary(final JsonEnvelope envelope) {
        final JsonObject payload = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.unassign.judiciary requested : {}", payload);

        JsonObject validate = judiciariesApiValidator.validateUnassignJudiciaryRequest(payload);

        if (!validate.isEmpty()) {
            return envelopeFor(envelope, validate, ERROR);
        }

        // Build map of judiciaryId -> List of sessionIds
        final Map<String, List<String>> judiciaryToSessionIds = new HashMap<>();
        final javax.json.JsonArray judiciaries = payload.getJsonArray(JUDICIARIES);
        
        for (int i = 0; i < judiciaries.size(); i++) {
            final JsonObject judiciary = judiciaries.getJsonObject(i);
            final String judiciaryId = judiciary.getString(JUDICIARY_ID, "");
            final javax.json.JsonArray sessionIds = judiciary.getJsonArray(SESSIONIDS);

            final List<String> sessionIdList = new ArrayList<>();
            for (int j = 0; j < sessionIds.size(); j++) {
                final String sessionId = sessionIds.getString(j, "");
                sessionIdList.add(sessionId);
            }
            judiciaryToSessionIds.put(judiciaryId, sessionIdList);
        }

        unassignJudiciaries(judiciaryToSessionIds);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.unassign.judiciary").apply(createObjectBuilder().build());
    }

    private void unassignJudiciaries(Map<String, List<String>> judiciaryToSessionIds) {
        try {
            judiciaryService.unassignJudiciary(judiciaryToSessionIds);
            LOGGER.info("courtscheduler.unassign.judiciary: successfully unassigned judiciaries from sessions");
        } catch (IllegalStateException e) {
            final String errorMessage = e.getMessage();
            LOGGER.warn("courtscheduler.unassign.judiciary: cannot unassign - {}", errorMessage);
            throw new BadRequestException(errorMessage);
        } catch (Exception e) {
            final String errorMessage = e.getMessage();
            LOGGER.warn("courtscheduler.unassign.judiciary: not found - {}", errorMessage);
            throw new BadRequestException(errorMessage);
        }
    }

    private JsonEnvelope envelopeFor(final JsonEnvelope originalEnvelope, JsonValue jsonValue, String key) {
        JsonObject build = createObjectBuilder().add(key, jsonValue).build();
        String name = originalEnvelope.metadata().name();
        return enveloper.withMetadataFrom(originalEnvelope, name).apply(build);
    }
}
