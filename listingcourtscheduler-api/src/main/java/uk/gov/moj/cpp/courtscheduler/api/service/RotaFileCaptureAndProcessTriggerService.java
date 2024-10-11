package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.exception.RotaFileProcessorException;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileProcessorService;

import java.util.Map;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
public class RotaFileCaptureAndProcessTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileCaptureAndProcessTriggerService.class);

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private RotaFileProcessorService rotaFileProcessorService;

    @Inject
    private AzureBlobClientService azureBlobClientService;

    private static final String IT_TEST_BLOB_PREFIX = "IT_Test_";
    private static final String ORIGINAL_BLOB_PREFIX = "lja_";

    @Asynchronous
    public Future<String> captureRotaFilesAndProcessEach(final Requester requester, boolean isForItTest) {
        logger.info("RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach called");
        final String blobPrefix = isForItTest ? IT_TEST_BLOB_PREFIX : ORIGINAL_BLOB_PREFIX;
        // download all the files in the input container
        final Map<String, ListBlobItem> downloadedBlobsByteArrayMap = azureBlobClientService.collectListBlobItems(blobPrefix);
        if (!downloadedBlobsByteArrayMap.isEmpty()) {
            loadReferenceData(requester);
        }
        // for each of the files process rotasl
        downloadedBlobsByteArrayMap.keySet().forEach(blobName -> {
            final BlobContent blobContent = azureBlobClientService.downloadFiles(downloadedBlobsByteArrayMap.get(blobName));
            try {
                rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);
            } catch (StorageException exception) {
                throw new RotaFileProcessorException(exception);
            } catch (AzureBlobClientException ignoredException) {
                logger.info("File already leased and skipping to the next file");
            }
        });

        return new AsyncResult<>("SUCCESS");
    }

    private void loadReferenceData(final Requester requester) {
        referenceDataMapperService.loadCourtRooms(requester);
        referenceDataMapperService.loadJudiciaries(requester);
        referenceDataMapperService.loadCourtRoomSessionAllocations(requester);
    }
}
