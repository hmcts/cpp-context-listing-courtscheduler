package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataMapperServiceTest {

    @InjectMocks
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private ReferenceDataCache referenceDataCache;

    @Mock
    private Requester requester;

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private static final Integer LOCATION_ID = 77;
    private static final Integer VENUE_ID = 23917;
    private static final Integer NOT_MATCHING_VENUE_ID = 29999;
    private static final String VENUE_NAME = "Court 8";
    private static final String NOT_MATCHING_VENUE_NAME = "Court 08";
    private static final String MULTIPLE_MATCH_VENUE_NAME = "Court 5";
    private static final Integer MULTIPLE_MATCH_LOCATION_ID = 277;

    @Test
    void shouldFindByEmail() throws JsonProcessingException {
        final List<Judiciary> judiciaries = getJudiciaries();
        when(referenceDataCache.getJudiciaries(eq(requester))).thenReturn(judiciaries);

        final String emailFilter = "TienaTSSvenTS@moj.gov.uk";

        final Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findByEmail(requester, emailFilter);

        assertTrue(judiciaryOptional.isPresent());
        judiciaries.stream()
                .filter(judiciary -> judiciary.getEmailAddress().equals(emailFilter))
                .findFirst()
                .ifPresent(judiciary -> {
                            assertEquals(judiciary.getId(), judiciaryOptional.get().getId());
                            assertEquals(judiciary.getForenames(), judiciaryOptional.get().getForenames());
                            assertEquals(judiciary.getSurname(), judiciaryOptional.get().getSurname());
                            assertEquals(judiciary.getJudiciaryType(), judiciaryOptional.get().getJudiciaryType());
                        }

                );

        verify(referenceDataCache, atLeastOnce()).getJudiciaries(eq(requester));
    }

    @Test
    void shouldFindJudiciaryById() throws JsonProcessingException {
        final List<Judiciary> judiciaries = getJudiciaries();
        when(referenceDataCache.getJudiciaries(eq(requester))).thenReturn(judiciaries);

        final String judiciaryId = judiciaries.get(0).getId();

        final Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findById(requester, judiciaryId);

        assertTrue(judiciaryOptional.isPresent());
        assertEquals(judiciaryId, judiciaryOptional.get().getId());
        verify(referenceDataCache, atLeastOnce()).getJudiciaries(eq(requester));
    }

    @Test
    void shouldReturnEmptyWhenJudiciaryIdNotFound() throws JsonProcessingException {
        when(referenceDataCache.getJudiciaries(eq(requester))).thenReturn(getJudiciaries());

        final Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findById(requester, "unknown-id");

        assertTrue(judiciaryOptional.isEmpty());
        verify(referenceDataCache, atLeastOnce()).getJudiciaries(eq(requester));
    }

    @Test
    void shouldFindByOuCodeAndRoomIdAndListingSessionAndBusinessType() throws JsonProcessingException {

        when(referenceDataCache.getCourtRoomSessionAllocations(eq(requester))).thenReturn(getCourtRoomSessionAllocations());

        final Optional<CourtRoomSessionAllocation> courtRoomSessionAllocationOptional = referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(requester, "B01KR00", 2035, "FRIPM", "GEN");

        assertTrue(courtRoomSessionAllocationOptional.isPresent());
        assertEquals("93231aab-a87e-3dbd-b334-402e07643f2f", courtRoomSessionAllocationOptional.get().getId());

        verify(referenceDataCache, atLeastOnce()).getCourtRoomSessionAllocations(eq(requester));
    }

    @Test
    void shouldFindByVenue() throws JsonProcessingException {

        when(referenceDataCache.getCourtRooms(eq(requester))).thenReturn(getCourtRoomsFromRefData());

        final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(new Venue(LOCATION_ID, VENUE_ID, VENUE_NAME), new HashMap<>(), requester);

        assertTrue(courtRoomOptional.isPresent());
        assertEquals("26de1ba8-fad7-3747-81e2-0dc6dce6ed7a", courtRoomOptional.get().getId());

        verify(referenceDataCache, atLeastOnce()).getCourtRooms(eq(requester));
    }

    @Test
    void shouldFindByMatchingVenueIdAndNotMatchingVenueName() throws JsonProcessingException {

        when(referenceDataCache.getCourtRooms(eq(requester))).thenReturn(getCourtRoomsFromRefData());

        final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(new Venue(LOCATION_ID, VENUE_ID, NOT_MATCHING_VENUE_NAME), new HashMap<>(), requester);

        assertTrue(courtRoomOptional.isPresent());
        assertEquals("26de1ba8-fad7-3747-81e2-0dc6dce6ed7a", courtRoomOptional.get().getId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());

        verify(referenceDataCache, atLeastOnce()).getCourtRooms(eq(requester));
    }

    @Test
    void shouldFindByVenueEvenVenueIdIsNotMatching() throws JsonProcessingException {

        when(referenceDataCache.getCourtRooms(eq(requester))).thenReturn(getCourtRoomsFromRefData());

        final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(new Venue(LOCATION_ID, NOT_MATCHING_VENUE_ID, VENUE_NAME), new HashMap<>(), requester);

        assertTrue(courtRoomOptional.isPresent());
        assertEquals("26de1ba8-fad7-3747-81e2-0dc6dce6ed7a", courtRoomOptional.get().getId());
        assertEquals(VENUE_ID, courtRoomOptional.get().getRotaVenueId());
        assertEquals(VENUE_NAME, courtRoomOptional.get().getRotaVenueName());
        assertEquals(LOCATION_ID, courtRoomOptional.get().getRotaLocationId());

        verify(referenceDataCache, atLeastOnce()).getCourtRooms(eq(requester));
    }

    @Test
    void shouldFindByVenueEvenVenueIdIsNotMatchingAndThereAre2Matching() throws JsonProcessingException {

        when(referenceDataCache.getCourtRooms(eq(requester))).thenReturn(getCourtRoomsFromRefData());

        final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(new Venue(MULTIPLE_MATCH_LOCATION_ID, NOT_MATCHING_VENUE_ID, MULTIPLE_MATCH_VENUE_NAME), new HashMap<>(), requester);

        assertTrue(courtRoomOptional.isPresent());
        assertTrue(List.of("aaaa26c8-0630-3fec-8336-d260a5a9c756", "c8c3ef69-e640-3ac5-bd7a-7765396cc38d").contains(courtRoomOptional.get().getId()));

        verify(referenceDataCache, atLeastOnce()).getCourtRooms(eq(requester));
    }

    @Test
    void shouldNotFindByVenueIfRefDataCacheMissing() {

        when(referenceDataCache.getCourtRooms(eq(requester))).thenReturn(emptyList());

        final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(new Venue(MULTIPLE_MATCH_LOCATION_ID, NOT_MATCHING_VENUE_ID, MULTIPLE_MATCH_VENUE_NAME), new HashMap<>(), requester);

        assertTrue(courtRoomOptional.isEmpty());
        verify(referenceDataCache, atLeastOnce()).getCourtRooms(eq(requester));
    }

    @Test
    void shouldLoadJudiciaries() throws JsonProcessingException {
        when(referenceDataCache.getJudiciaries(eq(requester))).thenReturn(getJudiciaries());

        referenceDataMapperService.loadJudiciaries(requester);

        verify(referenceDataCache).getJudiciaries(eq(requester));
    }

    private List<CourtRoomSessionAllocation> getCourtRoomSessionAllocations() throws JsonProcessingException {
        final String courtRoomSessionAllocationsJsonStr = FileUtil.fileToString("/test-data/court-room-session-allocations-domain-data.json");

        return objectMapper.readValue(courtRoomSessionAllocationsJsonStr, new TypeReference<>() {});
    }


    private List<Judiciary> getJudiciaries() throws JsonProcessingException {
        final String judiciariesJsonStr = FileUtil.fileToString("/test-data/judiciaries-domain-data.json");

        return objectMapper.readValue(judiciariesJsonStr, new TypeReference<>() {});
    }

    private List<CourtRoom> getCourtRoomsFromRefData() throws JsonProcessingException {
        final String courtRoomsJsonStr = FileUtil.fileToString("/test-data/reference-data-court-rooms.json");

        return objectMapper.readValue(courtRoomsJsonStr, new TypeReference<>() {});
    }

}
