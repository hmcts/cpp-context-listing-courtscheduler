package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;


import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;


import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.CREATE_SESSIONS_COURTROOM_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.utils.VenueNameComparator.matches;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.moj.cpp.courtscheduler.common.JsonObjects;
import uk.gov.moj.cpp.courtscheduler.common.service.CommonPlatformQueryClient;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnit;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrated from Justice Services {@code Requester#requestAsAdmin(JsonEnvelope, JsonObject.class)}
 * to {@link CommonPlatformQueryClient}: each query is now an explicit GET against the
 * referencedata-query-api with the system user UUID injected as {@code CJSCPPUID}.
 * The {@code Requester} parameter is dropped from each public method signature.
 */
@SuppressWarnings({"squid:S1312", "squid:S2629","squid:S6813","squid:S112"})
@Service
public class ReferenceDataService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataService.class);

    private static final String REFERENCEDATA_BASE_PATH = "/referencedata-query-api/query/api/rest/referencedata";
    private static final String PUBLIC_HOLIDAYS_PATH = REFERENCEDATA_BASE_PATH + "/public-holidays";
    private static final String ROTA_BUSINESS_TYPES_PATH = REFERENCEDATA_BASE_PATH + "/rota-business-types";
    private static final String ROTA_COURT_ROOM_MAPPINGS_PATH = REFERENCEDATA_BASE_PATH + "/cp-rota-courtroom-mappings";
    private static final String JUDICIARIES_PATH = REFERENCEDATA_BASE_PATH + "/judiciaries";
    private static final String COURT_ROOM_SESSION_ALLOCATIONS_PATH = REFERENCEDATA_BASE_PATH + "/courtroom-session-allocations";
    private static final String OU_COURT_ROOMS_PATH = REFERENCEDATA_BASE_PATH + "/courtrooms";
    private static final String ORGANISATION_UNITS_PATH_PREFIX = REFERENCEDATA_BASE_PATH + "/organisation-units/";

    private static final String ACCEPT_PUBLIC_HOLIDAYS = "application/vnd.referencedata.query.public-holidays+json";
    private static final String ACCEPT_ROTA_BUSINESS_TYPES = "application/vnd.referencedata.query.rota-business-types+json";
    private static final String ACCEPT_ROTA_COURT_ROOM_MAPPINGS = "application/vnd.referencedata.query.cp-rota-courtroom-mappings+json";
    // The upstream referencedata-query-api uses 'reference-data' (with hyphen) for the
    // judiciaries endpoint and drops the 'query' segment — verified against
    // ~/devenv/project/msjs/cpp-context-reference-data/referencedata-query/referencedata-query-api/src/raml/referencedata-query-api.raml line 1158.
    private static final String ACCEPT_JUDICIARIES = "application/vnd.reference-data.judiciaries+json";
    private static final String ACCEPT_COURT_ROOM_SESSION_ALLOCATIONS = "application/vnd.referencedata.query.courtroom-session-allocations+json";
    // /courtrooms uses 'ou-courtrooms' as the response token, not 'query.courtrooms'.
    // Verified against the referencedata-query-api RAML and matches the constant
    // cp-court-list-publishing-service uses (ACCEPT_OU_COURTROOMS).
    private static final String ACCEPT_OU_COURT_ROOMS = "application/vnd.referencedata.ou-courtrooms+json";
    private static final String ACCEPT_ORGANISATION_UNIT = "application/vnd.referencedata.query.organisation-unit+json";

    private static final String PUBLIC_HOLIDAYS = "publicHolidays";
    private static final String DATE = "date";
    private static final String CP_ROTA_COURT_ROOM_MAPPINGS = "cpRotaCourtRoomMappings";
    private static final String ORGANISATION_UNITS = "organisationunits";
    private static final String COURTROOMS = "courtrooms";
    private static final String COURTROOM_ID = "courtroomId";
    private static final String VENUE_NAME = "rotaVenueName";
    private static final String LOCATION_ID = "rotaLocationId";

    private static final String COURT_DETAIL_NOT_FOUND = "COURT_DETAIL_NOT_FOUND";
    private static final String COURT_ROOM_FETCHED_BY_VENUE_NAME = "CourtRoom fetched by VenueName: %s%n,can't find by VenueId:%s%n";
    private static final String MULTIPLE_COURTROOMS_FOUND_BY_VENUE_NAME = "Multiple courtrooms found by VenueName : %s%n , but VenueId: %s%n selected by created_on";
    private static final String ALL = "ALL";

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @Inject
    private CommonPlatformQueryClient commonPlatformQueryClient;

    public List<LocalDate> getPublicHolidays(final String division, final LocalDate fromDate, final LocalDate toDate) {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                PUBLIC_HOLIDAYS_PATH,
                ACCEPT_PUBLIC_HOLIDAYS,
                Map.of("division", division,
                        "dateFrom", fromDate.toString(),
                        "dateTo", toDate.toString()));
        if (!payload.containsKey(PUBLIC_HOLIDAYS) || payload.getJsonArray(PUBLIC_HOLIDAYS).isEmpty()) {
            return emptyList();
        }

        final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        return payload.getJsonArray(PUBLIC_HOLIDAYS).getValuesAs(JsonObject.class).stream()
                .map(jsonObject -> jsonObject.getString(DATE))
                .map(date -> LocalDate.parse(date, dateFormat))
                .toList();
    }

    public List<CourtRoom> getRotaCourtRoomMappings() {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                ROTA_COURT_ROOM_MAPPINGS_PATH, ACCEPT_ROTA_COURT_ROOM_MAPPINGS, Map.of());
        final int resultsCount = JsonObjects.getJsonArray(payload, CP_ROTA_COURT_ROOM_MAPPINGS).orElseThrow(() -> new RuntimeException("No courtrooms  found: ")).size();
        LOGGER.debug("Total courtrooms found: {}", resultsCount);
        Set<String> seenCourtRoomIds = new HashSet<>();
        Set<String> duplicateCourtRoomIds = new HashSet<>();
        List<RotaProcessLog> errorLogs = new ArrayList<>();

        List<CourtRoom> courtRooms = JsonObjects.getJsonArray(payload, CP_ROTA_COURT_ROOM_MAPPINGS)
                .orElseThrow(() -> new RuntimeException("No courtrooms found: "))
                .stream()
                .map(JsonObject.class::cast)
                .map(jsonObject -> {
                    try {
                        final String courtRoomId = getStringOrElse(jsonObject, COURTROOM_ID, null);
                        if (nonNull(courtRoomId) && !seenCourtRoomIds.add(courtRoomId)) {
                            duplicateCourtRoomIds.add(courtRoomId);
                            return null;
                        }
                        return toCourtRoom(jsonObject);
                    } catch (Exception e) {
                        LOGGER.error(format("Error while converting court room with ID: %d", jsonObject.getInt("cppCourtRoomId")), e);
                        LOGGER.error(format("Skipping the failed records, %d records left", resultsCount - 1));
                        String cppCourtRoomId =
                                jsonObject.containsKey("cppCourtRoomId") && !jsonObject.isNull("cppCourtRoomId")
                                        ? valueOf(jsonObject.getInt("cppCourtRoomId"))
                                        : getStringOrElse(jsonObject, COURTROOM_ID, "unknown");
                        errorLogs.add(
                                rotaProcessLog()
                                        .withErrorCode(CREATE_SESSIONS_COURTROOM_NOT_FOUND.code())
                                        .withErrorText(cppCourtRoomId)
                                        .build()
                        );
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();

        if (!errorLogs.isEmpty()) {
            final String combinedMissingCourtrooms = errorLogs.stream()
                    .map(RotaProcessLog::getErrorText)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));
            rotaProcessLogService.saveRotaProcessLog(
                    rotaProcessLog()
                            .withErrorCode(CREATE_SESSIONS_COURTROOM_NOT_FOUND.code())
                            .withErrorText(CREATE_SESSIONS_COURTROOM_NOT_FOUND.format(combinedMissingCourtrooms))
                            .build()
            );
        }

        if (!duplicateCourtRoomIds.isEmpty()) {
            LOGGER.error(format("Duplicate courtroom IDs found: %s", duplicateCourtRoomIds));
            rotaProcessLogService.saveRotaProcessLog(
                    rotaProcessLog()
                            .withErrorCode(CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND.code())
                            .withErrorText(CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND.format(duplicateCourtRoomIds))
                            .build()
            );
        }

        // Return only distinct courtrooms based on their IDs
        return courtRooms.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<BusinessType> getRotaBusinessTypes() {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                ROTA_BUSINESS_TYPES_PATH, ACCEPT_ROTA_BUSINESS_TYPES,
                Map.of("jurisdiction", ALL));
        final JsonArray rotaBusinessTypes = JsonObjects.getJsonArray(payload, "rotaBusinessTypes").orElseThrow(() -> new RuntimeException("No business type found: "));
        LOGGER.info("Number of rotaBusinessTypes returned: {}", rotaBusinessTypes.size());
        return rotaBusinessTypes
                .stream()
                .map(JsonObject.class::cast)
                .map(this::toBusinessType)
                .toList();
    }

    public Optional<BusinessType> getRotaBusinessTypeByCode(final String typeCode) {
        return getRotaBusinessTypes().stream()
                .filter(businessType -> businessType.getTypeCode().equals(typeCode))
                .findFirst();
    }

    public Map<String, BusinessType> getRotaBusinessTypesMap() {
        return getRotaBusinessTypes().stream().collect(Collectors.toMap(
                BusinessType::getTypeCode,
                b -> b,
                (existing, replacement) -> {
                    LOGGER.warn("Duplicate business type code found: {}. Keeping the first occurrence.", existing.getTypeCode());
                    return existing;
                }
        ));
    }

    public Map<UUID, CourtRoom> getCourtRoomsMap() {
        return getRotaCourtRoomMappings().stream()
                .filter(courtRoom -> nonNull(courtRoom.getCourtroomId()))
                .collect(Collectors.toMap(courtRoom -> UUID.fromString(courtRoom.getCourtroomId()), c -> c));
    }

    public List<CourtRoom> getCpCourtRooms() {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                OU_COURT_ROOMS_PATH, ACCEPT_OU_COURT_ROOMS, Map.of());

        if (!payload.containsKey(ORGANISATION_UNITS)) {
            return emptyList();
        }

        return payload.getJsonArray(ORGANISATION_UNITS).stream()
                .map(JsonObject.class::cast)
                .flatMap(ou -> {
                    if (ou.containsKey(COURTROOMS)) {
                        return ou.getJsonArray(COURTROOMS).stream()
                                .map(JsonObject.class::cast)
                                .map(courtRoomJson -> toCpCourtRoom(courtRoomJson, ou));
                    }
                    return java.util.stream.Stream.empty();
                })
                .toList();
    }

    public Optional<CourtRoom> getRotaCourtRoomByCourtRoomId(final String courtRoomId) {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                ROTA_COURT_ROOM_MAPPINGS_PATH, ACCEPT_ROTA_COURT_ROOM_MAPPINGS, Map.of());
        JsonArray courtRoomMappings = payload.getJsonArray(CP_ROTA_COURT_ROOM_MAPPINGS);
        if (isNull(courtRoomMappings)) {
            throw new RuntimeException("No court room found: " + courtRoomId);
        }
        List<CourtRoom> courtRoomList = courtRoomMappings.stream()
                .map(JsonObject.class::cast)
                .filter(jsonObject -> {
                    JsonString id = jsonObject.getJsonString(COURTROOM_ID);
                    return id != null && courtRoomId.equals(id.getString());
                })
                .map(this::toCourtRoom)
                .toList();
        return isEmpty(courtRoomList) ? Optional.empty() : Optional.of(courtRoomList.get(0));
    }

    public Optional<CourtRoom> getRotaCourtRoomByVenue(final Venue venue, final Map<String, String> exceptionMessages) {
        LOGGER.debug("getRotaCourtRoomByVenue called - venue: {}", venue);
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                ROTA_COURT_ROOM_MAPPINGS_PATH, ACCEPT_ROTA_COURT_ROOM_MAPPINGS, Map.of());
        LOGGER.debug("getRotaCourtRoomByVenue called - request has been sent and the response payload : {}", payload);
        JsonArray courtRoomMappings = payload.getJsonArray(CP_ROTA_COURT_ROOM_MAPPINGS);
        if (isNull(courtRoomMappings)) {
            throw new RuntimeException(format("No court room found with venue: %d-%d-%s", venue.getLocationId(), venue.getVenueId(), venue.getVenueName()));
        }
        final List<CourtRoom> courtRoomList = courtRoomMappings.stream()
                .map(JsonObject.class::cast)
                .filter(jsonObject -> {
                    final Integer locationId = jsonObject.containsKey(LOCATION_ID) ? jsonObject.getInt(LOCATION_ID) : null;
                    final String venueName = jsonObject.containsKey(VENUE_NAME) ? jsonObject.getString(VENUE_NAME) : null;
                    return nonNull(locationId) && venue.getLocationId().equals(locationId) &&
                            nonNull(venueName) && matches(venue.getVenueName(), venueName);

                })
                .map(this::toCourtRoom)
                .toList();
        final Optional<CourtRoom> courtRoomOptional = courtRoomList.stream().filter(courtRoom -> courtRoom.getRotaVenueId().equals(venue.getVenueId())).findAny();
        if (courtRoomOptional.isPresent()) {
            return courtRoomOptional;
        } else {
            if (courtRoomList.size() > 1) {
                exceptionMessages.put(format(MULTIPLE_COURTROOMS_FOUND_BY_VENUE_NAME, venue.getVenueName(), venue.getVenueId()), COURT_DETAIL_NOT_FOUND);
            } else {
                exceptionMessages.put(format(COURT_ROOM_FETCHED_BY_VENUE_NAME, venue.getVenueName(), venue.getVenueId()), COURT_DETAIL_NOT_FOUND);
            }

        }

        return isEmpty(courtRoomList) ? Optional.empty() : Optional.of(courtRoomList.get(0));
    }

    public List<Judiciary> getJudiciariesMap() {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                JUDICIARIES_PATH, ACCEPT_JUDICIARIES, Map.of());

        final List<Judiciary> judiciaries = new ArrayList<>();
        JsonObjects.getJsonArray(payload, "judiciaries").ifPresent(judiciariesJsonArray -> {
            for(JsonValue jsonValue: judiciariesJsonArray) {
                final JsonObject jsonObject = (JsonObject) jsonValue;
                judiciaries.add(toJudiciary(jsonObject));
            }
        });


        return judiciaries;
    }

    public List<Judiciary> getJudiciariesWithSpecialismByIds(final List<String> judiciaryIds) {
        if (judiciaryIds == null || judiciaryIds.isEmpty()) {
            return emptyList();
        }

        final String idsParam = String.join(",", judiciaryIds);

        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                JUDICIARIES_PATH, ACCEPT_JUDICIARIES,
                Map.of("ids", idsParam,
                        "withSpecialism", true));

        final List<Judiciary> judiciaries = new ArrayList<>();
        JsonObjects.getJsonArray(payload, "judiciaries").ifPresent(judiciariesJsonArray -> {
            for(JsonValue jsonValue: judiciariesJsonArray) {
                final JsonObject jsonObject = (JsonObject) jsonValue;
                judiciaries.add(toJudiciary(jsonObject));
            }
        });

        return judiciaries;
    }

    /**
     * Typeahead search against reference data judiciaries (orchestrator use).
     * Query params are passed through to the referencedata judiciaries endpoint; refdata must accept
     * {@code search}, {@code judiciaryGroup}, {@code limit}, and {@code withSpecialism}.
     */
    public List<Judiciary> searchJudiciaries(final String search,
                                             final String judiciaryGroup,
                                             final int limit,
                                             final boolean withSpecialism) {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                JUDICIARIES_PATH, ACCEPT_JUDICIARIES,
                Map.of("search", search,
                        "judiciaryGroup", judiciaryGroup != null ? judiciaryGroup : "",
                        "limit", limit,
                        "withSpecialism", withSpecialism));

        final List<Judiciary> judiciaries = new ArrayList<>();
        JsonObjects.getJsonArray(payload, "judiciaries").ifPresent(judiciariesJsonArray -> {
            for (JsonValue jsonValue : judiciariesJsonArray) {
                final JsonObject jsonObject = (JsonObject) jsonValue;
                judiciaries.add(toJudiciaryFromSearchResult(jsonObject));
            }
        });

        return judiciaries;
    }

    /**
     * Parses judiciary JSON from refdata search results; tolerates optional fields not present on full records.
     */
    private Judiciary toJudiciaryFromSearchResult(final JsonObject jsonObject) {
        final List<uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType> specialisms = new ArrayList<>();
        JsonObjects.getJsonArray(jsonObject, "specialisms").ifPresent(specialismsJsonArray -> {
            for (JsonValue specialismValue : specialismsJsonArray) {
                final String specialismString = specialismValue.getValueType() == JsonValue.ValueType.STRING
                        ? ((JsonString) specialismValue).getString()
                        : specialismValue.toString();
                try {
                    final uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType specialismType =
                            uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType.valueOf(specialismString);
                    specialisms.add(specialismType);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown specialism value received from referencedata service: {}", specialismString);
                }
            }
        });

        final Integer seqId = jsonObject.containsKey("seqId") && !jsonObject.isNull("seqId")
                ? jsonObject.getInt("seqId")
                : null;

        return Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(getStringOrElse(jsonObject, "id", null))
                .withCpUserId(getStringOrElse(jsonObject, "cpUserId", null))
                .withEmailAddress(getStringOrElse(jsonObject, "emailAddress", null))
                .withJudiciaryType(getStringOrElse(jsonObject, "judiciaryType", null))
                .withPersonId(getStringOrElse(jsonObject, "personId", null))
                .withSurname(getStringOrElse(jsonObject, "surname", ""))
                .withForenames(getStringOrElse(jsonObject, "forenames", ""))
                .withSeqId(seqId != null ? seqId : 0)
                .withTitleJudicialPrefix(getStringOrElse(jsonObject, "titleJudicialPrefix", null))
                .withTitleJudicialPrefixWelsh(getStringOrElse(jsonObject, "titleJudicialPrefixWelsh", null))
                .withTitleSuffix(getStringOrElse(jsonObject, "titleSuffix", null))
                .withTitleSuffixWelsh(getStringOrElse(jsonObject, "titleSuffixWelsh", null))
                .withValidFrom(getStringOrElse(jsonObject, "validFrom", null))
                .withValidTo(getStringOrElse(jsonObject, "validTo", null))
                .withTitlePrefix(getStringOrElse(jsonObject, "titlePrefix", null))
                .withTitlePrefixWelsh(getStringOrElse(jsonObject, "titlePrefixWelsh", null))
                .withSpecialisms(specialisms)
                .withRequestedName(getStringOrElse(jsonObject, "requestedName", null))
                .build();
    }

    public List<CourtRoomSessionAllocation> getCourtRoomSessionAllocationsMap() {
        final JsonObject payload = commonPlatformQueryClient.getReferenceData(
                COURT_ROOM_SESSION_ALLOCATIONS_PATH, ACCEPT_COURT_ROOM_SESSION_ALLOCATIONS, Map.of());

        final List<CourtRoomSessionAllocation> courtRoomSessionAllocations = new ArrayList<>();
        JsonObjects.getJsonArray(payload, "courtRoomSessionAllocations").ifPresent(courtRoomSessionAllocationsJsonArray -> {
            for(JsonValue jsonValue: courtRoomSessionAllocationsJsonArray) {
                final JsonObject jsonObject = (JsonObject) jsonValue;
                courtRoomSessionAllocations.add(toCourtRoomSessionAllocation(jsonObject));
            }
        });


        return courtRoomSessionAllocations;
    }

    /**
     * Looks up a court centre (organisation unit) by its UUID and returns its configured default
     * session start time. Returns {@link Optional#empty()} when the id is blank, the organisation
     * unit does not exist (referencedata responds 404 — treated as "no default configured", not an
     * error), or the query yields no payload — callers fall back to the hardcoded per-session-type
     * default rather than failing the whole session-creation request.
     */
    public Optional<OrganisationUnit> getOrganisationUnit(final String organisationUnitId) {
        if (isBlank(organisationUnitId)) {
            return Optional.empty();
        }
        final JsonObject payload;
        try {
            payload = commonPlatformQueryClient.getReferenceData(
                    ORGANISATION_UNITS_PATH_PREFIX + organisationUnitId, ACCEPT_ORGANISATION_UNIT, Map.of());
        } catch (final org.springframework.web.client.HttpClientErrorException.NotFound notFound) {
            LOGGER.debug("No organisation-unit found for id: {} - falling back to hardcoded default", organisationUnitId);
            return Optional.empty();
        }
        if (payload.isEmpty() || !payload.containsKey("id")) {
            return Optional.empty();
        }
        return Optional.of(OrganisationUnit.OrganisationUnitBuilder.anOrganisationUnit()
                .withId(getStringOrElse(payload, "id", null))
                .withDefaultStartTime(getStringOrElse(payload, "defaultStartTime", null))
                .build());
    }

    private BusinessType toBusinessType(JsonObject jsonObject) {
        return BusinessType.BusinessTypeBuilder.aBusinessType()
                .withId(jsonObject.getString("id"))
                .withSeqNum(jsonObject.getInt("seqNum"))
                .withTypeCode(jsonObject.getString("typeCode"))
                .withTypeDescription(jsonObject.getString("typeDescription"))
                .withSlot(jsonObject.getBoolean("slot"))
                .withDuration(jsonObject.getBoolean("duration"))
                .withJurisdiction(getStringOrElse(jsonObject, "jurisdiction", null))
                .build();
    }

    private CourtRoom toCourtRoom(JsonObject jsonObject) {
        return CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withId(jsonObject.getString("id"))
                .withRotaLocationId(jsonObject.getInt(LOCATION_ID))
                .withRotaVenueName(jsonObject.getString(VENUE_NAME))
                .withCppCourtRoomId(jsonObject.getInt("cppCourtRoomId"))
                .withRotaVenueId(getIntOrElse(jsonObject,"rotaVenueId", null))
                .withOucode(jsonObject.getString("oucode"))
                .withOucodeL3Name(getStringOrElse(jsonObject, "oucodeL3Name", null))
                .withOucodeL2Name(getStringOrElse(jsonObject, "oucodeL2Name", null))
                .withOucodeL2Code(getStringOrElse(jsonObject, "oucodeL2Code", null))
                .withOucodeUUID(jsonObject.getString("oucodeUUID"))
                .withCourtRoomName(getStringOrElse(jsonObject, "courtroomName", null))
                .withCourtRoomId(getStringOrElse(jsonObject, COURTROOM_ID, null))
                .build();
    }

    private Judiciary toJudiciary(JsonObject jsonObject) {
        final List<uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType> specialisms = new ArrayList<>();
        JsonObjects.getJsonArray(jsonObject, "specialisms").ifPresent(specialismsJsonArray -> {
            for (JsonValue specialismValue : specialismsJsonArray) {
                final String specialismString = specialismValue.getValueType() == JsonValue.ValueType.STRING
                        ? ((jakarta.json.JsonString) specialismValue).getString()
                        : specialismValue.toString();
                try {
                    final uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType specialismType =
                            uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType.valueOf(specialismString);
                    specialisms.add(specialismType);
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown specialism value received from referencedata service: {}", specialismString);
                }
            }
        });

        return Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(getStringOrElse(jsonObject, "id", null))
                .withCpUserId(getStringOrElse(jsonObject, "cpUserId", null))
                .withEmailAddress(getStringOrElse(jsonObject, "emailAddress", null))
                .withJudiciaryType(getStringOrElse(jsonObject, "judiciaryType", null))
                .withPersonId(getStringOrElse(jsonObject, "personId", null))
                .withSurname(jsonObject.getString("surname"))
                .withForenames(jsonObject.getString("forenames"))
                .withSeqId(jsonObject.getInt("seqId"))
                .withTitleJudicialPrefix(getStringOrElse(jsonObject, "titleJudicialPrefix", null))
                .withTitleJudicialPrefixWelsh(getStringOrElse(jsonObject, "titleJudicialPrefixWelsh", null))
                .withTitleSuffix(getStringOrElse(jsonObject, "titleSuffix", null))
                .withTitleSuffixWelsh(getStringOrElse(jsonObject, "titleSuffixWelsh", null))
                .withValidFrom(getStringOrElse(jsonObject, "validFrom", null))
                .withValidTo(getStringOrElse(jsonObject, "validTo", null))
                .withTitlePrefix(getStringOrElse(jsonObject, "titlePrefix", null))
                .withTitlePrefixWelsh(getStringOrElse(jsonObject, "titlePrefixWelsh", null))
                .withSpecialisms(specialisms)
                .withRequestedName(getStringOrElse(jsonObject, "requestedName", null))
                .build();
    }

    private CourtRoomSessionAllocation toCourtRoomSessionAllocation(JsonObject jsonObject) {
        return CourtRoomSessionAllocation.CourtRoomSessionAllocationBuilder.aCourtRoomSessionAllocation()
                .withId(jsonObject.getString("id"))
                .withCourtRoomId(jsonObject.getInt("courtRoomId"))
                .withOucode(jsonObject.getString("oucode"))
                .withMaxSlot(getIntOrElse(jsonObject, "maxSlot", 0))
                .withMaxDurationMins(getIntOrElse(jsonObject, "maxDurationMins", 0))
                .withCourtSession(getStringOrElse(jsonObject, "courtSession", null))
                .withRotaBusinessTypeCode(getStringOrElse(jsonObject, "rotaBusinessTypeCode", null))
                .withValidFrom(getStringOrElse(jsonObject, "validFrom", null))
                .withValidTo(getStringOrElse(jsonObject, "validTo", null))
                .withSessionStartTime(getStringOrElse(jsonObject, "sessionStartTime", null))
                .withSessionEndTime(getStringOrElse(jsonObject, "sessionEndTime", null))
                .build();
    }

    private CourtRoom toCpCourtRoom(JsonObject jsonObject, JsonObject ou) {
        return CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withId(getStringOrElse(jsonObject, "id", null))
                .withCppCourtRoomId(getIntOrElse(jsonObject, "courtroomId", null))
                .withCourtRoomId(String.valueOf(getIntOrElse(jsonObject, "courtroomId", 0)))
                .withCourtRoomName(getStringOrElse(jsonObject, "courtroomName", null))
                .withRotaVenueId(getIntOrElse(jsonObject, "venueId", null))
                .withRotaVenueName(getStringOrElse(jsonObject, "venueName", null))
                .withOucode(getStringOrElse(ou, "oucode", null))
                .withOucodeL3Name(getStringOrElse(ou, "oucodeL3Name", null))
                .withOucodeL2Name(getStringOrElse(ou, "oucodeL2Name", null))
                .withOucodeL2Code(getStringOrElse(ou, "oucodeL2Code", null))
                .withOucodeUUID(getStringOrElse(ou, "id", null))
                .build();
    }

    private String getStringOrElse(final JsonObject jsonObject, final String key, final String defaultValue) {
        return jsonObject.containsKey(key) ? jsonObject.getString(key) : defaultValue;
    }

    private Integer getIntOrElse(final JsonObject jsonObject, final String key, final Integer defaultValue) {
        return jsonObject.containsKey(key) ? jsonObject.getInt(key) : defaultValue;
    }

}
