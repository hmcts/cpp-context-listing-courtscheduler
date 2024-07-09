package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.CourtScheduleService;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;

import java.io.IOException;
import java.util.Collections;
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
    private Requester requester;
    @Mock
    private CreateSessionsRequestParamConverter createSessionsRequestParamConverter;
    @Mock
    private ProvisionalBookingApiValidator provisionalBookingApiValidator;
    @Mock
    private ProvisionalBookingService provisionalBookingService;
    @Mock
    private CourtScheduleService courtScheduleService;
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
    private JsonEnvelope envelope;

    @Test
    void shouldCreateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("create-court-schedule.json"));
        final String requestName = "courtscheduler.create";

        final JsonEnvelope createCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        when(this.enveloper.withMetadataFrom(createCourtScheduleJsonEnvelope, requestName)).thenReturn(function);
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
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

        when(createSessionsRequestParamConverter.convert(any())).thenReturn(new CreateSessionRequestParam(Collections.emptyList(), null));

        when(sessionsApiValidator.getSessionsCreateValidation(any(CreateSessionRequestParam.class))).thenReturn(validationError);

         try {
            // Act
            courtSchedulerApi.createCourtSchedule(createCourtScheduleJsonEnvelope);
        } catch (ValidationException e) {
            // Assert
            assertEquals("Validation failed", e.getMessage());
            assertEquals(e.getErrors().getString("errorMessage"), validationError.getString("errorMessage"));
         }
    }


    @Test
    void shouldDeleteCourtSchedule() throws IOException {
        String payload = FileUtil.getPayload("delete-courtscheduler-sessions.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.delete";

        final JsonEnvelope deleteCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(deleteCourtScheduleJsonEnvelope, requestName)).thenReturn(function);
        when(courtScheduleService.deleteCourtScheduleSessions(sessionsConverter.convert(jsonObject.toString()))).thenReturn(EMPTY_JSON_OBJECT);

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
        when(courtScheduleService.update(any(), eq(requester))).thenReturn(success);
        when(objectToJsonObjectConverter.convert(success)).thenReturn(createObjectBuilder()
                .add(RESULTS, "ok")
                .build());


        courtSchedulerApi.updateCourtSchedule(updateCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(updateCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void shouldGetCourtSchedules() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("get-court-schedule.json"));
        final String requestName = "courtscheduler.get.court_schedule";
        final JsonEnvelope exportCourtScheduleEnvelope = createEnvelope(requestName, jsonObject);
        when(enveloper.withMetadataFrom(exportCourtScheduleEnvelope, requestName)).thenReturn(function);

        List<CourtSchedule> courtSchedules = Lists.newArrayList();
        when(courtScheduleService.getCourtSchedules(any(), any())).thenReturn(courtSchedules);
        when(courtScheduleApiValidator.getCourtSchedulesValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(courtScheduleRequestParamConverter.convert(any())).thenReturn(new CourtScheduleRequestParamConverter().convert(jsonObject));

        courtSchedulerApi.getCourtSchedule(exportCourtScheduleEnvelope);

        verify(courtScheduleService, atLeastOnce()).getCourtSchedules(any(), any());
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
    void shouldExportCourtSchedules() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.export.court_schedule.json"));
        final String requestName = "courtscheduler.export.court_schedule.json";
        final JsonEnvelope exportCourtScheduleEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(exportCourtScheduleEnvelope, requestName)).thenReturn(function);
        List<CourtSchedule> courtSchedules = Lists.newArrayList();
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
        List<CourtScheduleJudiciary> courtScheduleJudiciaries = Lists.newArrayList();
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
}
