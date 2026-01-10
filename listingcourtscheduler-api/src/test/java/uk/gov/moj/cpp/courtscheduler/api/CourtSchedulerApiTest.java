package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.CourtSchedulerApi.RESULTS;
import static uk.gov.moj.cpp.courtscheduler.api.service.OrganisationUnitHMIStatusService.getJsonObject;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.payloadToObject;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_.HEARING_ID;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CreateSessionsRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AddJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.DeleteJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.OuCodeMigrateConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ValidateSessionAvailabilityRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.OrganisationUnitHMIStatusService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciaryAvailabilityRuleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnitHMIStatus;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnitHMIStatusList;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private SlotsRemoveService slotsRemoveService;
    @Mock
    private SessionsService sessionsService;
    @Mock
    private SlotsSearchService slotsSearchService;
    @Mock
    private AllocatedListingService allocatedListingService;
    @Mock
    private Requester requester;
    @Mock
    private CreateSessionsRequestParamConverter createSessionsRequestParamConverter;
    @Mock
    private ProvisionalBookingApiValidator provisionalBookingApiValidator;
    @Mock
    private ProvisionalBookingService provisionalBookingService;
    @Mock
    private MiService miService;
    @Mock
    private MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter;
    @Mock
    private CourtScheduleRequestParamConverter courtScheduleRequestParamConverter;
    @Mock
    private SessionsConverter sessionsConverter;
    @Mock
    private AllocatedSlotConverter allocatedSlotConverter;
    @Mock
    private HearingSlotRequestParamConverter hearingSlotRequestParamConverter;
    @Mock
    private HearingSlotSearchRequestConverter hearingSlotSearchRequestConverter;
    @Mock
    private HearingSlotsApiValidator hearingSlotsApiValidator;
    @Mock
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;
    @Mock
    private ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter;

    @Mock
    private Function<Object, JsonEnvelope> function;
    @Mock
    private SessionsApiValidator sessionsApiValidator;
    @Mock
    private OrganisationUnitHMIStatusService organisationUnitHMIStatusService;
    @InjectMocks
    private CourtSchedulerApi courtSchedulerApi;
    @Mock
    private JsonObject payload;
    @Mock
    private ProvisionalSlotConverter provisionalSlotConverter;
    @Mock
    private UpdateCourtScheduleConverter updateCourtScheduleConverter;
    @Mock
    private CourtScheduleApiValidator courtScheduleApiValidator;
    @Mock
    private OuCodeMigrateConverter ouCodeMigrateConverter;
    @Mock
    private JsonEnvelope envelope;
    @Mock
    private ListHearingSlotConverter listHearingSlotConverter;
    @Mock
    private AddJudiciaryAvailabilityRuleConverter addJudiciaryAvailabilityRuleConverter;
    @Mock
    private DeleteJudiciaryAvailabilityRuleConverter deleteJudiciaryAvailabilityRuleConverter;
    @Mock
    private UpdateJudiciaryAvailabilityRuleConverter updateJudiciaryAvailabilityRuleConverter;
    @Mock
    private FindJudiciaryAvailabilityConverter findJudiciaryAvailabilityConverter;
    @Mock
    private FindJudiciaryAvailabilityRuleConverter findJudiciaryAvailabilityRuleConverter;
    @Mock
    private FindJudiciaryAvailabilityRuleResponseConverter findJudiciaryAvailabilityRuleResponseConverter;
    @Mock
    private GetJudiciaryAvailabilityRuleConverter getJudiciaryAvailabilityRuleConverter;
    @Mock
    private GetJudiciaryAvailabilityRuleResponseConverter getJudiciaryAvailabilityRuleResponseConverter;
    @Mock
    private JudiciaryAvailabilityService judiciaryAvailabilityService;
    @Mock
    private JudiciaryAvailabilityRuleApiValidator judiciaryAvailabilityRuleApiValidator;


    @Test
    void shouldCreateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(getPayload("create-court-schedule.json"));
        final String requestName = "courtscheduler.create";

        final JsonEnvelope createCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        when(this.enveloper.withMetadataFrom(createCourtScheduleJsonEnvelope, requestName)).thenReturn(function);
        when(sessionsApiValidator.getSessionsCreateValidation(any(), any(Requester.class))).thenReturn(EMPTY_JSON_OBJECT);
        courtSchedulerApi.createCourtSchedule(createCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(createCourtScheduleJsonEnvelope, requestName);
        verify(sessionsService, atLeastOnce()).create(any(), any());
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() throws IOException {
        // Arrange
        final JsonObject jsonPayloadObject = payloadToObject(getPayload("create-court-schedule.json"));
        final String requestName = "courtscheduler.create";
        final JsonEnvelope createCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Invalid parameters")
                .build();

        when(createSessionsRequestParamConverter.convert(any())).thenReturn(new CreateSessionRequestParam(Collections.emptyList(), null, null));

        when(sessionsApiValidator.getSessionsCreateValidation(any(CreateSessionRequestParam.class), any(Requester.class))).thenReturn(validationError);

        try {
            // Act
            courtSchedulerApi.createCourtSchedule(createCourtScheduleJsonEnvelope);
        } catch (ValidationException e) {
            // Assert
            assertEquals("Invalid parameters", e.getMessage());
            assertEquals(e.getMessage(), validationError.getString("errorMessage"));
        }
    }

    @Test
    void shouldDeleteCourtSchedule() throws IOException {
        String payload = getPayload("delete-courtscheduler-sessions.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.delete";

        final JsonEnvelope deleteCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(deleteCourtScheduleJsonEnvelope, requestName)).thenReturn(function);
        when(sessionsConverter.convert(anyString())).thenReturn(new SessionsConverter().convert(payload));

        courtSchedulerApi.deleteCourtSchedule(deleteCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(deleteCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void shouldUpdateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(getPayload("update-court-schedule.json"));
        final String requestName = "courtscheduler.update.court_schedule";

        final JsonEnvelope updateCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        when(enveloper.withMetadataFrom(updateCourtScheduleJsonEnvelope, requestName)).thenReturn(function);
        Result success = Result.SUCCESS();
        when(sessionsService.update(any(), eq(requester))).thenReturn(success);
        when(objectToJsonObjectConverter.convert(success)).thenReturn(createObjectBuilder()
                .add(RESULTS, "ok")
                .build());
        when(sessionsApiValidator.getSessionsUpdateValidation(any(), any(Requester.class))).thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.updateCourtSchedule(updateCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(updateCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void updateCourtSchedule_ShouldReturnError_CourtScheduleIdNotFound() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(getPayload("update-court-schedule.json"));
        final String requestName = "courtscheduler.update.court_schedule";

        final JsonEnvelope updateCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        Result failure = new Result("Court Schedule not found", false);
        when(sessionsService.update(any(), eq(requester))).thenReturn(failure);
        when(sessionsApiValidator.getSessionsUpdateValidation(any(), any(Requester.class))).thenReturn(EMPTY_JSON_OBJECT);

        BadRequestException badRequestException = assertThrows(BadRequestException.class, () -> courtSchedulerApi.updateCourtSchedule(updateCourtScheduleJsonEnvelope));

        assertTrue("{\"errorMessage\":\"Court Schedule not found\"}".contains(badRequestException.getMessage()));
    }

    @Test
    void shouldGetCourtSchedules() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("get-court-schedule.json"));
        final String requestName = "courtscheduler.get.court_schedule";
        final JsonEnvelope exportCourtScheduleEnvelope = createEnvelope(requestName, jsonObject);
        when(enveloper.withMetadataFrom(exportCourtScheduleEnvelope, requestName)).thenReturn(function);

        List<CourtSchedule> courtSchedules = Lists.newArrayList();
        when(sessionsService.getCourtSchedules(any(), any())).thenReturn(courtSchedules);
        when(courtScheduleApiValidator.getCourtSchedulesValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(courtScheduleRequestParamConverter.convert(any())).thenReturn(new CourtScheduleRequestParamConverter().convert(jsonObject));

        courtSchedulerApi.getCourtSchedule(exportCourtScheduleEnvelope);

        verify(sessionsService, atLeastOnce()).getCourtSchedules(any(), any());
        verify(enveloper, atLeastOnce()).withMetadataFrom(exportCourtScheduleEnvelope, requestName);
    }

    @Test
    void shouldUpdateHearingSlots() throws IOException {
        String payload = getPayload("courtscheduler.update.hearing.slots.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.update.hearing.slots";

        final JsonEnvelope updateHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);

        String hearingDateJsonName = "hearingDate";
        String courtScheduleIdJsonName = "courtScheduleId";
        JsonObject schedule1 = createObjectBuilder().add(courtScheduleIdJsonName, "Court-Schedule-Id1")
                                                    .add(hearingDateJsonName, "2025-04-25")
                                                    .build();
        JsonObject schedule2 = createObjectBuilder().add(courtScheduleIdJsonName, "Court-Schedule-Id2")
                                                    .add(hearingDateJsonName, "2025-04-26")
                                                    .build();
        JsonArrayBuilder schedulesJsonArrayBuilder = createArrayBuilder().add(schedule1).add(schedule2);
        when(slotsUpdateService.update(any())).thenReturn(createObjectBuilder().add("schedules", schedulesJsonArrayBuilder).build());
        when(enveloper.withMetadataFrom(updateHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(allocatedSlotConverter.convert(jsonObject.toString())).thenReturn(new AllocatedSlotConverter().convert(payload));

        courtSchedulerApi.updateHearingSlots(updateHearingSlotsEnvelope);
        ArgumentCaptor<JsonObject> hearingDaysArgCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(function).apply(hearingDaysArgCaptor.capture());
        JsonArray schedulesResponseJsonArr = hearingDaysArgCaptor.getValue().getJsonArray("schedules");
        JsonObject scheduleRespJsonObj1 = schedulesResponseJsonArr.getJsonObject(0);
        JsonObject scheduleRespJsonObj2 = schedulesResponseJsonArr.getJsonObject(1);
        assertEquals("Court-Schedule-Id1", scheduleRespJsonObj1.getString(courtScheduleIdJsonName));
        assertEquals("2025-04-25", scheduleRespJsonObj1.getString(hearingDateJsonName));
        assertEquals("Court-Schedule-Id2", scheduleRespJsonObj2.getString(courtScheduleIdJsonName));
        assertEquals("2025-04-26", scheduleRespJsonObj2.getString(hearingDateJsonName));
        verify(slotsUpdateService, atLeastOnce()).update(new AllocatedSlotConverter().convert(payload).getHearingSlots());
        verify(enveloper, atLeastOnce()).withMetadataFrom(updateHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldUpdateRequestedListHearingSlots() throws IOException {
        final String payload = FileUtil.getPayload("courtscheduler.list.hearings-in-court-sessions.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.list.hearings-in-court-sessions";
        final String responseName = "courtscheduler.list.hearings-in-court-sessions.response";

        final JsonEnvelope updateRequestedListHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);
        when(enveloper.withMetadataFrom(updateRequestedListHearingSlotsEnvelope, responseName)).thenReturn(function);

        final RequestedSlots wrapper = new RequestedSlots();
        wrapper.setHearingSlots(Collections.emptyList());
        when(listHearingSlotConverter.convert(anyString())).thenReturn(wrapper);

        when(hearingSlotsApiValidator.listHearingSlotsValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(slotsUpdateService.listHearingSlots(any())).thenReturn(new ListHearingSlotsResponse());

        JsonObject jsonResponse = Json.createObjectBuilder().add("any", "any").build();
        when(objectToJsonObjectConverter.convert(any())).thenReturn(jsonResponse);

        courtSchedulerApi.listHearingSlotsInCourtSchedules(updateRequestedListHearingSlotsEnvelope);

        verify(slotsUpdateService, atLeastOnce()).listHearingSlots(wrapper);
        verify(enveloper, atLeastOnce()).withMetadataFrom(updateRequestedListHearingSlotsEnvelope, responseName);
    }

    @Test
    void shouldRetrieveHearingSlots() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("courtscheduler.get.hearing.slots.json"));
        final String requestName = "courtscheduler.get.hearing.slots";
        final JsonEnvelope getHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(getHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(hearingSlotRequestParamConverter.convert(jsonObject)).thenReturn(new HearingSlotRequestParamConverter().convert(jsonObject));
        when(slotsSearchService.search(hearingSlotRequestParamConverter.convert(jsonObject))).thenReturn(EMPTY_JSON_OBJECT);
        when(hearingSlotsApiValidator.getHearingSlotsValidation(any())).thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.getHearingSlots(getHearingSlotsEnvelope);

        verify(slotsSearchService, atLeastOnce()).search(hearingSlotRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(getHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldRetrieveHearingIds() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("courtscheduler.get.hearing.slots.json"));
        final String requestName = "courtscheduler.get.hearing.ids";
        final JsonEnvelope hearingIdsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(hearingIdsEnvelope, requestName)).thenReturn(function);
        when(hearingSlotRequestParamConverter.convert(jsonObject)).thenReturn(new HearingSlotRequestParamConverter().convert(jsonObject));
        when(allocatedListingService.getHearingIds(hearingSlotRequestParamConverter.convert(jsonObject))).thenReturn(EMPTY_JSON_OBJECT);
        when(hearingSlotsApiValidator.getHearingSlotsValidation(any())).thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.getHearingIds(hearingIdsEnvelope);

        verify(allocatedListingService, atLeastOnce()).getHearingIds(hearingSlotRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(hearingIdsEnvelope, requestName);
    }

    @Test
    void shouldRetrieveOrganisationUnitsHmiStatus() {
        JsonObject jsonObj = createObjectBuilder().build();
        String requestName = "listingcourtscheduler.query.organisation-units-hmi-status";
        JsonEnvelope orgUnitsHmiStatusEnvelope = createEnvelope(requestName, jsonObj);

        List<OrganisationUnitHMIStatus> statusList = new ArrayList<>();
        String payload = getPayload("test-data/listingcourtscheduler.query.organisation-units-hmi-status.json");
        JsonObject payloadJsonObj = getJsonObject(payload);
        JsonArray jsonArr = payloadJsonObj.getJsonArray("organisationUnitHMIStatus");
        JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new ObjectMapperProducer().objectMapper());
        for (int i = 0; i < jsonArr.size(); i++) {
            final JsonObject json = jsonArr.getJsonObject(i);
            final OrganisationUnitHMIStatus organisationUnitHmiStatus = jsonObjectToObjectConverter.convert(json, OrganisationUnitHMIStatus.class);
            statusList.add(organisationUnitHmiStatus);
        }
        when(organisationUnitHMIStatusService.getAllOrganisationUnitsHMIStatus()).thenReturn(new OrganisationUnitHMIStatusList(statusList));
        when(enveloper.withMetadataFrom(orgUnitsHmiStatusEnvelope, requestName)).thenReturn(function);
        courtSchedulerApi.getOrganisationUnitsHmiStatus(orgUnitsHmiStatusEnvelope);

        ArgumentCaptor<JsonObject> orgUnitsHmiStatusRespArgCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(function).apply(orgUnitsHmiStatusRespArgCaptor.capture());

        JsonObject respJsonObj = orgUnitsHmiStatusRespArgCaptor.getValue();
        JsonArray orgUnitsHMIStatusJsonArr = respJsonObj.getJsonArray("organisationUnitHMIStatus");
        assertEquals(3, orgUnitsHMIStatusJsonArr.size());
        assertEquals("A01AF00", orgUnitsHMIStatusJsonArr.getJsonObject(0).getString("oucode"));
        assertEquals("42f44290-c183-3cab-9fbe-e22fc25a5fe4", orgUnitsHMIStatusJsonArr.getJsonObject(0).getString("courtCentreId"));

        assertEquals("A01BE00", orgUnitsHMIStatusJsonArr.getJsonObject(1).getString("oucode"));
        assertEquals("5907faec-be0c-37dc-8513-4685b74ae9ae", orgUnitsHMIStatusJsonArr.getJsonObject(1).getString("courtCentreId"));

        assertEquals("A01CT00", orgUnitsHMIStatusJsonArr.getJsonObject(2).getString("oucode"));
        assertEquals("05cf1c11-c18d-3b05-ae56-0c8f6d7264bf", orgUnitsHMIStatusJsonArr.getJsonObject(2).getString("courtCentreId"));
    }

    @Test
    void shouldRetrieveOrganisationUnitHmiStatusByOucode() {
        String oucode = "A01AF00";
        JsonObject jsonObj = createObjectBuilder().add("oucode", oucode).build();
        String requestName = "listingcourtscheduler.query.organisation-unit-hmi-status";
        JsonEnvelope orgUnitsHmiStatusEnvelope = createEnvelope(requestName, jsonObj);
        UUID courtCentreId = randomUUID();
        OrganisationUnitHMIStatus orgUnitHMIStatus = new OrganisationUnitHMIStatus.Builder()
                .withOucode(oucode)
                .withIsHMIListingEnabled(true)
                .withIsHMISchedulingEnabled(true)
                .withIsHMIPubHubEnabled(true)
                .withUpdatedOn(new Timestamp(System.currentTimeMillis()))
                .withCourtCentreId(courtCentreId.toString())
                .withCourtId("Court-1").build();

        when(organisationUnitHMIStatusService.getOrganisationUnitHMIStatus(anyString())).thenReturn(of(orgUnitHMIStatus));
        when(enveloper.withMetadataFrom(orgUnitsHmiStatusEnvelope, requestName)).thenReturn(function);
        courtSchedulerApi.getOrganisationUnitHmiStatus(orgUnitsHmiStatusEnvelope);
        ArgumentCaptor<JsonObject> orgUnitsHmiStatusRespArgCaptor = ArgumentCaptor.forClass(JsonObject.class);
        verify(function).apply(orgUnitsHmiStatusRespArgCaptor.capture());

        JsonObject respJsonObj = orgUnitsHmiStatusRespArgCaptor.getValue().getJsonObject("organisationUnitHMIStatus");
        assertEquals(oucode, respJsonObj.getString("oucode"));
        assertEquals(courtCentreId.toString(), respJsonObj.getString("courtCentreId"));
    }

    @Test
    void shouldRemoveHearingSlots() {
        final String hearingId = randomUUID().toString();
        final String requestName = "courtscheduler.remove.hearing.slots";

        given(envelope.payloadAsJsonObject()).willReturn(payload);
        when(enveloper.withMetadataFrom(envelope, requestName)).thenReturn(function);
        when(payload.getString(HEARING_ID)).thenReturn(hearingId);

        courtSchedulerApi.removeHearingSlots(envelope);

        verify(slotsRemoveService, atLeastOnce()).remove(hearingId);
        verify(enveloper, atLeastOnce()).withMetadataFrom(envelope, requestName);
    }

    @Test
    void shouldSearchListHearingSlots() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.search.book.hearing.slots.json"));
        final String requestName = "courtscheduler.search.book.hearing.slots";
        final JsonEnvelope getSearchListHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);
        final HearingSlotSearchAndBookResponse hearingSlotSearchAndBookResponse = createHearingSlotsResponse("432c067d-eaca-4ce5-ad90-a366ef3e4bb6");

        when(enveloper.withMetadataFrom(getSearchListHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(hearingSlotSearchRequestConverter.convert(jsonObject)).thenReturn(new HearingSlotSearchRequestConverter().convert(jsonObject));
        when(slotsUpdateService.searchAndBook(hearingSlotSearchRequestConverter.convert(jsonObject))).thenReturn(hearingSlotSearchAndBookResponse);
        when(hearingSlotsApiValidator.searchAndBookRequestValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(objectToJsonObjectConverter.convert(hearingSlotSearchAndBookResponse)).thenReturn(createObjectBuilder()
                .add(RequestParameterConstant.HEARING_SLOTS.getLabel(), "ok")
                .build());

        courtSchedulerApi.searchBookHearingSlots(getSearchListHearingSlotsEnvelope);

        verify(slotsUpdateService, atLeastOnce()).searchAndBook(hearingSlotSearchRequestConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(getSearchListHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldExportCourtSchedules() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("courtscheduler.export.court_schedule.json"));
        final String requestName = "courtscheduler.export.court_schedule.json";
        final JsonEnvelope exportCourtScheduleEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(exportCourtScheduleEnvelope, requestName)).thenReturn(function);
        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> courtSchedules = Lists.newArrayList();
        when(miService.getCourtSchedules(miFilterCriteriaRequestParamConverter.convert(jsonObject))).thenReturn(courtSchedules);

        courtSchedulerApi.exportCourtSchedule(exportCourtScheduleEnvelope);

        verify(miService, atLeastOnce()).getCourtSchedules(miFilterCriteriaRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(exportCourtScheduleEnvelope, requestName);
    }

    @Test
    void shouldCourtScheduleJudiciaries() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("courtscheduler.export.court_schedule_judiciary.json"));
        final String requestName = "courtscheduler.export.court_schedule_judiciary";
        final JsonEnvelope exportAllocatedListingsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(exportAllocatedListingsEnvelope, requestName)).thenReturn(function);
        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> courtScheduleJudiciaries = Lists.newArrayList();
        when(miService.getCourtSchedulesJudiciary(miFilterCriteriaRequestParamConverter.convert(jsonObject))).thenReturn(courtScheduleJudiciaries);

        courtSchedulerApi.exportCourtScheduleJudiciary(exportAllocatedListingsEnvelope);

        verify(miService, atLeastOnce()).getCourtSchedulesJudiciary(miFilterCriteriaRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(exportAllocatedListingsEnvelope, requestName);
    }

    @Test
    void shouldExportAllocatedListings() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("courtscheduler.export.allocated_listings.json"));
        final String requestName = "courtscheduler.export.allocated_listings";
        final JsonEnvelope exportAllocatedListingsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(exportAllocatedListingsEnvelope, requestName)).thenReturn(function);
        when(miService.getAllocatedListings(miFilterCriteriaRequestParamConverter.convert(jsonObject))).thenReturn(Collections.emptyList());

        courtSchedulerApi.exportAllocatedListings(exportAllocatedListingsEnvelope);

        verify(miService, atLeastOnce()).getAllocatedListings(miFilterCriteriaRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(exportAllocatedListingsEnvelope, requestName);
    }

    @Test
    void shouldCreateProvisionalBooking() throws IOException {
        String payload = getPayload("create.provisional.booking.json");
        final String requestName = "courtscheduler.create.provisional.booking";
        final JsonEnvelope createCourtScheduleJsonEnvelope = createEnvelope(requestName, payloadToObject(payload));

        when(enveloper.withMetadataFrom(createCourtScheduleJsonEnvelope, requestName)).thenReturn(function);
        when(provisionalBookingService.bookProvisionalSlots(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(provisionalBookingApiValidator.createProvisionalBookingValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(provisionalSlotConverter.convert(any())).thenReturn(new ProvisionalBookingSlots());

        courtSchedulerApi.createProvisionalBooking(createCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(createCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void shouldRetrieveProvisionalBookingSlots() throws IOException {
        final JsonObject jsonObject = payloadToObject(getPayload("courtscheduler.get.provisional.booking.json"));
        final String requestName = "courtscheduler.get.provisional.booking";
        final JsonEnvelope getProvisionalBookingEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(getProvisionalBookingEnvelope, requestName)).thenReturn(function);
        when(provisionalBookingService.fetchProvisionalSlots(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(provisionalBookingApiValidator.getProvisionalBookingValidation(any())).thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.getProvisionalBooking(getProvisionalBookingEnvelope);

        verify(provisionalBookingService, atLeastOnce()).fetchProvisionalSlots(anyString());
        verify(enveloper, atLeastOnce()).withMetadataFrom(getProvisionalBookingEnvelope, requestName);
    }

    @Test
    void shouldMigrateOuCodes() throws IOException {
        String payload = getPayload("oucode-migrate-courtscheduler.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.oucode.migrate";

        final JsonEnvelope migrateOuCodeEnvelope = createEnvelope(requestName, jsonObject);
        when(ouCodeMigrateConverter.convert(anyString())).thenReturn(new OuCodeMigrateRequest());
        when(enveloper.withMetadataFrom(migrateOuCodeEnvelope, requestName)).thenReturn(function);
        when(sessionsService.migrateOuCodes(any())).thenReturn(Result.SUCCESS());
        courtSchedulerApi.migrateOuCode(migrateOuCodeEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(migrateOuCodeEnvelope, requestName);
    }

    @Test
    void shouldReturnFailureWhenValidationFails() throws IOException {
        String payload = getPayload("courtscheduler.validate.create.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.validate.create";

        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonObject);
        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "Validation Failed")
                .build();

        when(sessionsApiValidator.getSessionsCreateValidation(any(), any(Requester.class))).thenReturn(validationResult);

        //verify it returns bad request with error message
        assertThrows(ValidationException.class, () -> courtSchedulerApi.validateCreateCourtSchedule(validationEnvelope));
    }

    @Test
    void shouldReturnFailureWhenSessionAvailabilityValidationFails() throws IOException {
        // Load JSON payload for session availability validation
        String payload = FileUtil.getPayload("courtscheduler.validate.session.availability-id-not-found.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.validate.session.availability";

        // Create a JsonEnvelope using the request payload
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonObject);

        // Define expected validation failure response
        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "Court Schedule Ids not found")
                .build();

        // Mock the behavior of request param converter
        ValidateSessionAvailabilityRequestParam convertedParam = mock(ValidateSessionAvailabilityRequestParam.class);
        when(validateSessionAvailabilityRequestParamConverter.convert(any())).thenReturn(convertedParam);

        // Mock the validator to return an error response
        when(sessionsApiValidator.getSessionsAvailabilityValidation(any())).thenReturn(validationResult);

        // Verify that the method throws a ValidationException when an error occurs
        assertThrows(ValidationException.class, () ->
                courtSchedulerApi.validateSessionAvailabilityCourtSchedule(validationEnvelope)
        );
    }

    @Test
    void shouldThrowValidationExceptionWhenCourtScheduleIdsAreEmpty() throws IOException {
        // Given a request with empty court schedule IDs
        String payload = FileUtil.getPayload("courtscheduler.validate.session.availability-empty.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final JsonEnvelope validationEnvelope = createEnvelope("courtscheduler.validate.session.availability", jsonObject);

        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "Court Schedule Ids cannot be empty")
                .build();

        when(validateSessionAvailabilityRequestParamConverter.convert(any())).thenReturn(mock(ValidateSessionAvailabilityRequestParam.class));
        when(sessionsApiValidator.getSessionsAvailabilityValidation(any())).thenReturn(validationResult);

        assertThrows(ValidationException.class, () ->
                courtSchedulerApi.validateSessionAvailabilityCourtSchedule(validationEnvelope));
    }

    @Test
    void shouldThrowValidationExceptionWhenSchedulesAreMixedSlotAndDurationBased() throws IOException {
        // Given a request with mixed slot-based and duration-based schedules
        String payload = FileUtil.getPayload("courtscheduler.validate.session.availability-mixed.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final JsonEnvelope validationEnvelope = createEnvelope("courtscheduler.validate.session.availability", jsonObject);

        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "All court schedules should be either slot-based or duration-based")
                .build();

        when(validateSessionAvailabilityRequestParamConverter.convert(any())).thenReturn(mock(ValidateSessionAvailabilityRequestParam.class));
        when(sessionsApiValidator.getSessionsAvailabilityValidation(any())).thenReturn(validationResult);

        assertThrows(ValidationException.class, () ->
                courtSchedulerApi.validateSessionAvailabilityCourtSchedule(validationEnvelope));
    }

    @Test
    void shouldThrowValidationExceptionWhenSlotBasedScheduleIsFullyBooked() throws IOException {
        // Given a request where slot-based court schedules are fully booked
        String payload = FileUtil.getPayload("courtscheduler.validate.session.availability-fullybooked.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final JsonEnvelope validationEnvelope = createEnvelope("courtscheduler.validate.session.availability", jsonObject);

        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "Court Schedule Id: 12345678-90ab-cdef-0123-456789abcdef is fully booked")
                .build();

        when(validateSessionAvailabilityRequestParamConverter.convert(any())).thenReturn(mock(ValidateSessionAvailabilityRequestParam.class));
        when(sessionsApiValidator.getSessionsAvailabilityValidation(any())).thenReturn(validationResult);

        assertThrows(ValidationException.class, () ->
                courtSchedulerApi.validateSessionAvailabilityCourtSchedule(validationEnvelope));
    }

    @Test
    void shouldThrowValidationExceptionWhenDurationBasedScheduleHasInsufficientAvailability() throws IOException {
        // Given a request where duration-based schedules do not have enough available time
        String payload = FileUtil.getPayload("courtscheduler.validate.session.availability-insufficient-duration.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final JsonEnvelope validationEnvelope = createEnvelope("courtscheduler.validate.session.availability", jsonObject);

        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "Not enough available durations for all court schedules")
                .build();

        when(validateSessionAvailabilityRequestParamConverter.convert(any())).thenReturn(mock(ValidateSessionAvailabilityRequestParam.class));
        when(sessionsApiValidator.getSessionsAvailabilityValidation(any())).thenReturn(validationResult);

        assertThrows(ValidationException.class, () ->
                courtSchedulerApi.validateSessionAvailabilityCourtSchedule(validationEnvelope));
    }

    @Test
    void shouldThrowValidationExceptionWhenAllDaySplitHasInsufficientSessionDuration() throws IOException {
        // Given a request where all-day split sessions do not have sufficient session duration
        String payload = FileUtil.getPayload("courtscheduler.validate.session.availability-allday-insufficient.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final JsonEnvelope validationEnvelope = createEnvelope("courtscheduler.validate.session.availability", jsonObject);

        final JsonObject validationResult = createObjectBuilder()
                .add("errorMessage", "Requested duration must fit within either the morning or afternoon session for all-day split schedules.")
                .build();

        when(validateSessionAvailabilityRequestParamConverter.convert(any())).thenReturn(mock(ValidateSessionAvailabilityRequestParam.class));
        when(sessionsApiValidator.getSessionsAvailabilityValidation(any())).thenReturn(validationResult);

        assertThrows(ValidationException.class, () ->
                courtSchedulerApi.validateSessionAvailabilityCourtSchedule(validationEnvelope));
    }

    @Test
    void shouldDeleteJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule";
        final JsonEnvelope deleteEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        when(this.enveloper.withMetadataFrom(deleteEnvelope, requestName)).thenReturn(function);
        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.deleteJudiciaryAvailabilityRule(deleteEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(deleteEnvelope, requestName);
        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).deleteJudiciaryAvailabilityRule(any(DeleteJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenDeleteValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule";
        final JsonEnvelope deleteEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        // This test verifies that validation exceptions are still thrown when validator returns an error
        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Request cannot be null")
                .build();

        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                courtSchedulerApi.deleteJudiciaryAvailabilityRule(deleteEnvelope));

        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService, org.mockito.Mockito.never()).deleteJudiciaryAvailabilityRule(any());
    }

    @Test
    void shouldAddJudiciaryAvailabilityRule() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("sessionType", "AM")
                .add("repeatDays", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("day", "Monday")
                                .build())
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule";
        final JsonEnvelope addEnvelope = createEnvelope(requestName, jsonPayloadObject);

        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(addEnvelope, requestName)).thenReturn(function);
        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.addJudiciaryAvailabilityRule(addEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(addEnvelope, requestName);
        verify(addJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).addJudiciaryAvailabilityRule(any(AddJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenAddJudiciaryAvailabilityRuleHasInvalidData() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule";
        final JsonEnvelope addEnvelope = createEnvelope(requestName, jsonPayloadObject);

        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        // courtHouseId not set to trigger validation error

        final JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "courtHouseId cannot be null")
                .build();

        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () -> courtSchedulerApi.addJudiciaryAvailabilityRule(addEnvelope));

        verify(addJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(AddJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService, org.mockito.Mockito.never()).addJudiciaryAvailabilityRule(any());
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("sessionType", "AM")
                .add("repeatDays", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("day", "Monday")
                                .build())
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(updateEnvelope, requestName)).thenReturn(function);
        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.updateJudiciaryAvailabilityRule(updateEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(updateEnvelope, requestName);
        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).updateJudiciaryAvailabilityRule(any(UpdateJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithRuleIdFromRequestParameter() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(updateEnvelope, requestName)).thenReturn(function);
        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.updateJudiciaryAvailabilityRule(updateEnvelope);

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).updateJudiciaryAvailabilityRule(any(UpdateJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldThrowValidationExceptionWhenUpdateValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "ruleId cannot be blank")
                .build();

        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                courtSchedulerApi.updateJudiciaryAvailabilityRule(updateEnvelope));

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService, org.mockito.Mockito.never()).updateJudiciaryAvailabilityRule(any());
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithUnAvailabilities() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", "2026-02-01")
                .add("endDate", "2026-02-28")
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .add("unavailabilities", createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("startDate", "2026-02-10")
                                .add("endDate", "2026-02-12")
                                .add("reason", "ANNUAL_LEAVE")
                                .build())
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule";
        final JsonEnvelope updateEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(this.enveloper.withMetadataFrom(updateEnvelope, requestName)).thenReturn(function);
        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);

        courtSchedulerApi.updateJudiciaryAvailabilityRule(updateEnvelope);

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(UpdateJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
        verify(judiciaryAvailabilityService).updateJudiciaryAvailabilityRule(any(UpdateJudiciaryAvailabilityRuleRequest.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRules() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 10)
                .add("pageNumber", 1)
                .add("withJudiciary", true)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability.rule";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(true);

        final List<JudiciaryAvailabilityRuleResponse> rules = new ArrayList<>();
        final JudiciaryAvailabilityRuleResponse ruleResponse = new JudiciaryAvailabilityRuleResponse();
        ruleResponse.setId(randomUUID().toString());
        ruleResponse.setJudiciaryId(judiciaryId);
        ruleResponse.setCourtHouseId(courtHouseId);
        rules.add(ruleResponse);

        final FindJudiciaryAvailabilityRuleResponse serviceResponse = new FindJudiciaryAvailabilityRuleResponse(rules, 1, 1, 10);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rules", createArrayBuilder().build())
                .add("totalCount", 1)
                .add("pageNumber", 1)
                .add("pageSize", 10)
                .add("judiciaries", createArrayBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(findJudiciaryAvailabilityRuleResponseConverter.convert(any(FindJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        courtSchedulerApi.findJudiciaryAvailabilityRules(findEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(findJudiciaryAvailabilityRuleResponseConverter).convert(any(FindJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaryId() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 20)
                .add("pageNumber", 1)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability.rule";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(20);
        request.setPageNumber(1);

        final FindJudiciaryAvailabilityRuleResponse serviceResponse = new FindJudiciaryAvailabilityRuleResponse(Collections.emptyList(), 0, 1, 20);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rules", createArrayBuilder().build())
                .add("totalCount", 0)
                .add("pageNumber", 1)
                .add("pageSize", 20)
                .add("judiciaries", createArrayBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(findJudiciaryAvailabilityRuleResponseConverter.convert(any(FindJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        courtSchedulerApi.findJudiciaryAvailabilityRules(findEnvelope);

        verify(findJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(findJudiciaryAvailabilityRuleResponseConverter).convert(any(FindJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPagination() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .add("pageSize", 5)
                .add("pageNumber", 2)
                .add("withJudiciary", false)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability.rule";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(5);
        request.setPageNumber(2);
        request.setWithJudiciary(false);

        final FindJudiciaryAvailabilityRuleResponse serviceResponse = new FindJudiciaryAvailabilityRuleResponse(Collections.emptyList(), 0, 2, 5);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rules", createArrayBuilder().build())
                .add("totalCount", 0)
                .add("pageNumber", 2)
                .add("pageSize", 5)
                .add("judiciaries", createArrayBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailabilityRules(any(FindJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(findJudiciaryAvailabilityRuleResponseConverter.convert(any(FindJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        courtSchedulerApi.findJudiciaryAvailabilityRules(findEnvelope);

        verify(findJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        final ArgumentCaptor<FindJudiciaryAvailabilityRuleRequest> requestCaptor = ArgumentCaptor.forClass(FindJudiciaryAvailabilityRuleRequest.class);
        verify(judiciaryAvailabilityService).findJudiciaryAvailabilityRules(requestCaptor.capture(), eq(requester));
        assertEquals(5, requestCaptor.getValue().getPageSize(), "pageSize should be 5");
        assertEquals(2, requestCaptor.getValue().getPageNumber(), "pageNumber should be 2");
        assertEquals(false, requestCaptor.getValue().getWithJudiciary(), "withJudiciary should be false");
        verify(findJudiciaryAvailabilityRuleResponseConverter).convert(any(FindJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldFindJudiciaryAvailability() {
        final String judiciaryId1 = randomUUID().toString();
        final String judiciaryId2 = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);

        final List<String> availableJudiciaries = new ArrayList<>();
        availableJudiciaries.add(judiciaryId1);
        availableJudiciaries.add(judiciaryId2);
        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse(availableJudiciaries);

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = courtSchedulerApi.findJudiciaryAvailability(findEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityWithEmptyList() {
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);

        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse(Collections.emptyList());
        final JsonEnvelope expectedResult = createEnvelope(requestName, createObjectBuilder().add("availableJudiciaries", Json.createArrayBuilder().build()).build());

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(function.apply(any())).thenReturn(expectedResult);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = courtSchedulerApi.findJudiciaryAvailability(findEnvelope);

        assertNotNull(result, "Result should not be null");
        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class));
    }

    @Test
    void shouldFindJudiciaryAvailabilityWithJudiciaryId() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);
        request.setJudiciaryId(judiciaryId);

        final List<String> availableJudiciaries = new ArrayList<>();
        availableJudiciaries.add(judiciaryId);
        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse(availableJudiciaries);

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = courtSchedulerApi.findJudiciaryAvailability(findEnvelope);

        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        final ArgumentCaptor<FindJudiciaryAvailabilityRequest> requestCaptor = ArgumentCaptor.forClass(FindJudiciaryAvailabilityRequest.class);
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(requestCaptor.capture());
        assertEquals(judiciaryId, requestCaptor.getValue().getJudiciaryId(), "judiciaryId should be set");
        assertEquals(courtHouseId, requestCaptor.getValue().getCourtHouseId(), "courtHouseId should be set");
    }

    @Test
    void shouldFindJudiciaryAvailabilityWithNullResponse() {
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("startDate", "2026-01-01")
                .add("endDate", "2026-01-31")
                .add("courtHouseId", courtHouseId)
                .build();
        final String requestName = "courtscheduler.judiciary.find.availability";
        final JsonEnvelope findEnvelope = createEnvelope(requestName, jsonPayloadObject);

        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setCourtHouseId(courtHouseId);

        final FindJudiciaryAvailabilityResponse serviceResponse = new FindJudiciaryAvailabilityResponse();
        serviceResponse.setAvailableJudiciaries(null);
        final JsonEnvelope expectedResult = createEnvelope(requestName, createObjectBuilder().add("availableJudiciaries", Json.createArrayBuilder().build()).build());

        when(this.enveloper.withMetadataFrom(findEnvelope, requestName)).thenReturn(function);
        when(function.apply(any())).thenReturn(expectedResult);
        when(findJudiciaryAvailabilityConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class)))
                .thenReturn(serviceResponse);

        final JsonEnvelope result = courtSchedulerApi.findJudiciaryAvailability(findEnvelope);

        assertNotNull(result, "Result should not be null");
        verify(enveloper, atLeastOnce()).withMetadataFrom(findEnvelope, requestName);
        verify(findJudiciaryAvailabilityConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).findJudiciaryAvailability(any(FindJudiciaryAvailabilityRequest.class));
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

    private HearingSlotSearchAndBookResponse createHearingSlotsResponse(String hearingId) {
        Date sessionStartTime = Date.from(LocalTime.parse("09:00").atDate(LocalDate.of(2024, 7, 15)).atZone(ZoneId.of("UTC")).toInstant());
        return new HearingSlotSearchAndBookResponse(hearingId, "432c067d-eaca-4ce5-ad90-a366ef3e4bb6", "001c067d-eaca-4ce5-ad90-a366ef3e4bb6",
                sessionStartTime.toString(), 20,
                List.of(CourtScheduleJudiciary.judiciary().withCourtScheduleId("432c067d-eaca-4ce5-ad90-a366ef3e4bb6").withJudiciaryId(randomUUID().toString()).build()));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        // ruleId is a path parameter, may be included in payload by framework
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("withJudiciary", true)
                .build();
        final String requestName = "courtscheduler.judiciary.get.availability.rule";
        final JsonEnvelope getEnvelope = createEnvelope(requestName, jsonPayloadObject);

        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(true);

        final JudiciaryAvailabilityRuleResponse ruleResponse = new JudiciaryAvailabilityRuleResponse();
        ruleResponse.setId(ruleId);
        ruleResponse.setJudiciaryId(judiciaryId);

        final uk.gov.moj.cpp.courtscheduler.domain.Judiciary judiciary = new uk.gov.moj.cpp.courtscheduler.domain.Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");

        final GetJudiciaryAvailabilityRuleResponse serviceResponse = new GetJudiciaryAvailabilityRuleResponse(ruleResponse, judiciary);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rule", createObjectBuilder().build())
                .add("judiciary", createObjectBuilder().build())
                .build();

        when(this.enveloper.withMetadataFrom(getEnvelope, requestName)).thenReturn(function);
        when(getJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(getJudiciaryAvailabilityRuleResponseConverter.convert(any(GetJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        courtSchedulerApi.getJudiciaryAvailabilityRule(getEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(getEnvelope, requestName);
        verify(getJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(getJudiciaryAvailabilityRuleResponseConverter).convert(any(GetJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithoutJudiciary() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        // ruleId is a path parameter, may be included in payload by framework
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("withJudiciary", false)
                .build();
        final String requestName = "courtscheduler.judiciary.get.availability.rule";
        final JsonEnvelope getEnvelope = createEnvelope(requestName, jsonPayloadObject);

        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRuleResponse ruleResponse = new JudiciaryAvailabilityRuleResponse();
        ruleResponse.setId(ruleId);
        ruleResponse.setJudiciaryId(judiciaryId);

        final GetJudiciaryAvailabilityRuleResponse serviceResponse = new GetJudiciaryAvailabilityRuleResponse(ruleResponse, null);
        final JsonObject responseJsonObject = createObjectBuilder()
                .add("rule", createObjectBuilder().build())
                .addNull("judiciary")
                .build();

        when(this.enveloper.withMetadataFrom(getEnvelope, requestName)).thenReturn(function);
        when(getJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityService.getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), any(Requester.class)))
                .thenReturn(serviceResponse);
        when(getJudiciaryAvailabilityRuleResponseConverter.convert(any(GetJudiciaryAvailabilityRuleResponse.class)))
                .thenReturn(responseJsonObject);

        courtSchedulerApi.getJudiciaryAvailabilityRule(getEnvelope);

        verify(getJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityService).getJudiciaryAvailabilityRule(any(GetJudiciaryAvailabilityRuleRequest.class), eq(requester));
        verify(getJudiciaryAvailabilityRuleResponseConverter).convert(any(GetJudiciaryAvailabilityRuleResponse.class));
    }

    @Test
    void shouldReturnSuccessWhenAddJudiciaryAvailabilityValidationPasses() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest request =
                new uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(EMPTY_JSON_OBJECT);
        when(enveloper.withMetadataFrom(validationEnvelope, requestName)).thenReturn(function);
        when(function.apply(any(JsonObject.class))).thenReturn(validationEnvelope);

        courtSchedulerApi.validateAddJudiciaryAvailabilityRule(validationEnvelope);

        verify(addJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(), any());
        verify(enveloper).withMetadataFrom(validationEnvelope, requestName);
    }

    @Test
    void shouldReturnFailureWhenAddJudiciaryAvailabilityValidationFails() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.add.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest request =
                new uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        final JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Date range cannot exceed 3 years")
                .build();

        when(addJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                courtSchedulerApi.validateAddJudiciaryAvailabilityRule(validationEnvelope));
    }

    @Test
    void shouldReturnSuccessWhenUpdateJudiciaryAvailabilityValidationPasses() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(EMPTY_JSON_OBJECT);
        when(enveloper.withMetadataFrom(validationEnvelope, requestName)).thenReturn(function);
        when(function.apply(any(JsonObject.class))).thenReturn(validationEnvelope);

        courtSchedulerApi.validateUpdateJudiciaryAvailabilityRule(validationEnvelope);

        verify(updateJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(), any());
        verify(enveloper).withMetadataFrom(validationEnvelope, requestName);
    }

    @Test
    void shouldReturnFailureWhenUpdateJudiciaryAvailabilityValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", LocalDate.now().plusDays(1).toString())
                .add("endDate", LocalDate.now().plusDays(31).toString())
                .add("repeatDays", createArrayBuilder()
                        .add("Monday")
                        .build())
                .build();
        final String requestName = "courtscheduler.judiciary.update.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);

        final JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "If start date is changed, it must be in the future")
                .build();

        when(updateJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(any(), any()))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                courtSchedulerApi.validateUpdateJudiciaryAvailabilityRule(validationEnvelope));
    }

    @Test
    void shouldValidateDeleteJudiciaryAvailabilityRuleSuccessfully() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(EMPTY_JSON_OBJECT);
        when(enveloper.withMetadataFrom(validationEnvelope, requestName)).thenReturn(function);
        when(function.apply(any(JsonObject.class))).thenReturn(validationEnvelope);

        courtSchedulerApi.validateDeleteJudiciaryAvailabilityRule(validationEnvelope);

        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
    }

    @Test
    void shouldThrowUnprocessableEntityExceptionWhenDeleteValidationFails() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final JsonObject jsonPayloadObject = createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build();
        final String requestName = "courtscheduler.judiciary.delete.availability.rule.validate";
        final JsonEnvelope validationEnvelope = createEnvelope(requestName, jsonPayloadObject);

        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        JsonObject validationError = createObjectBuilder()
                .add("errorMessage", "Cannot delete availability rule. Rule is already applied to session session-123 on 2026-01-15 (AM)")
                .build();

        when(deleteJudiciaryAvailabilityRuleConverter.convert(any(JsonObject.class))).thenReturn(request);
        when(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class)))
                .thenReturn(validationError);

        assertThrows(UnprocessableEntityException.class, () ->
                courtSchedulerApi.validateDeleteJudiciaryAvailabilityRule(validationEnvelope));

        verify(deleteJudiciaryAvailabilityRuleConverter).convert(any(JsonObject.class));
        verify(judiciaryAvailabilityRuleApiValidator).validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(any(DeleteJudiciaryAvailabilityRuleRequest.class), any(JudiciaryAvailabilityService.class));
    }
}
