package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.utils.VenueNameComparator.matches;

import org.springframework.beans.factory.annotation.Value;
import uk.gov.moj.cpp.courtscheduler.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.cache.CacheService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReferenceDataCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceDataCache.class);
    @Inject
    private CacheService cacheService;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private StringToJsonObjectConverter stringToJsonObjectConverter;

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Value("${redis.common-cache.enabled:false}")
    private String redisCommonCacheEnabled;

    @Value("${redis.common-cache.key-5-mins-ttl:300}")
    private String redisCommonCacheKey5MinsTTL;

    private static final String COURT_DETAIL_NOT_FOUND = "COURT_DETAIL_NOT_FOUND";
    private static final String COURT_ROOM_FETCHED_BY_VENUE_NAME = "CourtRoom fetched by VenueName: %s%n,can't find by VenueId:%s%n";
    private static final String MULTIPLE_COURTROOMS_FOUND_BY_VENUE_NAME = "Multiple courtrooms found by VenueName : %s%n , but VenueId: %s%n selected by created_on";

    private static final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public static final String ROTA_BUSINESS_TYPE_CACHE_PREFIX = "RotaBusinessType_";
    public static final String ROTA_COURTROOM_CACHE_PREFIX = "RotaCourtRoom_";
    public static final String CP_COURTROOM_CACHE_PREFIX = "CpCourtRoom_";
    public static final String ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX = "RotaCourtRoomByVenue_%d_%s";
    public static final String ROTA_BUSINESS_TYPES_CACHE_KEY = "RotaBusinessTypes";
    public static final String ROTA_JUDICIARIES_CACHE_KEY = "RotaJudiciaries_";
    public static final String ROTA_COURTROOMS_CACHE_KEY = "RotaCourtRooms_";
    public static final String ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY = "RotaCourtRoomSessionAllocations_";

    public ReferenceDataCache() {
        //Default Constructor
    }

    public Optional<BusinessType> getRotaBusinessTypeByCode(final String businessTypeCode) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getBusinessTypeByCodeFromTheCache(businessTypeCode);
        } else {
            return referenceDataService.getRotaBusinessTypeByCode(businessTypeCode);
        }
    }

    public List<BusinessType> getRotaBusinessTypes() {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getBusinessTypesFromTheCache();
        } else {
            return referenceDataService.getRotaBusinessTypes();
        }
    }

    public List<Judiciary> getJudiciaries() {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getJudiciariesFromTheCache();
        } else {
            return referenceDataService.getJudiciariesMap();
        }
    }

    public List<CourtRoom> getCourtRooms() {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getCourtRoomsFromTheCache();
        } else {
            return referenceDataService.getRotaCourtRoomMappings();
        }
    }

    public List<CourtRoomSessionAllocation> getCourtRoomSessionAllocations() {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getCourtRoomSessionAllocationsFromTheCache();
        } else {
            return referenceDataService.getCourtRoomSessionAllocationsMap();
        }
    }

    public Optional<CourtRoom> getCourtRoomByVenue(final Venue venue, final Map<String, String> exceptionMessages) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getCourtRoomByVenueFromTheCache(venue, exceptionMessages);
        } else {
            return referenceDataService.getRotaCourtRoomByVenue(venue, exceptionMessages);
        }
    }

    public Optional<CourtRoom> getRotaCourtRoomByCourtRoomId(final String courtRoomId) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getCourtRoomByIdFromTheCache(courtRoomId);
        } else {
            return referenceDataService.getRotaCourtRoomByCourtRoomId(courtRoomId);
        }
    }

    public Optional<CourtRoom> getCpCourtRoomByCourtRoomId(final String courtRoomId) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getCpCourtRoomByIdFromTheCache(courtRoomId);
        } else {
            return referenceDataService.getCpCourtRooms().stream()
                    .filter(c -> c.getId().equals(courtRoomId))
                    .findFirst();
        }
    }

    private Optional<BusinessType> getBusinessTypeByCodeFromTheCache(final String businessTypeCode) {
        final String cacheResult = cacheService.get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + businessTypeCode);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for BusinessTypeCode: {} in getBusinessTypeByCodeFromTheCache", businessTypeCode);
            final AtomicReference<BusinessType> businessTypeAtomicReference = new AtomicReference<>();
            return processRotaBusinessTypeMap(businessTypeCode, businessTypeAtomicReference);
        } else {
            LOGGER.debug("cacheResult has been found for BusinessTypeCode: {} in getBusinessTypeByCodeFromTheCache", businessTypeCode);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final BusinessType rotaBusinessType = jsonObjectToObjectConverter.convert(cacheResultJsonObject, BusinessType.class);
            return of(rotaBusinessType);
        }
    }

    private List<BusinessType> getBusinessTypesFromTheCache() {
        final String cacheResult = cacheService.get(ROTA_BUSINESS_TYPES_CACHE_KEY);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for businessTypes in getBusinessTypesFromTheCache");
            return processRotaBusinessTypes();
        } else {
            try {
                LOGGER.debug("cacheResult has been found for BusinessTypes in getBusinessTypesFromTheCache");
                return objectMapper.readValue(cacheResult, new TypeReference<>() {
                });
            } catch (final JsonProcessingException jsonProcessingException) {
                LOGGER.error("exception whilst reading cacheResult and converting to List<BusinessType> with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
            }
            return emptyList();
        }
    }

    private List<Judiciary> getJudiciariesFromTheCache() {
        final String cacheResult = cacheService.get(ROTA_JUDICIARIES_CACHE_KEY);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for judiciaries in getJudiciariesFromTheCache");
            return processJudiciaries();
        } else {
            try {
                LOGGER.debug("cacheResult has been found for judiciaries in getJudiciariesFromTheCache for key: {}", ROTA_JUDICIARIES_CACHE_KEY);
                return objectMapper.readValue(cacheResult, new TypeReference<>() {});
            } catch (final JsonProcessingException jsonProcessingException) {
                LOGGER.error("exception whilst reading cacheResult and converting to List<Judiciary> with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
            }
            return emptyList();
        }
    }

    private List<CourtRoom> getCourtRoomsFromTheCache() {
        final String cacheResult = cacheService.get(ROTA_COURTROOMS_CACHE_KEY);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for courtRooms in getCourtRoomsFromTheCache");
            return processCourtRooms();
        } else {
            try {
                LOGGER.debug("cacheResult has been found for courtRooms in getCourtRoomsFromTheCache for key : {}", ROTA_COURTROOMS_CACHE_KEY);
                return objectMapper.readValue(cacheResult, new TypeReference<>() {});
            } catch (final JsonProcessingException jsonProcessingException) {
                LOGGER.error("exception whilst reading cacheResult and converting to List<CourtRoom> with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
            }
            return emptyList();
        }
    }

    private List<CourtRoomSessionAllocation> getCourtRoomSessionAllocationsFromTheCache() {
        final String cacheResult = cacheService.get(ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for courtRoomSessionAllocations in getCourtRoomSessionAllocationsFromTheCache");
            return processCourtRoomSessionAllocations();
        } else {
            try {
                LOGGER.debug("cacheResult has been found for courtRoomSessionAllocations in getCourtRoomSessionAllocationsFromTheCache");
                return objectMapper.readValue(cacheResult, new TypeReference<>() {
                });
            } catch (final JsonProcessingException jsonProcessingException) {
                LOGGER.error("exception whilst reading cacheResult and converting to List<CourtRoomSessionAllocation> with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
            }
            return emptyList();
        }
    }

    private Optional<CourtRoom> getCourtRoomByIdFromTheCache(final String courtRoomId) {
        final String cacheResult = cacheService.get(ROTA_COURTROOM_CACHE_PREFIX + courtRoomId);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for courtroomId: {} in getCourtRoomByIdFromTheCache", courtRoomId);
            final AtomicReference<CourtRoom> courtRoomAtomicReference = new AtomicReference<>();
            return processCourtRoomMap(courtRoomId, courtRoomAtomicReference);
        } else {
            LOGGER.debug("cacheResult has been found for courtroomId: {} in getBusinessTypeByCodeFromTheCache", courtRoomId);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final CourtRoom courtRoom = jsonObjectToObjectConverter.convert(cacheResultJsonObject, CourtRoom.class);
            return of(courtRoom);
        }
    }

    private Optional<CourtRoom> getCpCourtRoomByIdFromTheCache(final String courtRoomId) {
        final String cacheResult = cacheService.get(CP_COURTROOM_CACHE_PREFIX + courtRoomId);

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for cp courtroomId: {} in getCpCourtRoomByIdFromTheCache", courtRoomId);
            final AtomicReference<CourtRoom> courtRoomAtomicReference = new AtomicReference<>();
            return processCpCourtRoomMap(courtRoomId, courtRoomAtomicReference);
        } else {
            LOGGER.debug("cacheResult has been found for cp courtroomId: {} in getCpCourtRoomByIdFromTheCache", courtRoomId);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final CourtRoom courtRoom = jsonObjectToObjectConverter.convert(cacheResultJsonObject, CourtRoom.class);
            return of(courtRoom);
        }
    }

    private Optional<CourtRoom> getCourtRoomByVenueFromTheCache(final Venue venue, final Map<String, String> exceptionMessages) {
        final String cacheResult = cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, venue.getLocationId(), venue.getVenueName()));

        if (isNull(cacheResult)) {
            LOGGER.debug("no cache result found for venue: {} in getCourtRoomByVenueFromTheCache", venue);
            final AtomicReference<CourtRoom> courtRoomsForVenue = new AtomicReference<>();
            return processCourtRoomMapByVenue(venue, courtRoomsForVenue, exceptionMessages);
        } else {
            try {
                LOGGER.debug("cacheResult has been found for venue: {} in getBusinessTypeByCodeFromTheCache", venue);
                final List<CourtRoom> courtRooms = objectMapper.readValue(cacheResult, new TypeReference<>() {});

                final Optional<CourtRoom> courtRoomWithVenueIdOptional = courtRooms.stream().filter(courtRoom -> courtRoom.getRotaVenueId().equals(venue.getVenueId())).findAny();
                return courtRoomWithVenueIdOptional.isPresent() ? courtRoomWithVenueIdOptional : of(courtRooms.get(0));
            } catch (final JsonProcessingException jsonProcessingException) {
                LOGGER.error("exception whilst reading cacheResult for getCourtRoomByVenueFromTheCache and converting to List<CourtRoom> with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
            }
        }
        return Optional.empty();
    }

    private List<BusinessType> processRotaBusinessTypes() {
        final List<BusinessType> rotaBusinessTypes = referenceDataService.getRotaBusinessTypes();

        try {
            if (isNotEmpty(rotaBusinessTypes)) {
                cacheService.add(ROTA_BUSINESS_TYPES_CACHE_KEY, objectMapper.writeValueAsString(rotaBusinessTypes));
                return rotaBusinessTypes;
            }
        } catch (final JsonProcessingException jsonProcessingException) {
            LOGGER.error("exception whilst adding into the cache for BusinessTypes with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
        }
        return emptyList();
    }

    private List<Judiciary> processJudiciaries() {
        final List<Judiciary> judiciaries = referenceDataService.getJudiciariesMap();

        try {
            if (isNotEmpty(judiciaries)) {
                cacheService.add(ROTA_JUDICIARIES_CACHE_KEY, objectMapper.writeValueAsString(judiciaries));
                return judiciaries;
            }
        } catch (final JsonProcessingException jsonProcessingException) {
            LOGGER.error("exception whilst adding into the cache for Judiciaries with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
        }
        return emptyList();
    }

    private List<CourtRoom> processCourtRooms() {
        final List<CourtRoom> courtRooms = referenceDataService.getRotaCourtRoomMappings();

        try {
            if (isNotEmpty(courtRooms)) {
                cacheService.add(ROTA_COURTROOMS_CACHE_KEY, objectMapper.writeValueAsString(courtRooms));
                return courtRooms;
            }
        } catch (final JsonProcessingException jsonProcessingException) {
            LOGGER.error("exception whilst adding into the cache for CourtRooms with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
        }
        return emptyList();
    }

    private List<CourtRoomSessionAllocation> processCourtRoomSessionAllocations() {
        final List<CourtRoomSessionAllocation> courtRoomSessionAllocations = referenceDataService.getCourtRoomSessionAllocationsMap();

        try {
            if (isNotEmpty(courtRoomSessionAllocations)) {
                cacheService.add(ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY, objectMapper.writeValueAsString(courtRoomSessionAllocations), redisCommonCacheKey5MinsTTL());
                return courtRoomSessionAllocations;
            }
        } catch (final JsonProcessingException jsonProcessingException) {
            LOGGER.error("exception whilst adding into the cache for CourtRoomSessionAllocations with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
        }
        return emptyList();
    }

    private Optional<BusinessType> processRotaBusinessTypeMap(final String businessTypeCode, final AtomicReference<BusinessType> businessTypeForCode) {
        final Map<String, BusinessType> rotaBusinessTypesMap = referenceDataService.getRotaBusinessTypesMap();

        if (!rotaBusinessTypesMap.isEmpty()) {
            rotaBusinessTypesMap.forEach((typeCode, businessType) -> {
                try {
                    cacheService.add(ROTA_BUSINESS_TYPE_CACHE_PREFIX + typeCode, objectMapper.writeValueAsString(businessType));
                    if (businessTypeCode.equals(typeCode)) {
                        businessTypeForCode.set(businessType);
                    }
                } catch (final JsonProcessingException jsonProcessingException) {
                    LOGGER.error("exception whilst adding into the cache for BusinessTypeCode: {} with exception: {}", typeCode, jsonProcessingException.getMessage(), jsonProcessingException);
                }
            });

            return ofNullable(businessTypeForCode.get());
        }
        return empty();
    }

    private Optional<CourtRoom> processCourtRoomMap(final String courtRoomId, final AtomicReference<CourtRoom> courtRoomForId) {
        final Map<UUID, CourtRoom> courtRoomsMap = referenceDataService.getCourtRoomsMap();
        if (!courtRoomsMap.isEmpty()) {
            courtRoomsMap.forEach((courtRoomUUID, courtRoom) -> {
                try {
                    cacheService.add(ROTA_COURTROOM_CACHE_PREFIX + courtRoomUUID, objectMapper.writeValueAsString(courtRoom));
                    if (courtRoomId.equals(courtRoomUUID.toString())) {
                        courtRoomForId.set(courtRoom);
                    }
                } catch (final JsonProcessingException jsonProcessingException) {
                    LOGGER.error("exception whilst adding into the cache for courtRoomId: {} with exception: {}", courtRoomUUID, jsonProcessingException.getMessage(), jsonProcessingException);
                }
            });

            return ofNullable(courtRoomForId.get());
        }
        return empty();
    }

    private Optional<CourtRoom> processCpCourtRoomMap(final String courtRoomId, final AtomicReference<CourtRoom> courtRoomForId) {
        final List<CourtRoom> courtRooms = referenceDataService.getCpCourtRooms();
        if (isNotEmpty(courtRooms)) {
            courtRooms.forEach(courtRoom -> {
                try {
                    cacheService.add(CP_COURTROOM_CACHE_PREFIX + courtRoom.getId(), objectMapper.writeValueAsString(courtRoom));
                    if (courtRoomId.equals(courtRoom.getId())) {
                        courtRoomForId.set(courtRoom);
                    }
                } catch (final JsonProcessingException jsonProcessingException) {
                    LOGGER.error("exception whilst adding into the cache for cp courtRoomId: {} with exception: {}", courtRoom.getId(), jsonProcessingException.getMessage(), jsonProcessingException);
                }
            });

            return ofNullable(courtRoomForId.get());
        }
        return empty();
    }

    private Optional<CourtRoom> processCourtRoomMapByVenue(final Venue venue, final AtomicReference<CourtRoom> courtRoomsForVenue, final Map<String, String> exceptionMessages) {
        final List<CourtRoom> courtRooms = referenceDataService.getRotaCourtRoomMappings();
        if (isNotEmpty(courtRooms)) {
            final Map<Integer, Map<String, List<CourtRoom>>> courtRoomGroupByLocationIdAndVenueName = courtRooms.stream().collect(Collectors.groupingBy(CourtRoom::getRotaLocationId, Collectors.groupingBy(CourtRoom::getRotaVenueName)));
            courtRoomGroupByLocationIdAndVenueName.forEach((locationId, mapByVenueName) ->
                    mapByVenueName.keySet().forEach(venueName -> {
                        final List<CourtRoom> courtRoomList = mapByVenueName.get(venueName).stream().filter(courtRoomByLocationId -> courtRoomByLocationId.getRotaLocationId().equals(locationId)).toList();
                        try {
                            cacheService.add(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, locationId, venueName), objectMapper.writeValueAsString(courtRoomList));

                            if (locationId.equals(venue.getLocationId()) && matches(venueName, venue.getVenueName())) {
                                processFoundCourtRoomWithVenue(venue, courtRoomsForVenue, exceptionMessages, courtRoomList);
                            }
                        } catch (final JsonProcessingException jsonProcessingException) {
                            LOGGER.error("exception whilst adding into the cache for locationId: {} and venueName {} with exception: {}", locationId, venueName, jsonProcessingException.getMessage(), jsonProcessingException);
                        }
                    })
            );

            return ofNullable(courtRoomsForVenue.get());
        }
        return empty();
    }

    private static void processFoundCourtRoomWithVenue(final Venue venue, final AtomicReference<CourtRoom> courtRoomsForVenue, final Map<String, String> exceptionMessages, final List<CourtRoom> courtRoomList) {
        final Optional<CourtRoom> courtRoomOptional = courtRoomList.stream().filter(courtRoom -> venue.getVenueId().equals(courtRoom.getRotaVenueId())).findAny();

        if (courtRoomOptional.isPresent()) {
            courtRoomsForVenue.set(courtRoomOptional.get());
        } else {
            if (courtRoomList.size() > 1) {
                exceptionMessages.put(format(MULTIPLE_COURTROOMS_FOUND_BY_VENUE_NAME, venue.getVenueName(), venue.getVenueId()), COURT_DETAIL_NOT_FOUND);
            } else {
                exceptionMessages.put(format(COURT_ROOM_FETCHED_BY_VENUE_NAME, venue.getVenueName(), venue.getVenueId()), COURT_DETAIL_NOT_FOUND);
            }
            courtRoomsForVenue.set(courtRoomList.get(0));
        }
    }

    private Integer redisCommonCacheKey5MinsTTL() {
        return Integer.parseInt(redisCommonCacheKey5MinsTTL);
    }
}
