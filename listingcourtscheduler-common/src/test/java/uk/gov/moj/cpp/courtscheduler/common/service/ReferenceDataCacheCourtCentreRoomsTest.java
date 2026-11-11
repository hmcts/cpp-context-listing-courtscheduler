package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache.CP_COURTROOMS_BY_COURT_CENTRE_CACHE_PREFIX;

import uk.gov.moj.cpp.courtscheduler.cache.CacheService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SPRDT-1550: {@link ReferenceDataCache#getCpCourtRoomsByCourtCentreId}. */
@ExtendWith(MockitoExtension.class)
class ReferenceDataCacheCourtCentreRoomsTest {

    private static final String COURT_CENTRE_ID = randomUUID().toString();
    private static final String OTHER_COURT_CENTRE_ID = randomUUID().toString();
    private static final String ROOM_A = randomUUID().toString();
    private static final String ROOM_B = randomUUID().toString();

    @Mock
    private CacheService cacheService;
    @Mock
    private ReferenceDataService referenceDataService;
    @InjectMocks
    private ReferenceDataCache referenceDataCache;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldReturnOnlyTheCourtCentresRoomsInReferenceDataOrderWhenCacheDisabled() {
        setField(referenceDataCache, "redisCommonCacheEnabled", "false");
        when(referenceDataService.getCpCourtRooms()).thenReturn(allCourtRooms());

        final List<CourtRoom> rooms = referenceDataCache.getCpCourtRoomsByCourtCentreId(COURT_CENTRE_ID);

        assertThat(rooms.stream().map(CourtRoom::getId).toList(), contains(ROOM_B, ROOM_A));
        verify(cacheService, never()).get(anyString());
    }

    @Test
    void shouldReturnEmptyForANullCourtCentre() {
        assertThat(referenceDataCache.getCpCourtRoomsByCourtCentreId(null), empty());
    }

    @Test
    void shouldLoadAndCacheOnACacheMiss() {
        setField(referenceDataCache, "redisCommonCacheEnabled", "true");
        setField(referenceDataCache, "redisCommonCacheKey5MinsTTL", "300");
        when(cacheService.get(CP_COURTROOMS_BY_COURT_CENTRE_CACHE_PREFIX + COURT_CENTRE_ID)).thenReturn(null);
        when(referenceDataService.getCpCourtRooms()).thenReturn(allCourtRooms());

        final List<CourtRoom> rooms = referenceDataCache.getCpCourtRoomsByCourtCentreId(COURT_CENTRE_ID);

        assertThat(rooms.stream().map(CourtRoom::getId).toList(), contains(ROOM_B, ROOM_A));
        verify(cacheService).add(eq(CP_COURTROOMS_BY_COURT_CENTRE_CACHE_PREFIX + COURT_CENTRE_ID), anyString(), eq(300));
    }

    @Test
    void shouldServeFromTheCacheOnAHit() throws Exception {
        setField(referenceDataCache, "redisCommonCacheEnabled", "true");
        final List<CourtRoom> cached = List.of(room(ROOM_A, COURT_CENTRE_ID));
        when(cacheService.get(CP_COURTROOMS_BY_COURT_CENTRE_CACHE_PREFIX + COURT_CENTRE_ID))
                .thenReturn(objectMapper.writeValueAsString(cached));

        final List<CourtRoom> rooms = referenceDataCache.getCpCourtRoomsByCourtCentreId(COURT_CENTRE_ID);

        assertThat(rooms.stream().map(CourtRoom::getId).toList(), contains(ROOM_A));
        verify(referenceDataService, never()).getCpCourtRooms();
        verify(cacheService, never()).add(anyString(), anyString(), anyInt());
    }

    private static List<CourtRoom> allCourtRooms() {
        return List.of(
                room(ROOM_B, COURT_CENTRE_ID),
                room(randomUUID().toString(), OTHER_COURT_CENTRE_ID),
                room(ROOM_A, COURT_CENTRE_ID));
    }

    private static CourtRoom room(final String id, final String courtCentreId) {
        return new CourtRoom().id(id).oucodeUUID(courtCentreId);
    }
}
