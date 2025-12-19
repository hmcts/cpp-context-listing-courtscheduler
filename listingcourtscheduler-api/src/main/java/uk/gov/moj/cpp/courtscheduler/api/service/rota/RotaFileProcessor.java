package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.util.Optional.empty;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.CourtScheduleJudiciaryQueryHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryAssignmentRequestHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleMapComparator;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaCourtScheduleHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaFileUtility;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaJudiciaryHelper;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesResponse;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
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
    private CourtScheduleJudiciaryQueryHelper courtScheduleJudiciaryQueryHelper;

    @Inject
    private JudiciaryCourtScheduleMapComparator mapComparator;

    @Inject
    private JudiciaryAssignmentService judiciaryAssignmentService;

    @Inject
    private JudiciaryUnassignmentService judiciaryUnassignmentService;

    @Inject
    private RotaJudiciaryHelper rotaJudiciaryHelper;

    @Inject
    private RotaCourtScheduleHelper rotaCourtScheduleHelper;

    @Inject
    private JudiciaryAssignmentRequestHelper judiciaryAssignmentRequestHelper;

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void downloadAndProcessForEachFile(final Requester requester, final BlobContent blobContent, final String blobName, final String leaseId) {
        logger.info("downloadAndProcessForEachFile called for blob with name: {}", blobName);
        final byte[] blobByteArray = blobContent.getBlobByteArray();
        try {
            processBlob(blobName, blobByteArray, requester);
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
    private void processBlob(final String blobName, final byte[] blobByteArray, final Requester requester) {
        if (!shouldProcessFile(blobName)) {
            return;
        }

        final long processStart = System.nanoTime();

        final ParseResult parseResult = parseFileContent(blobName, blobByteArray);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseResult.records();
        final String executionId = parseResult.executionId();
        final RotaFileProcessHistory rotaFileProcessHistory = parseResult.rotaFileProcessHistory();

        final ProcessingMaps processingMaps = createProcessingMaps(records, requester, executionId, blobName);

        final Map<String, List<UUID>> judiciaryCourtScheduleIdsFromDb = queryDatabaseForCourtScheduleIds(
                processingMaps.judiciaryCourtScheduleMapFromRotaFeed(), blobName);

        processJudiciaryAssignmentsAndUnassignments(
                processingMaps.judiciaryCourtScheduleMapFromRotaFeed(),
                judiciaryCourtScheduleIdsFromDb,
                requester,
                executionId,
                blobName);

        updateFileProcessHistory(rotaFileProcessHistory, blobName);

        final long processEnd = System.nanoTime();
        logProcessingTime(blobName, processStart, processEnd);
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
     *
     * @param records      the parsed rota file records
     * @param requester    the requester for making service calls
     * @param executionId  the execution ID for logging
     * @param blobName     the name of the blob file
     * @return ProcessingMaps containing all created maps
     */
    private ProcessingMaps createProcessingMaps(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                 final Requester requester,
                                                 final String executionId,
                                                 final String blobName) {
        final Map<String, UUID> justiceIdJudiciaryIdMap = rotaJudiciaryHelper.createJudiciaryMap(records, requester, executionId);
        logger.info("Created judiciary map with {} entries for blob: {}", justiceIdJudiciaryIdMap.size(), blobName);

        final Map<String, List<UUID>> courtListingProfileIdListOfCourscheduleIdMap =
                rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);
        logger.info("Created court schedule map with {} entries for blob: {}",
                courtListingProfileIdListOfCourscheduleIdMap.size(), blobName);

        final Map<String, List<UUID>> judiciaryIdListOfCourtScheduleIdMapFromRotaFeed =
                rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                        records, justiceIdJudiciaryIdMap, courtListingProfileIdListOfCourscheduleIdMap, requester, executionId);
        logger.info("Created judiciary court schedule map with {} entries for blob: {}",
                judiciaryIdListOfCourtScheduleIdMapFromRotaFeed.size(), blobName);

        return new ProcessingMaps(judiciaryIdListOfCourtScheduleIdMapFromRotaFeed);
    }

    /**
     * Queries the database for court schedule IDs by judiciary IDs.
     *
     * @param judiciaryCourtScheduleMapFromRotaFeed the map from rota feed
     * @param blobName                               the name of the blob file
     * @return map of judiciary IDs to court schedule IDs from database
     */
    private Map<String, List<UUID>> queryDatabaseForCourtScheduleIds(
            final Map<String, List<UUID>> judiciaryCourtScheduleMapFromRotaFeed,
            final String blobName) {
        if (judiciaryCourtScheduleMapFromRotaFeed.isEmpty()) {
            logger.debug("Skipping database query - no judiciary court schedule map entries for blob: {}", blobName);
            return Collections.emptyMap();
        }

        final Map<String, List<UUID>> judiciaryCourtScheduleIdsFromDb =
                courtScheduleJudiciaryQueryHelper.queryCourtScheduleIdsByJudiciaryIds(judiciaryCourtScheduleMapFromRotaFeed);
        logger.info("Queried court schedule IDs from database for {} judiciary IDs for blob: {}",
                judiciaryCourtScheduleIdsFromDb.size(), blobName);
        return judiciaryCourtScheduleIdsFromDb;
    }

    /**
     * Processes judiciary assignments and unassignments based on differences between rota feed and database.
     *
     * @param judiciaryCourtScheduleMapFromRotaFeed the map from rota feed
     * @param judiciaryCourtScheduleIdsFromDb        the map from database
     * @param requester                               the requester for making service calls
     * @param executionId                             the execution ID for logging
     * @param blobName                                the name of the blob file
     */
    private void processJudiciaryAssignmentsAndUnassignments(
            final Map<String, List<UUID>> judiciaryCourtScheduleMapFromRotaFeed,
            final Map<String, List<UUID>> judiciaryCourtScheduleIdsFromDb,
            final Requester requester,
            final String executionId,
            final String blobName) {
        final Map<String, List<UUID>> judiciaryAssignmentMap = mapComparator.findMissingCourtScheduleIdsInDB(
                judiciaryCourtScheduleMapFromRotaFeed, judiciaryCourtScheduleIdsFromDb);
        logger.info("Found missing court schedule IDs for {} judiciary IDs for blob: {}",
                judiciaryAssignmentMap.size(), blobName);

        final Map<String, List<UUID>> judiciaryUnAssignmentMap = mapComparator.findMissingCourtScheduleIdsInRotaFeed(
                judiciaryCourtScheduleIdsFromDb, judiciaryCourtScheduleMapFromRotaFeed);
        logger.info("Found court schedule IDs in database missing in rota feed for {} judiciary IDs for blob: {}",
                judiciaryUnAssignmentMap.size(), blobName);

        executeJudiciaryAssignments(judiciaryAssignmentMap, requester, executionId, blobName);
        executeJudiciaryUnassignments(judiciaryUnAssignmentMap, executionId, blobName);
    }

    /**
     * Executes judiciary assignments if there are any to process.
     *
     * @param judiciaryAssignmentMap the map of assignments to process
     * @param requester              the requester for making service calls
     * @param executionId            the execution ID for logging
     * @param blobName               the name of the blob file
     */
    private void executeJudiciaryAssignments(final Map<String, List<UUID>> judiciaryAssignmentMap,
                                             final Requester requester,
                                             final String executionId,
                                             final String blobName) {
        if (judiciaryAssignmentMap.isEmpty()) {
            logger.debug("Skipping judiciary assignment - no assignments to process for blob: {}", blobName);
            return;
        }

        final AssignJudiciariesResponse assignResponse = processJudiciaryAssignments(
                judiciaryAssignmentMap, requester, executionId);
        logger.info("Assigned judiciaries for blob: {} - requested: {}, successful: {}, failures: {}",
                blobName, assignResponse.getRequestedAssignments(), assignResponse.getSuccessfulAssignments(),
                assignResponse.getFailures().size());
    }

    /**
     * Executes judiciary unassignments if there are any to process.
     *
     * @param judiciaryUnAssignmentMap the map of unassignments to process
     * @param executionId              the execution ID for logging
     * @param blobName                 the name of the blob file
     */
    private void executeJudiciaryUnassignments(final Map<String, List<UUID>> judiciaryUnAssignmentMap,
                                               final String executionId,
                                               final String blobName) {
        if (judiciaryUnAssignmentMap.isEmpty()) {
            logger.debug("Skipping judiciary unassignment - no unassignments to process for blob: {}", blobName);
            return;
        }

        processJudiciaryUnassignments(judiciaryUnAssignmentMap, executionId);
        logger.info("Unassigned judiciaries for blob: {} - processed {} judiciary IDs",
                blobName, judiciaryUnAssignmentMap.size());
    }

    /**
     * Updates the file process history with end date if history exists.
     *
     * @param rotaFileProcessHistory the file process history to update
     * @param blobName               the name of the blob file
     */
    private void updateFileProcessHistory(final RotaFileProcessHistory rotaFileProcessHistory, final String blobName) {
        if (rotaFileProcessHistory != null) {
            rotaFileProcessHistoryService.update(rotaFileProcessHistory);
            logger.info("Updated file process history with end date for blob: {}", blobName);
        }
    }

    /**
     * Logs the processing time for a blob.
     *
     * @param blobName     the name of the blob file
     * @param processStart the start time in nanoseconds
     * @param processEnd   the end time in nanoseconds
     */
    private void logProcessingTime(final String blobName, final long processStart, final long processEnd) {
        logger.info("{}Processing and parsing completed for blob {} in {} ms",
                LOG_PREFIX_PRF, blobName, rotaFileUtility.convertNanosToMillis(processEnd - processStart));
    }

    private void uploadAndCleanup(final byte[] blobByteArray, final String blobName, final String leaseId) {
        final long uploadStart = System.nanoTime();
        final long fileLength = blobByteArray.length;
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(blobByteArray), fileLength, blobName, empty());
        final long uploadEnd = System.nanoTime();
        logger.info("{}Upload completed for blob {} in {} ms", LOG_PREFIX_PRF, blobName,
                rotaFileUtility.convertNanosToMillis(uploadEnd - uploadStart));

        azureBlobClientService.releaseLease(blobName, leaseId, false);
        azureBlobClientService.deleteFile(blobName, empty());
        logger.info("Blob {} processed and cleaned up", blobName);
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
     *
     * @param judiciaryAssignmentMap map of judiciary IDs to court schedule UUIDs
     * @param requester            the requester for making service calls
     * @param executionId          the execution ID for logging
     * @return the assignment response
     */
    private AssignJudiciariesResponse processJudiciaryAssignments(final Map<String, List<UUID>> judiciaryAssignmentMap,
                                                                  final Requester requester,
                                                                  final String executionId) {
        final var assignRequest = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentMap);
        return judiciaryAssignmentService.assignJudiciaries(assignRequest, requester, executionId);
    }

    /**
     * Processes judiciary unassignments by converting the map and calling the unassignment service.
     *
     * @param judiciaryUnAssignmentMap map of judiciary IDs to court schedule UUIDs
     * @param executionId             the execution ID for logging
     */
    private void processJudiciaryUnassignments(final Map<String, List<UUID>> judiciaryUnAssignmentMap,
                                               final String executionId) {
        final Map<String, List<String>> unassignMap = judiciaryAssignmentRequestHelper.convertToUnassignmentMap(judiciaryUnAssignmentMap);
        judiciaryUnassignmentService.unassignJudiciary(unassignMap, executionId, true);
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
     *
     * @param judiciaryCourtScheduleMapFromRotaFeed the map of judiciary IDs to court schedule IDs from rota feed
     */
    private record ProcessingMaps(Map<String, List<UUID>> judiciaryCourtScheduleMapFromRotaFeed) {
    }
}
