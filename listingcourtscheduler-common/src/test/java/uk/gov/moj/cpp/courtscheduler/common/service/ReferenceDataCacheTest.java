package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.String.format;
import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_BUSINESS_TYPES_CACHE_KEY;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_BUSINESS_TYPE_CACHE_PREFIX;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_COURTROOMS_CACHE_KEY;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_COURTROOM_CACHE_PREFIX;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.ROTA_JUDICIARIES_CACHE_KEY;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.CP_COURTROOMS_BY_ID_CACHE_PREFIX;

import uk.gov.moj.cpp.courtscheduler.common.converter.JsonObjectToObjectConverter;
import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;

import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.cache.CacheService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    @Spy
    private StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
    @Spy
    private JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

    @InjectMocks
    private ReferenceDataCache referenceDataCache;

    private static final String BUSINESS_TYPE_CODE = "DVLA";
    private static final String COURT_ROOM_ID = randomUUID().toString();
    private static final Integer LOCATION_ID = 77;
    private static final Integer VENUE_ID = 23917;
    private static final String VENUE_NAME = "Court 8";

    private ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        setField(this.jsonObjectToObjectConverter, "objectMapper", objectMapper);
        setField(this.referenceDataCache, "redisCommonCacheKey5MinsTTL", "300");
    }

    @Test
    void shouldReturnBusinessTypeFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setBusinessTypeCache();

        referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE);
        verify(cacheService).get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + BUSINESS_TYPE_CODE);
    }

    @Test
    void shouldReturnBusinessTypeFromServiceWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();

        when(cacheService.get(ROTA_BUSINESS_TYPE_CACHE_PREFIX + BUSINESS_TYPE_CODE)).thenReturn(null);
        when(referenceDataService.getRotaBusinessTypesMap()).thenReturn(Map.of(BUSINESS_TYPE_CODE, BusinessType.BusinessTypeBuilder.aBusinessType().withTypeCode(BUSINESS_TYPE_CODE).build()));
        referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE);

        verify(referenceDataService, atLeastOnce()).getRotaBusinessTypesMap();
    }

    @Test
    void shouldReturnBusinessTypeFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        when(referenceDataService.getRotaBusinessTypeByCode(eq(BUSINESS_TYPE_CODE))).thenReturn(Optional.of(new BusinessType()));
        referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE);
        verify(referenceDataService).getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE);
    }

    @Test
    void shouldReturnRotaBusinessTypesFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setBusinessTypesCache();
        referenceDataCache.getRotaBusinessTypes();
        verify(cacheService).get(ROTA_BUSINESS_TYPES_CACHE_KEY);
    }

    @Test
    void shouldReturnEmptyListForRotaBusinessTypesIfTheReturnDataIsNotCorrect() {
        setCommonCacheEnabled();
        when(cacheService.get(ROTA_BUSINESS_TYPES_CACHE_KEY)).thenReturn("corrupted data");

        final List<BusinessType> businessTypes = referenceDataCache.getRotaBusinessTypes();

        assertTrue(isEmpty(businessTypes));
        verify(cacheService).get(ROTA_BUSINESS_TYPES_CACHE_KEY);
    }

    @Test
    void shouldReturnBusinessTypesFromServiceWhenCacheEnableHoweverNotInTheCache() {
        setCommonCacheEnabled();

        when(cacheService.get(ROTA_BUSINESS_TYPES_CACHE_KEY)).thenReturn(null);
        when(referenceDataService.getRotaBusinessTypes()).thenReturn(List.of(new BusinessType(), new BusinessType()));

        referenceDataCache.getRotaBusinessTypes();
        verify(referenceDataService).getRotaBusinessTypes();
    }

    @Test
    void shouldReturnBusinessTypesFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        when(referenceDataService.getRotaBusinessTypes()).thenReturn(List.of(new BusinessType()));
        referenceDataCache.getRotaBusinessTypes();
        verify(referenceDataService).getRotaBusinessTypes();
    }

    @Test
    void shouldReturnEmptyListWhenTheReturnTypeDifferent() {
        setCommonCacheEnabled();

        when(cacheService.get(ROTA_BUSINESS_TYPES_CACHE_KEY)).thenReturn("corrupted json data");

        final List<BusinessType> businessTypes = referenceDataCache.getRotaBusinessTypes();

        assertTrue(isEmpty(businessTypes));
        verify(cacheService).get(ROTA_BUSINESS_TYPES_CACHE_KEY);
    }

    @Test
    void shouldReturnCourtRoomsFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setCourtRoomsCache();

        referenceDataCache.getCourtRooms();

        verify(cacheService).get(ROTA_COURTROOMS_CACHE_KEY);
    }

    @Test
    void shouldReturnCourtRoomsFromServiceWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();
        when(cacheService.get(ROTA_COURTROOMS_CACHE_KEY)).thenReturn(null);
        when(referenceDataService.getRotaCourtRoomMappings()).thenReturn(List.of(new CourtRoom()));

        referenceDataCache.getCourtRooms();

        verify(referenceDataService).getRotaCourtRoomMappings();
    }

    @Test
    void shouldReturnCourtRoomsFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        when(referenceDataService.getRotaCourtRoomMappings()).thenReturn(List.of(new CourtRoom()));

        referenceDataCache.getCourtRooms();

        verify(referenceDataService).getRotaCourtRoomMappings();
    }

    @Test
    void shouldReturnJudiciariesFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setJudiciariesCache();

        referenceDataCache.getJudiciaries();

        verify(cacheService).get(ROTA_JUDICIARIES_CACHE_KEY);
    }

    @Test
    void shouldReturnJudiciariesFromServiceWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();
        when(cacheService.get(ROTA_JUDICIARIES_CACHE_KEY)).thenReturn(null);
        when(referenceDataService.getJudiciariesMap()).thenReturn(List.of(new Judiciary()));

        referenceDataCache.getJudiciaries();

        verify(referenceDataService).getJudiciariesMap();
    }

    @Test
    void shouldReturnJudiciariesFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        when(referenceDataService.getJudiciariesMap()).thenReturn(List.of(new Judiciary()));

        referenceDataCache.getJudiciaries();

        verify(referenceDataService).getJudiciariesMap();
    }

    @Test
    void shouldReturnEmptyListForJudiciariesWhenTheReturnTypeDifferent() {
        setCommonCacheEnabled();

        when(cacheService.get(ROTA_JUDICIARIES_CACHE_KEY)).thenReturn("corrupted json data");

        final List<Judiciary> judiciaries = referenceDataCache.getJudiciaries();

        assertTrue(isEmpty(judiciaries));
        verify(cacheService).get(ROTA_JUDICIARIES_CACHE_KEY);
    }

    @Test
    void shouldReturnCourtRoomSessionAllocationsFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setCourtRoomSessionAllocationsCache();

        referenceDataCache.getCourtRoomSessionAllocations();

        verify(cacheService).get(ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY);
    }

    @Test
    void shouldReturnCourtRoomSessionAllocationsFromServiceWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();

        when(cacheService.get(ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY)).thenReturn(null);
        when(referenceDataService.getCourtRoomSessionAllocationsMap()).thenReturn(List.of(CourtRoomSessionAllocation.CourtRoomSessionAllocationBuilder.aCourtRoomSessionAllocation().build()));

        referenceDataCache.getCourtRoomSessionAllocations();

        verify(referenceDataService).getCourtRoomSessionAllocationsMap();
    }


    @Test
    void shouldReturnCourtRoomSessionAllocationsFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        when(referenceDataService.getCourtRoomSessionAllocationsMap()).thenReturn(List.of(CourtRoomSessionAllocation.CourtRoomSessionAllocationBuilder.aCourtRoomSessionAllocation().build()));

        referenceDataCache.getCourtRoomSessionAllocations();

        verify(referenceDataService).getCourtRoomSessionAllocationsMap();
    }

    @Test
    void shouldReturnCourtRoomFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setCourtRoomCache();
        referenceDataCache.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID);
        verify(cacheService).get(ROTA_COURTROOM_CACHE_PREFIX + COURT_ROOM_ID);
    }

    @Test
    void shouldReturnCourtRoomFromCacheWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();

        when(cacheService.get(ROTA_COURTROOM_CACHE_PREFIX + COURT_ROOM_ID)).thenReturn(null);
        when(referenceDataService.getCourtRoomsMap()).thenReturn(Map.of(fromString(COURT_ROOM_ID), CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomId(COURT_ROOM_ID).build()));
        referenceDataCache.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID);
        verify(cacheService).get(ROTA_COURTROOM_CACHE_PREFIX + COURT_ROOM_ID);
    }

    @Test
    void shouldReturnCourtRoomFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();

        referenceDataCache.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID);
        verify(referenceDataService).getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID);
    }

    @Test
    void shouldReturnCourtRoomByVenueFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setCourtRoomByVenueCache();

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
    }

    @Test
    void shouldReturnOneOfCourtRoomsHavingSameLocationIdAndVenueNameByVenueFromCacheWhenCacheEnabledAnd() {
        setCommonCacheEnabled();
        setCourtRoomWithMultipleValuesHavingSameLocationIdAndVenueNameByVenueCache();

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
    }

    @Test
    void shouldReturnCourtRoomByVenueFromCacheWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();
        when(cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME))).thenReturn(null);
        when(referenceDataService.getRotaCourtRoomMappings()).thenReturn(List.of(CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withRotaLocationId(LOCATION_ID)
                .withRotaVenueName(VENUE_NAME)
                .withRotaVenueId(VENUE_ID)
                .withCourtRoomId(COURT_ROOM_ID).build()));

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
        verify(referenceDataService, atLeastOnce()).getRotaCourtRoomMappings();
    }

    @Test
    void shouldReturnCourtRoomByVenueFromCacheWhenCacheEnabledHoweverNotInTheCacheAndDealWithMultipleMatching() throws JsonProcessingException {
        setCommonCacheEnabled();
        when(cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME))).thenReturn(null);
        when(referenceDataService.getRotaCourtRoomMappings()).thenReturn(courtRoomsFromReferenceData());

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
        verify(referenceDataService, atLeastOnce()).getRotaCourtRoomMappings();
    }

    @Test
    void shouldReturnOneOfCourtRoomsHavingSameLocationIdAndVenueNameByVenueFromCacheWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();
        when(cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME))).thenReturn(null);
        when(referenceDataService.getRotaCourtRoomMappings()).thenReturn(List.of(
                CourtRoom.CourtRoomBuilder.aCourtRoom()
                        .withRotaLocationId(LOCATION_ID)
                        .withRotaVenueName(VENUE_NAME)
                        .withCppCourtRoomId(2345)
                        .withCourtRoomId(COURT_ROOM_ID).build(),
                CourtRoom.CourtRoomBuilder.aCourtRoom()
                        .withRotaLocationId(LOCATION_ID)
                        .withRotaVenueName(VENUE_NAME)
                        .withCppCourtRoomId(2346)
                        .withCourtRoomId(COURT_ROOM_ID).build()));

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertNull(courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
        verify(referenceDataService, atLeastOnce()).getRotaCourtRoomMappings();
    }

    @Test
    void shouldReturnCourtRoomEvenVenueIdNotMatchingButLoggedByVenueFromCacheWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();
        when(cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME))).thenReturn(null);
        when(referenceDataService.getRotaCourtRoomMappings()).thenReturn(List.of(
                CourtRoom.CourtRoomBuilder.aCourtRoom()
                        .withRotaLocationId(LOCATION_ID)
                        .withRotaVenueName(VENUE_NAME)
                        .withCppCourtRoomId(2345)
                        .withCourtRoomId(COURT_ROOM_ID).build()));

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertNull(courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
        verify(referenceDataService, atLeastOnce()).getRotaCourtRoomMappings();
    }

    @Test
    void shouldReturnCourtRoomByVenueFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();
        final Venue venue = new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME);
        when(referenceDataService.getRotaCourtRoomByVenue(eq(venue), anyMap()))
                .thenReturn(Optional.of(CourtRoom.CourtRoomBuilder.aCourtRoom()
                        .withRotaLocationId(LOCATION_ID)
                        .withRotaVenueName(VENUE_NAME)
                        .withRotaVenueId(VENUE_ID)
                        .withCourtRoomId(COURT_ROOM_ID).build()));

        final Optional<CourtRoom> courtRoomOptional = referenceDataCache.getCourtRoomByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>());

        assertTrue(courtRoomOptional.isPresent());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        verify(cacheService, never()).get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME));
        verify(referenceDataService, atLeastOnce()).getRotaCourtRoomByVenue(eq(venue), anyMap());
    }

    @Test
    void shouldReturnCpCourtRoomFromCacheWhenCacheEnabled() {
        setCommonCacheEnabled();
        setCpCourtRoomCache();
        referenceDataCache.getCpCourtRoomByCourtRoomId(COURT_ROOM_ID);
        verify(cacheService).get(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID);
    }

    @Test
    void shouldReturnCpCourtRoomFromCacheWhenCacheEnabledHoweverNotInTheCache() {
        setCommonCacheEnabled();
        when(cacheService.get(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID)).thenReturn(null);
        when(referenceDataService.getCpCourtRooms()).thenReturn(List.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).build()));

        referenceDataCache.getCpCourtRoomByCourtRoomId(COURT_ROOM_ID);
        verify(cacheService).get(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID);
        verify(referenceDataService).getCpCourtRooms();
    }

    @Test
    void shouldReturnCpCourtRoomFromServiceWhenCacheDisabled() {
        setCommonCacheDisabled();
        when(referenceDataService.getCpCourtRooms()).thenReturn(List.of(CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).build()));

        referenceDataCache.getCpCourtRoomByCourtRoomId(COURT_ROOM_ID);
        verify(referenceDataService).getCpCourtRooms();
    }

    @Test
    void shouldReturnAllCourtCentreMembershipsForCpCourtRoomSharedBetweenCourtCentres() {
        setCommonCacheDisabled();
        final String centreA = "161141dc-a01f-3b0a-85d1-7a90a8099b6a";
        final String centreB = "049b5d11-e3dd-356f-b742-bd5e71eb7af6";
        when(referenceDataService.getCpCourtRooms()).thenReturn(List.of(
                CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).withOucodeUUID(centreA).build(),
                CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).withOucodeUUID(centreB).build()));

        final List<CourtRoom> memberships = referenceDataCache.getCpCourtRoomsByCourtRoomId(COURT_ROOM_ID);
        assertEquals(2, memberships.size());

        final Optional<CourtRoom> courtRoomForCentreB = referenceDataCache.getCpCourtRoomByCourtRoomIdAndCourtCentreId(COURT_ROOM_ID, centreB);
        assertTrue(courtRoomForCentreB.isPresent());
        assertEquals(centreB, courtRoomForCentreB.get().getOucodeUUID());
    }

    @Test
    void shouldIgnoreCpCourtRoomsWithoutIdWhenPopulatingCacheOnMiss() {
        // toCpCourtRoom allows a null id; an id-less courtroom in the reference data must be
        // skipped, not break cache population for every other courtroom
        setCommonCacheEnabled();
        when(cacheService.get(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID)).thenReturn(null);
        when(referenceDataService.getCpCourtRooms()).thenReturn(List.of(
                CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomName("no id").build(),
                CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).build()));

        final List<CourtRoom> memberships = referenceDataCache.getCpCourtRoomsByCourtRoomId(COURT_ROOM_ID);

        assertEquals(1, memberships.size());
        assertEquals(COURT_ROOM_ID, memberships.get(0).getId());
    }

    @Test
    void shouldIgnoreCpCourtRoomsWithoutIdWhenCacheDisabled() {
        setCommonCacheDisabled();
        when(referenceDataService.getCpCourtRooms()).thenReturn(List.of(
                CourtRoom.CourtRoomBuilder.aCourtRoom().withCourtRoomName("no id").build(),
                CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).build()));

        final List<CourtRoom> memberships = referenceDataCache.getCpCourtRoomsByCourtRoomId(COURT_ROOM_ID);

        assertEquals(1, memberships.size());
        assertEquals(COURT_ROOM_ID, memberships.get(0).getId());
    }

    @Test
    void shouldCacheAllMembershipsOfCpCourtRoomSharedBetweenCourtCentresWhenCacheEnabled() {
        setCommonCacheEnabled();
        final String centreA = "161141dc-a01f-3b0a-85d1-7a90a8099b6a";
        final String centreB = "049b5d11-e3dd-356f-b742-bd5e71eb7af6";
        when(cacheService.get(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID)).thenReturn(null);
        when(referenceDataService.getCpCourtRooms()).thenReturn(List.of(
                CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).withOucodeUUID(centreA).build(),
                CourtRoom.CourtRoomBuilder.aCourtRoom().withId(COURT_ROOM_ID).withOucodeUUID(centreB).build()));

        final List<CourtRoom> memberships = referenceDataCache.getCpCourtRoomsByCourtRoomId(COURT_ROOM_ID);

        assertEquals(2, memberships.size());
        verify(cacheService).add(eq(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID), contains(centreB));
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

    private void setBusinessTypesCache() {
        final String businessTypesJsonStr = FileUtil.getPayload("test-data/business-types.json");
        when(cacheService.get(ROTA_BUSINESS_TYPES_CACHE_KEY)).thenReturn(businessTypesJsonStr);
    }

    private void setJudiciariesCache() {
        final String judiciariesJsonStr = FileUtil.getPayload("test-data/referencedata-judiciaries.json");
        when(cacheService.get(ROTA_JUDICIARIES_CACHE_KEY)).thenReturn(judiciariesJsonStr);
    }

    private void setCourtRoomsCache() {
        final String judiciariesJsonStr = FileUtil.getPayload("test-data/reference-data-court-rooms.json");
        when(cacheService.get(ROTA_COURTROOMS_CACHE_KEY)).thenReturn(judiciariesJsonStr);
    }

    private void setCourtRoomSessionAllocationsCache() {
        final String courtRoomSessionAllocationsJsonStr = FileUtil.getPayload("test-data/referencedata-court-room-session-allocations.json");
        when(cacheService.get(ROTA_COURT_ROOM_SESSION_ALLOCATIONS_KEY)).thenReturn(courtRoomSessionAllocationsJsonStr);
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

    private void setCpCourtRoomCache() {
        when(cacheService.get(CP_COURTROOMS_BY_ID_CACHE_PREFIX + COURT_ROOM_ID)).thenReturn("[{\"id\":\"" + COURT_ROOM_ID + "\"}]");
    }

    private void setCourtRoomByVenueCache() {
        when(cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME))).thenReturn(" [{\n" +
                "      \"id\": \"26de1ba8-fad7-3747-81e2-0dc6dce6ed7a\",\n" +
                "      \"rotaLocationId\": 77,\n" +
                "      \"rotaVenueName\": \"Court 8\",\n" +
                "      \"cppCourtRoomId\": 2034,\n" +
                "      \"rotaVenueId\": 23917,\n" +
                "      \"oucode\": \"B43KQ00\",\n" +
                "      \"oucodeL3Name\": \"Reading Magistrates' Court\",\n" +
                "      \"oucodeL2Name\": \"Thames Valley\",\n" +
                "      \"oucodeL2Code\": \"43\",\n" +
                "      \"oucodeUUID\": \"49db2271-1941-3847-a7fb-dbd92b035e40\",\n" +
                "      \"courtroomName\": \"Courtroom 08\",\n" +
                "      \"courtroomId\": \"7b5c87f5-d964-3311-a700-c40f67213cd5\"\n" +
                "    }]");

    }

    private void setCourtRoomWithMultipleValuesHavingSameLocationIdAndVenueNameByVenueCache() {
        when(cacheService.get(format(ROTA_COURTROOM_BY_VENUE_CACHE_PREFIX, LOCATION_ID, VENUE_NAME))).thenReturn(" [{\n" +
                "      \"id\": \"26de1ba8-fad7-3747-81e2-0dc6dce6ed7a\",\n" +
                "      \"rotaLocationId\": 77,\n" +
                "      \"rotaVenueName\": \"Court 8\",\n" +
                "      \"cppCourtRoomId\": 2034,\n" +
                "      \"rotaVenueId\": 23917,\n" +
                "      \"oucode\": \"B43KQ00\",\n" +
                "      \"oucodeL3Name\": \"Reading Magistrates' Court\",\n" +
                "      \"oucodeL2Name\": \"Thames Valley\",\n" +
                "      \"oucodeL2Code\": \"43\",\n" +
                "      \"oucodeUUID\": \"49db2271-1941-3847-a7fb-dbd92b035e40\",\n" +
                "      \"courtroomName\": \"Courtroom 08\",\n" +
                "      \"courtroomId\": \"7b5c87f5-d964-3311-a700-c40f67213cd5\"\n" +
                "    }, \n {\n" +
                "      \"id\": \"26de1ba8-fad7-3747-81e2-0dc6dce6ed7a\",\n" +
                "      \"rotaLocationId\": 77,\n" +
                "      \"rotaVenueName\": \"Court 8\",\n" +
                "      \"cppCourtRoomId\": 2035,\n" +
                "      \"rotaVenueId\": 24253,\n" +
                "      \"oucode\": \"B43KQ00\",\n" +
                "      \"oucodeL3Name\": \"Reading Magistrates' Court\",\n" +
                "      \"oucodeL2Name\": \"Thames Valley\",\n" +
                "      \"oucodeL2Code\": \"43\",\n" +
                "      \"oucodeUUID\": \"49db2271-1941-3847-a7fb-dbd92b035e40\",\n" +
                "      \"courtroomName\": \"Courtroom 08\",\n" +
                "      \"courtroomId\": \"7b5c87f5-d964-3311-a700-c40f67213cd5\"\n" +
                "    }]");

    }

    private void setCommonCacheEnabled() {
        setField(referenceDataCache, "redisCommonCacheEnabled", "true");
    }
    private void setCommonCacheDisabled() {
        setField(referenceDataCache, "redisCommonCacheEnabled", "false");
    }

    private List<CourtRoom> courtRoomsFromReferenceData() throws JsonProcessingException {
        final String courtRoomsFromRefDataJsonStr = uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString("/test-data/reference-data-court-rooms.json");

        return objectMapper.readValue(courtRoomsFromRefDataJsonStr, new TypeReference<List<CourtRoom>>(){});
    }

}