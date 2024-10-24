package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileProcessorService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import com.azure.storage.blob.models.BlobItem;
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

        boolean filesProcessed = false;
        boolean referenceDataLoaded = false;
        boolean fileAvailable;

        do {
            // Look for an available file without an active lease
            logger.info("Searching for available file");
            final Optional<Map.Entry<String, BlobItem>> availableFile = azureBlobClientService.findAvailableFile(blobPrefix);
            fileAvailable = availableFile.isPresent();
            if (fileAvailable) {
                logger.info("Found file {}",availableFile.get());
                final String blobName = availableFile.get().getKey();
                final BlobItem blobItem = availableFile.get().getValue();

                try {
                    if (!referenceDataLoaded) {
                        loadReferenceData(requester);
                        referenceDataLoaded = true;
                    }

                    final BlobContent blobContent = azureBlobClientService.downloadFiles(blobItem);
                    rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);
                    filesProcessed = true;
                } catch (AzureBlobClientException ignoredException) {
                    logger.info("File {} already leased and skipping to the next file", blobName);
                }
            }
        } while (fileAvailable);

        if (filesProcessed) {
            referenceDataMapperService.clearReferenceDataInMemory();
        }

        logger.info("RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach completed");
        return new AsyncResult<>("SUCCESS");
    }

    private void loadReferenceData(final Requester requester) {
        referenceDataMapperService.loadCourtRooms(requester);
        referenceDataMapperService.loadJudiciaries(requester);
        referenceDataMapperService.loadCourtRoomSessionAllocations(requester);
        referenceDataMapperService.loadBusinessTypeMap(requester);
    }
}
