package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import org.springframework.stereotype.Service;

import static java.util.Optional.empty;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryAssignmentRequestHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleData;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryScheduleAssignment;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaCourtScheduleHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaFileUtility;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaJudiciaryHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaLocationPeriodHelper;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@org.springframework.transaction.annotation.Transactional
public class RotaFileProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileProcessor.class);
    
    private static final String LOG_PREFIX_PRF = "PRF: ";

    @Inject
    private AzureBlobClientService azureBlobClientService;

    @Inject
    private RotaFileParser rotaFileParser;

    @Inject
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Inject
    private RotaFileUtility rotaFileUtility;

    @Inject
    private JudiciaryAssignmentService judiciaryAssignmentService;

    @Inject
    private RotaJudiciaryHelper rotaJudiciaryHelper;

    @Inject
    private RotaCourtScheduleHelper rotaCourtScheduleHelper;

    @Inject
    private JudiciaryAssignmentRequestHelper judiciaryAssignmentRequestHelper;

    @Inject
    private RotaLocationPeriodHelper rotaLocationPeriodHelper;

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void downloadAndProcessForEachFile(final BlobContent blobContent, final String blobName, final String leaseId) {
        logger.info("downloadAndProcessForEachFile called for blob with name: {}", blobName);
        final byte[] blobByteArray = blobContent.getBlobByteArray();
        try {
            processBlob(blobName, blobByteArray);
            uploadAndCleanup(blobByteArray, blobName, leaseId);
        } catch (final RuntimeException ex) {
            logger.error("Error processing blob: {}", blobName, ex);
            azureBlobClientService.releaseLease(blobName, leaseId, true);
        }
    }

    // ============================================================================
    // PRIVATE PROCESSING METHODS - Main Flow
    // ============================================================================

    /**
     * Processes a blob file through the complete rota file processing pipeline.
     *
     * @param blobName       the name of the blob file
     * @param blobByteArray  the content of the blob file
     * @param requester      the requester for making service calls
     */
    private void processBlob(final String blobName, final byte[] blobByteArray) {
        if (!shouldProcessFile(blobName)) {
            return;
        }

        logger.info("Starting processing for blob: {}", blobName);
        final long processStart = System.nanoTime();

        final ParseResult parseResult = parseFileContent(blobName, blobByteArray);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseResult.records();
        final String executionId = parseResult.executionId();
        final RotaFileProcessHistory rotaFileProcessHistory = parseResult.rotaFileProcessHistory();

        logger.info("Processing blob: {} with execution ID: {} - parsed {} record types", blobName, executionId, records.size());

        // Extract locations and resolve OU codes
        final var locations = rotaLocationPeriodHelper.getLocationFromRecords(records);
        logger.info("Extracted {} location IDs from blob: {}", locations.size(), blobName);
        final var ouCodes = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locations);
        logger.info("Resolved {} OU codes for blob: {}", ouCodes.size(), blobName);

        // Get rota period dates and delete unallocated court schedule judiciaries
        final var rotaPeriodDateInfoProvider = rotaLocationPeriodHelper.getRotaPeriodDates(records);
        final int deletedCount = rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
                rotaPeriodDateInfoProvider.getRotaPeriodStartDate(),
                rotaPeriodDateInfoProvider.getRotaPeriodEndDate(),
                ouCodes);
        logger.info("Deleted {} unallocated court schedule judiciaries for blob: {}", deletedCount, blobName);

        final ProcessingMaps processingMaps = createProcessingMaps(records, executionId, blobName);

        // Execute judiciary assignments from the rota feed
        executeJudiciaryAssignments(
                processingMaps.judiciaryCourtScheduleMapFromRotaFeed(),
                executionId,
                blobName);

        rotaFileUtility.updateFileProcessHistory(logger, rotaFileProcessHistory, blobName, rotaFileProcessHistoryService);

        final long processEnd = System.nanoTime();
        rotaFileUtility.logProcessingTime(logger, blobName, processStart, processEnd);
        logger.info("Rota file parsed successfully for blob: {} - parsed {} record types", blobName, records.size());
    }

    /**
     * Validates if the file should be processed.
     *
     * @param blobName the name of the blob file
     * @return true if the file should be processed, false otherwise
     */
    private boolean shouldProcessFile(final String blobName) {
        if (rotaFileUtility.isDummyFile(blobName)) {
            logger.warn("Dummy support file detected: {}", blobName);
            return false;
        }

        if (rotaFileUtility.isSnapshotFile(blobName) && rotaFileUtility.isNewerSnapshotFileProcessed(blobName)) {
            logger.warn("Skipping snapshot file - newer version already processed: {}", blobName);
            return false;
        }

        return true;
    }

    /**
     * Creates all processing maps required for judiciary and court schedule processing.
     * This includes the judiciary map, court schedule map, and the judiciary court schedule map
     * which contains full assignment data including position, isBenchChairman, and isDeputy.
     *
     * @param records      the parsed rota file records
     * @param requester    the requester for making service calls
     * @param executionId  the execution ID for logging
     * @param blobName     the name of the blob file
     * @return ProcessingMaps containing the judiciary court schedule map with full assignment data
     */
    private ProcessingMaps createProcessingMaps(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                 final String executionId,
                                                 final String blobName) {
        final Map<String, UUID> justiceIdJudiciaryIdMap = rotaJudiciaryHelper.createJudiciaryMap(records, executionId);
        logger.info("Created judiciary map with {} entries for blob: {}", justiceIdJudiciaryIdMap.size(), blobName);

        final Map<String, Set<UUID>> courtListingProfileIdListOfCourscheduleIdMap =
                rotaCourtScheduleHelper.createCourtScheduleMap(records, executionId);
        logger.info("Created court schedule map with {} entries for blob: {}",
                courtListingProfileIdListOfCourscheduleIdMap.size(), blobName);

        final Map<String, List<JudiciaryCourtScheduleData>> judiciaryIdListOfCourtScheduleIdMapFromRotaFeed =
                rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                        records, justiceIdJudiciaryIdMap, courtListingProfileIdListOfCourscheduleIdMap, executionId);
        logger.info("Created judiciary court schedule map with {} entries for blob: {}",
                judiciaryIdListOfCourtScheduleIdMapFromRotaFeed.size(), blobName);

        return new ProcessingMaps(judiciaryIdListOfCourtScheduleIdMapFromRotaFeed);
    }

    /**
     * Executes judiciary assignments if there are any to process.
     * The assignment data includes court schedule IDs along with assignment metadata
     * (position, isBenchChairman, isDeputy) which will be persisted to the database.
     *
     * @param judiciaryAssignmentDataMap the map of assignments to process, containing
     *                                   lists of court schedule IDs and assignment metadata
     * @param requester                  the requester for making service calls
     * @param executionId                the execution ID for logging
     * @param blobName                   the name of the blob file
     */
    private void executeJudiciaryAssignments(
            final Map<String, List<JudiciaryCourtScheduleData>> judiciaryAssignmentDataMap,
            final String executionId,
            final String blobName) {
        if (judiciaryAssignmentDataMap.isEmpty()) {
            logger.debug("Skipping judiciary assignment - no assignments to process for blob: {}", blobName);
            return;
        }

        // Convert map to list of JudiciaryScheduleAssignment using streams
        final List<JudiciaryScheduleAssignment> assignmentList = judiciaryAssignmentDataMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .flatMap(entry -> entry.getValue().stream()
                        .map(scheduleData -> new JudiciaryScheduleAssignment(entry.getKey(), scheduleData)))
                .toList();

        final AssignJudiciariesResponse assignResponse = processJudiciaryAssignments(
                assignmentList, executionId);
        logger.info("Assigned judiciaries for blob: {} - requested: {}, successful: {}, failures: {}",
                blobName, assignResponse.getRequestedAssignments(), assignResponse.getSuccessfulAssignments(),
                assignResponse.getFailures().size());
    }


    private void uploadAndCleanup(final byte[] blobByteArray, final String blobName, final String leaseId) {
        logger.info("Starting upload and cleanup for blob: {}", blobName);
        final long uploadStart = System.nanoTime();
        final long fileLength = blobByteArray.length;
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(blobByteArray), fileLength, blobName, empty());
        final long uploadEnd = System.nanoTime();
        logger.info("{}Upload completed for blob {} in {} ms", LOG_PREFIX_PRF, blobName,
                rotaFileUtility.convertNanosToMillis(uploadEnd - uploadStart));

        azureBlobClientService.releaseLease(blobName, leaseId, false);
        logger.info("Released lease for blob: {}", blobName);
        azureBlobClientService.deleteFile(blobName, empty());
        logger.info("Blob {} processed and cleaned up successfully", blobName);
    }

    // ============================================================================
    // PRIVATE PROCESSING METHODS - File Parsing
    // ============================================================================

    /**
     * Processes the rota file and parses it, returning the parsed records, execution ID, and file process history.
     * Handles snapshot file validation, execution ID generation, and file parsing.
     *
     * @param fileName the name of the file being processed
     * @param content  the byte content of the file
     * @return ParseResult containing the parsed records, execution ID, and file process history
     */
    private ParseResult parseFileContent(final String fileName, final byte[] content) {
        final RotaFileProcessHistory rotaFileProcessHistory = rotaFileUtility.createAndSaveFileProcessHistory(fileName, content, rotaFileProcessHistoryService);
        final String executionId = rotaFileProcessHistory != null 
                ? rotaFileProcessHistory.getExecutionId() 
                : UUID.randomUUID().toString();
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseFile(fileName, content);
        return new ParseResult(records, executionId, rotaFileProcessHistory);
    }

    private Map<RotaPayload, Map<String, Map<String, String>>> parseFile(final String fileName, final byte[] content) {
        final long parsingStartTime = System.nanoTime();
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(fileName, content);
        final long parsingEndTime = System.nanoTime();

        logger.info("{}Parsed file {} in {} ms", LOG_PREFIX_PRF, fileName,
                rotaFileUtility.convertNanosToMillis(parsingEndTime - parsingStartTime));
        logger.info("File parsed successfully for file: {}", fileName);

        return records;
    }


    // ============================================================================
    // PRIVATE PROCESSING METHODS - Judiciary Assignment Processing
    // ============================================================================

    /**
     * Processes judiciary assignments by building the request and calling the assignment service.
     * The assignment data includes court schedule IDs and assignment metadata (position,
     * isBenchChairman, isDeputy) which are included in the assignment request.
     *
     * @param assignmentList list of JudiciaryScheduleAssignment containing judiciary IDs
     *                       and court schedule data with assignment metadata
     * @param requester      the requester for making service calls
     * @param executionId    the execution ID for logging
     * @return the assignment response containing success/failure information
     */
    private AssignJudiciariesResponse processJudiciaryAssignments(
            final List<JudiciaryScheduleAssignment> assignmentList,
            final String executionId) {
        final var assignRequest = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(assignmentList);
        // Use repository for Rota processing (useRepository = true)
        return judiciaryAssignmentService.assignJudiciaries(assignRequest, executionId, true);
    }

    // ============================================================================
    // RECORDS/INNER CLASSES
    // ============================================================================

    /**
     * Result of parsing a rota file.
     *
     * @param records                the parsed records from the file
     * @param executionId            the execution ID for this processing run
     * @param rotaFileProcessHistory the file process history record, or null if not created
     */
    public record ParseResult(Map<RotaPayload, Map<String, Map<String, String>>> records,
                              String executionId,
                              RotaFileProcessHistory rotaFileProcessHistory) {
    }

    /**
     * Container for processing maps used during rota file processing.
     * Contains the judiciary court schedule map with full assignment data including
     * court schedule IDs, position, isBenchChairman, and isDeputy.
     *
     * @param judiciaryCourtScheduleMapFromRotaFeed the map of judiciary IDs to List of JudiciaryCourtScheduleData
     *                                               from rota feed, containing court schedule IDs
     *                                               and assignment metadata (position, isBenchChairman, isDeputy)
     */
    private record ProcessingMaps(Map<String, List<JudiciaryCourtScheduleData>> judiciaryCourtScheduleMapFromRotaFeed) {
    }
}
