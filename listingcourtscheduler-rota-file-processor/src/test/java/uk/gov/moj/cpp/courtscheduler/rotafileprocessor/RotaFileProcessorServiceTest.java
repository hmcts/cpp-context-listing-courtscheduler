package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.time.LocalDate.parse;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private Requester requester;

    @Mock
    private Map<RotaPayload, Map<String, Map<String, String>>> records;

    @Mock
    private Map<String, CourtSchedule> slotsMock;
    @Mock
    private CloudBlockBlob blob;

    @Mock
    private Collection<CourtScheduleJudiciary> schedules;

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
    void shouldCaptureMasterRotaFileAndProcess() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

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
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), eq(requester))).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataMapperService.getBusinessTypeMap(eq(requester))).thenReturn(getRotaBusinessTypes());
        doNothing().when(blob).releaseLease(any(AccessCondition.class));
        doNothing().when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap(eq(requester));
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcessForDurationBasedSlots() throws IOException, StorageException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "corrupted value-normally should be a number");

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

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
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), eq(requester))).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(referenceDataMapperService.getBusinessTypeMap(eq(requester))).thenReturn(getRotaBusinessTypesAsHavingCJUandNCPTonly());
        doNothing().when(blob).releaseLease(any(AccessCondition.class));
        doNothing().when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap());
        mockMigratedMapByOuCode("CABC90", false);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap(eq(requester));
    }


    @Test
    void shouldCaptureMasterRotaFileAndProcessIfAlsoThereIsNoExistingSchedules() throws IOException, StorageException {
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", null);

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);
        List<DateRange> dateRanges = rotaFileProcessorService.weeksCovering(rotaPeriodStartDate, rotaPeriodEndDate);
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
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), eq(requester))).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(emptyList());
        when(referenceDataMapperService.getBusinessTypeMap(eq(requester))).thenReturn(getRotaBusinessTypes());
        doNothing().when(blob).releaseLease(any(AccessCondition.class));
        doNothing().when(rotaFilePartialProcessor).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap());
        mockMigratedMapByOuCode("CABC90", false);

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(false), anyList(), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(rotaFilePartialProcessor, atLeastOnce()).processFullRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), any(LocalDate.class), any(LocalDate.class), anyList(), anyList(), anyMap(), anyMap());
    }

    @Test
    void shouldBreakRotaFileProcessIfCourtRoomsMapIsEmpty() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        final Map<String, byte[]> downloadedBlobsByteArrayMap = Map.of(blobName, blobByteArray);

        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList();
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), PSV_AS_EXISTING_BUSINESS_TYPE, true));
        }

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));
        doNothing().when(blob).releaseLease(any(AccessCondition.class));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester))).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester))).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap(eq(requester))).thenReturn(emptyMap());

        final Map<String, String> rotaDetails = new HashMap();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(new HashMap<>());

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester));
    }

    @Test
    void shouldNotProcessDummyFile() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "dummysupport.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));
        doNothing().when(blob).releaseLease(any(AccessCondition.class));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester));
    }

    @Test
    void shouldCaptureSnapshotFileAndProcess() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));
        doNothing().when(blob).releaseLease(any(AccessCondition.class));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester))).thenReturn(slotsMock);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester))).thenReturn(schedules);
        when(referenceDataMapperService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());
        doNothing().when(rotaFileProcessHistoryService).update(anyString(), any());
        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class))).thenReturn(emptyList());
        doNothing().when(rotaFilePartialProcessor).processSnapshotRotaFile(anyMap(), anyMap(), anyCollection(), anyCollection(), anyMap(), anyList(), anyList(), anyMap(), anyMap());

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(Map.of("175", Map.of("175", "Cheltenham MC"), "177", Map.of("177", "Gloucester County Court")));

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester));
        verify(rotaDataEnricher, atLeastOnce()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester));
        verify(rotaFileParser, atLeastOnce()).parse(any(), any());
        verify(referenceDataMapperService, atLeastOnce()).getCourtRoomsMap(eq(requester));
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
    }

    @Test
    void shouldNotProcessSnapshotFileIfThereIsOneAlreadyProcessedHavingANewerFileDate() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_20240402T180039Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));
        doNothing().when(blob).releaseLease(any(AccessCondition.class));

        when(rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class)))
                .thenReturn(List.of(new RotaFileProcessHistory()));

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester));
        verify(rotaDataEnricher, never()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester));
        verify(rotaFileParser, never()).parse(any(), any());
        verify(referenceDataMapperService, never()).getCourtRoomsMap(eq(requester));
        verify(rotaFileProcessHistoryRepository, atLeastOnce()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, never()).update(anyString(), any());
    }

    @Test
    void shouldNotProcessSnapshotFileIfTheFileNameMissingFileDateTimePart() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_bedfordshire_snapshot_.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        blobContent.setBlob(blob);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        doNothing().when(azureBlobClientService).uploadProcessedFile(any(InputStream.class), anyLong(), eq(blobName), eq(empty()));
        doNothing().when(azureBlobClientService).deleteFile(anyString(), eq(empty()));
        doNothing().when(blob).releaseLease(any(AccessCondition.class));

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap<>();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);


        rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);

        verify(judiciaryScheduleEnricher, never()).enrichJudiciarySchedules(eq(slotsMock), eq(records), eq(false), anyList(), eq(requester));
        verify(rotaDataEnricher, never()).enrichCourtListings(eq(records), any(LocalDate.class), anyMap(), anyBoolean(), anyList(), eq(requester));
        verify(rotaFileParser, never()).parse(any(), any());
        verify(referenceDataMapperService, never()).getCourtRoomsMap(eq(requester));
        verify(rotaFileProcessHistoryRepository, never()).findByFileNamePrefixAndFileDateGreaterThan(anyString(), any(Timestamp.class));
        verify(rotaFileProcessHistoryService, never()).update(anyString(), any());
    }

    @Test
    void shouldSplitDateRangeIntoWeeks() {
        LocalDate startDate = LocalDate.of(2024, 4, 21);
        LocalDate endDate = LocalDate.of(2024, 8, 9);

        List<DateRange> dateRanges = rotaFileProcessorService.weeksCovering(startDate, endDate);

        assertNotNull(dateRanges);
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
