package uk.gov.moj.cpp.courtscheduler.referencedata.service;

import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.cache.CacheService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    private Requester requester;

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

    public ReferenceDataCache() {
        System.out.println("ReferenceDataCache constructor");
    }

    public Optional<BusinessType> getRotaBusinessTypeByCode(final String businessTypeCode) {
        if (parseBoolean(redisCommonCacheEnabled)) {
            return getBusinessTypeByCodeFromTheCache(businessTypeCode);
        } else {
            return referenceDataService.getRotaBusinessTypeByCode(businessTypeCode, requester);
        }
    }

    public Optional<CourtRoom> getRotaCourtRoomByCourtRoomId(final String courtRoomId) {
        if (parseBoolean(redisCommonCacheEnabled) ) {
            return getCourtRoomByIdFromTheCache(courtRoomId);
        } else {
            return referenceDataService.getRotaCourtRoomByCourtRoomId(courtRoomId,requester);
        }
    }

    private Optional<BusinessType> getBusinessTypeByCodeFromTheCache(final String businessTypeCode) {
        final String cacheResult = cacheService.get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + businessTypeCode);

        if (isNull(cacheResult)) {
            LOGGER.info("no cache result found for BusinessTypeCode: {} in getBusinessTypeByCodeFromTheCache", businessTypeCode);
            final AtomicReference<BusinessType> businessTypeAtomicReference = new AtomicReference<>();
            return processRotaBusinessTypeMap(businessTypeCode, businessTypeAtomicReference);
        } else {
            LOGGER.info("cacheResult has been found for BusinessTypeCode: {} in getBusinessTypeByCodeFromTheCache", businessTypeCode);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final BusinessType rotaBusinessType = jsonObjectToObjectConverter.convert(cacheResultJsonObject, BusinessType.class);
            return of(rotaBusinessType);
        }
    }

    private Optional<CourtRoom> getCourtRoomByIdFromTheCache(final String courtRoomId) {
        final String cacheResult = cacheService.get(ROTA_COURTROOM_CACHE_PREFIX + courtRoomId);

        if (isNull(cacheResult)) {
            LOGGER.info("no cache result found for courtroomId: {} in getCourtRoomByIdFromTheCache", courtRoomId);
            final AtomicReference<CourtRoom> courtRoomAtomicReference = new AtomicReference<>();
            return processCourtRoomMap(courtRoomId, courtRoomAtomicReference);
        } else {
            LOGGER.info("cacheResult has been found for courtroomId: {} in getBusinessTypeByCodeFromTheCache", courtRoomId);
            final JsonObject cacheResultJsonObject = stringToJsonObjectConverter.convert(cacheResult);
            final CourtRoom courtRoom = jsonObjectToObjectConverter.convert(cacheResultJsonObject, CourtRoom.class);
            return of(courtRoom);
        }
    }

    private Optional<BusinessType> processRotaBusinessTypeMap(final String businessTypeCode, final AtomicReference<BusinessType> businessTypeForCode) {
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

    private Optional<CourtRoom> processCourtRoomMap(final String courtRoomId, final AtomicReference<CourtRoom> courtRoomForId) {
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
