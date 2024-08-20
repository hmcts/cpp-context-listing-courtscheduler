package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;

import java.util.Map;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

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

    @Asynchronous
    public Future<String> captureRotaFilesAndProcessEach(final Requester requester) {
        logger.info("RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach called");
        // download all the files in the input container
        final Map<String, byte[]> downloadedBlobsByteArrayMap = azureBlobClientService.downloadFiles();
        if (!downloadedBlobsByteArrayMap.isEmpty()) {
            loadReferenceData(requester);
        }
        // for each of the files process rotasl
        downloadedBlobsByteArrayMap.keySet().forEach(blobName -> {
            final byte[] blobContent = downloadedBlobsByteArrayMap.get(blobName);
            rotaFileProcessorService.downloadAndProcessForEachFile(requester, blobContent, blobName);
        });

        return new AsyncResult<>("SUCCESS");
    }

    private void loadReferenceData(final Requester requester) {
        referenceDataMapperService.loadCourtRooms(requester);
        referenceDataMapperService.loadJudiciaries(requester);
        referenceDataMapperService.loadCourtRoomSessionAllocations(requester);
    }
}
