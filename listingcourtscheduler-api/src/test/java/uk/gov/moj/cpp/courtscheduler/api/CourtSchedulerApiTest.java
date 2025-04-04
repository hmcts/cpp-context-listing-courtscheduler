package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.CourtSchedulerApi.RESULTS;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.payloadToObject;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing_.HEARING_ID;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CreateSessionsRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.OuCodeMigrateConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Result;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import javax.json.JsonObject;
import javax.json.JsonValue;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private Function<Object, JsonEnvelope> function;
    @Mock
    private SessionsApiValidator sessionsApiValidator;
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

    @Test
    void shouldCreateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("create-court-schedule.json"));
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
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("create-court-schedule.json"));
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
        String payload = FileUtil.getPayload("delete-courtscheduler-sessions.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.delete";

        final JsonEnvelope deleteCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(deleteCourtScheduleJsonEnvelope, requestName)).thenReturn(function);

        courtSchedulerApi.deleteCourtSchedule(deleteCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(deleteCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void shouldUpdateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("update-court-schedule.json"));
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
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("update-court-schedule.json"));
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
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("get-court-schedule.json"));
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
        String payload = FileUtil.getPayload("courtscheduler.update.hearing.slots.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.update.hearing.slots";

        final JsonEnvelope updateHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(updateHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(allocatedSlotConverter.convert(jsonObject.toString())).thenReturn(new AllocatedSlotConverter().convert(payload));

        courtSchedulerApi.updateHearingSlots(updateHearingSlotsEnvelope);

        verify(slotsUpdateService, atLeastOnce()).update(new AllocatedSlotConverter().convert(payload).getHearingSlots());
        verify(enveloper, atLeastOnce()).withMetadataFrom(updateHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldRetrieveHearingSlots() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.get.hearing.slots.json"));
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
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.get.hearing.slots.json"));
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
        final HearingSlotSearchResponse hearingSlotSearchResponse = createHearingSlotsResponse("432c067d-eaca-4ce5-ad90-a366ef3e4bb6");

        when(enveloper.withMetadataFrom(getSearchListHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(hearingSlotSearchRequestConverter.convert(jsonObject)).thenReturn(new HearingSlotSearchRequestConverter().convert(jsonObject));
        when(slotsUpdateService.searchAndBook(hearingSlotSearchRequestConverter.convert(jsonObject))).thenReturn(hearingSlotSearchResponse);
        when(hearingSlotsApiValidator.searchAndBookRequestValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(objectToJsonObjectConverter.convert(hearingSlotSearchResponse)).thenReturn(createObjectBuilder()
                .add(RequestParameterConstant.HEARING_SLOTS.getLabel(), "ok")
                .build());

        courtSchedulerApi.searchBookHearingSlots(getSearchListHearingSlotsEnvelope);

        verify(slotsUpdateService, atLeastOnce()).searchAndBook(hearingSlotSearchRequestConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(getSearchListHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldExportCourtSchedules() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.export.court_schedule.json"));
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
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.export.court_schedule_judiciary.json"));
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
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.export.allocated_listings.json"));
        final String requestName = "courtscheduler.export.allocated_listings";
        final JsonEnvelope exportAllocatedListingsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(exportAllocatedListingsEnvelope, requestName)).thenReturn(function);
        when(miService.getAllocatedListings(miFilterCriteriaRequestParamConverter.convert(jsonObject))).thenReturn(Collections.emptyList());

        courtSchedulerApi.exportAlloctedListings(exportAllocatedListingsEnvelope);

        verify(miService, atLeastOnce()).getAllocatedListings(miFilterCriteriaRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(exportAllocatedListingsEnvelope, requestName);
    }

    @Test
    void shouldCreateProvisionalBooking() throws IOException {
        String payload = FileUtil.getPayload("create.provisional.booking.json");
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
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.get.provisional.booking.json"));
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
        String payload = FileUtil.getPayload("oucode-migrate-courtscheduler.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.oucode.migrate";

        final JsonEnvelope migrateOuCodeEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(migrateOuCodeEnvelope, requestName)).thenReturn(function);
        when(sessionsService.migrateOuCodes(any())).thenReturn(Result.SUCCESS());

        courtSchedulerApi.migrateOuCode(migrateOuCodeEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(migrateOuCodeEnvelope, requestName);
    }

    @Test
    void shouldReturnFailureWhenValidationFails() throws IOException {
        String payload = FileUtil.getPayload("courtscheduler.validate.create.json");
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
    private HearingSlotSearchResponse createHearingSlotsResponse(String hearingId) {
        Date sessionStartTime = Date.from(LocalTime.parse("09:00").atDate(LocalDate.of(2024, 7, 15)).atZone(ZoneId.of("UTC")).toInstant());
        return new HearingSlotSearchResponse(hearingId, "432c067d-eaca-4ce5-ad90-a366ef3e4bb6", "001c067d-eaca-4ce5-ad90-a366ef3e4bb6", sessionStartTime.toString(), 20);
    }
}
