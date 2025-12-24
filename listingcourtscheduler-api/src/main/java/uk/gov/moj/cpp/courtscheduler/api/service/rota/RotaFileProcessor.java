package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.util.Optional.empty;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.CourtScheduleJudiciaryQueryHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryAssignmentRequestHelper;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleData;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        final Map<String, List<UUID>> rotaFeedMapForQuery = extractCourtScheduleIdsMap(
                processingMaps.judiciaryCourtScheduleMapFromRotaFeed());
        final Map<String, List<UUID>> judiciaryCourtScheduleIdsFromDb = queryDatabaseForCourtScheduleIds(
                rotaFeedMapForQuery, blobName);

        processJudiciaryAssignmentsAndUnassignments(
                processingMaps.judiciaryCourtScheduleMapFromRotaFeed(),
                rotaFeedMapForQuery,
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
                                                 final Requester requester,
                                                 final String executionId,
                                                 final String blobName) {
        final Map<String, UUID> justiceIdJudiciaryIdMap = rotaJudiciaryHelper.createJudiciaryMap(records, requester, executionId);
        logger.info("Created judiciary map with {} entries for blob: {}", justiceIdJudiciaryIdMap.size(), blobName);

        final Map<String, Set<UUID>> courtListingProfileIdListOfCourscheduleIdMap =
                rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);
        logger.info("Created court schedule map with {} entries for blob: {}",
                courtListingProfileIdListOfCourscheduleIdMap.size(), blobName);

        final Map<String, JudiciaryCourtScheduleData> judiciaryIdListOfCourtScheduleIdMapFromRotaFeed =
                rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                        records, justiceIdJudiciaryIdMap, courtListingProfileIdListOfCourscheduleIdMap, requester, executionId);
        logger.info("Created judiciary court schedule map with {} entries for blob: {}",
                judiciaryIdListOfCourtScheduleIdMapFromRotaFeed.size(), blobName);

        return new ProcessingMaps(judiciaryIdListOfCourtScheduleIdMapFromRotaFeed);
    }

    /**
     * Extracts court schedule IDs map from the judiciary court schedule data map.
     * This creates a simplified map containing only the court schedule IDs, which is used
     * for database queries and comparison operations.
     *
     * @param judiciaryCourtScheduleDataMap the map containing JudiciaryCourtScheduleData records
     *                                      with full assignment metadata (position, isBenchChairman, isDeputy)
     * @return map of judiciary IDs to lists of court schedule UUIDs (metadata is excluded)
     */
    private Map<String, List<UUID>> extractCourtScheduleIdsMap(
            final Map<String, JudiciaryCourtScheduleData> judiciaryCourtScheduleDataMap) {
        return judiciaryCourtScheduleDataMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().courtScheduleIds()
                ));
    }

    /**
     * Queries the database for court schedule IDs by judiciary IDs.
     * This method uses only the court schedule IDs (not the full assignment metadata)
     * to query existing assignments in the database.
     *
     * @param judiciaryCourtScheduleMapFromRotaFeed the map from rota feed containing
     *                                               judiciary IDs to lists of court schedule UUIDs
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
     * Compares the rota feed data with database records to identify:
     * - Assignments: court schedules in rota feed but not in database
     * - Unassignments: court schedules in database but not in rota feed
     *
     * @param judiciaryCourtScheduleDataMapFromRotaFeed the map from rota feed with full assignment data
     *                                                   including position, isBenchChairman, and isDeputy
     * @param rotaFeedMapForQuery the map from rota feed with just court schedule IDs for comparison
     * @param judiciaryCourtScheduleIdsFromDb the map from database containing existing assignments
     * @param requester the requester for making service calls
     * @param executionId the execution ID for logging
     * @param blobName the name of the blob file
     */
    private void processJudiciaryAssignmentsAndUnassignments(
            final Map<String, JudiciaryCourtScheduleData> judiciaryCourtScheduleDataMapFromRotaFeed,
            final Map<String, List<UUID>> rotaFeedMapForQuery,
            final Map<String, List<UUID>> judiciaryCourtScheduleIdsFromDb,
            final Requester requester,
            final String executionId,
            final String blobName) {
        final Map<String, List<UUID>> judiciaryAssignmentIdsMap = mapComparator.findMissingCourtScheduleIdsInDB(
                rotaFeedMapForQuery, judiciaryCourtScheduleIdsFromDb);
        logger.info("Found missing court schedule IDs for {} judiciary IDs for blob: {}",
                judiciaryAssignmentIdsMap.size(), blobName);

        final Map<String, List<UUID>> judiciaryUnAssignmentMap = mapComparator.findMissingCourtScheduleIdsInRotaFeed(
                judiciaryCourtScheduleIdsFromDb, rotaFeedMapForQuery);
        logger.info("Found court schedule IDs in database missing in rota feed for {} judiciary IDs for blob: {}",
                judiciaryUnAssignmentMap.size(), blobName);

        final Map<String, JudiciaryCourtScheduleData> judiciaryAssignmentDataMap =
                buildAssignmentDataMap(judiciaryAssignmentIdsMap, judiciaryCourtScheduleDataMapFromRotaFeed);

        executeJudiciaryUnassignments(judiciaryUnAssignmentMap, executionId, blobName);
        executeJudiciaryAssignments(judiciaryAssignmentDataMap, requester, executionId, blobName);
    }

    /**
     * Builds assignment data map by filtering schedule IDs from the original data map.
     * Only includes the schedule IDs that need to be assigned, while preserving
     * the assignment metadata (position, isBenchChairman, isDeputy) from the original data.
     *
     * @param judiciaryAssignmentIdsMap map of judiciary IDs to schedule IDs that need assignment
     * @param judiciaryCourtScheduleDataMapFromRotaFeed the original data map with all schedule information
     *                                                  including assignment metadata
     * @return filtered assignment data map containing only the schedules to be assigned,
     *         with metadata preserved from the original data
     */
    private Map<String, JudiciaryCourtScheduleData> buildAssignmentDataMap(
            final Map<String, List<UUID>> judiciaryAssignmentIdsMap,
            final Map<String, JudiciaryCourtScheduleData> judiciaryCourtScheduleDataMapFromRotaFeed) {
        return judiciaryAssignmentIdsMap.entrySet().stream()
                .filter(entry -> judiciaryCourtScheduleDataMapFromRotaFeed.containsKey(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> filterScheduleData(
                                judiciaryCourtScheduleDataMapFromRotaFeed.get(entry.getKey()),
                                entry.getValue())
                ));
    }

    /**
     * Filters schedule data to only include the schedule IDs that need to be assigned.
     * Preserves all assignment metadata (position, isBenchChairman, isDeputy) from the original data.
     *
     * @param originalData the original schedule data containing all court schedule IDs and metadata
     * @param scheduleIdsToAssign the schedule IDs that need to be assigned (subset of original IDs)
     * @return filtered JudiciaryCourtScheduleData containing only the specified schedule IDs
     *         with all original metadata preserved
     */
    private JudiciaryCourtScheduleData filterScheduleData(
            final JudiciaryCourtScheduleData originalData,
            final List<UUID> scheduleIdsToAssign) {
        final List<UUID> filteredScheduleIds = originalData.courtScheduleIds().stream()
                .filter(scheduleIdsToAssign::contains)
                .collect(Collectors.toList());
        return new JudiciaryCourtScheduleData(
                filteredScheduleIds,
                originalData.position(),
                originalData.isBenchChairman(),
                originalData.isDeputy()
        );
    }

    /**
     * Executes judiciary assignments if there are any to process.
     * The assignment data includes court schedule IDs along with assignment metadata
     * (position, isBenchChairman, isDeputy) which will be persisted to the database.
     *
     * @param judiciaryAssignmentDataMap the map of assignments to process, containing
     *                                   court schedule IDs and assignment metadata
     * @param requester                  the requester for making service calls
     * @param executionId                the execution ID for logging
     * @param blobName                   the name of the blob file
     */
    private void executeJudiciaryAssignments(
            final Map<String, JudiciaryCourtScheduleData> judiciaryAssignmentDataMap,
            final Requester requester,
            final String executionId,
            final String blobName) {
        if (judiciaryAssignmentDataMap.isEmpty()) {
            logger.debug("Skipping judiciary assignment - no assignments to process for blob: {}", blobName);
            return;
        }

        final AssignJudiciariesResponse assignResponse = processJudiciaryAssignments(
                judiciaryAssignmentDataMap, requester, executionId);
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
     * The assignment data includes court schedule IDs and assignment metadata (position,
     * isBenchChairman, isDeputy) which are included in the assignment request.
     *
     * @param judiciaryAssignmentDataMap map of judiciary IDs to JudiciaryCourtScheduleData
     *                                   containing court schedule UUIDs and assignment metadata
     * @param requester                  the requester for making service calls
     * @param executionId                the execution ID for logging
     * @return the assignment response containing success/failure information
     */
    private AssignJudiciariesResponse processJudiciaryAssignments(
            final Map<String, JudiciaryCourtScheduleData> judiciaryAssignmentDataMap,
            final Requester requester,
            final String executionId) {
        final var assignRequest = judiciaryAssignmentRequestHelper.buildAssignJudiciariesRequest(judiciaryAssignmentDataMap);
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
     * Contains the judiciary court schedule map with full assignment data including
     * court schedule IDs, position, isBenchChairman, and isDeputy.
     *
     * @param judiciaryCourtScheduleMapFromRotaFeed the map of judiciary IDs to JudiciaryCourtScheduleData
     *                                               from rota feed, containing court schedule IDs
     *                                               and assignment metadata (position, isBenchChairman, isDeputy)
     */
    private record ProcessingMaps(Map<String, JudiciaryCourtScheduleData> judiciaryCourtScheduleMapFromRotaFeed) {
    }
}
