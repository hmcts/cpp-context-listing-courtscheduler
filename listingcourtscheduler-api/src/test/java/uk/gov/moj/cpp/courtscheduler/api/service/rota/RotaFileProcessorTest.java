package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryAssignmentRequestHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleData;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryScheduleAssignment;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaCourtScheduleHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaFileUtility;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaJudiciaryHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaLocationPeriodHelper;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RotaFileProcessor Tests")
class RotaFileProcessorTest {

    @Mock
    private AzureBlobClientService azureBlobClientService;

    @Mock
    private RotaFileParser rotaFileParser;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Mock
    private RotaFileUtility rotaFileUtility;

    @Mock
    private JudiciaryAssignmentService judiciaryAssignmentService;

    @Mock
    private RotaJudiciaryHelper rotaJudiciaryHelper;

    @Mock
    private RotaCourtScheduleHelper rotaCourtScheduleHelper;

    @Mock
    private JudiciaryAssignmentRequestHelper judiciaryAssignmentRequestHelper;

    @Mock
    private RotaLocationPeriodHelper rotaLocationPeriodHelper;


    @InjectMocks
    private RotaFileProcessor rotaFileProcessor;

    private String blobName;
    private String leaseId;
    private byte[] blobContent;
    private BlobContent blobContentWrapper;
    private String executionId;
    private RotaFileProcessHistory rotaFileProcessHistory;
    private Map<RotaPayload, Map<String, Map<String, String>>> records;
    private Judiciary judiciary;
    private CourtSchedule courtSchedule;

    @BeforeEach
    void setUp() {
        blobName = "test_rota_file.xml";
        leaseId = "lease-123";
        blobContent = "test content".getBytes();
        blobContentWrapper = new BlobContent(blobContent);
        executionId = "execution-123";
        rotaFileProcessHistory = new RotaFileProcessHistory();
        rotaFileProcessHistory.setExecutionId(executionId);
        records = new HashMap<>();

        judiciary = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(randomUUID().toString())
                .withEmailAddress("judge@example.com")
                .withForenames("John")
                .withSurname("Doe")
                .withTitlePrefix("Mr")
                .withJudiciaryType("Judge")
                .build();

        courtSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId("courtroom-1")
                .withPanel("PANEL1")
                .withSessionDate(LocalDate.parse("2024-01-15"))
                .withCourtSession("AM")
                .build();
    }

    // ============================================================================
    // Tests for downloadAndProcessForEachFile - Main Entry Point
    // ============================================================================

    @Nested
    @DisplayName("File Processing Entry Point Tests")
    class FileProcessingEntryPointTests {

        @Test
        @DisplayName("Should process blob and upload successfully")
        void shouldProcessBlobAndUploadSuccessfully() {
            // given
            setupSuccessfulProcessing();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(azureBlobClientService).uploadProcessedFile(any(ByteArrayInputStream.class), eq((long) blobContent.length), eq(blobName), eq(empty()));
            verify(azureBlobClientService).releaseLease(blobName, leaseId, false);
            verify(azureBlobClientService).deleteFile(blobName, empty());
        }

        @Test
        @DisplayName("Should release lease on error")
        void shouldReleaseLeaseOnError() {
            // given
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(rotaFileProcessHistory);
            when(rotaFileParser.parse(anyString(), any())).thenThrow(new RuntimeException("Parsing error"));

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
            verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
            verify(azureBlobClientService, never()).deleteFile(anyString(), any());
        }
    }

    // ============================================================================
    // Tests for File Type Validation (shouldProcessFile)
    // ============================================================================

    @Nested
    @DisplayName("File Type Validation Tests")
    class FileTypeValidationTests {

