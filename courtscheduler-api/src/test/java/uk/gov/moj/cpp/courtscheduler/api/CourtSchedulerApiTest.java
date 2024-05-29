package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.payloadToObject;

import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.service.MiService;
import uk.gov.moj.cpp.courtscheduler.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.service.SlotsUpdateService;

import java.io.IOException;
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
    private SlotsSearchService slotsSearchService;
    @Mock
    private ProvisionalBookingService provisionalBookingService;
    @Mock
    private MiService miService;
    @Mock
    private Function<Object, JsonEnvelope> function;
    @InjectMocks
    private CourtSchedulerApi courtSchedulerApi;

    @Test
    void shouldCreateCourtSchedule() throws IOException {
        final JsonObject jsonPayloadObject = payloadToObject(FileUtil.getPayload("create-court-schedule.json"));
        final String requestName = "courtscheduler.create";

        final JsonEnvelope createCourtScheduleJsonEnvelope = createEnvelope(requestName, jsonPayloadObject);

        when(enveloper.withMetadataFrom(createCourtScheduleJsonEnvelope, requestName)).thenReturn(function);

        courtSchedulerApi.createCourtSchedule(createCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(createCourtScheduleJsonEnvelope, requestName);
    }

    @Test
    void shouldUpdateHearingSlots() throws IOException {
        String payload = FileUtil.getPayload("courtscheduler.update.hearing.slots.json");
        final JsonObject jsonObject = payloadToObject(payload);
        final String requestName = "courtscheduler.update.hearing.slots";

        final JsonEnvelope updateHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);

        when(enveloper.withMetadataFrom(updateHearingSlotsEnvelope, requestName)).thenReturn(function);

        courtSchedulerApi.updateHearingSlots(updateHearingSlotsEnvelope);

        verify(slotsUpdateService, atLeastOnce()).update(new AllocatedSlotConverter().convert(payload).getHearingSlots());
        verify(enveloper, atLeastOnce()).withMetadataFrom(updateHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldRetrieveHearingSlots() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.get.hearing.slots.json"));
        final String requestName = "courtscheduler.get.hearing.slots";
        final JsonEnvelope getHearingSlotsEnvelope = createEnvelope(requestName, jsonObject);
        final HearingSlotRequestParamConverter hearingSlotRequestParamConverter = new HearingSlotRequestParamConverter();

        when(enveloper.withMetadataFrom(getHearingSlotsEnvelope, requestName)).thenReturn(function);
        when(slotsSearchService.search(hearingSlotRequestParamConverter.convert(jsonObject))).thenReturn(JsonObject.EMPTY_JSON_OBJECT);

        courtSchedulerApi.getHearingSlots(getHearingSlotsEnvelope);

        verify(slotsSearchService, atLeastOnce()).search(hearingSlotRequestParamConverter.convert(jsonObject));
        verify(enveloper, atLeastOnce()).withMetadataFrom(getHearingSlotsEnvelope, requestName);
    }

    @Test
    void shouldExportCourtSchedules() throws IOException {
        final JsonObject jsonObject = payloadToObject(FileUtil.getPayload("courtscheduler.export.court_schedule.json"));
        final String requestName = "courtscheduler.export.court_schedule.json";
        final JsonEnvelope exportCourtScheduleEnvelope = createEnvelope(requestName, jsonObject);
        final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter = new MiFilterCriteriaRequestParamConverter();

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
        final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter = new MiFilterCriteriaRequestParamConverter();

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
        final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter = new MiFilterCriteriaRequestParamConverter();

        when(enveloper.withMetadataFrom(exportAllocatedListingsEnvelope, requestName)).thenReturn(function);
        List<AllocatedListing> allocatedListings = Lists.newArrayList();
        when(miService.getAllocatedListings(miFilterCriteriaRequestParamConverter.convert(jsonObject))).thenReturn(allocatedListings);

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
        when(provisionalBookingService.bookProvisionalSlots(any())).thenReturn(JsonObject.EMPTY_JSON_OBJECT);

        courtSchedulerApi.createProvisionalBooking(createCourtScheduleJsonEnvelope);

        verify(enveloper, atLeastOnce()).withMetadataFrom(createCourtScheduleJsonEnvelope, requestName);
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
