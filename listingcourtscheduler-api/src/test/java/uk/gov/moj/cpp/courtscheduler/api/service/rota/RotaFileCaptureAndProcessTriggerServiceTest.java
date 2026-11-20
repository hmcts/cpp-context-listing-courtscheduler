package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.ChangeJudiciaryForHearingsHelper;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileProcessorService;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import com.azure.storage.blob.models.BlobItem;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileCaptureAndProcessTriggerServiceTest {

    private static final String LJA_PREFIX = "lja_";
    private static final String LEASE_1 = "lease-1";
    private static final String LEASE_2 = "lease-2";
    private static final String LJA_FILE_1 = "lja_file1.xml";
    private static final String LJA_FILE_2 = "lja_file2.xml";
    private static final String ROTA_PROCESS = "new";

    @InjectMocks
    @Spy
    private RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService;

    @Mock
    private AzureBlobClientService azureBlobClientService;

    @Mock
    private RotaFileProcessorService rotaFileProcessorService;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private RotaFileProcessor newRotaFileProcessor;

    @Mock
    private ChangeJudiciaryForHearingsHelper changeJudiciaryForHearingsHelper;

    @Mock
    private BlobItem blobItem;

    @Test
    @Disabled("Will be fixed later")
    void shouldCaptureRotaFilesAndProcessEach() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final Optional<Map.Entry<String, BlobItem>> listBlobItemMap = Optional.of(new AbstractMap.SimpleEntry<>(blobName, blobItem));
        final String leaseId = RandomStringUtils.randomAlphabetic(10);

        when(azureBlobClientService.findAvailableFile(eq(LJA_PREFIX))).thenReturn(listBlobItemMap);
        when(azureBlobClientService.downloadFiles(any(BlobItem.class))).thenReturn(blobContent);
        doNothing().when(rotaFileProcessorService).downloadAndProcessForEachFile(  eq(blobContent), eq(blobName), eq(leaseId));
        doNothing().when(referenceDataMapperService).loadJudiciaries();
        doNothing().when(referenceDataMapperService).loadCourtRooms();
        doNothing().when(referenceDataMapperService).loadCourtRoomSessionAllocations();

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, ROTA_PROCESS);

        verify(azureBlobClientService, atLeastOnce()).findAvailableFile(eq(LJA_PREFIX));
        verify(rotaFileProcessorService, atLeastOnce()).downloadAndProcessForEachFile(  eq(blobContent), eq(blobName), eq(leaseId));
    }

    @Test
    void shouldCollectChangedCourtScheduleIdsAndSendChangeJudiciaryCommands() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final Optional<Map.Entry<String, BlobItem>> availableFile = Optional.of(new AbstractMap.SimpleEntry<>(LEASE_1, blobItem));
        final List<String> changedCourtScheduleIds = List.of("court-schedule-1", "court-schedule-2");
        final JsonObject payload = Json.createObjectBuilder()
                .add("hearings", Json.createArrayBuilder().add("hearing-1"))
                .add("judiciary", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("judicialId", "jud-1")
                                .add("judicialRoleType", Json.createObjectBuilder()
                                        .add("judiciaryType", "Magistrate"))))
                .build();

        when(blobItem.getName()).thenReturn(blobName);
        when(azureBlobClientService.findAvailableFile(eq(LJA_PREFIX)))
                .thenReturn(availableFile)
                .thenReturn(Optional.empty());
        when(azureBlobClientService.downloadFiles(any(BlobItem.class))).thenReturn(blobContent);
        when(newRotaFileProcessor.downloadAndProcessForEachFile(eq(blobContent), eq(blobName), eq(LEASE_1)))
                .thenReturn(changedCourtScheduleIds);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(changedCourtScheduleIds)))
                .thenReturn(List.of(payload));
        when(changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(eq(List.of(payload))))
                .thenReturn(1);

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, ROTA_PROCESS);

        verify(newRotaFileProcessor).downloadAndProcessForEachFile(eq(blobContent), eq(blobName), eq(LEASE_1));
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(eq(changedCourtScheduleIds));
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(eq(List.of(payload)));
    }

    @Test
    void shouldCallSyncHelperOncePerBlobWithThatBlobsChangedIds() {
        final BlobItem blobItem2 = mock(BlobItem.class);
        final BlobContent blobContent1 = new BlobContent(new byte[0]);
        final BlobContent blobContent2 = new BlobContent(new byte[0]);
        final List<String> changedIds1 = List.of("cs-1");
        final List<String> changedIds2 = List.of("cs-2", "cs-3");

        when(blobItem.getName()).thenReturn(LJA_FILE_1);
        when(blobItem2.getName()).thenReturn(LJA_FILE_2);
        when(azureBlobClientService.findAvailableFile(eq(LJA_PREFIX)))
                .thenReturn(Optional.of(new AbstractMap.SimpleEntry<>(LEASE_1, blobItem)))
                .thenReturn(Optional.of(new AbstractMap.SimpleEntry<>(LEASE_2, blobItem2)))
                .thenReturn(Optional.empty());
        when(azureBlobClientService.downloadFiles(any(BlobItem.class)))
                .thenReturn(blobContent1)
                .thenReturn(blobContent2);
        when(newRotaFileProcessor.downloadAndProcessForEachFile(eq(blobContent1), eq(LJA_FILE_1), eq(LEASE_1)))
                .thenReturn(changedIds1);
        when(newRotaFileProcessor.downloadAndProcessForEachFile(eq(blobContent2), eq(LJA_FILE_2), eq(LEASE_2)))
                .thenReturn(changedIds2);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(changedIds1)))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(changedIds2)))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(eq(List.of())))
                .thenReturn(0);

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, ROTA_PROCESS);

        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(eq(changedIds1));
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(eq(changedIds2));
    }

    @Test
    void shouldContinueProcessingNextBlobWhenSyncFailsForCurrentBlob() {
        final BlobItem blobItem2 = mock(BlobItem.class);
        final BlobContent blobContent1 = new BlobContent(new byte[0]);
        final BlobContent blobContent2 = new BlobContent(new byte[0]);
        final List<String> changedIds1 = List.of("cs-1");
        final List<String> changedIds2 = List.of("cs-2");

        when(blobItem.getName()).thenReturn(LJA_FILE_1);
        when(blobItem2.getName()).thenReturn(LJA_FILE_2);
        when(azureBlobClientService.findAvailableFile(eq(LJA_PREFIX)))
                .thenReturn(Optional.of(new AbstractMap.SimpleEntry<>(LEASE_1, blobItem)))
                .thenReturn(Optional.of(new AbstractMap.SimpleEntry<>(LEASE_2, blobItem2)))
                .thenReturn(Optional.empty());
        when(azureBlobClientService.downloadFiles(any(BlobItem.class)))
                .thenReturn(blobContent1)
                .thenReturn(blobContent2);
        when(newRotaFileProcessor.downloadAndProcessForEachFile(eq(blobContent1), eq(LJA_FILE_1), eq(LEASE_1)))
                .thenReturn(changedIds1);
        when(newRotaFileProcessor.downloadAndProcessForEachFile(eq(blobContent2), eq(LJA_FILE_2), eq(LEASE_2)))
                .thenReturn(changedIds2);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(changedIds1)))
                .thenThrow(new RuntimeException("listing unavailable"));
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(changedIds2)))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(eq(List.of())))
                .thenReturn(0);

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, ROTA_PROCESS);

        // blob 2 sync must be attempted even though blob 1 threw
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(eq(changedIds2));
    }

    @Test
    void shouldBuildAndSendNoCommandsWhenNoFilesWereProcessed() {
        when(azureBlobClientService.findAvailableFile(eq(LJA_PREFIX))).thenReturn(Optional.empty());

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, ROTA_PROCESS);

        verifyNoInteractions(changeJudiciaryForHearingsHelper);
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(file)) {
            return toByteArray(inputStream);
        }
    }
}