        @Test
        @DisplayName("Should skip processing for dummy file")
        void shouldSkipProcessingForDummyFile() {
            // given
            blobName = "dummysupport_file.xml";
            when(rotaFileUtility.isDummyFile(blobName)).thenReturn(true);
            doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
            doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
            doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileUtility).isDummyFile(blobName);
            verify(rotaFileParser, never()).parse(anyString(), any());
            verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
            verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), eq(blobName), any());
        }

        @Test
        @DisplayName("Should skip processing for newer snapshot file")
        void shouldSkipProcessingForNewerSnapshotFile() {
            // given
            blobName = "test_snapshot_20240115T120000Z.xml";
            when(rotaFileUtility.isDummyFile(blobName)).thenReturn(false);
            when(rotaFileUtility.isSnapshotFile(blobName)).thenReturn(true);
            when(rotaFileUtility.isNewerSnapshotFileProcessed(blobName)).thenReturn(true);
            doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
            doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
            doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileUtility).isNewerSnapshotFileProcessed(blobName);
            verify(rotaFileParser, never()).parse(anyString(), any());
            verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
            verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), eq(blobName), any());
        }

        @Test
        @DisplayName("Should process snapshot file when not newer")
        void shouldHandleSnapshotFileProcessing() {
            // given
            blobName = "test_snapshot_20240115_120000.xml";
            setupBasicProcessingMocks();
            setupRecordsWithRotaPeriod();
            setupLocationAndPeriodHelpers();
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileUtility).createAndSaveFileProcessHistory(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
        }
    }

    // ============================================================================
    // Tests for File Process History
    // ============================================================================

    @Nested
    @DisplayName("File Process History Tests")
    class FileProcessHistoryTests {

        @Test
        @DisplayName("Should handle null file process history")
        void shouldHandleNullFileProcessHistory() {
            // given
            setupBasicProcessingMocks();
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(null);
            setupRecordsWithRotaPeriod();
            setupLocationAndPeriodHelpers();
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
            verify(rotaFileParser).parse(blobName, blobContent);
        }

        @Test
        @DisplayName("Should generate execution ID when file process history is null")
        void shouldGenerateExecutionIdWhenFileProcessHistoryIsNull() {
            // given
            setupBasicProcessingMocks();
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(null);
            setupRecordsWithRotaPeriod();
            setupLocationAndPeriodHelpers();
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileUtility).createAndSaveFileProcessHistory(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
            verify(rotaFileParser).parse(blobName, blobContent);
        }

        @Test
        @DisplayName("Should update file process history after successful processing")
        void shouldUpdateFileProcessHistoryAfterProcessing() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();
            
            // Mock assignment service - setupRecordsWithData creates a non-empty assignment map
            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
            final AssignJudiciariesRequest assignRequest = createAssignRequest(judiciary.getId(), sessionId1);
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);
            final AssignJudiciariesResponse assignResponse = createAssignResponse(1, 1);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            // Verify processing completed successfully
            verify(rotaFileParser).parse(blobName, blobContent);
            verify(judiciaryAssignmentService).assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true));
            verify(rotaFileUtility).logProcessingTime(any(), eq(blobName), anyLong(), anyLong());
            // Verify that updateFileProcessHistory utility method is called (which internally calls the service)
            verify(rotaFileUtility).updateFileProcessHistory(any(), any(RotaFileProcessHistory.class), eq(blobName), eq(rotaFileProcessHistoryService));
        }

        @Test
        @DisplayName("Should use execution ID from file process history when it exists")
        void shouldUseExecutionIdFromFileProcessHistoryWhenItExists() {
            // given
            final String expectedExecutionId = "expected-execution-id-123";
            rotaFileProcessHistory.setExecutionId(expectedExecutionId);
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(rotaFileProcessHistory);
            when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
            when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
            when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
            lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
            doNothing().when(rotaFileUtility).logProcessingTime(any(), anyString(), anyLong(), anyLong());
            doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
            doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
            doNothing().when(azureBlobClientService).deleteFile(anyString(), any());
            
            setupRecordsWithData();
            setupLocationAndPeriodHelpers();
            // Mock assignment service - the setupRecordsWithData creates a non-empty assignment map
            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
            final AssignJudiciariesRequest assignRequest = createAssignRequest(judiciary.getId(), sessionId1);
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);
            final AssignJudiciariesResponse assignResponse = createAssignResponse(1, 1);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(expectedExecutionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileUtility).createAndSaveFileProcessHistory(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
            assertThat(rotaFileProcessHistory.getExecutionId(), is(expectedExecutionId));
        }
    }

    // ============================================================================
    // Tests for Location and Period Operations
    // ============================================================================

    @Nested
    @DisplayName("Location and Period Operations Tests")
    class LocationAndPeriodOperationsTests {

        @Test
        @DisplayName("Should extract locations from records")
        void shouldExtractLocationsFromRecords() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();
            final List<String> expectedLocations = List.of("100", "200");
            when(rotaLocationPeriodHelper.getLocationFromRecords(anyMap())).thenReturn(expectedLocations);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaLocationPeriodHelper).getLocationFromRecords(anyMap());
        }

        @Test
        @DisplayName("Should resolve OU codes from location IDs")
        void shouldResolveOuCodesFromLocationIds() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();
            final List<String> locations = List.of("100", "200");
            final List<String> expectedOuCodes = List.of("OU001", "OU002");
            when(rotaLocationPeriodHelper.getLocationFromRecords(anyMap())).thenReturn(locations);
            when(rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locations))
                    .thenReturn(expectedOuCodes);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaLocationPeriodHelper).getOuCodesFromCourtRoomMappingsByLocationId(locations);
        }

        @Test
        @DisplayName("Should get rota period dates from records")
        void shouldGetRotaPeriodDatesFromRecords() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaLocationPeriodHelper).getRotaPeriodDates(anyMap());
        }

        @Test
        @DisplayName("Should delete unallocated court schedule judiciaries for rota period")
        void shouldDeleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();
            final List<String> ouCodes = List.of("OU001", "OU002");
            when(rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(anyList()))
                    .thenReturn(ouCodes);
            when(rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(any(), any(), eq(ouCodes)))
                    .thenReturn(5);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaLocationPeriodHelper).deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(any(), any(), eq(ouCodes));
        }

        @Test
        @DisplayName("Should handle empty locations list")
        void shouldHandleEmptyLocationsList() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithRotaPeriod();
            when(rotaLocationPeriodHelper.getLocationFromRecords(anyMap())).thenReturn(List.of());
            when(rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(anyList()))
                    .thenReturn(List.of());
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaLocationPeriodHelper).getLocationFromRecords(anyMap());
            verify(rotaLocationPeriodHelper).getOuCodesFromCourtRoomMappingsByLocationId(List.of());
        }
    }

    // ============================================================================
    // Tests for Record Processing
    // ============================================================================

    @Nested
    @DisplayName("Record Processing Tests")
    class RecordProcessingTests {

        @Test
        @DisplayName("Should process blob with all maps created")
        void shouldProcessBlobWithAllMapsCreated() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaFileParser).parse(blobName, blobContent);
            verify(rotaJudiciaryHelper).createJudiciaryMap(anyMap(), anyString());
            verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), anyString());
            verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString());
        }

        @Test
        @DisplayName("Should process judiciaries from records")
        void shouldProcessJudiciariesFromRecords() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> magistrates = createMagistratesRecord("mag-1", "magistrate@example.com");
            records.put(MAGISTRATES, magistrates);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), anyString()))
                    .thenReturn(Map.of("mag-1", UUID.fromString(judiciary.getId())));
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaJudiciaryHelper).createJudiciaryMap(anyMap(), eq(executionId));
        }

        @Test
        @DisplayName("Should process court listings from records")
        void shouldProcessCourtListingsFromRecords() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> courtListings = createCourtListingRecord("listing-1");
            records.put(COURT_LISTING, courtListings);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                    .thenReturn(Map.of("listing-1", Set.of(UUID.fromString(courtSchedule.getCourtScheduleId()))));
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), eq(executionId));
        }

        @Test
        @DisplayName("Should skip court listing with missing fields")
        void shouldSkipCourtListingWithMissingFields() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> courtListings = new HashMap<>();
            final Map<String, String> listingData = new HashMap<>();
            listingData.put("panel", "PANEL1");
            // Missing sessionDate and session
            courtListings.put("listing-1", listingData);
            records.put(COURT_LISTING, courtListings);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                    .thenReturn(emptyMap());
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), eq(executionId));
        }

        @Test
        @DisplayName("Should skip court listing with invalid date")
        void shouldSkipCourtListingWithInvalidDate() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> courtListings = new HashMap<>();
            final Map<String, String> listingData = new HashMap<>();
            listingData.put("panel", "PANEL1");
            listingData.put("sessionDate", "invalid-date");
            listingData.put("session", "AM");
            courtListings.put("listing-1", listingData);
            records.put(COURT_LISTING, courtListings);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                    .thenReturn(emptyMap());
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), eq(executionId));
        }

        @Test
        @DisplayName("Should process schedules from records")
        void shouldProcessSchedulesFromRecords() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> schedules = createScheduleRecord("schedule-1", "judge-1", "listing-1");
            records.put(SCHEDULE, schedules);
            final Map<String, Map<String, String>> magistrates = createMagistratesRecord("judge-1", "magistrate@example.com");
            records.put(MAGISTRATES, magistrates);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), anyString()))
                    .thenReturn(Map.of("judge-1", UUID.fromString(judiciary.getId())));
            when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                    .thenReturn(Map.of("listing-1", Set.of(UUID.randomUUID())));
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(Map.of(judiciary.getId(), List.of(new JudiciaryCourtScheduleData(
                            List.of(UUID.randomUUID()), null, "CHAIR", true, false))));

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), eq(executionId));
        }

        @Test
        @DisplayName("Should skip schedule with missing rota justice ID")
        void shouldSkipScheduleWithMissingRotaJusticeId() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> schedules = new HashMap<>();
            final Map<String, String> scheduleData = new HashMap<>();
            // Missing ROTA_JUDICIARY_ID
            scheduleData.put(COURT_LISTING_PROFILE_ID, "listing-1");
            schedules.put("schedule-1", scheduleData);
            records.put(SCHEDULE, schedules);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), eq(executionId));
        }

        @Test
        @DisplayName("Should skip schedule with missing court listing profile ID")
        void shouldSkipScheduleWithMissingCourtListingProfileId() {
            // given
            setupSuccessfulProcessing();
            final Map<String, Map<String, String>> schedules = new HashMap<>();
            final Map<String, String> scheduleData = new HashMap<>();
            scheduleData.put(ROTA_JUDICIARY_ID, "judge-1");
            // Missing COURT_LISTING_PROFILE_ID
            schedules.put("schedule-1", scheduleData);
            records.put(SCHEDULE, schedules);
            final Map<String, Map<String, String>> magistrates = createMagistratesRecord("judge-1", "magistrate@example.com");
            records.put(MAGISTRATES, magistrates);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), anyString()))
                    .thenReturn(Map.of("judge-1", UUID.fromString(judiciary.getId())));
            when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                    .thenReturn(emptyMap());
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(Collections.emptyMap());

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), eq(executionId));
        }
    }

    // ============================================================================
    // Tests for Judiciary Assignment Operations
    // ============================================================================

    @Nested
    @DisplayName("Judiciary Assignment Tests")
    class JudiciaryAssignmentTests {

        @Test
        @DisplayName("Should use execution ID from parse result in processing pipeline")
        void shouldUseExecutionIdFromParseResultInProcessingPipeline() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();
            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
            final Map<String, JudiciaryCourtScheduleData> assignmentDataMap = new HashMap<>();
            assignmentDataMap.put(judiciary.getId(), new JudiciaryCourtScheduleData(
                    List.of(sessionId1), null, "CHAIR", true, false));

            final AssignJudiciariesRequest assignRequest = createAssignRequest(judiciary.getId(), sessionId1);
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);

            final AssignJudiciariesResponse assignResponse = createAssignResponse(1, 1);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(rotaJudiciaryHelper).createJudiciaryMap(anyMap(), eq(executionId));
            verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), eq(executionId));
            verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), eq(executionId));
            verify(judiciaryAssignmentService).assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true));
        }

        @Test
        @DisplayName("Should call assign judiciaries when assignment map is not empty")
        void shouldCallAssignJudiciariesWhenAssignmentMapIsNotEmpty() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();

            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
            final UUID sessionId2 = randomUUID();

            final Map<String, List<JudiciaryCourtScheduleData>> rotaFeedDataMap = new HashMap<>();
            rotaFeedDataMap.put(judiciary.getId(), List.of(new JudiciaryCourtScheduleData(
                    List.of(sessionId1, sessionId2), null, "CHAIR", true, false)));
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(rotaFeedDataMap);

            final AssignJudiciariesRequest assignRequest = createAssignRequest(judiciary.getId(), sessionId1, sessionId2);
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);

            final AssignJudiciariesResponse assignResponse = createAssignResponse(2, 2);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            final ArgumentCaptor<AssignJudiciariesRequest> requestCaptor = ArgumentCaptor.forClass(AssignJudiciariesRequest.class);
            verify(judiciaryAssignmentService).assignJudiciaries(requestCaptor.capture(), eq(executionId), eq(true));

            final AssignJudiciariesRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getJudiciaries().size(), is(1));
            assertThat(capturedRequest.getJudiciaries().get(0).getJudiciaryId(), is(judiciary.getId()));
            assertThat(capturedRequest.getJudiciaries().get(0).getSessionIds().size(), is(2));
            assertThat(capturedRequest.getJudiciaries().get(0).getSessionIds(), is(List.of(sessionId1.toString(), sessionId2.toString())));
        }

        @Test
        @DisplayName("Should not call assign judiciaries when assignment map is empty")
        void shouldNotCallAssignJudiciariesWhenAssignmentMapIsEmpty() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithRotaPeriod();
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(emptyMap());
            setupEmptyProcessingMaps();

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(judiciaryAssignmentService, never()).assignJudiciaries(any(AssignJudiciariesRequest.class), anyString());
        }

        @Test
        @DisplayName("Should handle multiple judiciaries in assignment map")
        void shouldHandleMultipleJudiciariesInAssignmentMap() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();

            final String judiciaryId1 = judiciary.getId();
            final String judiciaryId2 = randomUUID().toString();
            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
            final UUID sessionId2 = randomUUID();

            final Map<String, List<JudiciaryCourtScheduleData>> rotaFeedDataMap = new HashMap<>();
            rotaFeedDataMap.put(judiciaryId1, List.of(new JudiciaryCourtScheduleData(
                    List.of(sessionId1), null, "CHAIR", true, false)));
            rotaFeedDataMap.put(judiciaryId2, List.of(new JudiciaryCourtScheduleData(
                    List.of(sessionId2), null, "CHAIR", true, false)));
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(rotaFeedDataMap);

            final AssignJudiciariesRequest assignRequest = createMultiJudiciaryAssignRequest(judiciaryId1, sessionId1, judiciaryId2, sessionId2);
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);

            final AssignJudiciariesResponse assignResponse = createAssignResponse(2, 2);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            final ArgumentCaptor<AssignJudiciariesRequest> requestCaptor = ArgumentCaptor.forClass(AssignJudiciariesRequest.class);
            verify(judiciaryAssignmentService).assignJudiciaries(requestCaptor.capture(), eq(executionId), eq(true));

            final AssignJudiciariesRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.getJudiciaries().size(), is(2));
        }

        @Test
        @DisplayName("Should handle multiple schedule data entries for same judiciary")
        void shouldHandleMultipleScheduleDataEntriesForSameJudiciary() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();

            final String judiciaryId1 = judiciary.getId();
            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
            final UUID sessionId2 = randomUUID();
            final UUID sessionId3 = randomUUID();

            final Map<String, List<JudiciaryCourtScheduleData>> rotaFeedDataMap = new HashMap<>();
            // Same judiciary with multiple schedule data entries
            rotaFeedDataMap.put(judiciaryId1, List.of(
                    new JudiciaryCourtScheduleData(List.of(sessionId1), null, "CHAIR", true, false),
                    new JudiciaryCourtScheduleData(List.of(sessionId2), null, "LEFT_WINGER", false, true),
                    new JudiciaryCourtScheduleData(List.of(sessionId3), null, "RIGHT_WINGER", false, false)
            ));
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(rotaFeedDataMap);

            final AssignJudiciariesRequest assignRequest = AssignJudiciariesRequest.builder()
                    .withJudiciaries(List.of())
                    .withSkipValidations(true)
                    .build();
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);

            final AssignJudiciariesResponse assignResponse = createAssignResponse(3, 3);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<JudiciaryScheduleAssignment>> listCaptor = 
                    ArgumentCaptor.forClass(List.class);
            verify(judiciaryAssignmentRequestHelper).buildAssignJudiciariesRequest(listCaptor.capture());
            final List<JudiciaryScheduleAssignment> capturedList = listCaptor.getValue();
            assertThat(capturedList.size(), is(3)); // Should have 3 assignments for same judiciary
            // Verify all assignments are for the same judiciary
            assertThat(capturedList.stream().allMatch(a -> a.judiciaryId().equals(judiciaryId1)), is(true));
        }

        @Test
        @DisplayName("Should skip empty lists in assignment map")
        void shouldSkipEmptyListsInAssignmentMap() {
            // given
            setupSuccessfulProcessing();
            setupRecordsWithData();

            final String judiciaryId1 = judiciary.getId();
            final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());

            final Map<String, List<JudiciaryCourtScheduleData>> rotaFeedDataMap = new HashMap<>();
            rotaFeedDataMap.put(judiciaryId1, List.of(new JudiciaryCourtScheduleData(
                    List.of(sessionId1), null, "CHAIR", true, false)));
            rotaFeedDataMap.put("judiciary-2", Collections.emptyList()); // Empty list
            rotaFeedDataMap.put("judiciary-3", null); // Null list
            when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                    .thenReturn(rotaFeedDataMap);

            final AssignJudiciariesRequest assignRequest = createAssignRequest(judiciaryId1, sessionId1);
            when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(anyList()))
                    .thenReturn(assignRequest);

            final AssignJudiciariesResponse assignResponse = createAssignResponse(1, 1);
            when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(executionId), eq(true)))
                    .thenReturn(assignResponse);

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<JudiciaryScheduleAssignment>> listCaptor = 
                    ArgumentCaptor.forClass(List.class);
            verify(judiciaryAssignmentRequestHelper).buildAssignJudiciariesRequest(listCaptor.capture());
            final List<JudiciaryScheduleAssignment> capturedList = listCaptor.getValue();
            // Should only have 1 assignment (empty and null lists filtered out)
            assertThat(capturedList.size(), is(1));
            assertThat(capturedList.get(0).judiciaryId(), is(judiciaryId1));
        }
    }

    // ============================================================================
    // Tests for Error Handling
    // ============================================================================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle exception during court listing processing")
        void shouldHandleExceptionDuringCourtListingProcessing() {
            // given
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(rotaFileProcessHistory);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
            when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
            when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
            lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
            doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());

            final Map<String, Map<String, String>> courtListings = createCourtListingRecord("listing-1");
            records.put(COURT_LISTING, courtListings);

            setupLocationAndPeriodHelpers();
            when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                    .thenThrow(new RuntimeException("Date parsing error"));

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
            verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("Should handle exception during schedule processing")
        void shouldHandleExceptionDuringScheduleProcessing() {
            // given
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(rotaFileProcessHistory);
            when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
            final Map<String, Map<String, String>> schedules = createScheduleRecord("schedule-1", "judge-1", "listing-1");
            records.put(SCHEDULE, schedules);

            final Map<String, Map<String, String>> magistrates = createMagistratesRecord("judge-1", "magistrate@example.com");
            records.put(MAGISTRATES, magistrates);

            setupLocationAndPeriodHelpers();
            when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), anyString()))
                    .thenThrow(new RuntimeException("Validation error"));

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
            verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
            verify(azureBlobClientService, never()).deleteFile(anyString(), any());
        }

        @Test
        @DisplayName("Should handle exception during location processing")
        void shouldHandleExceptionDuringLocationProcessing() {
            // given
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(rotaFileProcessHistory);
            setupRecordsWithRotaPeriod();
            when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
            when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
            when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
            lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
            doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());

            when(rotaLocationPeriodHelper.getLocationFromRecords(anyMap()))
                    .thenThrow(new RuntimeException("Location processing error"));

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
            verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
        }

        @Test
        @DisplayName("Should handle exception during period processing")
        void shouldHandleExceptionDuringPeriodProcessing() {
            // given
            when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                    .thenReturn(rotaFileProcessHistory);
            setupRecordsWithRotaPeriod();
            when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
            when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
            when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
            lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
            doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());

            when(rotaLocationPeriodHelper.getLocationFromRecords(anyMap())).thenReturn(List.of());
            when(rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(anyList()))
                    .thenReturn(List.of());
            when(rotaLocationPeriodHelper.getRotaPeriodDates(anyMap()))
                    .thenThrow(new RuntimeException("Period processing error"));

            // when
            rotaFileProcessor.downloadAndProcessForEachFile(blobContentWrapper, blobName, leaseId);

            // then
            verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
            verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
        }
    }

    // ============================================================================
    // Helper Methods - Test Data Builders
    // ============================================================================

    private Map<String, Map<String, String>> createMagistratesRecord(final String id, final String email) {
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        final Map<String, String> magistrateData = new HashMap<>();
        magistrateData.put(MAGS_EMAIL, email);
        magistrates.put(id, magistrateData);
        return magistrates;
    }

    private Map<String, Map<String, String>> createCourtListingRecord(final String id) {
        final Map<String, Map<String, String>> courtListings = new HashMap<>();
        final Map<String, String> listingData = new HashMap<>();
        listingData.put("panel", "PANEL1");
        listingData.put("sessionDate", "2024-01-15");
        listingData.put("session", "AM");
        listingData.put("locationId", "100");
        listingData.put("venueId", "200");
        listingData.put("venueName", "Test Venue");
        courtListings.put(id, listingData);
        return courtListings;
    }

    private Map<String, Map<String, String>> createScheduleRecord(final String scheduleId, final String rotaJudiciaryId, final String courtListingProfileId) {
        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> scheduleData = new HashMap<>();
        scheduleData.put(ROTA_JUDICIARY_ID, rotaJudiciaryId);
        scheduleData.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        scheduleData.put(JUDICIARY_ID, judiciary.getId());
        schedules.put(scheduleId, scheduleData);
        return schedules;
    }

    private AssignJudiciariesRequest createAssignRequest(final String judiciaryId, final UUID... sessionIds) {
        return AssignJudiciariesRequest.builder()
                .withJudiciaries(List.of(
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciaryId)
                                .withSessionIds(java.util.Arrays.stream(sessionIds).map(UUID::toString).toList())
                                .withPosition("CHAIR")
                                .withIsBenchChairman(true)
                                .withIsDeputy(false)
                                .build()
                ))
                .build();
    }

    private AssignJudiciariesRequest createMultiJudiciaryAssignRequest(
            final String judiciaryId1, final UUID sessionId1,
            final String judiciaryId2, final UUID sessionId2) {
        return AssignJudiciariesRequest.builder()
                .withJudiciaries(List.of(
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciaryId1)
                                .withSessionIds(List.of(sessionId1.toString()))
                                .withPosition("CHAIR")
                                .withIsBenchChairman(true)
                                .withIsDeputy(false)
                                .build(),
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciaryId2)
                                .withSessionIds(List.of(sessionId2.toString()))
                                .withPosition("CHAIR")
                                .withIsBenchChairman(true)
                                .withIsDeputy(false)
                                .build()
                ))
                .build();
    }

    private AssignJudiciariesResponse createAssignResponse(final int requested, final int successful) {
        return AssignJudiciariesResponse.builder()
                .withRequestedAssignments(requested)
                .withSuccessfulAssignments(successful)
                .withFailures(List.of())
                .build();
    }

    // ============================================================================
    // Helper Methods - Mock Setup
    // ============================================================================

    private void setupBasicProcessingMocks() {
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(true);
        when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        lenient().when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                .thenReturn(rotaFileProcessHistory);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());
    }

    private void setupSuccessfulProcessing() {
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
        lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        lenient().when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                .thenReturn(rotaFileProcessHistory);
        lenient().doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        lenient().doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        lenient().doNothing().when(azureBlobClientService).deleteFile(anyString(), any());
        setupLocationAndPeriodHelpers();
    }

    private void setupLocationAndPeriodHelpers() {
        lenient().when(rotaLocationPeriodHelper.getLocationFromRecords(anyMap())).thenReturn(List.of());
        lenient().when(rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(anyList()))
                .thenReturn(List.of());
        final RotaPeriodDateInfoProvider mockPeriodProvider = createMockRotaPeriodDateInfoProvider();
        when(rotaLocationPeriodHelper.getRotaPeriodDates(anyMap())).thenReturn(mockPeriodProvider);
        lenient().when(rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(any(), any(), anyList()))
                .thenReturn(0);
    }

    private RotaPeriodDateInfoProvider createMockRotaPeriodDateInfoProvider() {
        final Map<RotaPayload, Map<String, Map<String, String>>> periodRecords = new HashMap<>();
        final Map<String, Map<String, String>> rotaPeriodMap = new HashMap<>();
        final Map<String, String> rotaPeriodData = new HashMap<>();
        rotaPeriodData.put("rotaPeriodStartDate", "2024-01-01");
        rotaPeriodData.put("rotaPeriodEndDate", "2024-12-31");
        rotaPeriodMap.put("period-1", rotaPeriodData);
        periodRecords.put(RotaPayload.ROTA_PERIOD, rotaPeriodMap);
        return new RotaPeriodDateInfoProvider(periodRecords);
    }

    private void setupRecordsWithRotaPeriod() {
        records.clear();
        final Map<String, Map<String, String>> rotaPeriodMap = new HashMap<>();
        final Map<String, String> rotaPeriodData = new HashMap<>();
        rotaPeriodData.put("rotaPeriodStartDate", "2024-01-01");
        rotaPeriodData.put("rotaPeriodEndDate", "2024-12-31");
        rotaPeriodMap.put("period-1", rotaPeriodData);
        records.put(RotaPayload.ROTA_PERIOD, rotaPeriodMap);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
    }

    private void setupRecordsWithData() {
        records.clear();
        
        // Setup ROTA_PERIOD (required for getRotaPeriodDates)
        final Map<String, Map<String, String>> rotaPeriodMap = new HashMap<>();
        final Map<String, String> rotaPeriodData = new HashMap<>();
        rotaPeriodData.put("rotaPeriodStartDate", "2024-01-01");
        rotaPeriodData.put("rotaPeriodEndDate", "2024-12-31");
        rotaPeriodMap.put("period-1", rotaPeriodData);
        records.put(RotaPayload.ROTA_PERIOD, rotaPeriodMap);

        // Setup magistrates
        records.put(MAGISTRATES, createMagistratesRecord("mag-1", "magistrate@example.com"));

        // Setup district judges
        final Map<String, Map<String, String>> districtJudges = new HashMap<>();
        final Map<String, String> judgeData = new HashMap<>();
        judgeData.put(JUDGE_EMAIL, "judge@example.com");
        districtJudges.put("judge-1", judgeData);
        records.put(DISTRICT_JUDGES, districtJudges);

        // Setup court listings
        records.put(COURT_LISTING, createCourtListingRecord("listing-1"));

        // Setup schedules
        records.put(SCHEDULE, createScheduleRecord("schedule-1", "judge-1", "listing-1"));

        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);

        // Mock RotaJudiciaryHelper
        final Map<String, UUID> judiciaryMap = new HashMap<>();
        judiciaryMap.put("mag-1", UUID.fromString(judiciary.getId()));
        judiciaryMap.put("judge-1", UUID.fromString(judiciary.getId()));
        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), anyString()))
                .thenReturn(judiciaryMap);

        // Mock RotaCourtScheduleHelper
        final Map<String, Set<UUID>> courtScheduleMap = new HashMap<>();
        courtScheduleMap.put("listing-1", Set.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                .thenReturn(courtScheduleMap);

        // Mock RotaJudiciaryHelper for judiciary court schedule map
        final Map<String, List<JudiciaryCourtScheduleData>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(new JudiciaryCourtScheduleData(
                List.of(UUID.fromString(courtSchedule.getCourtScheduleId())), null, "CHAIR", true, false)));
        when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                .thenReturn(rotaFeedMap);
    }

    private void setupEmptyProcessingMaps() {
        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), anyString()))
                .thenReturn(emptyMap());
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), anyString()))
                .thenReturn(emptyMap());
        when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), anyString()))
                .thenReturn(emptyMap());
    }
}
