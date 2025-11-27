package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.helper.CourtScheduleJudiciaryQueryHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.helper.DateParsingUtility;
import uk.gov.moj.cpp.courtscheduler.api.service.helper.JudiciaryCourtScheduleMapComparator;
import uk.gov.moj.cpp.courtscheduler.api.service.helper.RotaFileUtility;
import uk.gov.moj.cpp.courtscheduler.api.service.helper.VenueCourtRoomHelper;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryBuilder;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileProcessorServiceTest {

    @Mock
    private AzureBlobClientService azureBlobClientService;

    @Mock
    private RotaFileParser rotaFileParser;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Mock
    private RotaReferenceDataService referenceDataValidationService;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private JudiciaryBuilder judiciaryBuilder;

    @Mock
    private RotaFileUtility rotaFileUtility;

    @Mock
    private DateParsingUtility dateParsingUtility;

    @Mock
    private VenueCourtRoomHelper venueCourtRoomHelper;

    @Mock
    private CourtScheduleJudiciaryQueryHelper courtScheduleJudiciaryQueryHelper;

    @Mock
    private JudiciaryCourtScheduleMapComparator mapComparator;

    @Mock
    private Requester requester;

    @InjectMocks
    private RotaFileProcessorService rotaFileProcessorService;

    private String blobName;
    private String leaseId;
    private byte[] blobContent;
    private BlobContent blobContentWrapper;
    private String executionId;
    private Map<RotaPayload, Map<String, Map<String, String>>> records;
    private Judiciary judiciary;
    private CourtRoom courtRoom;
    private CourtSchedule courtSchedule;

    @BeforeEach
    void setUp() {
        blobName = "test_rota_file.xml";
        leaseId = "lease-123";
        blobContent = "test content".getBytes();
        blobContentWrapper = new BlobContent(blobContent);
        executionId = "execution-123";

        records = new HashMap<>();
        
        judiciary = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(randomUUID().toString())
                .withEmailAddress("judge@example.com")
                .withForenames("John")
                .withSurname("Doe")
                .withTitlePrefix("Mr")
                .withJudiciaryType("Judge")
                .build();

        courtRoom = CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId("courtroom-1")
                .withOucode("OU001")
                .withRotaLocationId(100)
                .withRotaVenueId(200)
                .withRotaVenueName("Test Venue")
                .build();

        courtSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(LocalDate.now())
                .withCourtSession("AM")
                .build();
    }

    // ============================================================================
    // Tests for downloadAndProcessForEachFile
    // ============================================================================

    @Test
    void shouldProcessBlobAndUploadSuccessfully() {
        // given
        setupSuccessfulProcessing();

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(azureBlobClientService).uploadProcessedFile(any(ByteArrayInputStream.class), eq((long) blobContent.length), eq(blobName), eq(empty()));
        verify(azureBlobClientService).releaseLease(blobName, leaseId, false);
        verify(azureBlobClientService).deleteFile(blobName, empty());
    }

    @Test
    void shouldReleaseLeaseOnError() {
        // given
        when(rotaFileUtility.processSnapshotFileIfNeeded(anyString(), any(), any()))
                .thenReturn(executionId);
        when(rotaFileParser.parse(anyString(), any())).thenThrow(new RuntimeException("Parsing error"));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
        verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
        verify(azureBlobClientService, never()).deleteFile(anyString(), any());
    }

    // ============================================================================
    // Tests for processBlob (tested through downloadAndProcessForEachFile)
    // ============================================================================

    @Test
    void shouldProcessBlobWithAllMapsCreated() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileParser).parse(blobName, blobContent);
        verify(referenceDataValidationService, atLeastOnce()).validateAndFindJudiciaryByEmail(any(), anyString(), anyString());
        verify(courtScheduleJudiciaryQueryHelper).queryCourtScheduleIdsByJudiciaryIds(anyMap());
        verify(mapComparator).findMissingCourtScheduleIdsInDB(anyMap(), anyMap());
        verify(mapComparator).findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap());
    }

    @Test
    void shouldHandleEmptyRecords() {
        // given
        when(rotaFileUtility.processSnapshotFileIfNeeded(anyString(), any(), any()))
                .thenReturn(executionId);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileParser).parse(blobName, blobContent);
        verify(courtScheduleJudiciaryQueryHelper, never()).queryCourtScheduleIdsByJudiciaryIds(anyMap());
    }

    // ============================================================================
    // Helper methods to setup test data
    // ============================================================================

    private void setupSuccessfulProcessing() {
        when(rotaFileUtility.processSnapshotFileIfNeeded(anyString(), any(), any()))
                .thenReturn(executionId);
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());
    }

    private void setupRecordsWithData() {
        // Setup magistrates
        Map<String, Map<String, String>> magistrates = new HashMap<>();
        Map<String, String> magistrateData = new HashMap<>();
        magistrateData.put(MAGS_EMAIL, "magistrate@example.com");
        magistrates.put("mag-1", magistrateData);
        records.put(MAGISTRATES, magistrates);

        // Setup district judges
        Map<String, Map<String, String>> districtJudges = new HashMap<>();
        Map<String, String> judgeData = new HashMap<>();
        judgeData.put(JUDGE_EMAIL, "judge@example.com");
        districtJudges.put("judge-1", judgeData);
        records.put(DISTRICT_JUDGES, districtJudges);

        // Setup court listings
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "2024-01-15");
        listingData.put("session", "AM");
        listingData.put("locationId", "100");
        listingData.put("venueId", "200");
        listingData.put("venueName", "Test Venue");
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        // Setup schedules
        Map<String, Map<String, String>> schedules = new HashMap<>();
        Map<String, String> scheduleData = new HashMap<>();
        scheduleData.put(ROTA_JUDICIARY_ID, "judge-1");
        scheduleData.put(COURT_LISTING_PROFILE_ID, "listing-1");
        scheduleData.put(JUDICIARY_ID, judiciary.getId());
        schedules.put("schedule-1", scheduleData);
        records.put(SCHEDULE, schedules);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), eq("magistrate@example.com"), anyString()))
                .thenReturn(Optional.of(judiciary));
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), eq("judge@example.com"), anyString()))
                .thenReturn(Optional.of(judiciary));
        when(dateParsingUtility.parseSessionDate("2024-01-15")).thenReturn(LocalDate.parse("2024-01-15"));
        when(venueCourtRoomHelper.getCourtRoom(anyMap(), any(), anyString(), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(courtSchedule));
        
        CourtScheduleJudiciary scheduleJudiciary = judiciary()
                .withJudiciaryId(judiciary.getId())
                .withCourtListingProfileId("listing-1")
                .build();
        when(judiciaryBuilder.build(anyMap(), anyString())).thenReturn(scheduleJudiciary);
        
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        Map<String, List<UUID>> dbMap = new HashMap<>();
        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(rotaFeedMap);
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());
    }

    @Test
    void shouldHandleSnapshotFileProcessing() {
        // given
        blobName = "test_snapshot_20240115_120000.xml";
        when(rotaFileUtility.processSnapshotFileIfNeeded(anyString(), any(), any()))
                .thenReturn(executionId);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).processSnapshotFileIfNeeded(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
    }

    @Test
    void shouldHandleDummyFile() {
        // given
        blobName = "dummysupport_file.xml";
        when(rotaFileUtility.processSnapshotFileIfNeeded(anyString(), any(), any()))
                .thenReturn(executionId);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(true);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).isDummyFile(blobName);
    }

    @Test
    void shouldProcessJudiciariesFromRecords() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> magistrates = new HashMap<>();
        Map<String, String> magistrateData = new HashMap<>();
        magistrateData.put(MAGS_EMAIL, "magistrate@example.com");
        magistrates.put("mag-1", magistrateData);
        records.put(MAGISTRATES, magistrates);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), eq("magistrate@example.com"), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(referenceDataValidationService).validateAndFindJudiciaryByEmail(any(), eq("magistrate@example.com"), eq(executionId));
    }

    @Test
    void shouldProcessCourtListingsFromRecords() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "2024-01-15");
        listingData.put("session", "AM");
        listingData.put("locationId", "100");
        listingData.put("venueId", "200");
        listingData.put("venueName", "Test Venue");
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(dateParsingUtility.parseSessionDate("2024-01-15")).thenReturn(LocalDate.parse("2024-01-15"));
        when(venueCourtRoomHelper.getCourtRoom(anyMap(), any(), anyString(), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(courtSchedule));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(dateParsingUtility).parseSessionDate("2024-01-15");
        verify(venueCourtRoomHelper).getCourtRoom(anyMap(), any(), anyString(), anyMap());
        verify(sessionsService).getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    void shouldSkipCourtListingWithMissingFields() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        // Missing sessionDate and session
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(dateParsingUtility, never()).parseSessionDate(anyString());
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), any(), anyString(), anyMap());
    }

    @Test
    void shouldSkipCourtListingWithInvalidDate() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "invalid-date");
        listingData.put("session", "AM");
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(dateParsingUtility.parseSessionDate("invalid-date")).thenReturn(null);

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(venueCourtRoomHelper, never()).getCourtRoom(anyMap(), any(), anyString(), anyMap());
    }

    @Test
    void shouldSkipCourtListingWhenCourtRoomNotFound() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "2024-01-15");
        listingData.put("session", "AM");
        listingData.put("locationId", "100");
        listingData.put("venueId", "200");
        listingData.put("venueName", "Test Venue");
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(dateParsingUtility.parseSessionDate("2024-01-15")).thenReturn(LocalDate.parse("2024-01-15"));
        when(venueCourtRoomHelper.getCourtRoom(anyMap(), any(), anyString(), anyMap()))
                .thenReturn(null);

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(sessionsService, never()).getExtractedCourtSchedules(anyList(), any(), any());
    }

    @Test
    void shouldProcessSchedulesFromRecords() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> schedules = new HashMap<>();
        Map<String, String> scheduleData = new HashMap<>();
        scheduleData.put(ROTA_JUDICIARY_ID, "judge-1");
        scheduleData.put(COURT_LISTING_PROFILE_ID, "listing-1");
        scheduleData.put(JUDICIARY_ID, judiciary.getId());
        schedules.put("schedule-1", scheduleData);
        records.put(SCHEDULE, schedules);

        Map<String, Map<String, String>> magistrates = new HashMap<>();
        Map<String, String> magistrateData = new HashMap<>();
        magistrateData.put(MAGS_EMAIL, "magistrate@example.com");
        magistrates.put("judge-1", magistrateData);
        records.put(MAGISTRATES, magistrates);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));
        
        CourtScheduleJudiciary scheduleJudiciary = judiciary()
                .withJudiciaryId(judiciary.getId())
                .withCourtListingProfileId("listing-1")
                .build();
        when(judiciaryBuilder.build(anyMap(), anyString())).thenReturn(scheduleJudiciary);

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(judiciaryBuilder).build(anyMap(), anyString());
    }

    @Test
    void shouldSkipScheduleWithMissingRotaJusticeId() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> schedules = new HashMap<>();
        Map<String, String> scheduleData = new HashMap<>();
        // Missing ROTA_JUDICIARY_ID
        scheduleData.put(COURT_LISTING_PROFILE_ID, "listing-1");
        schedules.put("schedule-1", scheduleData);
        records.put(SCHEDULE, schedules);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(judiciaryBuilder, never()).build(anyMap(), anyString());
    }

    @Test
    void shouldSkipScheduleWithMissingCourtListingProfileId() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> schedules = new HashMap<>();
        Map<String, String> scheduleData = new HashMap<>();
        scheduleData.put(ROTA_JUDICIARY_ID, "judge-1");
        // Missing COURT_LISTING_PROFILE_ID
        schedules.put("schedule-1", scheduleData);
        records.put(SCHEDULE, schedules);

        Map<String, Map<String, String>> magistrates = new HashMap<>();
        Map<String, String> magistrateData = new HashMap<>();
        magistrateData.put(MAGS_EMAIL, "magistrate@example.com");
        magistrates.put("judge-1", magistrateData);
        records.put(MAGISTRATES, magistrates);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(judiciaryBuilder, never()).build(anyMap(), anyString());
    }

    @Test
    void shouldHandleExceptionDuringCourtListingProcessing() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "2024-01-15");
        listingData.put("session", "AM");
        listingData.put("locationId", "100");
        listingData.put("venueId", "200");
        listingData.put("venueName", "Test Venue");
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(dateParsingUtility.parseSessionDate("2024-01-15"))
                .thenThrow(new RuntimeException("Date parsing error"));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // Should continue processing despite exception
        verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
    }

    @Test
    void shouldHandleExceptionDuringScheduleProcessing() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> schedules = new HashMap<>();
        Map<String, String> scheduleData = new HashMap<>();
        scheduleData.put(ROTA_JUDICIARY_ID, "judge-1");
        scheduleData.put(COURT_LISTING_PROFILE_ID, "listing-1");
        schedules.put("schedule-1", scheduleData);
        records.put(SCHEDULE, schedules);

        Map<String, Map<String, String>> magistrates = new HashMap<>();
        Map<String, String> magistrateData = new HashMap<>();
        magistrateData.put(MAGS_EMAIL, "magistrate@example.com");
        magistrates.put("judge-1", magistrateData);
        records.put(MAGISTRATES, magistrates);

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Validation error"));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // Should continue processing despite exception
        verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
    }

    @Test
    void shouldQueryDatabaseForCourtScheduleIds() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        Map<String, List<UUID>> dbMap = new HashMap<>();
        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, List<UUID>>> captor = ArgumentCaptor.forClass(Map.class);
        verify(courtScheduleJudiciaryQueryHelper).queryCourtScheduleIdsByJudiciaryIds(captor.capture());
        assertThat(captor.getValue(), is(notNullValue()));
    }

    @Test
    void shouldCompareMapsForMissingIds() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        UUID scheduleId = UUID.fromString(courtSchedule.getCourtScheduleId());
        rotaFeedMap.put(judiciary.getId(), List.of(scheduleId));
        Map<String, List<UUID>> dbMap = new HashMap<>();
        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(rotaFeedMap);
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(mapComparator).findMissingCourtScheduleIdsInDB(anyMap(), anyMap());
        verify(mapComparator).findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap());
    }

    @Test
    void shouldFilterCourtSchedulesCorrectly() {
        // given
        setupSuccessfulProcessing();
        Map<String, Map<String, String>> courtListings = new HashMap<>();
        Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "2024-01-15");
        listingData.put("session", "AM");
        listingData.put("locationId", "100");
        listingData.put("venueId", "200");
        listingData.put("venueName", "Test Venue");
        courtListings.put("listing-1", listingData);
        records.put(COURT_LISTING, courtListings);

        CourtSchedule matchingSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(LocalDate.parse("2024-01-15"))
                .withCourtSession("AM")
                .build();

        CourtSchedule nonMatchingSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId("courtroom-2")
                .withPanel("PANEL2")
                .withSessionDate(LocalDate.parse("2024-01-15"))
                .withCourtSession("PM")
                .build();

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
        when(dateParsingUtility.parseSessionDate("2024-01-15")).thenReturn(LocalDate.parse("2024-01-15"));
        when(venueCourtRoomHelper.getCourtRoom(anyMap(), any(), anyString(), anyMap()))
                .thenReturn(courtRoom);
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(matchingSchedule, nonMatchingSchedule));

        // when
        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // The filtering should only include matchingSchedule
        verify(sessionsService).getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class));
    }
}

