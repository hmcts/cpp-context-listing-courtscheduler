package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileProcessorService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
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
    private Requester requester;

    @Test
    @Disabled
    void shouldCaptureRotaFilesAndProcessEach() throws IOException, StorageException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobByteArray = givenBlobContent(file);
        final BlobContent blobContent = new BlobContent();
        blobContent.setLeaseId(blobName);
        blobContent.setBlobByteArray(blobByteArray);
        final Map<String, ListBlobItem> listBlobItemMap = Map.of();

        when(azureBlobClientService.collectListBlobItems(eq("lja_"))).thenReturn(listBlobItemMap);
        when(azureBlobClientService.downloadFiles(any(CloudBlob.class))).thenReturn(any());
        doNothing().when(rotaFileProcessorService).downloadAndProcessForEachFile(eq(requester), eq(blobContent), eq(blobName));
        doNothing().when(referenceDataMapperService).loadJudiciaries(eq(requester));
        doNothing().when(referenceDataMapperService).loadCourtRooms(eq(requester));
        doNothing().when(referenceDataMapperService).loadCourtRoomSessionAllocations(eq(requester));

        rotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach(requester, false);
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileCaptureAndProcessTriggerServiceTest.class.getClassLoader().getResourceAsStream(file)) {
            return toByteArray(inputStream);
        }
    }
}
