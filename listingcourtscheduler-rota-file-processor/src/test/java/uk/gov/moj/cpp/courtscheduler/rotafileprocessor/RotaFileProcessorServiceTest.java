package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.time.LocalDate.parse;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.DateRange;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryScheduleEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingReferenceDataMappingLogger;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.RotaDataEnricher;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileProcessorServiceTest {

    @InjectMocks
    @Spy
    private RotaFileProcessorService rotaFileProcessorService;

    @Mock
    private AzureBlobClientService azureBlobClientService;

    @Mock
    private RotaFileParser rotaFileParser;

    @Mock
    private RotaDataEnricher rotaDataEnricher;

    @Mock
    private JudiciaryScheduleEnricher judiciaryScheduleEnricher;

    @Mock
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private RotaFilePartialProcessor rotaFilePartialProcessor;

    @Mock
    private RotaFileProcessHistory rotaFileProcessHistory;

    @Spy
    private MissingReferenceDataMappingLogger missingMessageLogger = new MissingReferenceDataMappingLogger();

    @Mock
    private Map<RotaPayload, Map<String, Map<String, String>>> records;

    @Mock
    private Map<String, CourtSchedule> slotsMock;
    @Mock
    private Collection<CourtScheduleJudiciary> schedules;

    private Map<String, Map<String, String>> rotaPeriodMap;

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private static final String PSV_AS_EXISTING_BUSINESS_TYPE = "PSV";
    private static final String NCPT_AS_EXISTING_BUSINESS_TYPE = "NCPT";
    private static final String CJU_AS_MISSING_BUSINESS_TYPE = "CJU";

    @BeforeEach
    public void setUp() {
        setField(rotaFileProcessorService, "rotaMonthsOfProvisionalDataToPopulate", "6");
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "28");
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcess() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);
        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        final List<String> businessTypes = List.of(PSV_AS_EXISTING_BUSINESS_TYPE, CJU_AS_MISSING_BUSINESS_TYPE);
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), businessTypes.get(i % 2), true));
        }

        final Map<String, CourtSchedule> slots = new HashMap<>();
        IntStream.range(0, 5).forEach(index -> {
            final CourtSchedule courtSchedule = extractedSchedules.get(index);
            slots.put(courtSchedule.getListingProfileId(), courtSchedule);
        });

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataMapperService.getBusinessTypeMap()).thenReturn(getRotaBusinessTypes());
        doReturn(CompletableFuture.completedFuture(null)).when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap();
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcessForDurationBasedSlots() throws IOException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "corrupted value-normally should be a number");

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        final List<String> businessTypes = List.of(NCPT_AS_EXISTING_BUSINESS_TYPE, CJU_AS_MISSING_BUSINESS_TYPE);
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), businessTypes.get(i % 2), false));
        }

        final Map<String, CourtSchedule> slots = new HashMap<>();
        IntStream.range(0, 5).forEach(index -> {
            final CourtSchedule courtSchedule = extractedSchedules.get(index);
            slots.put(courtSchedule.getListingProfileId(), courtSchedule);
        });

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataMapperService.getBusinessTypeMap()).thenReturn(getRotaBusinessTypesAsHavingCJUandNCPTonly());
        doReturn(CompletableFuture.completedFuture(null)).when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap();
    }


    @Test
    void shouldCaptureMasterRotaFileAndProcessIfAlsoThereIsNoExistingSchedules() throws IOException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", null);

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);
        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        final List<String> businessTypes = List.of(PSV_AS_EXISTING_BUSINESS_TYPE, CJU_AS_MISSING_BUSINESS_TYPE);
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), businessTypes.get(i % 2), true));
        }

        final Map<String, CourtSchedule> slots = new HashMap<>();
        IntStream.range(0, 5).forEach(index -> {
            final CourtSchedule courtSchedule = extractedSchedules.get(index);
            slots.put(courtSchedule.getListingProfileId(), courtSchedule);
        });

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(emptyList());
        when(referenceDataMapperService.getBusinessTypeMap()).thenReturn(getRotaBusinessTypes());
        doReturn(CompletableFuture.completedFuture(null)).when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());
        mockMigratedMapByOuCode("CABC90", false);

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap();
        verify(rotaFilePartialProcessor, atLeastOnce()).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());
    }

    @Test
    void shouldBreakRotaFileProcessIfCourtRoomsMapIsEmpty() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), PSV_AS_EXISTING_BUSINESS_TYPE, true));
        }

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(emptyMap());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(new HashMap<>());

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
    }

    @Test
    void shouldNotProcessDummyFile() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "dummysupport.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
    }

    @Test
    void shouldCaptureSnapshotFileAndProcess() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class))).thenReturn(emptyList());
        doReturn(CompletableFuture.completedFuture(null)).when(rotaFilePartialProcessor).processSnapshotRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), anyMap(), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));
        when(rotaFileProcessHistoryService.save(anyString(), any(), any(byte[].class), anyString())).thenReturn(rotaFileProcessHistory);

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap();
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, atLeastOnce()).save(anyString(), any(), any(byte[].class), anyString());

        final ArgumentCaptor<RotaFileProcessHistory> historyCaptor = ArgumentCaptor.forClass(RotaFileProcessHistory.class);
        final ArgumentCaptor<Boolean> lastRangeCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(rotaFilePartialProcessor, atLeastOnce()).processSnapshotRotaFile(
                anyMap(), anyMap(), anyCollection(), anyCollection(), anyMap(), anyList(), anyList(),
                anyMap(), anyMap(), anyString(), historyCaptor.capture(), lastRangeCaptor.capture());

        assertEquals(rotaFileProcessHistory, historyCaptor.getValue());
        assertTrue(lastRangeCaptor.getValue());
    }

    @Test
    void shouldNotProcessSnapshotFileIfThereIsOneAlreadyProcessedHavingANewerFileDate() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class)))
                .thenReturn(List.of(new RotaFileProcessHistory()));

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, never()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
        verify(rotaFileParser, never()).parse(any(), any());
        verify(referenceDataMapperService, never()).getCourtRoomsMap();
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(byte[].class), anyString());
    }

    @Test
    void shouldNotProcessSnapshotFileIfTheFileNameMissingFileDateTimePart() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);


        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, never()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
        verify(rotaFileParser, never()).parse(any(), any());
        verify(referenceDataMapperService, never()).getCourtRoomsMap();
        verify(rotaFileProcessHistoryRepository, never()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, never()).save(anyString(), any(), any(byte[].class), anyString());
    }

    @Test
    void shouldSplitDateRangeIntoWeeks() {
        final LocalDate startDate = LocalDate.of(2024, 4, 21);
        final LocalDate endDate = LocalDate.of(2024, 9, 30);

        List<DateRange> dateRanges = rotaFileProcessorService.weeksCovering(startDate, endDate);

        assertNotNull(dateRanges);
    }

    @Test
    void shouldSplitDateRangeIntoWeeksForTheBorder() {
        final LocalDate startDate = LocalDate.of(2024, 9, 17);
        final LocalDate endDate = LocalDate.of(2025, 3, 31);

        List<DateRange> dateRanges = rotaFileProcessorService.weeksCovering(startDate, endDate);

        assertNotNull(dateRanges);
        IntStream.range(1, dateRanges.size()).forEach(index -> {
            final DateRange previousDateRange = dateRanges.get(index - 1);
            final DateRange currentDateRange = dateRanges.get(index);

            assertNotEquals(previousDateRange.getEnd(), currentDateRange.getStart());
        });
    }

    @Test
    void shouldSplitDateRangeIntoWeeksForProper6Months() {
        final LocalDate startDate = LocalDate.of(2024, 10, 1);
        final LocalDate endDate = LocalDate.of(2025, 3, 31);

        List<DateRange> dateRanges = rotaFileProcessorService.weeksCovering(startDate, endDate);

        assertNotNull(dateRanges);
        IntStream.range(1, dateRanges.size()).forEach(index -> {
            final DateRange previousDateRange = dateRanges.get(index - 1);
            final DateRange currentDateRange = dateRanges.get(index);

            assertNotEquals(previousDateRange.getEnd(), currentDateRange.getStart());
        });
    }

    @Test
    void shouldProcessSnapshotFileWithNewerVersionCheck() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(rotaFileProcessHistoryService.save(anyString(), any(), any(byte[].class), anyString())).thenReturn(new RotaFileProcessHistory());
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class))).thenReturn(emptyList());
        doReturn(CompletableFuture.completedFuture(null)).when(rotaFilePartialProcessor).processSnapshotRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), anyMap(), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(rotaFileProcessHistoryRepository).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService).save(anyString(), any(), any(byte[].class), anyString());
    }

    @Test
    void shouldFilterSlotsByDateRange() {
        final LocalDate startDate = LocalDate.of(2024, 4, 1);
        final LocalDate endDate = LocalDate.of(2024, 4, 7);
        final DateRange dateRange = new DateRange(startDate, endDate);

        final Map<String, CourtSchedule> slots = new HashMap<>();
        slots.put("slot1", courtSchedule("2024-04-01", PSV_AS_EXISTING_BUSINESS_TYPE, true));
        slots.put("slot2", courtSchedule("2024-04-03", PSV_AS_EXISTING_BUSINESS_TYPE, true));
        slots.put("slot3", courtSchedule("2024-04-07", PSV_AS_EXISTING_BUSINESS_TYPE, true));
        slots.put("slot4", courtSchedule("2024-04-10", PSV_AS_EXISTING_BUSINESS_TYPE, true)); // Outside range

        Map<String, CourtSchedule> filteredSlots = rotaFileProcessorService.filterSlots(slots, dateRange);

        assertNotNull(filteredSlots);
        assertEquals(3, filteredSlots.size());
        assertTrue(filteredSlots.containsKey("slot1"));
        assertTrue(filteredSlots.containsKey("slot2"));
        assertTrue(filteredSlots.containsKey("slot3"));
        assertFalse(filteredSlots.containsKey("slot4"));
    }

    @Test
    void shouldFilterSlotsByDateRangeWhenEmpty() {
        final LocalDate startDate = LocalDate.of(2024, 4, 1);
        final LocalDate endDate = LocalDate.of(2024, 4, 7);
        final DateRange dateRange = new DateRange(startDate, endDate);

        final Map<String, CourtSchedule> slots = new HashMap<>();

        Map<String, CourtSchedule> filteredSlots = rotaFileProcessorService.filterSlots(slots, dateRange);

        assertNotNull(filteredSlots);
        assertTrue(filteredSlots.isEmpty());
    }

    @Test
    void shouldProcessFileWithDifferentRotaCycleLengths() throws IOException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "14");

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(emptyList());
        when(referenceDataMapperService.getBusinessTypeMap()).thenReturn(getRotaBusinessTypes());
        doReturn(CompletableFuture.completedFuture(null)).when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap();
    }

    @Test
    void shouldHandleExceptionInDownloadAndProcessForEachFile() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        when(rotaFileParser.parse(any(), any())).thenThrow(new RuntimeException("Parsing error"));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(azureBlobClientService).releaseLease(blobName, leaseId, true);
        verify(azureBlobClientService, never()).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        verify(azureBlobClientService, never()).deleteFile(anyString(), eq(empty()));
    }

    @Test
    void shouldProcessFileWithEmptyOuCodes() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_snapshot_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(emptyMap());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC")));
        final RotaFileProcessHistory t = new RotaFileProcessHistory();
        t.setExecutionId(randomUUID().toString());
        when(rotaFileProcessHistoryService.save(anyString(), any(), any(byte[].class), anyString())).thenReturn(t);

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap();
        verify(rotaFilePartialProcessor, never()).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap(), anyString(), any(), anyBoolean());
    }

    @Test
    void shouldCreateAndUpdateRotaFileProcessHistoryForMasterRotaFile() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);
        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        final List<String> businessTypes = List.of(PSV_AS_EXISTING_BUSINESS_TYPE);
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), businessTypes.get(0), true));
        }

        final Map<String, CourtSchedule> slots = new HashMap<>();
        IntStream.range(0, 5).forEach(index -> {
            final CourtSchedule courtSchedule = extractedSchedules.get(index);
            slots.put(courtSchedule.getListingProfileId(), courtSchedule);
        });

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap())).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap())).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataMapperService.getBusinessTypeMap()).thenReturn(getRotaBusinessTypes());

        final RotaFileProcessHistory savedHistory = new RotaFileProcessHistory();
        savedHistory.setExecutionId(randomUUID().toString());
        when(rotaFileProcessHistoryService.save(anyString(), any(), any(byte[].class), anyString())).thenReturn(savedHistory);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);

        // Verify that save is called for master rota file (not snapshot)
        verify(rotaFileProcessHistoryService, atLeastOnce()).save(eq("lja_avonandsomerset_rota_"), any(), eq(blobByteArray), anyString());
        // Verify that update is called after processing completes
        verify(rotaFileProcessHistoryService, atLeastOnce()).update(eq(savedHistory));
        // Verify that the executionId is used in processing
        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), anyString(), anyMap(), anyMap());
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), anyString(), anyMap());
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileProcessorServiceTest.class.getClassLoader().getResourceAsStream(file)) {

            return toByteArray(inputStream);
        }
    }

    private Map<UUID, CourtRoom> getCourtRoomsMap() throws JsonProcessingException {
        final String courtRoomsDataJsonStr = FileUtil.fileToString("/test-data/court-rooms-domain-data.json");

        return objectMapper.readValue(courtRoomsDataJsonStr, new TypeReference<List<CourtRoom>>(){})
                .stream()
                .collect(Collectors.toMap(courtRoom -> UUID.fromString(courtRoom.getCourtroomId()), c -> c));
    }

    private CourtSchedule courtSchedule(final String sessionDate, final String businessType, final Boolean slotBased) {
        return courtSchedule(sessionDate, null, random(10), businessType, null, null, null, null, slotBased);
    }

    private CourtSchedule courtSchedule(final String sessionDate,
                                        final String courtScheduleId,
                                        final String listingProfileId,
                                        final String businessType,
                                        final Integer maxDuration,
                                        final Integer availableSlots,
                                        final Integer availableDuration,
                                        final Integer maxSlots,
                                        final Boolean slotBased) {

        final String scheduleId = courtScheduleId != null ? courtScheduleId : randomUUID().toString();
        final String profileId = listingProfileId;
        final Integer mDuration = maxDuration != null ? maxDuration : 182;
        final Integer avSlots = availableSlots != null ? availableSlots : 125;
        final Integer avDuration = availableDuration != null ? availableDuration : 182;
        final Integer mSlots = maxSlots != null ? maxSlots : 125;

        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(scheduleId)
                .withListingProfileId(profileId)
                .withSessionDate(parse(sessionDate))
                .withOuCode("CABC90")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withOperationalUnit("ANC")
                .withBusinessType(businessType)
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(mDuration)
                .withAvailableSlots(avSlots)
                .withAvailableDuration(avDuration)
                .withMaxSlots(mSlots)
                .withSlotBased(slotBased)
                .build();
    }

    private Map<String, BusinessType> getRotaBusinessTypes() throws JsonProcessingException {
        final String businessTypesJsonStr = getPayload("test-data/business-types.json");
        final List<BusinessType> businessTypes = objectMapper.readValue(businessTypesJsonStr, new TypeReference<>(){});
        return businessTypes.stream().collect(Collectors.toMap(BusinessType::getTypeCode, Function.identity()));
    }

    private Map<String, BusinessType> getRotaBusinessTypesAsHavingCJUandNCPTonly() throws JsonProcessingException {
        final String businessTypesJsonStr = getPayload("test-data/business-types-cju-ncpt.json");
        final List<BusinessType> businessTypes = objectMapper.readValue(businessTypesJsonStr, new TypeReference<>(){});
        return businessTypes.stream().collect(Collectors.toMap(BusinessType::getTypeCode, Function.identity()));
    }

    private void mockMigratedMapByOuCode(final String ouCode, final boolean isMigrated) {
        final CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(ouCode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(isMigrated);
        when(sessionsService.migratedMapByOuCode()).thenReturn(Map.of(migrationStatus.getOuCode(), isMigrated));
    }
}
