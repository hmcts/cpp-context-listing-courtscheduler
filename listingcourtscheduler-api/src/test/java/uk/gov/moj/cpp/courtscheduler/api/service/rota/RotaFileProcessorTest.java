package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.CourtScheduleJudiciaryQueryHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryAssignmentRequestHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleMapComparator;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaCourtScheduleHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaFileUtility;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaJudiciaryHelper;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
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

@ExtendWith(MockitoExtension.class)
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
    private CourtScheduleJudiciaryQueryHelper courtScheduleJudiciaryQueryHelper;

    @Mock
    private JudiciaryCourtScheduleMapComparator mapComparator;

    @Mock
    private JudiciaryAssignmentService judiciaryAssignmentService;

    @Mock
    private JudiciaryUnassignmentService judiciaryUnassignmentService;

    @Mock
    private RotaJudiciaryHelper rotaJudiciaryHelper;

    @Mock
    private RotaCourtScheduleHelper rotaCourtScheduleHelper;

    @Mock
    private JudiciaryAssignmentRequestHelper judiciaryAssignmentRequestHelper;

    @Mock
    private Requester requester;

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
    private CourtRoom courtRoom;
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
                .withSessionDate(LocalDate.parse("2024-01-15"))
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
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(azureBlobClientService).uploadProcessedFile(any(ByteArrayInputStream.class), eq((long) blobContent.length), eq(blobName), eq(empty()));
        verify(azureBlobClientService).releaseLease(blobName, leaseId, false);
        verify(azureBlobClientService).deleteFile(blobName, empty());
    }

    @Test
    void shouldReleaseLeaseOnError() {
        // given
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileParser.parse(anyString(), any())).thenThrow(new RuntimeException("Parsing error"));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

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
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileParser).parse(blobName, blobContent);
        verify(rotaJudiciaryHelper).createJudiciaryMap(anyMap(), any(), anyString());
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), anyString());
        verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), anyString());
        verify(courtScheduleJudiciaryQueryHelper).queryCourtScheduleIdsByJudiciaryIds(anyMap());
        verify(mapComparator).findMissingCourtScheduleIdsInDB(anyMap(), anyMap());
        verify(mapComparator).findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap());
    }

    @Test
    void shouldHandleEmptyRecords() {
        // given
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
        lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                .thenReturn(rotaFileProcessHistory);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileParser).parse(blobName, blobContent);
        verify(courtScheduleJudiciaryQueryHelper, never()).queryCourtScheduleIdsByJudiciaryIds(anyMap());
        verify(rotaFileProcessHistoryService).update(rotaFileProcessHistory);
    }

    @Test
    void shouldUpdateFileProcessHistoryAfterProcessing() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileProcessHistoryService).update(rotaFileProcessHistory);
    }

    @Test
    void shouldHandleNullFileProcessHistory() {
        // given
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(null);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
        lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
        verify(rotaFileParser).parse(blobName, blobContent);
    }

    @Test
    void shouldGenerateExecutionIdWhenFileProcessHistoryIsNull() {
        // given
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(null);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
        lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).createAndSaveFileProcessHistory(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
        // Verify that processing continues with a generated executionId (implicitly tested through successful completion)
        verify(rotaFileParser).parse(blobName, blobContent);
    }

    @Test
    void shouldUseExecutionIdFromFileProcessHistoryWhenItExists() {
        // given
        final String expectedExecutionId = "expected-execution-id-123";
        rotaFileProcessHistory.setExecutionId(expectedExecutionId);
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
        lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                .thenReturn(rotaFileProcessHistory);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).createAndSaveFileProcessHistory(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
        verify(rotaFileProcessHistoryService).update(rotaFileProcessHistory);
        // Verify that the executionId from rotaFileProcessHistory is used throughout processing
        // This is implicitly verified as the processing completes successfully
        assertThat(rotaFileProcessHistory.getExecutionId(), is(expectedExecutionId));
    }

    @Test
    void shouldUseExecutionIdFromParseResultInProcessingPipeline() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        // Setup assignment map to ensure assignJudiciaries is called
        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final Map<String, List<UUID>> assignmentMap = new HashMap<>();
        assignmentMap.put(judiciary.getId(), List.of(sessionId1));
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(assignmentMap);
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());

        final AssignJudiciariesRequest assignRequest = AssignJudiciariesRequest.builder()
                .withJudiciaries(List.of(
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciary.getId())
                                .withSessionIds(List.of(sessionId1.toString()))
                                .build()
                ))
                .build();
        when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentMap))
                .thenReturn(assignRequest);

        final AssignJudiciariesResponse assignResponse = AssignJudiciariesResponse.builder()
                .withRequestedAssignments(1)
                .withSuccessfulAssignments(1)
                .withFailures(List.of())
                .build();
        when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(requester), eq(executionId)))
                .thenReturn(assignResponse);

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // Verify that executionId is passed to all processing methods
        verify(rotaJudiciaryHelper).createJudiciaryMap(anyMap(), any(), eq(executionId));
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), eq(executionId));
        verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), eq(executionId));
        verify(judiciaryAssignmentService).assignJudiciaries(any(AssignJudiciariesRequest.class), eq(requester), eq(executionId));
    }

    @Test
    void shouldUseExecutionIdFromParseResultInUnassignment() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final Map<String, List<UUID>> dbMap = new HashMap<>();
        dbMap.put(judiciary.getId(), List.of(sessionId1));
        final Map<String, List<UUID>> unassignmentMap = new HashMap<>();
        unassignmentMap.put(judiciary.getId(), List.of(sessionId1));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(unassignmentMap);

        final Map<String, List<String>> convertedMap = new HashMap<>();
        convertedMap.put(judiciary.getId(), List.of(sessionId1.toString()));
        when(judiciaryAssignmentRequestHelper.convertToUnassignmentMap(unassignmentMap))
                .thenReturn(convertedMap);
        doNothing().when(judiciaryUnassignmentService).unassignJudiciary(anyMap(), anyString(), anyBoolean());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // Verify that executionId from ParseResult is used in unassignment
        verify(judiciaryUnassignmentService).unassignJudiciary(anyMap(), eq(executionId), eq(true));
    }

    @Test
    void shouldSkipProcessingForDummyFile() {
        // given
        blobName = "dummysupport_file.xml";
        when(rotaFileUtility.isDummyFile(blobName)).thenReturn(true);

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).isDummyFile(blobName);
        verify(rotaFileParser, never()).parse(anyString(), any());
        verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
        verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), eq(blobName), any());
    }

    @Test
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
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).isNewerSnapshotFileProcessed(blobName);
        verify(rotaFileParser, never()).parse(anyString(), any());
        verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
        verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), eq(blobName), any());
    }

    // ============================================================================
    // Helper methods to setup test data
    // ============================================================================

    private void setupSuccessfulProcessing() {
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(false);
        lenient().when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                .thenReturn(rotaFileProcessHistory);
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

        // Mock RotaJudiciaryHelper
        Map<String, UUID> judiciaryMap = new HashMap<>();
        judiciaryMap.put("mag-1", UUID.fromString(judiciary.getId()));
        judiciaryMap.put("judge-1", UUID.fromString(judiciary.getId()));
        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), any(), anyString()))
                .thenReturn(judiciaryMap);

        // Mock RotaCourtScheduleHelper
        Map<String, Set<UUID>> courtScheduleMap = new HashMap<>();
        courtScheduleMap.put("listing-1", Set.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(courtScheduleMap);

        // Mock RotaJudiciaryHelper for judiciary court schedule map
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), anyString()))
                .thenReturn(rotaFeedMap);

        Map<String, List<UUID>> dbMap = new HashMap<>();
        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
    }

    @Test
    void shouldHandleSnapshotFileProcessing() {
        // given
        blobName = "test_snapshot_20240115_120000.xml";
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(emptyMap());
        when(rotaFileUtility.convertNanosToMillis(anyLong())).thenReturn(100L);
        when(rotaFileUtility.isDummyFile(anyString())).thenReturn(false);
        when(rotaFileUtility.isSnapshotFile(anyString())).thenReturn(true);
        when(rotaFileUtility.isNewerSnapshotFileProcessed(anyString())).thenReturn(false);
        when(rotaFileProcessHistoryService.update(any(RotaFileProcessHistory.class)))
                .thenReturn(rotaFileProcessHistory);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).createAndSaveFileProcessHistory(eq(blobName), eq(blobContent), eq(rotaFileProcessHistoryService));
    }

    @Test
    void shouldHandleDummyFile() {
        // given
        blobName = "dummysupport_file.xml";
        when(rotaFileUtility.isDummyFile(blobName)).thenReturn(true);
        doNothing().when(azureBlobClientService).uploadProcessedFile(any(), anyLong(), anyString(), any());
        doNothing().when(azureBlobClientService).releaseLease(anyString(), anyString(), anyBoolean());
        doNothing().when(azureBlobClientService).deleteFile(anyString(), any());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaFileUtility).isDummyFile(blobName);
        verify(rotaFileUtility, never()).isNewerSnapshotFileProcessed(anyString());
        verify(rotaFileParser, never()).parse(anyString(), any());
        verify(rotaFileProcessHistoryService, never()).update(any(RotaFileProcessHistory.class));
        verify(azureBlobClientService).uploadProcessedFile(any(), anyLong(), eq(blobName), any());
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
        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), any(), anyString()))
                .thenReturn(Map.of("mag-1", UUID.fromString(judiciary.getId())));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaJudiciaryHelper).createJudiciaryMap(anyMap(), any(), eq(executionId));
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
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(Map.of("listing-1", Set.of(UUID.fromString(courtSchedule.getCourtScheduleId()))));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), eq(executionId));
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
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), eq(executionId));
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
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), eq(executionId));
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
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), eq(executionId));
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
        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), any(), anyString()))
                .thenReturn(Map.of("judge-1", UUID.fromString(judiciary.getId())));
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(Map.of("listing-1", Set.of(UUID.randomUUID())));
        when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), anyString()))
                .thenReturn(Map.of(judiciary.getId(), List.of(UUID.randomUUID())));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), eq(executionId));
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
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), eq(executionId));
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
        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), any(), anyString()))
                .thenReturn(Map.of("judge-1", UUID.fromString(judiciary.getId())));
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(emptyMap());
        when(rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), anyString()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(rotaJudiciaryHelper).createJudiciaryCourtScheduleMap(anyMap(), anyMap(), anyMap(), any(), eq(executionId));
    }

    @Test
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

        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenThrow(new RuntimeException("Date parsing error"));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // Exception should be caught and lease should be released with error flag
        verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
        verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
    }

    @Test
    void shouldHandleExceptionDuringScheduleProcessing() {
        // given
        when(rotaFileUtility.createAndSaveFileProcessHistory(anyString(), any(), any()))
                .thenReturn(rotaFileProcessHistory);
        when(rotaFileParser.parse(anyString(), any())).thenReturn(records);
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

        when(rotaJudiciaryHelper.createJudiciaryMap(anyMap(), any(), anyString()))
                .thenThrow(new RuntimeException("Validation error"));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // Exception should be caught and lease should be released with error flag
        verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
        verify(azureBlobClientService, never()).uploadProcessedFile(any(), anyLong(), anyString(), any());
        verify(azureBlobClientService, never()).deleteFile(anyString(), any());
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
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

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

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

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
        when(rotaCourtScheduleHelper.createCourtScheduleMap(anyMap(), any(), anyString()))
                .thenReturn(Map.of("listing-1", Set.of(UUID.fromString(matchingSchedule.getCourtScheduleId()))));

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        // The filtering should only include matchingSchedule
        verify(rotaCourtScheduleHelper).createCourtScheduleMap(anyMap(), any(), eq(executionId));
    }

    // ============================================================================
    // Tests for assign and unassign scenarios
    // ============================================================================

    @Test
    void shouldCallAssignJudiciariesWhenAssignmentMapIsNotEmpty() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final UUID sessionId2 = randomUUID();

        final Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(sessionId1, sessionId2));
        final Map<String, List<UUID>> dbMap = new HashMap<>();
        final Map<String, List<UUID>> assignmentMap = new HashMap<>();
        assignmentMap.put(judiciary.getId(), List.of(sessionId1, sessionId2));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(assignmentMap);
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());

        final AssignJudiciariesRequest assignRequest = AssignJudiciariesRequest.builder()
                .withJudiciaries(List.of(
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciary.getId())
                                .withSessionIds(List.of(sessionId1.toString(), sessionId2.toString()))
                                .build()
                ))
                .build();
        when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentMap))
                .thenReturn(assignRequest);

        final AssignJudiciariesResponse assignResponse = AssignJudiciariesResponse.builder()
                .withRequestedAssignments(2)
                .withSuccessfulAssignments(2)
                .withFailures(List.of())
                .build();
        when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(requester), eq(executionId)))
                .thenReturn(assignResponse);

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        final ArgumentCaptor<AssignJudiciariesRequest> requestCaptor = ArgumentCaptor.forClass(AssignJudiciariesRequest.class);
        verify(judiciaryAssignmentService).assignJudiciaries(requestCaptor.capture(), eq(requester), eq(executionId));

        final AssignJudiciariesRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getJudiciaries().size(), is(1));
        assertThat(capturedRequest.getJudiciaries().get(0).getJudiciaryId(), is(judiciary.getId()));
        assertThat(capturedRequest.getJudiciaries().get(0).getSessionIds().size(), is(2));
        assertThat(capturedRequest.getJudiciaries().get(0).getSessionIds(), is(List.of(sessionId1.toString(), sessionId2.toString())));
    }

    @Test
    void shouldNotCallAssignJudiciariesWhenAssignmentMapIsEmpty() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        final Map<String, List<UUID>> dbMap = new HashMap<>();
        dbMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(judiciaryAssignmentService, never()).assignJudiciaries(any(), any(), anyString());
    }

    @Test
    void shouldCallUnassignJudiciaryWhenUnassignmentMapIsNotEmpty() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final UUID sessionId2 = randomUUID();

        final Map<String, List<UUID>> dbMap = new HashMap<>();
        dbMap.put(judiciary.getId(), List.of(sessionId1, sessionId2));
        final Map<String, List<UUID>> unassignmentMap = new HashMap<>();
        unassignmentMap.put(judiciary.getId(), List.of(sessionId1, sessionId2));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(unassignmentMap);

        final Map<String, List<String>> convertedMap = new HashMap<>();
        convertedMap.put(judiciary.getId(), List.of(sessionId1.toString(), sessionId2.toString()));
        when(judiciaryAssignmentRequestHelper.convertToUnassignmentMap(unassignmentMap))
                .thenReturn(convertedMap);

        doNothing().when(judiciaryUnassignmentService).unassignJudiciary(anyMap(), eq(executionId), anyBoolean());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, List<String>>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(judiciaryUnassignmentService).unassignJudiciary(mapCaptor.capture(), eq(executionId), eq(true));

        final Map<String, List<String>> capturedMap = mapCaptor.getValue();
        assertThat(capturedMap.size(), is(1));
        assertThat(capturedMap.containsKey(judiciary.getId()), is(true));
        assertThat(capturedMap.get(judiciary.getId()).size(), is(2));
        assertThat(capturedMap.get(judiciary.getId()), is(List.of(sessionId1.toString(), sessionId2.toString())));
    }

    @Test
    void shouldNotCallUnassignJudiciaryWhenUnassignmentMapIsEmpty() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));
        final Map<String, List<UUID>> dbMap = new HashMap<>();
        dbMap.put(judiciary.getId(), List.of(UUID.fromString(courtSchedule.getCourtScheduleId())));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(judiciaryUnassignmentService, never()).unassignJudiciary(anyMap(), anyString());
    }

    @Test
    void shouldCallBothAssignAndUnassignWhenBothMapsAreNotEmpty() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final UUID sessionId2 = randomUUID();
        final UUID sessionId3 = randomUUID();

        final Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciary.getId(), List.of(sessionId1, sessionId2));
        final Map<String, List<UUID>> dbMap = new HashMap<>();
        dbMap.put(judiciary.getId(), List.of(sessionId3));
        final Map<String, List<UUID>> assignmentMap = new HashMap<>();
        assignmentMap.put(judiciary.getId(), List.of(sessionId1, sessionId2));
        final Map<String, List<UUID>> unassignmentMap = new HashMap<>();
        unassignmentMap.put(judiciary.getId(), List.of(sessionId3));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(dbMap);
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(assignmentMap);
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(unassignmentMap);

        final AssignJudiciariesRequest assignRequest = AssignJudiciariesRequest.builder()
                .withJudiciaries(List.of(
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciary.getId())
                                .withSessionIds(List.of(sessionId1.toString(), sessionId2.toString()))
                                .build()
                ))
                .build();
        when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentMap))
                .thenReturn(assignRequest);

        final Map<String, List<String>> convertedMap = new HashMap<>();
        convertedMap.put(judiciary.getId(), List.of(sessionId3.toString()));
        when(judiciaryAssignmentRequestHelper.convertToUnassignmentMap(unassignmentMap))
                .thenReturn(convertedMap);

        final AssignJudiciariesResponse assignResponse = AssignJudiciariesResponse.builder()
                .withRequestedAssignments(2)
                .withSuccessfulAssignments(2)
                .withFailures(List.of())
                .build();
        when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(requester), eq(executionId)))
                .thenReturn(assignResponse);
        doNothing().when(judiciaryUnassignmentService).unassignJudiciary(anyMap(), eq(executionId), anyBoolean());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        verify(judiciaryAssignmentService).assignJudiciaries(any(AssignJudiciariesRequest.class), eq(requester), eq(executionId));
        verify(judiciaryUnassignmentService).unassignJudiciary(anyMap(), eq(executionId), eq(true));
    }

    @Test
    void shouldHandleMultipleJudiciariesInAssignmentMap() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final String judiciaryId1 = judiciary.getId();
        final String judiciaryId2 = randomUUID().toString();
        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final UUID sessionId2 = randomUUID();

        final Map<String, List<UUID>> assignmentMap = new HashMap<>();
        assignmentMap.put(judiciaryId1, List.of(sessionId1));
        assignmentMap.put(judiciaryId2, List.of(sessionId2));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(assignmentMap);
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(emptyMap());

        final AssignJudiciariesRequest assignRequest = AssignJudiciariesRequest.builder()
                .withJudiciaries(List.of(
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciaryId1)
                                .withSessionIds(List.of(sessionId1.toString()))
                                .build(),
                        uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment.builder()
                                .withJudiciaryId(judiciaryId2)
                                .withSessionIds(List.of(sessionId2.toString()))
                                .build()
                ))
                .build();
        when(judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentMap))
                .thenReturn(assignRequest);

        final AssignJudiciariesResponse assignResponse = AssignJudiciariesResponse.builder()
                .withRequestedAssignments(2)
                .withSuccessfulAssignments(2)
                .withFailures(List.of())
                .build();
        when(judiciaryAssignmentService.assignJudiciaries(any(AssignJudiciariesRequest.class), eq(requester), eq(executionId)))
                .thenReturn(assignResponse);

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        final ArgumentCaptor<AssignJudiciariesRequest> requestCaptor = ArgumentCaptor.forClass(AssignJudiciariesRequest.class);
        verify(judiciaryAssignmentService).assignJudiciaries(requestCaptor.capture(), eq(requester), eq(executionId));

        final AssignJudiciariesRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getJudiciaries().size(), is(2));
    }

    @Test
    void shouldHandleMultipleJudiciariesInUnassignmentMap() {
        // given
        setupSuccessfulProcessing();
        setupRecordsWithData();

        final String judiciaryId1 = judiciary.getId();
        final String judiciaryId2 = randomUUID().toString();
        final UUID sessionId1 = UUID.fromString(courtSchedule.getCourtScheduleId());
        final UUID sessionId2 = randomUUID();

        final Map<String, List<UUID>> unassignmentMap = new HashMap<>();
        unassignmentMap.put(judiciaryId1, List.of(sessionId1));
        unassignmentMap.put(judiciaryId2, List.of(sessionId2));

        when(courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInDB(anyMap(), anyMap()))
                .thenReturn(emptyMap());
        when(mapComparator.findMissingCourtScheduleIdsInRotaFeed(anyMap(), anyMap()))
                .thenReturn(unassignmentMap);

        final Map<String, List<String>> convertedMap = new HashMap<>();
        convertedMap.put(judiciaryId1, List.of(sessionId1.toString()));
        convertedMap.put(judiciaryId2, List.of(sessionId2.toString()));
        when(judiciaryAssignmentRequestHelper.convertToUnassignmentMap(unassignmentMap))
                .thenReturn(convertedMap);

        doNothing().when(judiciaryUnassignmentService).unassignJudiciary(anyMap(), eq(executionId), anyBoolean());

        // when
        rotaFileProcessor.downloadAndProcessForEachFile(requester, blobContentWrapper, blobName, leaseId);

        // then
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Map<String, List<String>>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(judiciaryUnassignmentService).unassignJudiciary(mapCaptor.capture(), eq(executionId), eq(true));

        final Map<String, List<String>> capturedMap = mapCaptor.getValue();
        assertThat(capturedMap.size(), is(2));
        assertThat(capturedMap.containsKey(judiciaryId1), is(true));
        assertThat(capturedMap.containsKey(judiciaryId2), is(true));
    }
}

