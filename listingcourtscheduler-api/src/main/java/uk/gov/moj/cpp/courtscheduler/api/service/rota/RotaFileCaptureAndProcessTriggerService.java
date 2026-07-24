package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import org.springframework.stereotype.Service;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileProcessorService;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;


import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.inject.Inject;

import com.azure.storage.blob.models.BlobItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@org.springframework.transaction.annotation.Transactional
public class RotaFileCaptureAndProcessTriggerService {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileCaptureAndProcessTriggerService.class);
    private static final String ROTA_PROCESS_OLD = "old";
    private static final String IT_TEST_BLOB_PREFIX = "IT_Test_";
    private static final String ORIGINAL_BLOB_PREFIX = "lja_";

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private RotaFileProcessorService rotaFileProcessorService;

    @Inject
    private RotaFileProcessor newRotaFileProcessor;

    @Inject
    private AzureBlobClientService azureBlobClientService;

    @Async
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Future<String> captureRotaFilesAndProcessEach(boolean isForItTest, final String rotaProcess) {
        logger.info("RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach called with rotaProcess: {}", rotaProcess);
        final String blobPrefix = isForItTest ? IT_TEST_BLOB_PREFIX : ORIGINAL_BLOB_PREFIX;

        boolean referenceDataLoaded = false;
        boolean fileAvailable;

        do {
            // Look for an available file without an active lease
            logger.info("Searching for available file");
            final Optional<Map.Entry<String, BlobItem>> availableFile = azureBlobClientService.findAvailableFile(blobPrefix);
            fileAvailable = availableFile.isPresent();
            if (fileAvailable) {
                logger.info("Found file {}", availableFile.get());
                final String leaseId = availableFile.get().getKey();
                final String blobName = availableFile.get().getValue().getName();
                final BlobItem blobItem = availableFile.get().getValue();

                try {
                    if (!referenceDataLoaded) {
                        loadReferenceData();
                        referenceDataLoaded = true;
                    }

                    final long downloadStart = System.nanoTime();
                    final BlobContent blobContent = azureBlobClientService.downloadFiles(blobItem);
                    final long downloadEnd = System.nanoTime();
                    logger.info("PRF: Downloaded blob {} in {} ms", blobName, (downloadEnd - downloadStart) / 1_000_000);

                    if (ROTA_PROCESS_OLD.equals(rotaProcess)) {
                        logger.info("Using old rota file processor service for blob: {}", blobName);
                        rotaFileProcessorService.downloadAndProcessForEachFile(blobContent, blobName, leaseId);
                    } else {
                        logger.info("Using new rota file processor service for blob: {}", blobName);
                        newRotaFileProcessor.downloadAndProcessForEachFile(blobContent, blobName, leaseId);
                    }
                } catch (AzureBlobClientException ignoredException) {
                    logger.info("File {} already leased and skipping to the next file", blobName);
                }
            }
        } while (fileAvailable);

        logger.info("RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach completed");
        return java.util.concurrent.CompletableFuture.completedFuture("SUCCESS");
    }

    private void loadReferenceData() {
        logger.info("Loading reference data for rota processing");
        referenceDataMapperService.loadCourtRooms();
        logger.info("Court rooms loaded");
        referenceDataMapperService.loadJudiciaries();
        logger.info("Judiciaries loaded");
        referenceDataMapperService.loadCourtRoomSessionAllocations();
        logger.info("Court room session allocations loaded");
        referenceDataMapperService.loadBusinessTypeMap();
        logger.info("Business type map loaded - reference data loading completed");
    }
}
