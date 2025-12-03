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

    private void processBlob(final String blobName, final byte[] blobByteArray, final Requester requester) {
        final long processStart = System.nanoTime();
        final ParseResult parseResult = parseFileContent(blobName, blobByteArray);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseResult.records();
        final String executionId = parseResult.executionId();
        final long processEnd = System.nanoTime();

        logger.info("PRF: Processing and parsing completed for blob {} in {} ms",
                blobName, rotaFileUtility.convertNanosToMillis(processEnd - processStart));
        logger.info("rota file parsed successfully for blob with name: {} - parsed {} record types",
                blobName, records.size());

        final Map<String, UUID> justiceIdJudiciaryIdMap = rotaJudiciaryHelper.createJudiciaryMap(records, requester, executionId);
        logger.info("Created judiciary map with {} entries for blob: {}", justiceIdJudiciaryIdMap.size(), blobName);

        final Map<String, List<UUID>> courtListingProfileIdListOfCourscheduleIdMap = rotaCourtScheduleHelper.createCourtScheduleMap(records, requester, executionId);
        logger.info("Created court schedule map with {} entries for blob: {}", courtListingProfileIdListOfCourscheduleIdMap.size(), blobName);

        final Map<String, List<UUID>> judiciaryIdListOfCourtScheduleIdMapFromRotaFeed = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, justiceIdJudiciaryIdMap, courtListingProfileIdListOfCourscheduleIdMap, requester, executionId);
        logger.info("Created judiciary court schedule map with {} entries for blob: {}",
                judiciaryIdListOfCourtScheduleIdMapFromRotaFeed.size(), blobName);

        final Map<String, List<UUID>> judiciaryCourtScheduleIdsFromDb;
        if (judiciaryIdListOfCourtScheduleIdMapFromRotaFeed.isEmpty()) {
            logger.debug("Skipping database query - no judiciary court schedule map entries for blob: {}", blobName);
            judiciaryCourtScheduleIdsFromDb = Collections.emptyMap();
        } else {
            judiciaryCourtScheduleIdsFromDb = courtScheduleJudiciaryQueryHelper
                    .queryCourtScheduleIdsByJudiciaryIds(judiciaryIdListOfCourtScheduleIdMapFromRotaFeed);
            logger.info("Queried court schedule IDs from database for {} judiciary IDs for blob: {}",
                    judiciaryCourtScheduleIdsFromDb.size(), blobName);
        }

        final Map<String, List<UUID>> judiciaryAssignmentMap = mapComparator.findMissingCourtScheduleIdsInDB(
                judiciaryIdListOfCourtScheduleIdMapFromRotaFeed, judiciaryCourtScheduleIdsFromDb);
        logger.info("Found missing court schedule IDs for {} judiciary IDs for blob: {}",
                judiciaryAssignmentMap.size(), blobName);

        final Map<String, List<UUID>> judiciaryUnAssignmentMap = mapComparator.findMissingCourtScheduleIdsInRotaFeed(
                judiciaryCourtScheduleIdsFromDb, judiciaryIdListOfCourtScheduleIdMapFromRotaFeed);
        logger.info("Found court schedule IDs in database missing in rota feed for {} judiciary IDs for blob: {}",
                judiciaryUnAssignmentMap.size(), blobName);

        if (!judiciaryAssignmentMap.isEmpty()) {
            final AssignJudiciariesResponse assignResponse = processJudiciaryAssignments(
                    judiciaryAssignmentMap, requester, executionId);
            logger.info("Assigned judiciaries for blob: {} - requested: {}, successful: {}, failures: {}",
                    blobName, assignResponse.getRequestedAssignments(), assignResponse.getSuccessfulAssignments(),
                    assignResponse.getFailures().size());
        } else {
            logger.debug("Skipping judiciary assignment - no assignments to process for blob: {}", blobName);
        }

        if (!judiciaryUnAssignmentMap.isEmpty()) {
            processJudiciaryUnassignments(judiciaryUnAssignmentMap, executionId);
            logger.info("Unassigned judiciaries for blob: {} - processed {} judiciary IDs",
                    blobName, judiciaryUnAssignmentMap.size());
        } else {
            logger.debug("Skipping judiciary unassignment - no unassignments to process for blob: {}", blobName);
        }
    }

    private void uploadAndCleanup(final byte[] blobByteArray, final String blobName, final String leaseId) {
        final long uploadStart = System.nanoTime();
        final long fileLength = blobByteArray.length;
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(blobByteArray), fileLength, blobName, empty());
        final long uploadEnd = System.nanoTime();
        logger.info("PRF: Upload completed for blob {} in {} ms", blobName, rotaFileUtility.convertNanosToMillis(uploadEnd - uploadStart));

        azureBlobClientService.releaseLease(blobName, leaseId, false);
        azureBlobClientService.deleteFile(blobName, empty());
        logger.info("Blob {} processed and cleaned up", blobName);
    }

    // ============================================================================
    // PRIVATE PROCESSING METHODS - File Parsing
    // ============================================================================

    /**
     * Processes the rota file and parses it, returning the parsed records and execution ID.
     * Handles snapshot file validation, execution ID generation, and file parsing.
     *
     * @param fileName the name of the file being processed
     * @param content  the byte content of the file
     * @return ParseResult containing the parsed records and execution ID
     */
    private ParseResult parseFileContent(final String fileName, final byte[] content) {
        final String executionId = rotaFileUtility.processSnapshotFileIfNeeded(fileName, content, rotaFileProcessHistoryService);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseFile(fileName, content);
        return new ParseResult(records, executionId);
    }

    private Map<RotaPayload, Map<String, Map<String, String>>> parseFile(final String fileName, final byte[] content) {
        final long parsingStartTime = System.nanoTime();
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(fileName, content);
        final long parsingEndTime = System.nanoTime();

        logger.info("PRF: Parsed file {} in {} ms", fileName, rotaFileUtility.convertNanosToMillis(parsingEndTime - parsingStartTime));
        logger.info("File parsed successfully for file: {}", fileName);

        if (rotaFileUtility.isDummyFile(fileName)) {
            logger.warn("Dummy support file detected: {}", fileName);
        }

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
        judiciaryUnassignmentService.unassignJudiciary(unassignMap, executionId);
    }

    // ============================================================================
    // RECORDS/INNER CLASSES
    // ============================================================================

    public record ParseResult(Map<RotaPayload, Map<String, Map<String, String>>> records,
                              String executionId) {
    }
}
