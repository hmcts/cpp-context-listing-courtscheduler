package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor;

import static java.time.LocalDate.parse;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.apache.deltaspike.core.util.CollectionUtils.isEmpty;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.api.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.BusinessTypeMatchingLogger;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.JudiciaryScheduleEnricher;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaDataEnricher;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.ProvisionalDataProducer;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Mock
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Mock
    private AllocatedListingService allocatedListingService;

    @Mock
    private ReferenceDataCache referenceDataCache;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private ProvisionalDataProducer provisionalDataProducer;

    @Mock
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Mock
    private Requester requester;

    @Mock
    private Map<RotaPayload, Map<String, Map<String, String>>> records;

    @Mock
    private Map<String, CourtSchedule> slotsMock;

    @Mock
    private Collection<CourtScheduleJudiciary> schedules;

    @Captor
    private ArgumentCaptor<List<String>> missingBusinessTypeCaptor;

    private Map<String, Map<String, String>> rotaPeriodMap;

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private static final String PSV_AS_EXISTING_BUSINESS_TYPE = "PSV";
    private static final String NCPT_AS_EXISTING_BUSINESS_TYPE = "NCPT";
    private static final String CJU_AS_MISSING_BUSINESS_TYPE = "CJU";

    @BeforeEach
    public void setUp() {
        setField(rotaFileProcessorService, "rotaMasterDataDaysLength", "168");
        setField(rotaFileProcessorService, "rotaMonthsOfProvisionalDataToPopulate", "6");
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "28");
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcess() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobContent = givenBlobContent(file);

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

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataCache.getRotaBusinessTypes(eq(requester))).thenReturn(getRotaBusinessTypes());
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(sessionsService, atLeastOnce()).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList());
        verify(businessTypeMatchingLogger, times(1)).logMissingBusinessType(missingBusinessTypeCaptor.capture());

        final List<List<String>> missingBusinessTypes = missingBusinessTypeCaptor.getAllValues();
        assertEquals(1, missingBusinessTypes.size());
        assertEquals(1, missingBusinessTypes.get(0).size());
        assertEquals(CJU_AS_MISSING_BUSINESS_TYPE, missingBusinessTypes.get(0).get(0));
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcessForDurationBasedSlots() throws IOException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "corrupted value-normally should be a number");

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobContent = givenBlobContent(file);

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

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataCache.getRotaBusinessTypes(eq(requester))).thenReturn(getRotaBusinessTypesAsHavingCJUandNCPTonly());
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(sessionsService, atLeastOnce()).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList());
        verify(businessTypeMatchingLogger, never()).logMissingBusinessType(missingBusinessTypeCaptor.capture());

        final List<List<String>> missingBusinessTypes = missingBusinessTypeCaptor.getAllValues();
        assertTrue(isEmpty(missingBusinessTypes));
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcessIfAlsoThereIsNoExistingSchedules() throws IOException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", null);

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobContent = givenBlobContent(file);

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

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(emptyList());
        when(referenceDataCache.getRotaBusinessTypes(eq(requester))).thenReturn(getRotaBusinessTypes());
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList());
        mockMigratedMapByOuCode("CABC90", false);

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(sessionsService, atLeastOnce()).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList());
        verify(businessTypeMatchingLogger, times(1)).logMissingBusinessType(missingBusinessTypeCaptor.capture());

        final List<List<String>> missingBusinessTypes = missingBusinessTypeCaptor.getAllValues();
        assertEquals(1, missingBusinessTypes.size());
        assertEquals(1, missingBusinessTypes.get(0).size());
        assertEquals(CJU_AS_MISSING_BUSINESS_TYPE, missingBusinessTypes.get(0).get(0));
    }

    @Test
    void shouldBreakRotaFileProcessIfCourtRoomsMapIsEmpty() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobContent = givenBlobContent(file);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        final Map<String, byte[]> downloadedBlobsByteArrayMap = Map.of(blobName, blobContent);

        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList();
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), PSV_AS_EXISTING_BUSINESS_TYPE, true));
        }

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(emptyMap());

        final Map<String, String> rotaDetails = new HashMap();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(new HashMap<>());

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester));
        verify(courtScheduleJudiciaryRepository, never()).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList());
        verify(courtScheduleRepository, never()).deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList());
        verify(courtScheduleRepository, never()).deleteUnAllocatedProvisionalEntries(anyList());
        verify(businessTypeMatchingLogger, never()).logMissingBusinessType(missingBusinessTypeCaptor.capture());
    }

    @Test
    void shouldNotProcessDummyFile() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "dummysupport.xml";
        final byte[] blobContent = givenBlobContent(file);

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester));
    }

    @Test
    void shouldCaptureSnapshotFileAndProcess() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobContent = givenBlobContent(file);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        doNothing().when(rotaFileProcessHistoryService).update(anyString(), any());
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class))).thenReturn(emptyList());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
    }

    @Test
    void shouldNotProcessSnapshotFileIfThereIsOneAlreadyProcessedHavingANewerFileDate() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobContent = givenBlobContent(file);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class)))
                .thenReturn(List.of(new RotaFileProcessHistory()));

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, never()).update(anyString(), any());
        verify(courtScheduleJudiciaryRepository, never()).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList());
        verify(courtScheduleRepository, never()).deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList());
        verify(courtScheduleRepository, never()).deleteUnAllocatedProvisionalEntries(anyList());
    }

    @Test
    void shouldNotProcessSnapshotFileIfTheFileNameMissingFileDateTimePart() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_.xml";
        final byte[] blobContent = givenBlobContent(file);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));
        doNothing().when(azureBlobClientService).deleteFile(anyString());

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester))).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(rotaFileProcessHistoryRepository, never()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, never()).update(anyString(), any());
        verify(courtScheduleJudiciaryRepository, never()).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList());
        verify(courtScheduleRepository, never()).deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList());
        verify(courtScheduleRepository, never()).deleteUnAllocatedProvisionalEntries(anyList());
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileParserTest.class.getClassLoader().getResourceAsStream(file)) {

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

    private List<BusinessType> getRotaBusinessTypes() throws JsonProcessingException {
        final String businessTypesJsonStr = getPayload("test-data/business-types.json");
        return objectMapper.readValue(businessTypesJsonStr, new TypeReference<List<BusinessType>>(){});
    }

    private List<BusinessType> getRotaBusinessTypesAsHavingCJUandNCPTonly() throws JsonProcessingException {
        final String businessTypesJsonStr = getPayload("test-data/business-types-cju-ncpt.json");
        return objectMapper.readValue(businessTypesJsonStr, new TypeReference<List<BusinessType>>(){});
    }

    private void mockMigratedMapByOuCode(final String ouCode, final boolean isMigrated) {
        final CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(ouCode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(isMigrated);
        when(sessionsService.migratedMapByOuCode()).thenReturn(Map.of(migrationStatus.getOuCode(), isMigrated));
    }
}
