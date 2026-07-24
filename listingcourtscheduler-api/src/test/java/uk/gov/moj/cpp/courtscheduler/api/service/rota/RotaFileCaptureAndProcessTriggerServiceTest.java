package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileProcessorService;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

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

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileCaptureAndProcessTriggerServiceTest.class.getClassLoader().getResourceAsStream(file)) {
            return toByteArray(inputStream);
        }
    }
}
