package uk.gov.moj.cpp.courtscheduler.referencedata.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;
import uk.gov.justice.services.test.utils.framework.api.JsonObjectConvertersFactory;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private Requester requester;


    @InjectMocks
    private ReferenceDataService referenceDataService;
    @Spy
    private JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectConvertersFactory().jsonObjectToObjectConverter();

    private static final String BUSINESS_TYPE_CODE = "typeCode";
    private static final String COURT_ROOM_ID = "courtRoomId";
    private static final String REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME = "referencedata.query.public-holidays";
    private static final String REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME = "referencedata.query.rota-business-types";
    private static final String REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME = "referencedata.query.cp-rota-courtroom-mappings";

    @BeforeEach
    void setUp() {
       /* when(referenceDataService.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE, requester)).thenReturn(Optional.of(new BusinessType()));
        when(referenceDataService.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID, requester)).thenReturn(Optional.of(new CourtRoom()));*/
    }


    @Test
    void shouldReturnBusinessTypeWhenTypeCodeIsProvided() {

        final JsonObject responsePayload = mockBusinessType("DVLA");

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), responsePayload);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final Optional<BusinessType> businessType = referenceDataService.getRotaBusinessTypeByCode("DVLA", requester);
        assertThat(businessType, Matchers.notNullValue());
    }

    @Test
    void shouldReturnCourtRoomWhenCourtRoomIdIsProvided() {
        final String courtRoomId = randomUUID().toString();
        final JsonObject responsePayload = mockCourtRooms(courtRoomId);

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), responsePayload);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final Optional<CourtRoom> courtRoom = referenceDataService.getRotaCourtRoomByCourtRoomId(courtRoomId, requester);
        assertThat(courtRoom, Matchers.notNullValue());

    }

    @Test
    void shouldRequestPublicHolidays() {

        final JsonObject responsePayload = Json.createObjectBuilder().build();

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME)
                .build(), responsePayload);
        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final List<LocalDate> publicholidays = referenceDataService.getPublicHolidays("DIV1", LocalDate.of(2024, 1, 1), LocalDate.of(2021, 12, 31), requester);
        assertThat(publicholidays, Matchers.empty());
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

    private JsonObject mockBusinessType(String businessType) {
        JsonObject businessTypeObject = Json.createObjectBuilder()
                .add("id", randomUUID().toString())
                .add("seqNum", 120)
                .add("typeCode", businessType)
                .add("typeDescription", businessType)
                .add("slot", true)
                .add("duration", false)
                .build();
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        arrayBuilder.add(businessTypeObject);
        return Json.createObjectBuilder().add("rotaBusinessTypes", arrayBuilder).build();


    }


    private JsonObject mockCourtRooms(String courtroomId) {
        JsonObject businessTypeObject = Json.createObjectBuilder()
                .add("id", randomUUID().toString())
                .add("rotaLocationId", 77)
                .add("rotaVenueName", "Court 9")
                .add("cppCourtRoomId", 2988)
                .add("rotaVenueId", 0)
                .add("oucode", "B43KQ00")
                .add("oucodeL3Name", "Reading Magistrates' Court")
                .add("oucodeL2Name", "Thames Valley")
                .add("oucodeL2Code", "43")
                .add("oucodeUUID", "49db2271-1941-3847-a7fb-dbd92b035e40")
                .add("courtroomName", "Courtroom 09")
                .add("courtRoomId", courtroomId)

                .build();
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        arrayBuilder.add(businessTypeObject);
        return Json.createObjectBuilder().add("cpRotaCourtRoomMappings", arrayBuilder).build();


    }

    public JsonObject getPayload(String path) {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString(path));
    }
}