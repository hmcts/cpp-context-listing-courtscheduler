package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.courtscheduler.api.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME;
import static uk.gov.moj.cpp.courtscheduler.api.helper.SessionsHelper.mockBusinessType;
import static uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataCache.ROTA_BUSINESS_TYPE_CACHE_PREFIX;
import static uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataCache.ROTA_COURTROOM_CACHE_PREFIX;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.moj.cpp.courtscheduler.cache.CacheService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;

import java.util.Optional;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataCacheTest {

    @Mock
    private CacheService cacheService;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private Requester requester;
    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter;
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @InjectMocks
    private ReferenceDataCache referenceDataCache;

    private static final String BUSINESS_TYPE_CODE = "DVLA";
    private static final String COURT_ROOM_ID = randomUUID().toString();


    @BeforeEach
    void setUp() {
        setField(this.jsonObjectToObjectConverter, "objectMapper", new ObjectMapperProducer().objectMapper());
    }

    @Test
    void shouldReturnBusinessTypeFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setBusinessTypeCache();
        referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE,requester);
        verify(cacheService).get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + BUSINESS_TYPE_CODE);
    }

    @Test
    void shouldReturnBusinessTypeFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();
        final JsonObject responsePayload = mockBusinessType(BUSINESS_TYPE_CODE);

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), responsePayload);
        when(referenceDataService.getRotaBusinessTypeByCode(eq(BUSINESS_TYPE_CODE), eq(requester))).thenReturn(Optional.of(new BusinessType()));
        referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE,requester);
        verify(referenceDataService).getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE, requester);
    }

    @Test
    void shouldReturnCourtRoomFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setCourtRoomCache();
        referenceDataCache.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID,requester);
        verify(cacheService).get(ROTA_COURTROOM_CACHE_PREFIX + COURT_ROOM_ID);
    }

    @Test
    void shouldReturnCourtRoomFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        referenceDataCache.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID,requester);
        verify(referenceDataService).getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID, requester);
    }

    private void setBusinessTypeCache() {
        when(cacheService.get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + BUSINESS_TYPE_CODE)).thenReturn(" {\n" +
                "      \"id\": \"0c90ad7e-7c8d-3bd6-a52d-c4b7ec107a78\",\n" +
                "      \"seqNum\": 120,\n" +
                "      \"typeCode\": \"DVLA\",\n" +
                "      \"typeDescription\": \"DVLA\",\n" +
                "      \"slot\": true,\n" +
                "      \"duration\": false\n" +
                "    }");
    }

    private void setCourtRoomCache() {
        when(cacheService.get(ROTA_COURTROOM_CACHE_PREFIX + COURT_ROOM_ID)).thenReturn(" {\n" +
                "      \"id\": \"0a48cb96-48d8-3f1c-a8dd-3d45b40b0ff5\",\n" +
                "      \"rotaLocationId\": 27,\n" +
                "      \"rotaVenueName\": \"Court 01\",\n" +
                "      \"cppCourtRoomId\": 1769,\n" +
                "      \"rotaVenueId\": 0,\n" +
                "      \"oucode\": \"B12JR00\",\n" +
                "      \"oucodeL3Name\": \"Northallerton Magistrates' Court\",\n" +
                "      \"oucodeL2Name\": \"North Yorkshire\",\n" +
                "      \"oucodeL2Code\": \"12\",\n" +
                "      \"oucodeUUID\": \"22c69328-70af-3e27-80c5-1a79e24903d2\",\n" +
                "      \"courtroomName\": \"Courtroom 01\",\n" +
                "      \"courtroomId\": \"2bd129f3-780e-37dd-b9aa-48690f91b69c\"\n" +
                "    }");

    }

    private void setCommonCacheEnabled() {
        setField(referenceDataCache, "redisCommonCacheEnabled", "true");
    }
    private void setCommonCacheDisabled() {
        setField(referenceDataCache, "redisCommonCacheEnabled", "false");
    }

}