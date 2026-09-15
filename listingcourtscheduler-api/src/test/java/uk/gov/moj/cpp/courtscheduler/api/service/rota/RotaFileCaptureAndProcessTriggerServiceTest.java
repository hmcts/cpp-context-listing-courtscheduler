package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
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

        when(azureBlobClientService.findAvailableFile(eq("lja_"))).thenReturn(listBlobItemMap);
        when(azureBlobClientService.downloadFiles(any(BlobItem.class))).thenReturn(blobContent);
        doNothing().when(rotaFileProcessorService).downloadAndProcessForEachFile(  eq(blobContent), eq(blobName), eq(leaseId));
        doNothing().when(referenceDataMapperService).loadJudiciaries();
        doNothing().when(referenceDataMapperService).loadCourtRooms();
        doNothing().when(referenceDataMapperService).loadCourtRoomSessionAllocations();

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, "new");

        verify(azureBlobClientService, atLeastOnce()).findAvailableFile(eq("lja_"));
        verify(rotaFileProcessorService, atLeastOnce()).downloadAndProcessForEachFile(  eq(blobContent), eq(blobName), eq(leaseId));
    }

    @Test
    void shouldCollectChangedCourtScheduleIdsAndSendChangeJudiciaryCommands() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent(blobByteArray);
        final Optional<Map.Entry<String, BlobItem>> availableFile = Optional.of(new AbstractMap.SimpleEntry<>("lease-1", blobItem));
        final List<String> changedCourtScheduleIds = List.of("court-schedule-1", "court-schedule-2");
        final JsonObject payload = Json.createObjectBuilder()
                .add("hearings", Json.createArrayBuilder().add("hearing-1"))
                .add("judiciary", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("judicialId", "jud-1")
                                .add("judicialRoleType", "Magistrate")))
                .build();

        when(blobItem.getName()).thenReturn(blobName);
        when(azureBlobClientService.findAvailableFile(eq("lja_")))
                .thenReturn(availableFile)
                .thenReturn(Optional.empty());
        when(azureBlobClientService.downloadFiles(any(BlobItem.class))).thenReturn(blobContent);
        when(newRotaFileProcessor.downloadAndProcessForEachFile(eq(blobContent), eq(blobName), eq("lease-1")))
                .thenReturn(changedCourtScheduleIds);
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(changedCourtScheduleIds)))
                .thenReturn(List.of(payload));
        when(changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(eq(List.of(payload))))
                .thenReturn(1);

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, "new");

        verify(newRotaFileProcessor).downloadAndProcessForEachFile(eq(blobContent), eq(blobName), eq("lease-1"));
        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(eq(changedCourtScheduleIds));
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(eq(List.of(payload)));
    }

    @Test
    void shouldBuildAndSendNoCommandsWhenNoFilesWereProcessed() {
        when(azureBlobClientService.findAvailableFile(eq("lja_"))).thenReturn(Optional.empty());
        when(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(eq(List.of())))
                .thenReturn(List.of());
        when(changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(eq(List.of())))
                .thenReturn(0);

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false, "new");

        verify(changeJudiciaryForHearingsHelper).createChangeJudiciaryForHearingsPayloads(eq(List.of()));
        verify(changeJudiciaryForHearingsHelper).sendChangeJudiciaryForHearingsCommands(eq(List.of()));
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileCaptureAndProcessTriggerServiceTest.class.getClassLoader().getResourceAsStream(file)) {
            return toByteArray(inputStream);
        }
    }
}
