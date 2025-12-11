package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaCourtScheduleHelperTest {

    @Mock
    private DateParsingUtility dateParsingUtility;

    @Mock
    private VenueCourtRoomHelper venueCourtRoomHelper;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private Requester requester;

    @InjectMocks
    private RotaCourtScheduleHelper rotaCourtScheduleHelper;

    private String executionId;
    private Map<RotaPayload, Map<String, Map<String, String>>> records;
    private CourtRoom courtRoom;
    private CourtSchedule courtSchedule;
    private LocalDate sessionDate;

    @BeforeEach
    void setUp() {
        executionId = "execution-123";
        records = new HashMap<>();
        sessionDate = LocalDate.parse("2024-01-15");

        courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-1")
                .withOucode("OU001")
                .build();

        courtSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();
    }

    // ============================================================================
    // Tests for createCourtScheduleMap
    // ============================================================================

    @Test
    void shouldCreateCourtScheduleMap_WhenValidCourtListing() {
        // given
        final String listingProfileId = "listing-1";
        final String panel = "PANEL1";
        final String session = "AM";
        final String sessionDateStr = "2024-01-15";

        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, panel);
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, session);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(courtSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(listingProfileId), is(true));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).get(0), is(UUID.fromString(courtSchedule.getCourtScheduleId())));
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsNull() {
        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(null, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), any(), anyString(), anyMap());
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsEmpty() {
        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(emptyMap(), requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
    }

    @Test
    void shouldReturnEmptyMap_WhenNoCourtListings() {
        // given
        records.put(RotaPayload.MAGISTRATES, new HashMap<>());

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
    }

    @Test
    void shouldSkipListing_WhenPanelIsMissing() {
        // given
        final String listingProfileId = "listing-1";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(SESSION_DATE, "2024-01-15");
        listingProfile.put(SESSION, "AM");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
    }

    @Test
    void shouldSkipListing_WhenSessionDateIsMissing() {
        // given
        final String listingProfileId = "listing-1";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION, "AM");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
    }

    @Test
    void shouldSkipListing_WhenSessionDateIsInvalid() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "invalid-date";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(null);

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), any(), anyString(), anyMap());
    }

    @Test
    void shouldSkipListing_WhenCourtRoomIsNull() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(null);

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(sessionsService, never()).getExtractedCourtSchedules(anyList(), any(), any());
    }

    @Test
    void shouldReturnEmptyList_WhenNoMatchingCourtSchedules() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldFilterCourtSchedules_ByPanelAndSession() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        final CourtSchedule matchingSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();

        final CourtSchedule nonMatchingSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL2")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(matchingSchedule, nonMatchingSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).get(0), is(UUID.fromString(matchingSchedule.getCourtScheduleId())));
    }

    @Test
    void shouldMatchADSession_WhenRequestedSessionIsAM() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, AM_SESSION);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        final CourtSchedule adSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession(ALL_DAY)
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(adSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).get(0), is(UUID.fromString(adSchedule.getCourtScheduleId())));
    }

    @Test
    void shouldMatchADSession_WhenRequestedSessionIsPM() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, PM_SESSION);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        final CourtSchedule adSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession(ALL_DAY)
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(adSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).get(0), is(UUID.fromString(adSchedule.getCourtScheduleId())));
    }

    @Test
    void shouldNotMatchAMSession_WhenRequestedSessionIsPM() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, PM_SESSION);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        final CourtSchedule amSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession(AM_SESSION)
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(amSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldMatchExactSession_WhenRequestedSessionIsAD() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, ALL_DAY);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        final CourtSchedule adSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession(ALL_DAY)
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(adSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).get(0), is(UUID.fromString(adSchedule.getCourtScheduleId())));
    }

    @Test
    void shouldNotMatchAMOrPM_WhenRequestedSessionIsAD() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, ALL_DAY);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        final CourtSchedule amSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession(AM_SESSION)
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(requester), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(amSchedule));

        // when
        final Map<String, List<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }
}

