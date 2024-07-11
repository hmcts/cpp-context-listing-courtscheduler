package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.cache.CacheService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
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

    @Inject
    @Value(key = "redisCommonCacheEnabled", defaultValue = "false")
    private String redisCommonCacheEnabled;


    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    public static final String ROTA_BUSINESS_TYPE_CACHE_PREFIX = "RotaBusinessType_";
    public static final String ROTA_COURTROOM_CACHE_PREFIX = "RotaCourtRoom_";
    public static final String ROTA_BUSINESS_TYPES_CACHE_KEY = "RotaBusinessTypes";

    public ReferenceDataCache() {
        LOGGER.info("ReferenceDataCache constructor");
    }

    public Optional<BusinessType> getRotaBusinessTypeByCode(final String businessTypeCode, final Requester requester) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            LOGGER.info("redisCommonCacheEnabled is true");
            return getBusinessTypeByCodeFromTheCache(businessTypeCode,requester);
        } else {
            LOGGER.info("redisCommonCacheEnabled is false");
            return referenceDataService.getRotaBusinessTypeByCode(businessTypeCode, requester);
        }
    }

    public List<BusinessType> getRotaBusinessTypes(final Requester requester) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getBusinessTypesFromTheCache(requester);
        } else {
            return referenceDataService.getRotaBusinessTypes(requester);
        }
    }

    public Optional<CourtRoom> getRotaCourtRoomByCourtRoomId(final String courtRoomId, final Requester requester) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getCourtRoomByIdFromTheCache(courtRoomId,requester);
        } else {
            return referenceDataService.getRotaCourtRoomByCourtRoomId(courtRoomId, requester);
        }
    }

    private Optional<BusinessType> getBusinessTypeByCodeFromTheCache(final String businessTypeCode,Requester requester) {
        final String cacheResult = cacheService.get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + businessTypeCode);

        if (isNull(cacheResult)) {
            LOGGER.info("no cache result found for BusinessTypeCode: {} in getBusinessTypeByCodeFromTheCache", businessTypeCode);
            final AtomicReference<BusinessType> businessTypeAtomicReference = new AtomicReference<>();
            return processRotaBusinessTypeMap(businessTypeCode, businessTypeAtomicReference,requester);
        } else {
            LOGGER.info("cacheResult has been found for BusinessTypeCode: {} in getBusinessTypeByCodeFromTheCache", businessTypeCode);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final BusinessType rotaBusinessType = jsonObjectToObjectConverter.convert(cacheResultJsonObject, BusinessType.class);
            return of(rotaBusinessType);
        }
    }

    private List<BusinessType> getBusinessTypesFromTheCache(Requester requester) {
        final String cacheResult = cacheService.get(ROTA_BUSINESS_TYPES_CACHE_KEY);

        if (isNull(cacheResult)) {
            LOGGER.info("no cache result found for businessTypes in getBusinessTypesFromTheCache");
            return processRotaBusinessTypes(requester);
        } else {
            try {
                LOGGER.info("cacheResult has been found for BusinessTypes in getBusinessTypesFromTheCache");
                return objectMapper.readValue(cacheResult, new TypeReference<List<BusinessType>>(){});
            } catch (final JsonProcessingException jsonProcessingException) {
                LOGGER.error("exception whilst reading cacheResult and converting to List<BusinessType> with exception: {}", jsonProcessingException.getMessage(), jsonProcessingException);
            }
            return emptyList();
        }
    }

    private Optional<CourtRoom> getCourtRoomByIdFromTheCache(final String courtRoomId, final Requester requester) {
        final String cacheResult = cacheService.get(ROTA_COURTROOM_CACHE_PREFIX + courtRoomId);

        if (isNull(cacheResult)) {
            LOGGER.info("no cache result found for courtroomId: {} in getCourtRoomByIdFromTheCache", courtRoomId);
            final AtomicReference<CourtRoom> courtRoomAtomicReference = new AtomicReference<>();
            return processCourtRoomMap(courtRoomId, courtRoomAtomicReference,requester);
        } else {
            LOGGER.info("cacheResult has been found for courtroomId: {} in getBusinessTypeByCodeFromTheCache", courtRoomId);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final CourtRoom courtRoom = jsonObjectToObjectConverter.convert(cacheResultJsonObject, CourtRoom.class);
            return of(courtRoom);
        }
    }

    private List<BusinessType> processRotaBusinessTypes(Requester requester) {
        final List<BusinessType> rotaBusinessTypes = referenceDataService.getRotaBusinessTypes(requester);

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

    private Optional<BusinessType> processRotaBusinessTypeMap(final String businessTypeCode, final AtomicReference<BusinessType> businessTypeForCode,Requester requester) {
        final Map<String, BusinessType> rotaBusinessTypesMap = referenceDataService.getRotaBusinessTypesMap(requester);

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

            return of(businessTypeForCode.get());
        }
        return empty();
    }

    private Optional<CourtRoom> processCourtRoomMap(final String courtRoomId, final AtomicReference<CourtRoom> courtRoomForId,Requester requester) {
        final Map<UUID, CourtRoom> courtRoomsMap = referenceDataService.getCourtRoomsMap(requester);
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

            return of(courtRoomForId.get());
        }
        return empty();
    }
}
