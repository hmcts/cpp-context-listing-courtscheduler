package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import com.azure.storage.blob.models.BlobItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileCaptureAndProcessTriggerServiceTest {

    private static final String LEASE_ID = "lease-1";
    private static final String BLOB_NAME = "lja_avonandsomerset_rota_20240314T160815Z.xml";

    @InjectMocks
    private RotaFileCaptureAndProcessTriggerService rotaFileCaptureAndProcessTriggerService;

    @Mock
    private AzureBlobClientService azureBlobClientService;

    @Mock
    private RotaFileProcessor rotaFileProcessor;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private BlobItem blobItem;

    @Test
    void shouldProcessEachAvailableFileWithTheRotaFileProcessor() {
        final BlobContent blobContent = new BlobContent(new byte[]{1, 2, 3});
        when(blobItem.getName()).thenReturn(BLOB_NAME);
        when(azureBlobClientService.findAvailableFile("lja_")).thenReturn(availableFile()).thenReturn(availableFile()).thenReturn(Optional.empty());
        when(azureBlobClientService.downloadFiles(blobItem)).thenReturn(blobContent);

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false);

        verify(rotaFileProcessor, times(2)).downloadAndProcessForEachFile(blobContent, BLOB_NAME, LEASE_ID);
        verify(referenceDataMapperService).loadCourtRooms();
        verify(referenceDataMapperService).loadJudiciaries();
        verify(referenceDataMapperService).loadCourtRoomSessionAllocations();
    }

    @Test
    void shouldUseTheItTestBlobPrefixWhenForItTest() {
        when(azureBlobClientService.findAvailableFile("IT_Test_")).thenReturn(Optional.empty());

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(true);

        verify(azureBlobClientService).findAvailableFile("IT_Test_");
        verify(rotaFileProcessor, never()).downloadAndProcessForEachFile(any(), any(), any());
        verify(referenceDataMapperService, never()).loadCourtRooms();
    }

    @Test
    void shouldSkipToTheNextFileWhenTheFileIsAlreadyLeased() {
        when(blobItem.getName()).thenReturn(BLOB_NAME);
        when(azureBlobClientService.findAvailableFile("lja_")).thenReturn(availableFile()).thenReturn(Optional.empty());
        when(azureBlobClientService.downloadFiles(blobItem)).thenThrow(new AzureBlobClientException("already leased"));

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(false);

        verify(rotaFileProcessor, never()).downloadAndProcessForEachFile(any(), any(), any());
    }

    private Optional<Map.Entry<String, BlobItem>> availableFile() {
        return Optional.of(new AbstractMap.SimpleEntry<>(LEASE_ID, blobItem));
    }
}
