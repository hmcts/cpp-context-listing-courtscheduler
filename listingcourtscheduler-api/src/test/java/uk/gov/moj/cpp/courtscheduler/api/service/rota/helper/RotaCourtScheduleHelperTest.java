package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.MISSING_COURT_SESSION;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;

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
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

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
                .withOucodeL3Name("Test Courthouse")
                .withCourtRoomName("Court Room 1")
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(courtSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(listingProfileId), is(true));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).contains(UUID.fromString(courtSchedule.getCourtScheduleId())), is(true));
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsNull() {
        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(null, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), anyString(), anyMap());
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsEmpty() {
        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(emptyMap(), executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
    }

    @Test
    void shouldReturnEmptyMap_WhenNoCourtListings() {
        // given
        records.put(RotaPayload.MAGISTRATES, new HashMap<>());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

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
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

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
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

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
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), anyString(), anyMap());
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(null);

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(matchingSchedule, nonMatchingSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).contains(UUID.fromString(matchingSchedule.getCourtScheduleId())), is(true));
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(adSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).contains(UUID.fromString(adSchedule.getCourtScheduleId())), is(true));
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(adSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).contains(UUID.fromString(adSchedule.getCourtScheduleId())), is(true));
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(amSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(adSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertThat(result.get(listingProfileId).contains(UUID.fromString(adSchedule.getCourtScheduleId())), is(true));
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(amSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    // ============================================================================
    // Tests for MISSING_COURT_SESSION logging
    // ============================================================================

    @Test
    void shouldLogMissingCourtSession_WhenNoCourtScheduleFound() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final String panel = "PANEL1";
        final String session = "AM";
        final String businessType = "CIVIL";

        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, panel);
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, session);
        listingProfile.put(BUSINESS_TYPE, businessType);
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));

        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        final RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(MISSING_COURT_SESSION.code()));
        assertThat(log.getErrorText(), containsString("OU001"));
        assertThat(log.getErrorText(), containsString("2024-01-15"));
        assertThat(log.getErrorText(), containsString("Test Courthouse"));
        assertThat(log.getErrorText(), containsString("Court Room 1"));
        assertThat(log.getErrorText(), containsString(businessType));
        assertThat(log.getErrorText(), containsString(session));
        assertThat(log.getErrorText(), containsString(panel));
    }

    @Test
    void shouldGroupMultipleMissingSessions_ForSameOuCode() {
        // given
        final String listingProfileId1 = "listing-1";
        final String listingProfileId2 = "listing-2";
        final String sessionDateStr1 = "2024-01-15";
        final String sessionDateStr2 = "2024-01-16";
        final LocalDate sessionDate2 = LocalDate.parse("2024-01-16");

        final Map<String, String> listingProfile1 = new HashMap<>();
        listingProfile1.put(PANEL, "PANEL1");
        listingProfile1.put(SESSION_DATE, sessionDateStr1);
        listingProfile1.put(SESSION, "AM");
        listingProfile1.put(BUSINESS_TYPE, "CIVIL");
        listingProfile1.put("locationId", "100");
        listingProfile1.put("venueId", "200");
        listingProfile1.put("venueName", "Test Venue");

        final Map<String, String> listingProfile2 = new HashMap<>();
        listingProfile2.put(PANEL, "PANEL2");
        listingProfile2.put(SESSION_DATE, sessionDateStr2);
        listingProfile2.put(SESSION, "PM");
        listingProfile2.put(BUSINESS_TYPE, "CRIMINAL");
        listingProfile2.put("locationId", "100");
        listingProfile2.put("venueId", "200");
        listingProfile2.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId1, listingProfile1);
        courtListings.put(listingProfileId2, listingProfile2);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr1)).thenReturn(sessionDate);
        when(dateParsingUtility.parseSessionDate(sessionDateStr2)).thenReturn(sessionDate2);
        when(venueCourtRoomHelper.getCourtRoom(anyMap(), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), any(), any()))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));

        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        final RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(MISSING_COURT_SESSION.code()));
        assertThat(log.getErrorText(), containsString("OU001"));
        // Both sessions should be in the error text
        assertThat(log.getErrorText(), containsString("2024-01-15"));
        assertThat(log.getErrorText(), containsString("2024-01-16"));
        assertThat(log.getErrorText(), containsString("PANEL1"));
        assertThat(log.getErrorText(), containsString("PANEL2"));
    }

    @Test
    void shouldLogSeparately_ForDifferentOuCodes() {
        // given
        final String listingProfileId1 = "listing-1";
        final String listingProfileId2 = "listing-2";
        final String sessionDateStr = "2024-01-15";

        final CourtRoom courtRoom2 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-2")
                .withOucode("OU002")
                .withOucodeL3Name("Another Courthouse")
                .withCourtRoomName("Court Room 2")
                .build();

        final Map<String, String> listingProfile1 = new HashMap<>();
        listingProfile1.put(PANEL, "PANEL1");
        listingProfile1.put(SESSION_DATE, sessionDateStr);
        listingProfile1.put(SESSION, "AM");
        listingProfile1.put(BUSINESS_TYPE, "CIVIL");
        listingProfile1.put("locationId", "100");
        listingProfile1.put("venueId", "200");
        listingProfile1.put("venueName", "Test Venue");

        final Map<String, String> listingProfile2 = new HashMap<>();
        listingProfile2.put(PANEL, "PANEL2");
        listingProfile2.put(SESSION_DATE, sessionDateStr);
        listingProfile2.put(SESSION, "PM");
        listingProfile2.put(BUSINESS_TYPE, "CRIMINAL");
        listingProfile2.put("locationId", "101");
        listingProfile2.put("venueId", "201");
        listingProfile2.put("venueName", "Another Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId1, listingProfile1);
        courtListings.put(listingProfileId2, listingProfile2);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile1), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile2), eq(executionId), anyMap()))
                .thenReturn(courtRoom2);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU002")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));

        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService, times(2)).saveRotaProcessLog(logCaptor.capture());

        final List<RotaProcessLog> logs = logCaptor.getAllValues();
        assertThat(logs.size(), is(2));

        // Verify first log for OU001
        final RotaProcessLog log1 = logs.get(0);
        assertThat(log1.getExecutionId(), is(executionId));
        assertThat(log1.getErrorCode(), is(MISSING_COURT_SESSION.code()));
        assertThat(log1.getErrorText(), containsString("OU001"));
        assertThat(log1.getErrorText(), containsString("Test Courthouse"));

        // Verify second log for OU002
        final RotaProcessLog log2 = logs.get(1);
        assertThat(log2.getExecutionId(), is(executionId));
        assertThat(log2.getErrorCode(), is(MISSING_COURT_SESSION.code()));
        assertThat(log2.getErrorText(), containsString("OU002"));
        assertThat(log2.getErrorText(), containsString("Another Courthouse"));
    }

    @Test
    void shouldNotLogMissingCourtSession_WhenExecutionIdIsNull() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(BUSINESS_TYPE, "CIVIL");
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(null), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, null);

        // then
        assertThat(result, is(emptyMap()));
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingCourtSession_WhenExecutionIdIsEmpty() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(BUSINESS_TYPE, "CIVIL");
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(""), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(Collections.emptyList());

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, "");

        // then
        assertThat(result, is(emptyMap()));
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingCourtSession_WhenCourtScheduleIsFound() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, sessionDateStr);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(BUSINESS_TYPE, "CIVIL");
        listingProfile.put("locationId", "100");
        listingProfile.put("venueId", "200");
        listingProfile.put("venueName", "Test Venue");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(courtSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(listingProfileId), is(true));
        // Should not log MISSING_COURT_SESSION when schedule is found
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldUseUnknownValues_WhenCourtRoomFieldsAreMissing() {
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

        final CourtRoom courtRoomWithMissingFields = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-1")
                .withOucode(null)  // Missing OU code
                .withOucodeL3Name(null)  // Missing court house name
                .withCourtRoomName(null)  // Missing court room name
                .build();

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoomWithMissingFields);

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));

        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        final RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getErrorCode(), is(MISSING_COURT_SESSION.code()));
        assertThat(log.getErrorText(), containsString("UNKNOWN_OUCODE"));
        assertThat(log.getErrorText(), containsString("UNKNOWN_COURTHOUSE"));
        assertThat(log.getErrorText(), containsString("UNKNOWN_COURTROOM"));
    }

    // ============================================================================
    // Tests for REF_DATA_VENUE_NOT_FOUND logging
    // ============================================================================

    @Test
    void shouldLogMissingReferenceData_WhenVenueNotFound() {
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
        // Venue not found - getCourtRoom returns null and populates missingReferenceDataMappingMap
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, String> missingRefDataMap = invocation.getArgument(2);
                    missingRefDataMap.put("100 - Test Venue - 200", REF_DATA_VENUE_NOT_FOUND.code());
                    return null;
                });

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));

        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        final RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(REF_DATA_VENUE_NOT_FOUND.code()));
        assertThat(log.getErrorText(), containsString("100 - Test Venue - 200"));
    }

    @Test
    void shouldNotLogMissingReferenceData_WhenExecutionIdIsNull() {
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(null), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, String> missingRefDataMap = invocation.getArgument(2);
                    missingRefDataMap.put("100 - Test Venue - 200", REF_DATA_VENUE_NOT_FOUND.code());
                    return null;
                });

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, null);

        // then
        assertThat(result, is(emptyMap()));
        // Should not log when executionId is null
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogMissingReferenceData_WhenMapIsEmpty() {
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(courtSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        // Should not log when no missing reference data
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    // ============================================================================
    // Tests for exception handling and edge cases
    // ============================================================================

    @Test
    void shouldHandleException_WhenProcessCourtListingThrowsException() {
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

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenThrow(new RuntimeException("Date parsing error"));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        // Should not crash, just log error and continue
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), anyString(), anyMap());
    }

    @Test
    void shouldReturnEmptyList_WhenOuCodeIsMissing() {
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

        final CourtRoom courtRoomWithoutOuCode = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-1")
                .withOucode(null)  // Missing OU code
                .build();

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoomWithoutOuCode);

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(sessionsService, never()).getExtractedCourtSchedules(anyList(), any(), any());
    }

    @Test
    void shouldReturnEmptyList_WhenCourtRoomIdIsMissing() {
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

        final CourtRoom courtRoomWithoutId = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(null)  // Missing court room ID
                .withOucode("OU001")
                .build();

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoomWithoutId);

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(sessionsService, never()).getExtractedCourtSchedules(anyList(), any(), any());
    }

    @Test
    void shouldHandleException_WhenFindCourtScheduleThrowsException() {
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
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenThrow(new RuntimeException("Service error"));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        // Should handle exception gracefully and return empty map
    }

    @Test
    void shouldMapMultipleCourtSchedules_WhenMultipleMatch() {
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

        final CourtSchedule schedule1 = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();

        final CourtSchedule schedule2 = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(schedule1, schedule2));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(2));
        assertTrue(result.get(listingProfileId).contains(UUID.fromString(schedule1.getCourtScheduleId())));
        assertTrue(result.get(listingProfileId).contains(UUID.fromString(schedule2.getCourtScheduleId())));
    }

    @Test
    void shouldSkipListing_WhenSessionIsMissing() {
        // given
        final String listingProfileId = "listing-1";
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put(PANEL, "PANEL1");
        listingProfile.put(SESSION_DATE, "2024-01-15");

        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        courtListings.put(listingProfileId, listingProfile);
        records.put(COURT_LISTING, courtListings);

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
    }

    @Test
    void shouldFilterByCourtRoomId_WhenMultipleCourtRooms() {
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
                .withCourtRoomId("courtroom-1")  // Matches
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();

        final CourtSchedule nonMatchingSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-2")  // Different court room
                .withPanel("PANEL1")
                .withSessionDate(sessionDate)
                .withCourtSession("AM")
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(matchingSchedule, nonMatchingSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertTrue(result.get(listingProfileId).contains(UUID.fromString(matchingSchedule.getCourtScheduleId())));
        assertFalse(result.get(listingProfileId).contains(UUID.fromString(nonMatchingSchedule.getCourtScheduleId())));
    }

    @Test
    void shouldFilterBySessionDate_WhenMultipleDates() {
        // given
        final String listingProfileId = "listing-1";
        final String sessionDateStr = "2024-01-15";
        final LocalDate differentDate = LocalDate.parse("2024-01-16");
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
                .withSessionDate(sessionDate)  // Matches
                .withCourtSession("AM")
                .build();

        final CourtSchedule nonMatchingSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(differentDate)  // Different date
                .withCourtSession("AM")
                .build();

        when(dateParsingUtility.parseSessionDate(sessionDateStr)).thenReturn(sessionDate);
        when(venueCourtRoomHelper.getCourtRoom(eq(listingProfile), eq(executionId), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(eq(List.of("OU001")), eq(sessionDate), eq(sessionDate)))
                .thenReturn(List.of(matchingSchedule, nonMatchingSchedule));

        // when
        final Map<String, Set<UUID>> result = rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.get(listingProfileId).size(), is(1));
        assertTrue(result.get(listingProfileId).contains(UUID.fromString(matchingSchedule.getCourtScheduleId())));
        assertFalse(result.get(listingProfileId).contains(UUID.fromString(nonMatchingSchedule.getCourtScheduleId())));
    }
}

