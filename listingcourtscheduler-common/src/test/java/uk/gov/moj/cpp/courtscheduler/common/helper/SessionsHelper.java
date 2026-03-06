package uk.gov.moj.cpp.courtscheduler.common.helper;

import static java.util.UUID.randomUUID;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;
import uk.gov.justice.services.messaging.spi.DefaultJsonEnvelopeProvider;

import java.util.UUID;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;

public class SessionsHelper {
    public static final String REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME = "referencedata.query.public-holidays";
    public static final String REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME = "referencedata.query.rota-business-types";
    public static final String REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME = "referencedata.query.cp-rota-courtroom-mappings";
    public static final String REFERENCEDATA_QUERY_OU_COURT_ROOMS_NAME = "referencedata.query.courtrooms";
    public static final String REFERENCEDATA_QUERY_ROTA_JUDICIARIES_NAME = "referencedata.query.judiciaries";
    public static final String REFERENCEDATA_QUERY_ROTA_COURT_ROOM_SESSION_ALLOCATIONS_NAME = "referencedata.query.courtroom-session-allocations";
    public static final String PUBLIC_HOLIDAYS = "publicHolidays";
    public static final String BUSINESS_TYPE_CODE = "typeCode";
    public static final String COURT_ROOM_ID = "courtRoomId";
    public static final String DATE = "date";

    public static JsonObject mockBusinessType(String businessType) {
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
    public static JsonObject mockCourtRooms(String courtroomId) {
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
                .add("courtroomId", courtroomId)

                .build();
        JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        arrayBuilder.add(businessTypeObject);
        return Json.createObjectBuilder().add("cpRotaCourtRoomMappings", arrayBuilder).build();
    }
    public static JsonEnvelope createEnvelope(final String name, final JsonValue payload) {
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

    public static JsonObject getPayload(String path) {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString(path));
    }

}
