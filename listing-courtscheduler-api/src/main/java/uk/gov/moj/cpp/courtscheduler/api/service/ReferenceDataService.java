package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static javax.json.Json.createObjectBuilder;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.messaging.JsonEnvelope.metadataBuilder;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.JsonObjects;
import uk.gov.justice.services.messaging.MetadataBuilder;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.json.JsonObject;

import org.apache.commons.collections.CollectionUtils;

@ApplicationScoped
public class ReferenceDataService {
    private static final String REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME = "referencedata.query.public-holidays";
    private static final String REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME = "referencedata.query.rota-business-types";
    private static final String REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME = "referencedata.query.cp-rota-courtroom-mappings";
    private static final String PUBLIC_HOLIDAYS = "publicHolidays";
    private static final String DATE = "date";

    public ReferenceDataService() {
    }


    public List<LocalDate> getPublicHolidays(final String division, final LocalDate fromDate, final LocalDate toDate, final Requester requester) {

        final MetadataBuilder metadataBuilder = metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME);

        final JsonObject params = createObjectBuilder()
                .add("division", division)
                .add("dateFrom", fromDate.toString())
                .add("dateTo", toDate.toString())
                .build();

        final JsonObject payload = requester.requestAsAdmin(envelopeFrom(metadataBuilder, params), JsonObject.class).payload();
        if (!payload.containsKey(PUBLIC_HOLIDAYS) || payload.getJsonArray(PUBLIC_HOLIDAYS).isEmpty()) {
            return emptyList();
        }

        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return payload.getJsonArray(PUBLIC_HOLIDAYS).getValuesAs(JsonObject.class).stream()
                .map(jsonObject -> jsonObject.getString(DATE))
                .map(date -> LocalDate.parse(date, dateFormat))
                .toList();
    }

    public List<CourtRoom> getRotaCourtRoomMappings(final Requester requester) {

        final JsonEnvelope envelope =
                envelopeFrom(metadataBuilder().withId(randomUUID()).withName(REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME).build(),
                        createObjectBuilder().build());

        final JsonObject payload = requester.requestAsAdmin(envelope, JsonObject.class).payload();
        return JsonObjects.getJsonArray(payload, "cpRotaCourtRoomMappings")
                .stream()
                .map(JsonObject.class::cast)
                .map(this::toCourtRoom)
                .toList();
    }



    public List<BusinessType> getRotaBusinessTypes(final Requester requester) {

        final JsonEnvelope envelope =
                envelopeFrom(metadataBuilder().withId(randomUUID()).withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME).build(),
                        createObjectBuilder().build());

        final JsonObject payload = requester.requestAsAdmin(envelope, JsonObject.class).payload();
        return JsonObjects.getJsonArray(payload, "rotaBusinessTypes")
                .stream()
                .map(JsonObject.class::cast)
                .map(this::toBusinessType)
                .toList();
    }

    public Optional<BusinessType> getRotaBusinessTypeByCode(final String typeCode, final Requester requester) {
        final JsonEnvelope envelope =
                envelopeFrom(metadataBuilder().withId(randomUUID()).withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME).build(),
                        createObjectBuilder().add("typeCode", typeCode).build());

        final JsonObject payload = requester.requestAsAdmin(envelope, JsonObject.class).payload();
        final List<BusinessType> businessTypeList = JsonObjects.getJsonArray(payload, "rotaBusinessTypes").orElseThrow(() -> new RuntimeException("No business type found: " + typeCode))
                .stream()
                .map(JsonObject.class::cast)
                .map(this::toBusinessType)
                .toList();
        return CollectionUtils.isEmpty(businessTypeList) ? Optional.empty() : Optional.of(businessTypeList.get(0));

    }
    public Map<String,BusinessType> getRotaBusinessTypesMap(final Requester requester) {
        return getRotaBusinessTypes(requester).stream().collect(Collectors.toMap(BusinessType::getTypeCode, b -> b));
    }

    public  Map<UUID,CourtRoom> getCourtRoomsMap(final Requester requester) {
        return getRotaCourtRoomMappings(requester).stream().collect(Collectors.toMap(courtRoom -> UUID.fromString(courtRoom.getCourtroomId()), c -> c));
    }

    public Optional<CourtRoom> getRotaCourtRoomByCourtRoomId(final String courtRoomId, final Requester requester) {
        final JsonEnvelope envelope =
                envelopeFrom(metadataBuilder().withId(randomUUID()).withName(REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME).build(),
                        createObjectBuilder().build());

        final JsonObject payload = requester.requestAsAdmin(envelope, JsonObject.class).payload();
        final List<CourtRoom> courtRoomList = JsonObjects.getJsonArray(payload, "cpRotaCourtRoomMappings").orElseThrow(() -> new RuntimeException("No court room found: " + courtRoomId))
                .stream()
                .map(JsonObject.class::cast)
                .filter(jsonObject -> courtRoomId.equals(jsonObject.getString("courtRoomId")))
                .map(this::toCourtRoom)
                .toList();
        return CollectionUtils.isEmpty(courtRoomList) ? Optional.empty() : Optional.of(courtRoomList.get(0));
    }



    private BusinessType toBusinessType(JsonObject jsonObject) {
        return BusinessType.BusinessTypeBuilder.aBusinessType()
                .withId(jsonObject.getString("id"))
                .withSeqNum(jsonObject.getInt("seqNum"))
                .withTypeCode(jsonObject.getString("typeCode"))
                .withTypeDescription(jsonObject.getString("typeDescription"))
                .withSlot(jsonObject.getBoolean("slot"))
                .withDuration(jsonObject.getBoolean("duration"))
                .build();
    }

    private CourtRoom toCourtRoom(JsonObject jsonObject) {
        return CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(jsonObject.getString("id"))
                .withRotaLocationId(jsonObject.getInt("rotaLocationId"))
                .withRotaVenueName(jsonObject.getString("rotaVenueName"))
                .withCppCourtRoomId(jsonObject.getInt("cppCourtRoomId"))
                .withRotaVenueId(jsonObject.getInt("rotaVenueId"))
                .withOucode(jsonObject.getString("oucode"))
                .withOucodeL3Name(jsonObject.getString("oucodeL3Name"))
                .withOucodeL2Name(jsonObject.getString("oucodeL2Name"))
                .withOucodeL2Code(jsonObject.getString("oucodeL2Code"))
                .withOucodeUUID(jsonObject.getString("oucodeUUID"))
                .withCourtRoomName(jsonObject.getString("courtroomName"))
                .withCourtRoomId(jsonObject.getString("courtRoomId"))
                .build();
    }


}
