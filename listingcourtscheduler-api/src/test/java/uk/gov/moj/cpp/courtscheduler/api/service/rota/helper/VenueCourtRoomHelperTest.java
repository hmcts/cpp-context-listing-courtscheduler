package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VenueCourtRoomHelperTest {
    private static final String VALUE_100 = "100";
    private static final String VALUE_200 = "200";
    private static final String TEST_VENUE = "Test Venue";
    private static final String LOCATION_ID = "locationId";
    private static final String VENUE_ID = "venueId";
    private static final String VENUE_NAME = "venueName";


    @Mock
    private RotaReferenceDataService referenceDataValidationService;

    @InjectMocks
    private VenueCourtRoomHelper venueCourtRoomHelper;

    private Map<String, String> listingProfile;
    private String executionId;
    private Map<String, String> missingReferenceDataMappingMap;
    private CourtRoom expectedCourtRoom;

    @BeforeEach
    void setUp() {
        listingProfile = new HashMap<>();
        executionId = "execution-123";
        missingReferenceDataMappingMap = new HashMap<>();
        expectedCourtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-1")
                .withOucode("OU001")
                .build();
    }

    @Test
    void shouldGetCourtRoom_WhenAllVenueInformationPresent() {
        // given
        listingProfile.put(LOCATION_ID, VALUE_100);
        listingProfile.put(VENUE_ID, VALUE_200);
        listingProfile.put(VENUE_NAME, TEST_VENUE);

        final Venue venue = new Venue(100, 200, TEST_VENUE);
        when(referenceDataValidationService.validateAndFindVenue(
                eq(venue), anyMap(), eq(executionId)))
                .thenReturn(Optional.of(expectedCourtRoom));

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertNotNull(result);
        assertThat(result.getCourtroomId(), is("courtroom-1"));
        assertThat(result.getOucode(), is("OU001"));
    }

    @Test
    void shouldReturnNull_WhenLocationIdMissing() {
        // given
        listingProfile.put(VENUE_ID, VALUE_200);
        listingProfile.put(VENUE_NAME, TEST_VENUE);

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
        verify(referenceDataValidationService, never()).validateAndFindVenue(any(), anyMap(), anyString());
    }

    @Test
    void shouldReturnNull_WhenVenueIdMissing() {
        // given
        listingProfile.put(LOCATION_ID, VALUE_100);
        listingProfile.put(VENUE_NAME, TEST_VENUE);

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
        verify(referenceDataValidationService, never()).validateAndFindVenue(any(), anyMap(), anyString());
    }

    @Test
    void shouldReturnNull_WhenVenueNameMissing() {
        // given
        listingProfile.put(LOCATION_ID, VALUE_100);
        listingProfile.put(VENUE_ID, VALUE_200);

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
        verify(referenceDataValidationService, never()).validateAndFindVenue(any(), anyMap(), anyString());
    }

    @Test
    void shouldReturnNull_WhenVenueNotFound() {
        // given
        listingProfile.put(LOCATION_ID, VALUE_100);
        listingProfile.put(VENUE_ID, VALUE_200);
        listingProfile.put(VENUE_NAME, TEST_VENUE);

        final Venue venue = new Venue(100, 200, TEST_VENUE);
        when(referenceDataValidationService.validateAndFindVenue(
                eq(venue), anyMap(), eq(executionId)))
                .thenReturn(Optional.empty());

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    void shouldReturnNull_WhenLocationIdIsInvalid() {
        // given
        listingProfile.put(LOCATION_ID, "invalid");
        listingProfile.put(VENUE_ID, VALUE_200);
        listingProfile.put(VENUE_NAME, TEST_VENUE);

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
        verify(referenceDataValidationService, never()).validateAndFindVenue(any(), anyMap(), anyString());
    }

    @Test
    void shouldReturnNull_WhenVenueIdIsInvalid() {
        // given
        listingProfile.put(LOCATION_ID, VALUE_100);
        listingProfile.put(VENUE_ID, "invalid");
        listingProfile.put(VENUE_NAME, TEST_VENUE);

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
        verify(referenceDataValidationService, never()).validateAndFindVenue(any(), anyMap(), anyString());
    }

    @Test
    void shouldHandleEmptyListingProfile() {
        // given
        listingProfile.clear();

        // when
        final CourtRoom result = venueCourtRoomHelper.getCourtRoom(
                listingProfile, executionId, missingReferenceDataMappingMap);

        // then
        assertThat(result, is(nullValue()));
        verify(referenceDataValidationService, never()).validateAndFindVenue(any(), anyMap(), anyString());
    }
}

